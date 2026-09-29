import { PLATFORM_ID, Service, computed, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import {
  MedicalRecord,
  MedicalRecordDetail,
  NewMedicalRecord,
  Treatment,
  TreatmentStatus,
} from '../models/health';
import { HealthApi } from './health-api';
import { LoadState } from './animal-store';

/** Medical records of the animal on screen, with each record's treatments loaded on demand. */
@Service()
export class HealthStore {
  private readonly api = inject(HealthApi);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  private readonly recordMap = signal<ReadonlyMap<string, MedicalRecord>>(new Map());
  private readonly detailMap = signal<ReadonlyMap<string, MedicalRecordDetail>>(new Map());
  private readonly detailStates = signal<ReadonlyMap<string, LoadState>>(new Map());

  readonly animalId = signal<string | null>(null);
  readonly state = signal<LoadState>('idle');
  readonly records = computed(() => [...this.recordMap().values()]);

  async load(animalId: string): Promise<void> {
    if (!this.isBrowser) {
      return;
    }
    if (this.animalId() === animalId && (this.state() === 'loading' || this.state() === 'ready')) {
      return;
    }
    // Another animal: drop everything, so its records never show under this one.
    if (this.animalId() !== animalId) {
      this.animalId.set(animalId);
      this.recordMap.set(new Map());
      this.detailMap.set(new Map());
      this.detailStates.set(new Map());
    }
    this.state.set('loading');
    try {
      const records = await this.api.listRecords(animalId);
      // A newer load for another animal has started: this response is stale.
      if (this.animalId() !== animalId) {
        return;
      }
      this.recordMap.set(new Map(records.map((r) => [r.id, r])));
      this.state.set('ready');
    } catch {
      if (this.animalId() === animalId) {
        this.state.set('error');
      }
    }
  }

  retry(): Promise<void> {
    const current = this.animalId();
    if (!current) {
      return Promise.resolve();
    }
    this.state.set('idle');
    return this.load(current);
  }

  detail(recordId: string): MedicalRecordDetail | undefined {
    return this.detailMap().get(recordId);
  }

  detailState(recordId: string): LoadState {
    return this.detailStates().get(recordId) ?? 'idle';
  }

  async loadDetail(recordId: string): Promise<void> {
    if (!this.isBrowser) {
      return;
    }
    const state = this.detailState(recordId);
    if (state === 'loading' || state === 'ready') {
      return;
    }
    this.detailStates.update((m) => new Map(m).set(recordId, 'loading'));
    try {
      const detail = await this.api.getRecord(recordId);
      if (this.animalId() !== detail.animalId) {
        return;
      }
      this.detailMap.update((m) => new Map(m).set(recordId, detail));
      this.detailStates.update((m) => new Map(m).set(recordId, 'ready'));
    } catch {
      this.detailStates.update((m) => new Map(m).set(recordId, 'error'));
    }
  }

  async createRecord(input: NewMedicalRecord): Promise<MedicalRecord> {
    const record = await this.api.createRecord(input);
    // The user may have opened another animal while the request was in flight.
    if (record.animalId !== this.animalId()) {
      return record;
    }
    this.applyRecord(record);
    // Mark its detail as ready with no treatments
    this.detailMap.update((m) => new Map(m).set(record.id, { ...record, treatments: [] }));
    this.detailStates.update((m) => new Map(m).set(record.id, 'ready'));
    return record;
  }

  async prescribe(recordId: string, description: string): Promise<Treatment> {
    const treatment = await this.api.prescribe(recordId, description);
    // Append to the record's detail if it's loaded
    this.detailMap.update((m) => {
      const detail = m.get(recordId);
      if (!detail) {
        return m;
      }
      return new Map(m).set(recordId, {
        ...detail,
        treatments: [...detail.treatments, treatment],
      });
    });
    return treatment;
  }

  async updateTreatmentStatus(
    recordId: string,
    treatmentId: string,
    status: TreatmentStatus,
  ): Promise<Treatment> {
    const treatment = await this.api.updateTreatmentStatus(treatmentId, status);
    // Replace in the record's detail if it's loaded
    this.detailMap.update((m) => {
      const detail = m.get(recordId);
      if (!detail) {
        return m;
      }
      return new Map(m).set(recordId, {
        ...detail,
        treatments: detail.treatments.map((t) => (t.id === treatmentId ? treatment : t)),
      });
    });
    return treatment;
  }

  private applyRecord(record: MedicalRecord): void {
    this.recordMap.update((map) => {
      const newMap = new Map(map);
      newMap.set(record.id, record);
      // Re-sort by examinedOn descending, then id ascending
      const sorted = [...newMap.values()].sort((a, b) => {
        if (a.examinedOn !== b.examinedOn) {
          return b.examinedOn.localeCompare(a.examinedOn);
        }
        return a.id.localeCompare(b.id);
      });
      return new Map(sorted.map((r) => [r.id, r]));
    });
  }
}
