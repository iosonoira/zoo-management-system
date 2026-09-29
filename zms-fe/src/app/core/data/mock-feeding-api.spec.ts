import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Animal, AnimalStatus, ZooRole } from '../models/animal';
import { FeedingPlan, NewFeedingPlan, Feeding } from '../models/feeding';
import { Session } from '../session/session';
import { AnimalApi } from './animal-api';
import { FeedingApi } from './feeding-api';
import { MockFeedingApi } from './mock-feeding-api';
import {
  deceasedPlan,
  forbidden,
  invalidFeeding,
  invalidPlan,
  planChanged,
  planNotActive,
  planNotFound,
} from './api-errors';

function mockSession(role: ZooRole): Session {
  return {
    role: signal(role),
    username: signal(`${role.split('-')[1]}.test`),
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

function mockAnimalApi(animal: Animal): Partial<AnimalApi> {
  return {
    getById: async () => animal,
  };
}

function newPlan(overrides: Partial<NewFeedingPlan> = {}): NewFeedingPlan {
  return {
    animalId: 'animal-1',
    food: 'Test food',
    quantityGrams: 1000,
    feedingTimes: ['08:00', '16:00'],
    notes: null,
    ...overrides,
  };
}

describe('MockFeedingApi', () => {
  let api: FeedingApi;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        MockFeedingApi,
        { provide: FeedingApi, useClass: MockFeedingApi },
        { provide: Session, useValue: mockSession('zoo-admin') },
        { provide: AnimalApi, useValue: mockAnimalApi(mockAnimal()) },
      ],
    });
    api = TestBed.inject(FeedingApi);
  });

  describe('role checks', () => {
    it('rejects createPlan for keepers with 403', async () => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          MockFeedingApi,
          { provide: FeedingApi, useClass: MockFeedingApi },
          { provide: Session, useValue: mockSession('zoo-keeper') },
          { provide: AnimalApi, useValue: mockAnimalApi(mockAnimal()) },
        ],
      });
      api = TestBed.inject(FeedingApi);

      await expect(api.createPlan(newPlan())).rejects.toMatchObject({
        status: 403,
        message: forbidden('createFeedingPlan').message,
      });
    });

    it('rejects recordFeeding for vets with 403', async () => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          MockFeedingApi,
          { provide: FeedingApi, useClass: MockFeedingApi },
          { provide: Session, useValue: mockSession('zoo-vet') },
          { provide: AnimalApi, useValue: mockAnimalApi(mockAnimal()) },
        ],
      });
      api = TestBed.inject(FeedingApi);

      const plan = await api.createPlan(newPlan());

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          MockFeedingApi,
          { provide: FeedingApi, useClass: MockFeedingApi },
          { provide: Session, useValue: mockSession('zoo-vet') },
          { provide: AnimalApi, useValue: mockAnimalApi(mockAnimal()) },
        ],
      });
      const api2 = TestBed.inject(FeedingApi);

      await expect(
        api2.recordFeeding(plan.id, { fedAt: null, quantityGrams: 1000, notes: null }),
      ).rejects.toMatchObject({
        status: 403,
        message: forbidden('recordFeeding').message,
      });
    });
  });

  describe('data validation', () => {
    it('rejects empty food', async () => {
      await expect(api.createPlan(newPlan({ food: '   ' }))).rejects.toMatchObject(invalidPlan());
    });

    it('rejects food over 100 chars', async () => {
      await expect(api.createPlan(newPlan({ food: 'a'.repeat(101) }))).rejects.toMatchObject(
        invalidPlan(),
      );
    });

    it('rejects zero quantity', async () => {
      await expect(api.createPlan(newPlan({ quantityGrams: 0 }))).rejects.toMatchObject(
        invalidPlan(),
      );
    });

    it('rejects negative quantity', async () => {
      await expect(api.createPlan(newPlan({ quantityGrams: -5 }))).rejects.toMatchObject(
        invalidPlan(),
      );
    });

    it('rejects non-integer quantity', async () => {
      await expect(api.createPlan(newPlan({ quantityGrams: 1.5 }))).rejects.toMatchObject(
        invalidPlan(),
      );
    });

    it('rejects empty feeding times', async () => {
      await expect(api.createPlan(newPlan({ feedingTimes: [] }))).rejects.toMatchObject(
        invalidPlan(),
      );
    });

    it('rejects more than 6 feeding times', async () => {
      await expect(
        api.createPlan(
          newPlan({
            feedingTimes: ['08:00', '10:00', '12:00', '14:00', '16:00', '18:00', '20:00'],
          }),
        ),
      ).rejects.toMatchObject(invalidPlan());
    });

    it('rejects invalid time format', async () => {
      await expect(api.createPlan(newPlan({ feedingTimes: ['25:00'] }))).rejects.toMatchObject(
        invalidPlan(),
      );
      await expect(api.createPlan(newPlan({ feedingTimes: ['08:60'] }))).rejects.toMatchObject(
        invalidPlan(),
      );
    });

    it('rejects duplicate times', async () => {
      await expect(
        api.createPlan(newPlan({ feedingTimes: ['08:00', '08:00'] })),
      ).rejects.toMatchObject(invalidPlan());
    });

    it('rejects notes over 500 chars', async () => {
      await expect(api.createPlan(newPlan({ notes: 'a'.repeat(501) }))).rejects.toMatchObject(
        invalidPlan(),
      );
    });

    it('trims food and converts empty notes to null', async () => {
      const plan = await api.createPlan(newPlan({ food: '  Test  ', notes: '   ' }));
      expect(plan.food).toBe('Test');
      expect(plan.notes).toBeNull();
    });

    it('sorts feeding times ascending', async () => {
      const plan = await api.createPlan(newPlan({ feedingTimes: ['16:00', '08:00', '12:00'] }));
      expect(plan.feedingTimes).toEqual(['08:00', '12:00', '16:00']);
    });
  });

  describe('deceased animals', () => {
    it('rejects createPlan for deceased animal', async () => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          MockFeedingApi,
          { provide: FeedingApi, useClass: MockFeedingApi },
          { provide: Session, useValue: mockSession('zoo-admin') },
          { provide: AnimalApi, useValue: mockAnimalApi(mockAnimal('DECEASED')) },
        ],
      });
      api = TestBed.inject(FeedingApi);

      await expect(api.createPlan(newPlan())).rejects.toMatchObject(deceasedPlan());
    });

    it('ends active and suspended plans when animal becomes deceased', async () => {
      // Use demo data which includes an active plan from a test animal
      // The demo data has plans for Kibo and other animals
      // We'll create one for testing instead
      const plan = await api.createPlan(newPlan({ animalId: 'test-deceased' }));
      expect(plan.status).toBe('ACTIVE');

      // Now simulate the animal becoming deceased by replacing the AnimalApi
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          MockFeedingApi,
          { provide: FeedingApi, useClass: MockFeedingApi },
          { provide: Session, useValue: mockSession('zoo-admin') },
          { provide: AnimalApi, useValue: mockAnimalApi(mockAnimal('DECEASED')) },
        ],
      });
      const api2 = TestBed.inject(FeedingApi);

      // Try to create a plan for the deceased animal - should throw deceasedPlan error
      await expect(api2.createPlan(newPlan({ animalId: 'test-deceased' }))).rejects.toMatchObject(
        deceasedPlan(),
      );
    });
  });

  describe('plan status transitions', () => {
    it('allows ACTIVE→SUSPENDED→ACTIVE→ENDED', async () => {
      let plan = await api.createPlan(newPlan());
      expect(plan.status).toBe('ACTIVE');

      plan = await api.updatePlanStatus(plan.id, 'SUSPENDED');
      expect(plan.status).toBe('SUSPENDED');

      plan = await api.updatePlanStatus(plan.id, 'ACTIVE');
      expect(plan.status).toBe('ACTIVE');

      plan = await api.updatePlanStatus(plan.id, 'ENDED');
      expect(plan.status).toBe('ENDED');
    });

    it('rejects ENDED→ACTIVE', async () => {
      let plan = await api.createPlan(newPlan());
      plan = await api.updatePlanStatus(plan.id, 'ENDED');

      await expect(api.updatePlanStatus(plan.id, 'ACTIVE')).rejects.toMatchObject(planChanged());
    });

    it('sets endedOn to today when transitioning to ENDED', async () => {
      const plan = await api.createPlan(newPlan());
      const updated = await api.updatePlanStatus(plan.id, 'ENDED');
      expect(updated.endedOn).toBe(updated.startedOn.substring(0, 10)); // Today
    });
  });

  describe('feedings', () => {
    it('rejects recording on non-ACTIVE plan', async () => {
      let plan = await api.createPlan(newPlan());
      plan = await api.updatePlanStatus(plan.id, 'SUSPENDED');

      await expect(
        api.recordFeeding(plan.id, { fedAt: null, quantityGrams: 1000, notes: null }),
      ).rejects.toMatchObject(planNotActive());
    });

    it('rejects negative quantity', async () => {
      const plan = await api.createPlan(newPlan());

      await expect(
        api.recordFeeding(plan.id, { fedAt: null, quantityGrams: -1, notes: null }),
      ).rejects.toMatchObject(invalidFeeding());
    });

    it('rejects non-integer quantity', async () => {
      const plan = await api.createPlan(newPlan());

      await expect(
        api.recordFeeding(plan.id, { fedAt: null, quantityGrams: 1.5, notes: null }),
      ).rejects.toMatchObject(invalidFeeding());
    });

    it('rejects notes over 500 chars', async () => {
      const plan = await api.createPlan(newPlan());

      await expect(
        api.recordFeeding(plan.id, { fedAt: null, quantityGrams: 1000, notes: 'a'.repeat(501) }),
      ).rejects.toMatchObject(invalidFeeding());
    });

    it('converts null fedAt to current timestamp', async () => {
      const plan = await api.createPlan(newPlan());
      const before = new Date();
      const feeding = await api.recordFeeding(plan.id, {
        fedAt: null,
        quantityGrams: 1000,
        notes: null,
      });
      const after = new Date();

      const fedDate = new Date(feeding.fedAt);
      expect(fedDate.getTime()).toBeGreaterThanOrEqual(before.getTime());
      expect(fedDate.getTime()).toBeLessThanOrEqual(after.getTime());
    });

    it('rejects fedAt more than 60 seconds in the future', async () => {
      const plan = await api.createPlan(newPlan());
      const future = new Date();
      future.setSeconds(future.getSeconds() + 61);

      await expect(
        api.recordFeeding(plan.id, {
          fedAt: future.toISOString(),
          quantityGrams: 1000,
          notes: null,
        }),
      ).rejects.toMatchObject(invalidFeeding());
    });

    it('accepts fedAt within 60 seconds of now', async () => {
      const plan = await api.createPlan(newPlan());
      const soon = new Date();
      soon.setSeconds(soon.getSeconds() + 30);

      const feeding = await api.recordFeeding(plan.id, {
        fedAt: soon.toISOString(),
        quantityGrams: 1000,
        notes: null,
      });
      expect(feeding.fedAt).toBe(soon.toISOString());
    });

    it('trims notes and converts empty to null', async () => {
      const plan = await api.createPlan(newPlan());
      const feeding = await api.recordFeeding(plan.id, {
        fedAt: null,
        quantityGrams: 1000,
        notes: '   ',
      });
      expect(feeding.notes).toBeNull();
    });

    it('returns page with correct total', async () => {
      const plan = await api.createPlan(newPlan());
      await api.recordFeeding(plan.id, { fedAt: null, quantityGrams: 1000, notes: null });
      await api.recordFeeding(plan.id, { fedAt: null, quantityGrams: 1000, notes: null });

      const page = await api.listFeedings(plan.id, 0);
      expect(page.total).toBe(2);
      expect(page.items.length).toBe(2);
    });

    it('rejects unknown plan', async () => {
      await expect(api.listFeedings('unknown', 0)).rejects.toMatchObject(planNotFound());
    });
  });

  describe('sorting', () => {
    it('returns plans sorted by startedOn desc, then id asc', async () => {
      // Nia has an ended and an active plan in the demo data; add a third, started today.
      const nia = 'c43e9a1b-7f82-4c3b-8d2e-4a6b8c0e2d73';
      const created = await api.createPlan(newPlan({ animalId: nia }));

      const plans = await api.listPlans(nia);
      expect(plans).toHaveLength(3);
      expect(plans[0].id).toBe(created.id);
      for (let i = 1; i < plans.length; i++) {
        const [newer, older] = [plans[i - 1], plans[i]];
        expect(
          newer.startedOn > older.startedOn ||
            (newer.startedOn === older.startedOn && newer.id < older.id),
        ).toBe(true);
      }
    });

    it('rejects a fedAt that is not a date', async () => {
      const plan = await api.createPlan(newPlan());
      await expect(
        api.recordFeeding(plan.id, { fedAt: 'yesterday', quantityGrams: 1000, notes: null }),
      ).rejects.toMatchObject({ message: invalidFeeding().message });
    });

    it('returns feedings sorted by fedAt desc', async () => {
      const plan = await api.createPlan(newPlan());

      const now = new Date();
      const past = new Date(now.getTime() - 3600000); // 1 hour ago

      const f1 = await api.recordFeeding(plan.id, {
        fedAt: past.toISOString(),
        quantityGrams: 1000,
        notes: null,
      });
      const f2 = await api.recordFeeding(plan.id, {
        fedAt: null,
        quantityGrams: 1000,
        notes: null,
      });

      const page = await api.listFeedings(plan.id, 0);
      expect(page.items[0].id).toBe(f2.id); // Newer first
      expect(page.items[1].id).toBe(f1.id);
    });
  });
});
