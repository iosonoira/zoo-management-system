import { inject } from '@angular/core';
import {
  MedicalRecord,
  MedicalRecordDetail,
  NewMedicalRecord,
  Treatment,
  TreatmentStatus,
  canTreatmentTransitionTo,
} from '../models/health';
import { can } from '../models/permissions';
import { Session } from '../session/session';
import { AnimalApi } from './animal-api';
import { HealthApi } from './health-api';
import {
  deceasedTreatment,
  forbidden,
  invalidRecord,
  invalidTreatment,
  recordNotFound,
  treatmentChanged,
  treatmentNotFound,
  treatmentNotStartable,
} from './api-errors';
import { DEMO_MEDICAL_RECORDS, DEMO_TREATMENTS } from './demo-health';

const LATENCY_MS = 380;

/**
 * In-memory adapter that enforces the same rules and status codes as health-service:
 * role checks (403), unknown ids (404), invalid data (400), invalid treatment
 * transitions (422), invalid dates (400), and deceased animals (422).
 * Mirrors backend request validation:
 * - `reason` ≤200, non-empty, trimmed
 * - `diagnosis` ≤1000, non-empty, trimmed
 * - `veterinarian` ≤100, non-empty, trimmed
 * - `examinedOn` matches `YYYY-MM-DD` and is not after today (local date)
 * - `description` ≤500, non-empty, trimmed
 *
 * Deceased animals: the mock imitates the Kafka consumer that the live health-service uses.
 * When an animal becomes DECEASED, its PRESCRIBED and ACTIVE treatments are moved to CANCELLED
 * with `endedOn = today` (but never before `startedOn`). Creating medical records stays allowed.
 */
export class MockHealthApi extends HealthApi {
  private readonly session = inject(Session);
  private readonly animals = inject(AnimalApi);
  private records = new Map<string, MedicalRecord>(DEMO_MEDICAL_RECORDS.map((r) => [r.id, r]));
  private treatments = new Map<string, Treatment>(DEMO_TREATMENTS.map((t) => [t.id, t]));

  async listRecords(animalId: string): Promise<MedicalRecord[]> {
    await delay();
    await this.settleDeceased(animalId);
    const records = [...this.records.values()].filter((r) => r.animalId === animalId);
    // Sort by examinedOn descending, then id ascending
    return records.sort((a, b) => {
      if (a.examinedOn !== b.examinedOn) {
        return b.examinedOn.localeCompare(a.examinedOn);
      }
      return a.id.localeCompare(b.id);
    });
  }

  async getRecord(id: string): Promise<MedicalRecordDetail> {
    await delay();
    const record = this.records.get(id);
    if (!record) {
      throw recordNotFound();
    }
    await this.settleDeceased(record.animalId);
    const treatments = [...this.treatments.values()].filter((t) => t.medicalRecordId === id);
    return { ...record, treatments };
  }

  async createRecord(input: NewMedicalRecord): Promise<MedicalRecord> {
    await delay();
    if (!can(this.session.role(), 'createMedicalRecord')) {
      throw forbidden('createMedicalRecord');
    }

    const reason = input.reason.trim();
    const diagnosis = input.diagnosis.trim();
    const veterinarian = input.veterinarian.trim();

    if (
      !reason ||
      reason.length > 200 ||
      !diagnosis ||
      diagnosis.length > 1000 ||
      !veterinarian ||
      veterinarian.length > 100
    ) {
      throw invalidRecord();
    }

    // Validate date format YYYY-MM-DD
    if (!/^\d{4}-\d{2}-\d{2}$/.test(input.examinedOn)) {
      throw invalidRecord();
    }

    // Check that date is not after today (local)
    const today = this.today();
    if (input.examinedOn > today) {
      throw invalidRecord();
    }

    const record: MedicalRecord = {
      ...input,
      reason,
      diagnosis,
      veterinarian,
      id: crypto.randomUUID(),
      createdBy: this.session.username(),
      updatedBy: this.session.username(),
    };

    this.records.set(record.id, record);
    return record;
  }

  async prescribe(recordId: string, description: string): Promise<Treatment> {
    await delay();
    if (!can(this.session.role(), 'prescribeTreatment')) {
      throw forbidden('prescribeTreatment');
    }

    const record = this.records.get(recordId);
    if (!record) {
      throw recordNotFound();
    }

    const trimmedDesc = description.trim();
    if (!trimmedDesc || trimmedDesc.length > 500) {
      throw invalidTreatment();
    }

    if (await this.settleDeceased(record.animalId)) {
      throw deceasedTreatment();
    }

    const treatment: Treatment = {
      id: crypto.randomUUID(),
      medicalRecordId: recordId,
      description: trimmedDesc,
      status: 'PRESCRIBED',
      startedOn: null,
      endedOn: null,
      createdBy: this.session.username(),
      updatedBy: this.session.username(),
    };

    this.treatments.set(treatment.id, treatment);

    return treatment;
  }

  async updateTreatmentStatus(id: string, status: TreatmentStatus): Promise<Treatment> {
    await delay();
    if (!can(this.session.role(), 'updateTreatmentStatus')) {
      throw forbidden('updateTreatmentStatus');
    }

    const found = this.treatments.get(id);
    if (!found) {
      throw treatmentNotFound();
    }

    // Like health-service, which settles a death before this change: an open treatment of a
    // deceased animal is already CANCELLED here, so starting or completing it is rejected.
    const record = this.records.get(found.medicalRecordId);
    const deceased = record ? await this.settleDeceased(record.animalId) : false;
    const currentTreatment = this.treatments.get(id) ?? found;

    if (!canTreatmentTransitionTo(currentTreatment, status)) {
      // The same copy HttpHealthApi shows for a 422 on these two targets.
      throw status === 'ACTIVE' ? treatmentNotStartable() : treatmentChanged();
    }
    if (status === 'ACTIVE' && deceased) {
      throw treatmentNotStartable();
    }

    const today = this.today();
    const updated: Treatment = {
      ...currentTreatment,
      status,
      startedOn: status === 'ACTIVE' && !currentTreatment.startedOn ? today : currentTreatment.startedOn,
      endedOn:
        (status === 'COMPLETED' || status === 'CANCELLED') && !currentTreatment.endedOn
          ? today
          : currentTreatment.endedOn,
      updatedBy: this.session.username(),
    };

    this.treatments.set(id, updated);

    return updated;
  }

  /**
   * Private helper: settles deceased animals by moving their open treatments to CANCELLED.
   * Returns true if the animal is deceased, false otherwise. When an animal dies, the
   * live health-service consumes that event from Kafka and cancels its treatments; this mock
   * does the same so demo and live modes show the same outcome.
   */
  private async settleDeceased(animalId: string): Promise<boolean> {
    try {
      const animal = await this.animals.getById(animalId);
      if (animal.status !== 'DECEASED') {
        return false;
      }

      const today = this.today();
      for (const treatment of this.treatments.values()) {
        if (
          this.records.get(treatment.medicalRecordId)?.animalId === animalId &&
          (treatment.status === 'PRESCRIBED' || treatment.status === 'ACTIVE')
        ) {
          const updated: Treatment = {
            ...treatment,
            status: 'CANCELLED',
            endedOn: !treatment.startedOn || today >= treatment.startedOn ? today : treatment.startedOn,
            updatedBy: animal.updatedBy ?? 'animal-service',
          };
          this.treatments.set(treatment.id, updated);
        }
      }

      return true;
    } catch {
      return false;
    }
  }

  private today(): string {
    const now = new Date();
    return (
      now.getFullYear() +
      '-' +
      String(now.getMonth() + 1).padStart(2, '0') +
      '-' +
      String(now.getDate()).padStart(2, '0')
    );
  }
}

function delay(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, LATENCY_MS));
}
