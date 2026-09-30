import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import {
  MedicalRecord,
  MedicalRecordDetail,
  NewMedicalRecord,
  Treatment,
  TreatmentStatus,
} from '../models/health';
import { HealthApi } from './health-api';
import { ApiError } from './api-error';
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
import { HttpHealthApi, RECORD_PAGE_SIZE } from './http-health-api';

const BASE = environment.api.health;

function medicalRecord(overrides: Partial<MedicalRecord> = {}): MedicalRecord {
  return {
    id: 'record-1',
    animalId: 'animal-1',
    reason: 'Lameness',
    diagnosis: 'Ligament strain',
    examinedOn: '2026-09-15',
    veterinarian: 'Dr. Bianchi',
    createdBy: 'vet.bianchi',
    updatedBy: null,
    ...overrides,
  };
}

function treatment(overrides: Partial<Treatment> = {}): Treatment {
  return {
    id: 'treatment-1',
    medicalRecordId: 'record-1',
    description: 'Rest and anti-inflammatory',
    status: 'PRESCRIBED',
    startedOn: null,
    endedOn: null,
    createdBy: 'vet.bianchi',
    updatedBy: null,
    ...overrides,
  };
}

function medicalRecordDetail(overrides: Partial<MedicalRecordDetail> = {}): MedicalRecordDetail {
  return {
    ...medicalRecord(),
    treatments: [treatment()],
    ...overrides,
  };
}

function newMedicalRecord(overrides: Partial<NewMedicalRecord> = {}): NewMedicalRecord {
  return {
    animalId: 'animal-1',
    reason: 'Lameness',
    diagnosis: 'Ligament strain',
    examinedOn: '2026-09-15',
    veterinarian: 'Dr. Bianchi',
    ...overrides,
  };
}

describe('HttpHealthApi', () => {
  let api: HealthApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: HealthApi, useClass: HttpHealthApi },
      ],
    });
    api = TestBed.inject(HealthApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for the first page with animalId in the URL', async () => {
    const pending = api.listRecords('animal-1');
    const request = http.expectOne(
      `${BASE}/medical-records?animalId=animal-1&page=0&size=${RECORD_PAGE_SIZE}`,
    );
    expect(request.request.method).toBe('GET');
    request.flush({ items: [medicalRecord()], page: 0, size: RECORD_PAGE_SIZE, total: 1 });
    await expect(pending).resolves.toEqual([medicalRecord()]);
  });

  it('keeps paging until it has all records', async () => {
    const first = Array.from({ length: RECORD_PAGE_SIZE }, (_, i) =>
      medicalRecord({ id: `page0-${i}`, reason: `Reason${i}` }),
    );
    const pending = api.listRecords('animal-1');

    http
      .expectOne(`${BASE}/medical-records?animalId=animal-1&page=0&size=${RECORD_PAGE_SIZE}`)
      .flush({ items: first, page: 0, size: RECORD_PAGE_SIZE, total: RECORD_PAGE_SIZE + 2 });

    const second = [
      medicalRecord({ id: 'page1-0', reason: 'Reason100' }),
      medicalRecord({ id: 'page1-1', reason: 'Reason101' }),
    ];
    const request = await vi.waitFor(() =>
      http.expectOne(`${BASE}/medical-records?animalId=animal-1&page=1&size=${RECORD_PAGE_SIZE}`),
    );
    request.flush({ items: second, page: 1, size: RECORD_PAGE_SIZE, total: RECORD_PAGE_SIZE + 2 });

    await expect(pending).resolves.toHaveLength(RECORD_PAGE_SIZE + 2);
  });

  it('stops paging when a page comes back short', async () => {
    const pending = api.listRecords('animal-1');
    http
      .expectOne(`${BASE}/medical-records?animalId=animal-1&page=0&size=${RECORD_PAGE_SIZE}`)
      .flush({ items: [medicalRecord()], page: 0, size: RECORD_PAGE_SIZE, total: 999 });
    await expect(pending).resolves.toHaveLength(1);
  });

  it('reads one record by id', async () => {
    const pending = api.getRecord('record-1');
    const request = http.expectOne(`${BASE}/medical-records/record-1`);
    expect(request.request.method).toBe('GET');
    request.flush(medicalRecordDetail());
    await expect(pending).resolves.toEqual(medicalRecordDetail());
  });

  it('sends createRecord POST to /medical-records with the body', async () => {
    const input = newMedicalRecord();
    const pending = api.createRecord(input);
    const request = http.expectOne(`${BASE}/medical-records`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(input);
    request.flush(medicalRecord());
    await expect(pending).resolves.toEqual(medicalRecord());
  });

  it('prescribes a treatment with POST to /medical-records/:id/treatments', async () => {
    const pending = api.prescribe('record-1', 'Rest and anti-inflammatory');
    const request = http.expectOne(`${BASE}/medical-records/record-1/treatments`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ description: 'Rest and anti-inflammatory' });
    request.flush(treatment());
    await expect(pending).resolves.toEqual(treatment());
  });

  it('updates treatment status with PUT to /treatments/:id/status', async () => {
    const pending = api.updateTreatmentStatus('treatment-1', 'ACTIVE');
    const request = http.expectOne(`${environment.api.health}/treatments/treatment-1/status`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ status: 'ACTIVE' });
    request.flush(treatment({ status: 'ACTIVE', startedOn: '2026-09-16' }));
    await expect(pending).resolves.toEqual(
      treatment({ status: 'ACTIVE', startedOn: '2026-09-16' }),
    );
  });

  it('maps 400 on createRecord to invalidRecord copy', async () => {
    const pending = api.createRecord(newMedicalRecord());
    http
      .expectOne(`${BASE}/medical-records`)
      .flush({ message: 'Invalid data' }, { status: 400, statusText: 'Bad Request' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(400);
      expect(error.message).toBe(invalidRecord().message);
      return true;
    });
  });

  it('maps 400 on prescribe to invalidTreatment copy', async () => {
    const pending = api.prescribe('record-1', 'Too long description');
    http
      .expectOne(`${BASE}/medical-records/record-1/treatments`)
      .flush({ message: 'Invalid data' }, { status: 400, statusText: 'Bad Request' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(400);
      expect(error.message).toBe(invalidTreatment().message);
      return true;
    });
  });

  it('maps 403 on prescribe to prescribe copy', async () => {
    const pending = api.prescribe('record-1', 'Rest');
    http
      .expectOne(`${BASE}/medical-records/record-1/treatments`)
      .flush({ message: 'Insufficient role' }, { status: 403, statusText: 'Forbidden' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(403);
      expect(error.message).toBe(forbidden('prescribeTreatment').message);
      return true;
    });
  });

  it('maps 404 on getRecord to recordNotFound', async () => {
    const pending = api.getRecord('missing');
    http
      .expectOne(`${BASE}/medical-records/missing`)
      .flush({ message: 'Record not found' }, { status: 404, statusText: 'Not Found' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(404);
      expect(error.message).toBe(recordNotFound().message);
      return true;
    });
  });

  it('maps 404 on treatment status update to treatmentNotFound', async () => {
    const pending = api.updateTreatmentStatus('missing', 'ACTIVE');
    http
      .expectOne(`${environment.api.health}/treatments/missing/status`)
      .flush({ message: 'Treatment not found' }, { status: 404, statusText: 'Not Found' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(404);
      expect(error.message).toBe(treatmentNotFound().message);
      return true;
    });
  });

  it('maps 422 on updateTreatmentStatus with invalid transition to treatmentChanged copy', async () => {
    const pending = api.updateTreatmentStatus('treatment-1', 'CANCELLED');
    http
      .expectOne(`${environment.api.health}/treatments/treatment-1/status`)
      .flush({ message: 'Unprocessable' }, { status: 422, statusText: 'Unprocessable Entity' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(422);
      expect(error.message).toBe(treatmentChanged().message);
      return true;
    });
  });

  it('maps network error to status 500', async () => {
    const pending = api.listRecords('animal-1');
    http
      .expectOne(`${BASE}/medical-records?animalId=animal-1&page=0&size=${RECORD_PAGE_SIZE}`)
      .error(new ProgressEvent('network error'));

    await expect(pending).rejects.toMatchObject({ status: 500 });
  });

  it('maps 401 to sessionExpired copy', async () => {
    const pending = api.getRecord('record-1');
    http
      .expectOne(`${BASE}/medical-records/record-1`)
      .flush({ message: 'Authentication required' }, { status: 401, statusText: 'Unauthorized' });

    await expect(pending).rejects.toMatchObject({ status: 401 });
  });

  it('maps 403 on createRecord to createMedicalRecord copy', async () => {
    const pending = api.createRecord(newMedicalRecord());
    http
      .expectOne(`${BASE}/medical-records`)
      .flush({ message: 'Insufficient role' }, { status: 403, statusText: 'Forbidden' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(403);
      expect(error.message).toBe(forbidden('createMedicalRecord').message);
      return true;
    });
  });

  it('maps 409 on createRecord to conflict copy', async () => {
    const pending = api.createRecord(newMedicalRecord());
    http
      .expectOne(`${BASE}/medical-records`)
      .flush({ message: 'Concurrent update' }, { status: 409, statusText: 'Conflict' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(409);
      expect(error.message).toContain('this medical record');
      return true;
    });
  });

  it('maps 409 on treatment status update to conflict copy naming treatment', async () => {
    const pending = api.updateTreatmentStatus('treatment-1', 'ACTIVE');
    http
      .expectOne(`${environment.api.health}/treatments/treatment-1/status`)
      .flush({ message: 'Concurrent update' }, { status: 409, statusText: 'Conflict' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(409);
      expect(error.message).toContain('this treatment');
      return true;
    });
  });

  it('maps 422 on prescribe to deceasedTreatment copy', async () => {
    const pending = api.prescribe('record-1', 'Rest');
    http
      .expectOne(`${BASE}/medical-records/record-1/treatments`)
      .flush({ message: 'Animal is deceased' }, { status: 422, statusText: 'Unprocessable Entity' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(422);
      expect(error.message).toBe(deceasedTreatment().message);
      return true;
    });
  });

  it('maps 422 on updateTreatmentStatus with ACTIVE to treatmentNotStartable copy', async () => {
    const pending = api.updateTreatmentStatus('treatment-1', 'ACTIVE');
    http
      .expectOne(`${environment.api.health}/treatments/treatment-1/status`)
      .flush({ message: 'Animal is deceased' }, { status: 422, statusText: 'Unprocessable Entity' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(422);
      expect(error.message).toBe(treatmentNotStartable().message);
      return true;
    });
  });

  it('maps 422 on updateTreatmentStatus with COMPLETED to treatmentChanged copy', async () => {
    const pending = api.updateTreatmentStatus('treatment-1', 'COMPLETED');
    http
      .expectOne(`${environment.api.health}/treatments/treatment-1/status`)
      .flush({ message: 'Invalid transition' }, { status: 422, statusText: 'Unprocessable Entity' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(422);
      expect(error.message).toBe(treatmentChanged().message);
      return true;
    });
  });
});
