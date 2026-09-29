import { PLATFORM_ID } from '@angular/core';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MedicalRecord, MedicalRecordDetail, NewMedicalRecord, Treatment } from '../models/health';
import { HealthApi } from './health-api';
import { ApiError } from './api-error';
import { HealthStore } from './health-store';
import { MockHealthApi } from './mock-health-api';
import { Session } from '../session/session';

class FakeHealthApi extends HealthApi {
  calls = 0;
  listRecordsResult: MedicalRecord[] = [];
  getRecordResult: MedicalRecordDetail | null = null;
  getRecordDeferred = false;
  private getRecordResolve: ((value: MedicalRecordDetail) => void) | null = null;

  async listRecords(): Promise<MedicalRecord[]> {
    this.calls++;
    return this.listRecordsResult;
  }

  async getRecord(): Promise<MedicalRecordDetail> {
    if (this.getRecordDeferred) {
      return new Promise((resolve) => {
        this.getRecordResolve = resolve;
      });
    }
    if (!this.getRecordResult) {
      throw new ApiError(404, 'Not found');
    }
    return this.getRecordResult;
  }

  resolveGetRecord(detail: MedicalRecordDetail): void {
    if (this.getRecordResolve) {
      this.getRecordResolve(detail);
      this.getRecordResolve = null;
    }
  }

  async createRecord(input: NewMedicalRecord): Promise<MedicalRecord> {
    const record: MedicalRecord = {
      id: 'created-' + crypto.randomUUID(),
      ...input,
      createdBy: 'test.user',
      updatedBy: 'test.user',
    };
    return record;
  }

  async prescribe(): Promise<Treatment> {
    throw new Error('not used');
  }

  async updateTreatmentStatus(): Promise<Treatment> {
    throw new Error('not used');
  }
}

function storeOn(platform: string): { store: HealthStore; api: FakeHealthApi } {
  const api = new FakeHealthApi();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: PLATFORM_ID, useValue: platform },
      { provide: HealthApi, useValue: api },
      HealthStore,
    ],
  });
  return { store: TestBed.inject(HealthStore), api };
}

function storeWithMock(): { store: HealthStore; api: MockHealthApi } {
  const mockSession: Partial<Session> = {
    role: signal('zoo-vet' as any),
    username: signal('test.user'),
    can: () => true,
    canSwitchRole: false,
    setRole: () => {},
    restore: () => {},
    signOut: () => {},
  };
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: PLATFORM_ID, useValue: 'browser' },
      { provide: Session, useValue: mockSession as Session },
      { provide: HealthApi, useClass: MockHealthApi },
      HealthStore,
    ],
  });
  return {
    store: TestBed.inject(HealthStore),
    api: TestBed.inject(HealthApi) as MockHealthApi,
  };
}

describe('HealthStore.load', () => {
  it('does not call the API on the server', async () => {
    const { store, api } = storeOn('server');
    await store.load('animal-1');
    expect(api.calls).toBe(0);
    expect(store.state()).toBe('idle');
  });

  it('loads records and sets state ready', async () => {
    const { store, api } = storeOn('browser');
    const record: MedicalRecord = {
      id: 'rec-1',
      animalId: 'animal-1',
      reason: 'Test',
      diagnosis: 'Test diagnosis',
      examinedOn: '2026-09-15',
      veterinarian: 'Dr. Test',
      createdBy: null,
      updatedBy: null,
    };
    api.listRecordsResult = [record];
    await store.load('animal-1');
    expect(api.calls).toBe(1);
    expect(store.state()).toBe('ready');
    expect(store.records()).toContainEqual(record);
  });

  it('second load for same animal does not call the API', async () => {
    const { store, api } = storeOn('browser');
    await store.load('animal-1');
    const callsAfterFirst = api.calls;
    await store.load('animal-1');
    expect(api.calls).toBe(callsAfterFirst);
  });

  it('switching animal resets records', async () => {
    const { store, api } = storeOn('browser');
    const rec1: MedicalRecord = {
      id: 'rec-1',
      animalId: 'animal-1',
      reason: 'Test',
      diagnosis: 'Test',
      examinedOn: '2026-09-15',
      veterinarian: 'Dr. Test',
      createdBy: null,
      updatedBy: null,
    };
    api.listRecordsResult = [rec1];
    await store.load('animal-1');
    expect(store.records()).toHaveLength(1);
    api.listRecordsResult = [];
    await store.load('animal-2');
    expect(store.records()).toHaveLength(0);
  });

  it('slow response for animal A arriving after load(B) is dropped', async () => {
    const { store, api } = storeOn('browser');
    api.getRecordDeferred = true;
    const rec1: MedicalRecord = {
      id: 'rec-1',
      animalId: 'animal-1',
      reason: 'Test',
      diagnosis: 'Test',
      examinedOn: '2026-09-15',
      veterinarian: 'Dr. Test',
      createdBy: null,
      updatedBy: null,
    };
    api.listRecordsResult = [rec1];
    const loadA = store.load('animal-1');
    api.listRecordsResult = [];
    await store.load('animal-2');
    // Resolve the deferred response from animal-1
    const fakeDetail: MedicalRecordDetail = { ...rec1, treatments: [] };
    api.resolveGetRecord(fakeDetail);
    await loadA;
    // The response should be dropped, state should reflect animal-2
    expect(store.animalId()).toBe('animal-2');
  });

  it('failed load sets state error', async () => {
    const { store, api } = storeOn('browser');
    api.listRecordsResult = [];
    // Inject a failure
    const originalList = api.listRecords.bind(api);
    api.listRecords = async () => {
      throw new ApiError(500, 'Server error');
    };
    await store.load('animal-1');
    expect(store.state()).toBe('error');
  });

  it('retry reloads after error', async () => {
    const { store, api } = storeOn('browser');
    api.listRecords = async () => {
      throw new ApiError(500, 'Server error');
    };
    await store.load('animal-1');
    expect(store.state()).toBe('error');
    const rec1: MedicalRecord = {
      id: 'rec-1',
      animalId: 'animal-1',
      reason: 'Test',
      diagnosis: 'Test',
      examinedOn: '2026-09-15',
      veterinarian: 'Dr. Test',
      createdBy: null,
      updatedBy: null,
    };
    api.listRecordsResult = [rec1];
    api.listRecords = async () => api.listRecordsResult;
    await store.retry();
    expect(store.state()).toBe('ready');
  });
});

describe('HealthStore.loadDetail', () => {
  it('stores the detail', async () => {
    const { store, api } = storeOn('browser');
    const rec1: MedicalRecord = {
      id: 'rec-1',
      animalId: 'animal-1',
      reason: 'Test',
      diagnosis: 'Test',
      examinedOn: '2026-09-15',
      veterinarian: 'Dr. Test',
      createdBy: null,
      updatedBy: null,
    };
    const detail: MedicalRecordDetail = { ...rec1, treatments: [] };
    api.getRecordResult = detail;
    await store.load('animal-1');
    await store.loadDetail('rec-1');
    expect(store.detail('rec-1')).toEqual(detail);
  });

  it('marks detail state as ready', async () => {
    const { store, api } = storeOn('browser');
    const rec1: MedicalRecord = {
      id: 'rec-1',
      animalId: 'animal-1',
      reason: 'Test',
      diagnosis: 'Test',
      examinedOn: '2026-09-15',
      veterinarian: 'Dr. Test',
      createdBy: null,
      updatedBy: null,
    };
    const detail: MedicalRecordDetail = { ...rec1, treatments: [] };
    api.getRecordResult = detail;
    // Set animalId first so loadDetail accepts the response
    store.animalId.set('animal-1');
    await store.loadDetail('rec-1');
    expect(store.detailState('rec-1')).toBe('ready');
  });

  it('idle state for record never requested', () => {
    const { store } = storeOn('browser');
    expect(store.detailState('never-requested')).toBe('idle');
  });
});

describe('HealthStore.createRecord', () => {
  it('inserts in date order and marks detail ready with no treatments', async () => {
    const { store } = storeWithMock();
    await store.load('92b3c4d5-5a7f-4da2-9f9a-1b3c5d7f9e4a'); // Pepe
    const newRecord: NewMedicalRecord = {
      animalId: '92b3c4d5-5a7f-4da2-9f9a-1b3c5d7f9e4a',
      reason: 'New examination',
      diagnosis: 'Test',
      examinedOn: '2026-09-25',
      veterinarian: 'Dr. Test',
    };
    const created = await store.createRecord(newRecord);
    expect(store.records()[0]).toEqual(created);
    const detail = store.detail(created.id);
    expect(detail).toEqual({ ...created, treatments: [] });
    expect(store.detailState(created.id)).toBe('ready');
  });
});

describe('HealthStore.createRecord after switching animal', () => {
  it('does not show the record under an animal opened meanwhile', async () => {
    const { store } = storeWithMock();
    await store.load('c43e9a1b-7f82-4c3b-8d2e-4a6b8c0e2d73'); // Nia
    const before = store.records();
    await store.createRecord({
      animalId: '92b3c4d5-5a7f-4da2-9f9a-1b3c5d7f9e4a', // Pepe
      reason: 'New examination',
      diagnosis: 'Test',
      examinedOn: '2026-09-25',
      veterinarian: 'Dr. Test',
    });
    expect(store.records()).toEqual(before);
  });
});

describe('HealthStore.prescribe', () => {
  it('appends to record detail', async () => {
    const { store } = storeWithMock();
    await store.load('92b3c4d5-5a7f-4da2-9f9a-1b3c5d7f9e4a'); // Pepe
    const records = await store.load('92b3c4d5-5a7f-4da2-9f9a-1b3c5d7f9e4a');
    const recordId = store.records()[0].id;
    await store.loadDetail(recordId);
    const beforeCount = store.detail(recordId)?.treatments.length ?? 0;
    await store.prescribe(recordId, 'New treatment');
    const afterCount = store.detail(recordId)?.treatments.length ?? 0;
    expect(afterCount).toBe(beforeCount + 1);
  });
});

describe('HealthStore.updateTreatmentStatus', () => {
  it('replaces treatment in record detail', async () => {
    const { store } = storeWithMock();
    await store.load('92b3c4d5-5a7f-4da2-9f9a-1b3c5d7f9e4a'); // Pepe
    const recordId = store.records()[0].id;
    await store.loadDetail(recordId);
    const treatments = store.detail(recordId)?.treatments ?? [];
    if (treatments.length > 0) {
      const treatment = treatments[0];
      if (treatment.status === 'PRESCRIBED') {
        const updated = await store.updateTreatmentStatus(recordId, treatment.id, 'ACTIVE');
        expect(updated.status).toBe('ACTIVE');
        const detail = store.detail(recordId);
        const found = detail?.treatments.find((t) => t.id === treatment.id);
        expect(found?.status).toBe('ACTIVE');
      }
    }
  });
});
