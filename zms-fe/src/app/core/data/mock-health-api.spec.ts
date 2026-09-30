import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Animal, AnimalStatus, ZooRole } from '../models/animal';
import { MedicalRecord, NewMedicalRecord, Treatment } from '../models/health';
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
import { AnimalApi } from './animal-api';
import { MockHealthApi } from './mock-health-api';
import { Session } from '../session/session';

function medicalRecord(overrides: Partial<MedicalRecord> = {}): NewMedicalRecord {
  return {
    animalId: 'test-animal',
    reason: 'Test reason',
    diagnosis: 'Test diagnosis',
    examinedOn: '2026-09-15',
    veterinarian: 'Dr. Test',
    ...overrides,
  };
}

function mockSession(role: ZooRole): Session {
  return {
    role: signal(role),
    username: signal('test.user'),
    can: () => true,
    canSwitchRole: false,
    setRole: () => {},
    restore: () => {},
    signOut: () => {},
  } as Session;
}

function mockAnimal(status: AnimalStatus = 'HEALTHY'): Animal {
  return {
    id: 'animal-1',
    name: 'Test Animal',
    species: 'Test',
    dangerous: false,
    habitat: 'TERRESTRIAL',
    enclosureId: 'enclosure-1',
    arrivalDate: '2026-01-01',
    status,
    createdBy: 'admin.test',
    updatedBy: 'admin.test',
  };
}

function mockAnimalApi(animal: Animal | (() => Animal)): Partial<AnimalApi> {
  return {
    getById: async () => (typeof animal === 'function' ? animal() : animal),
  };
}

function storeWithMock(
  role: ZooRole,
  animal: Animal | (() => Animal) = mockAnimal(),
): { api: HealthApi } {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: Session, useValue: mockSession(role) },
      { provide: AnimalApi, useValue: mockAnimalApi(animal) },
      { provide: HealthApi, useClass: MockHealthApi },
    ],
  });
  return { api: TestBed.inject(HealthApi) };
}

describe('MockHealthApi', () => {
  it('keeper gets 403 on createRecord', async () => {
    const { api } = storeWithMock('zoo-keeper');
    await expect(api.createRecord(medicalRecord())).rejects.toSatisfy((error: ApiError) => {
      expect(error.message).toBe(forbidden('createMedicalRecord').message);
      return true;
    });
  });

  it('keeper gets 403 on prescribe', async () => {
    const { api } = storeWithMock('zoo-keeper');
    await expect(api.prescribe('any-record', 'description')).rejects.toSatisfy(
      (error: ApiError) => {
        expect(error.message).toBe(forbidden('prescribeTreatment').message);
        return true;
      },
    );
  });

  it('keeper gets 403 on updateTreatmentStatus', async () => {
    const { api } = storeWithMock('zoo-keeper');
    await expect(api.updateTreatmentStatus('any-treatment', 'ACTIVE')).rejects.toSatisfy(
      (error: ApiError) => {
        expect(error.message).toBe(forbidden('updateTreatmentStatus').message);
        return true;
      },
    );
  });

  it('vet creates a record with trimmed values', async () => {
    const { api } = storeWithMock('zoo-vet');
    const input = medicalRecord({
      reason: '  Limping  ',
      diagnosis: '  Strain  ',
      veterinarian: '  Dr. Bianchi  ',
    });
    const record = await api.createRecord(input);
    expect(record.reason).toBe('Limping');
    expect(record.diagnosis).toBe('Strain');
    expect(record.veterinarian).toBe('Dr. Bianchi');
    expect(record.createdBy).toBe('test.user');
    expect(record.updatedBy).toBe('test.user');
  });

  it('created record appears first in listRecords', async () => {
    const { api } = storeWithMock('zoo-vet');
    const animalId = 'test-animal-123';
    const input = medicalRecord({ animalId, examinedOn: '2026-09-25' });
    const created = await api.createRecord(input);
    const list = await api.listRecords(animalId);
    expect(list[0]).toEqual(created);
  });

  it('future examinedOn throws invalidRecord', async () => {
    const { api } = storeWithMock('zoo-vet');
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const futureDate =
      tomorrow.getFullYear() +
      '-' +
      String(tomorrow.getMonth() + 1).padStart(2, '0') +
      '-' +
      String(tomorrow.getDate()).padStart(2, '0');
    await expect(api.createRecord(medicalRecord({ examinedOn: futureDate }))).rejects.toSatisfy(
      (error: ApiError) => {
        expect(error.message).toBe(invalidRecord().message);
        return true;
      },
    );
  });

  it('blank reason throws invalidRecord', async () => {
    const { api } = storeWithMock('zoo-vet');
    await expect(api.createRecord(medicalRecord({ reason: '  ' }))).rejects.toSatisfy(
      (error: ApiError) => {
        expect(error.message).toBe(invalidRecord().message);
        return true;
      },
    );
  });

  it('reason exceeding 200 chars throws invalidRecord', async () => {
    const { api } = storeWithMock('zoo-vet');
    const longReason = 'a'.repeat(201);
    await expect(api.createRecord(medicalRecord({ reason: longReason }))).rejects.toSatisfy(
      (error: ApiError) => {
        expect(error.message).toBe(invalidRecord().message);
        return true;
      },
    );
  });

  it('unknown record throws recordNotFound on getRecord', async () => {
    const { api } = storeWithMock('zoo-vet');
    await expect(api.getRecord('missing')).rejects.toSatisfy((error: ApiError) => {
      expect(error.message).toBe(recordNotFound().message);
      return true;
    });
  });

  it('unknown record throws recordNotFound on prescribe', async () => {
    const { api } = storeWithMock('zoo-vet');
    await expect(api.prescribe('missing', 'description')).rejects.toSatisfy((error: ApiError) => {
      expect(error.message).toBe(recordNotFound().message);
      return true;
    });
  });

  it('prescribe creates treatment with PRESCRIBED status', async () => {
    const { api } = storeWithMock('zoo-vet');
    const record = await api.createRecord(medicalRecord());
    const treatment = await api.prescribe(record.id, 'Rest');
    expect(treatment.status).toBe('PRESCRIBED');
    expect(treatment.startedOn).toBe(null);
    expect(treatment.endedOn).toBe(null);
    expect(treatment.description).toBe('Rest');
  });

  it('blank description throws invalidTreatment', async () => {
    const { api } = storeWithMock('zoo-vet');
    const record = await api.createRecord(medicalRecord());
    await expect(api.prescribe(record.id, '  ')).rejects.toSatisfy((error: ApiError) => {
      expect(error.message).toBe(invalidTreatment().message);
      return true;
    });
  });

  it('description exceeding 500 chars throws invalidTreatment', async () => {
    const { api } = storeWithMock('zoo-vet');
    const record = await api.createRecord(medicalRecord());
    const longDesc = 'a'.repeat(501);
    await expect(api.prescribe(record.id, longDesc)).rejects.toSatisfy((error: ApiError) => {
      expect(error.message).toBe(invalidTreatment().message);
      return true;
    });
  });

  it('PRESCRIBED to ACTIVE sets startedOn', async () => {
    const { api } = storeWithMock('zoo-vet');
    const record = await api.createRecord(medicalRecord());
    const treatment = await api.prescribe(record.id, 'Rest');
    const updated = await api.updateTreatmentStatus(treatment.id, 'ACTIVE');
    expect(updated.status).toBe('ACTIVE');
    expect(updated.startedOn).not.toBeNull();
  });

  it('ACTIVE to COMPLETED sets endedOn', async () => {
    const { api } = storeWithMock('zoo-vet');
    const record = await api.createRecord(medicalRecord());
    const treatment = await api.prescribe(record.id, 'Rest');
    const active = await api.updateTreatmentStatus(treatment.id, 'ACTIVE');
    const completed = await api.updateTreatmentStatus(active.id, 'COMPLETED');
    expect(completed.status).toBe('COMPLETED');
    expect(completed.endedOn).not.toBeNull();
  });

  it('COMPLETED to ACTIVE throws treatmentNotStartable, the copy HttpHealthApi shows for that 422', async () => {
    const { api } = storeWithMock('zoo-vet');
    const record = await api.createRecord(medicalRecord());
    const treatment = await api.prescribe(record.id, 'Rest');
    const active = await api.updateTreatmentStatus(treatment.id, 'ACTIVE');
    const completed = await api.updateTreatmentStatus(active.id, 'COMPLETED');
    await expect(api.updateTreatmentStatus(completed.id, 'ACTIVE')).rejects.toSatisfy(
      (error: ApiError) => {
        expect(error.message).toBe(treatmentNotStartable().message);
        return true;
      },
    );
  });

  it('COMPLETED to CANCELLED throws treatmentChanged', async () => {
    const { api } = storeWithMock('zoo-vet');
    const record = await api.createRecord(medicalRecord());
    const treatment = await api.prescribe(record.id, 'Rest');
    const active = await api.updateTreatmentStatus(treatment.id, 'ACTIVE');
    const completed = await api.updateTreatmentStatus(active.id, 'COMPLETED');
    await expect(api.updateTreatmentStatus(completed.id, 'CANCELLED')).rejects.toSatisfy(
      (error: ApiError) => {
        expect(error.message).toBe(treatmentChanged().message);
        return true;
      },
    );
  });

  it('unknown treatment throws treatmentNotFound', async () => {
    const { api } = storeWithMock('zoo-vet');
    await expect(api.updateTreatmentStatus('missing', 'ACTIVE')).rejects.toSatisfy(
      (error: ApiError) => {
        expect(error.message).toBe(treatmentNotFound().message);
        return true;
      },
    );
  });

  it('list of an animal with no records is empty', async () => {
    const { api } = storeWithMock('zoo-vet');
    const list = await api.listRecords('animal-with-no-records');
    expect(list).toEqual([]);
  });

  it('records are sorted by examinedOn descending, then id ascending', async () => {
    const { api } = storeWithMock('zoo-vet');
    const animalId = 'test-animal-sort';
    const rec1 = await api.createRecord(medicalRecord({ animalId, examinedOn: '2026-09-15' }));
    const rec2 = await api.createRecord(medicalRecord({ animalId, examinedOn: '2026-09-20' }));
    const rec3 = await api.createRecord(medicalRecord({ animalId, examinedOn: '2026-09-20' }));
    const list = await api.listRecords(animalId);
    // Newest first
    expect(list[0].examinedOn).toBe('2026-09-20');
    expect(list[1].examinedOn).toBe('2026-09-20');
    expect(list[2].examinedOn).toBe('2026-09-15');
    // For the same date, ascending id. The ids are random, so derive the expected order.
    const [first, second] = [rec2.id, rec3.id].sort((a, b) => a.localeCompare(b));
    expect(list[0].id).toBe(first);
    expect(list[1].id).toBe(second);
    expect(list[2].id).toBe(rec1.id);
  });

  describe('deceased animals', () => {
    it('rejects prescribe for deceased animal', async () => {
      const { api } = storeWithMock('zoo-vet', mockAnimal('DECEASED'));
      const record = await api.createRecord(medicalRecord());

      await expect(api.prescribe(record.id, 'Rest')).rejects.toSatisfy((error: ApiError) => {
        expect(error.message).toBe(deceasedTreatment().message);
        return true;
      });
    });

    it('cancels open treatments once the animal is deceased, and leaves closed ones alone', async () => {
      let animal = mockAnimal();
      const { api } = storeWithMock('zoo-vet', () => animal);
      const record = await api.createRecord(medicalRecord());
      const prescribed = await api.prescribe(record.id, 'Rest');
      const active = await api.prescribe(record.id, 'Antibiotics');
      await api.updateTreatmentStatus(active.id, 'ACTIVE');
      const completed = await api.prescribe(record.id, 'Bandage');
      await api.updateTreatmentStatus(completed.id, 'ACTIVE');
      await api.updateTreatmentStatus(completed.id, 'COMPLETED');

      animal = mockAnimal('DECEASED');
      const detail = await api.getRecord(record.id);

      const statusOf = (id: string) => detail.treatments.find((t) => t.id === id)?.status;
      expect(statusOf(prescribed.id)).toBe('CANCELLED');
      expect(statusOf(active.id)).toBe('CANCELLED');
      expect(statusOf(completed.id)).toBe('COMPLETED');
      expect(detail.treatments.find((t) => t.id === prescribed.id)?.updatedBy).toBe('admin.test');
    });

    it('rejects starting a treatment of a deceased animal', async () => {
      let animal = mockAnimal();
      const { api } = storeWithMock('zoo-vet', () => animal);
      const record = await api.createRecord(medicalRecord());
      const treatment = await api.prescribe(record.id, 'Rest');

      animal = mockAnimal('DECEASED');

      await expect(api.updateTreatmentStatus(treatment.id, 'ACTIVE')).rejects.toSatisfy(
        (error: ApiError) => {
          expect(error.status).toBe(422);
          expect(error.message).toBe(treatmentNotStartable().message);
          return true;
        },
      );
    });

    it('allows creating medical records for deceased animals', async () => {
      const { api } = storeWithMock('zoo-vet', mockAnimal('DECEASED'));
      const record = await api.createRecord(medicalRecord());
      expect(record).toBeDefined();
      expect(record.animalId).toBe('test-animal');
    });
  });
});
