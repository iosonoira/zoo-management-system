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
import { HealthApi } from './health-api';
import {
  forbidden,
  invalidRecord,
  invalidTreatment,
  recordNotFound,
  treatmentChanged,
  treatmentNotFound,
} from './api-errors';
import { DEMO_MEDICAL_RECORDS, DEMO_TREATMENTS } from './demo-health';

const LATENCY_MS = 380;

/**
 * In-memory adapter that enforces the same rules and status codes as health-service:
 * role checks (403), unknown ids (404), invalid data (400), invalid treatment
 * transitions (422), and invalid dates (400). Mirrors backend request validation:
 * - `reason` ≤200, non-empty, trimmed
 * - `diagnosis` ≤1000, non-empty, trimmed
 * - `veterinarian` ≤100, non-empty, trimmed
 * - `examinedOn` matches `YYYY-MM-DD` and is not after today (local date)
 * - `description` ≤500, non-empty, trimmed
 */
export class MockHealthApi extends HealthApi {
  private readonly session = inject(Session);
  private records = new Map<string, MedicalRecord>(DEMO_MEDICAL_RECORDS.map((r) => [r.id, r]));
  private treatments = new Map<string, Treatment>(DEMO_TREATMENTS.map((t) => [t.id, t]));

  async listRecords(animalId: string): Promise<MedicalRecord[]> {
    await delay();
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

    if (!this.records.has(recordId)) {
      throw recordNotFound();
    }

    const trimmedDesc = description.trim();
    if (!trimmedDesc || trimmedDesc.length > 500) {
      throw invalidTreatment();
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

    const treatment = this.treatments.get(id);
    if (!treatment) {
      throw treatmentNotFound();
    }

    if (!canTreatmentTransitionTo(treatment, status)) {
      throw treatmentChanged();
    }

    const today = this.today();
    const updated: Treatment = {
      ...treatment,
      status,
      startedOn: status === 'ACTIVE' && !treatment.startedOn ? today : treatment.startedOn,
      endedOn:
        (status === 'COMPLETED' || status === 'CANCELLED') && !treatment.endedOn
          ? today
          : treatment.endedOn,
      updatedBy: this.session.username(),
    };

    this.treatments.set(id, updated);

    return updated;
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
