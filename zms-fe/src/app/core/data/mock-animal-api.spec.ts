import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ZooRole } from '../models/animal';
import { AnimalApi } from './animal-api';
import { DemoEventFeed } from './demo-event-feed';
import { MockAnimalApi } from './mock-animal-api';
import { Session } from '../session/session';
import { DEMO_ANIMALS } from './demo-data';
import { ENCLOSURES } from './enclosure-directory';

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

describe('MockAnimalApi event publishing', () => {
  let api: AnimalApi;
  let feed: DemoEventFeed;
  const healthyAnimal = DEMO_ANIMALS[0]; // Kibo, HEALTHY
  const deceasedAnimal = DEMO_ANIMALS[4]; // Bruno, DECEASED

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        MockAnimalApi,
        DemoEventFeed,
        { provide: AnimalApi, useClass: MockAnimalApi },
        { provide: Session, useValue: mockSession('zoo-admin') },
      ],
    });
    api = TestBed.inject(AnimalApi);
    feed = TestBed.inject(DemoEventFeed);
  });

  describe('updateStatus', () => {
    it('publishes ANIMAL_STATUS_CHANGED event with previousStatus', async () => {
      const result = await api.updateStatus(healthyAnimal.id, 'UNDER_OBSERVATION');
      const events = feed.events();
      expect(events.length).toBe(1);
      expect(events[0].eventType).toBe('ANIMAL_STATUS_CHANGED');
      expect(events[0].previousStatus).toBe('HEALTHY');
      expect(events[0].animal.status).toBe('UNDER_OBSERVATION');
      expect(events[0].performedBy).toBe('admin.test');
    });

    it('does not publish event when status change is forbidden', async () => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          MockAnimalApi,
          DemoEventFeed,
          { provide: AnimalApi, useClass: MockAnimalApi },
          { provide: Session, useValue: mockSession('zoo-keeper') },
        ],
      });
      api = TestBed.inject(AnimalApi);
      feed = TestBed.inject(DemoEventFeed);

      await expect(api.updateStatus(healthyAnimal.id, 'UNDER_OBSERVATION')).rejects.toThrow();
      expect(feed.events().length).toBe(0);
    });

    it('does not publish event on invalid status transition', async () => {
      // Try to transition from DECEASED to something else (should fail)
      await expect(api.updateStatus(deceasedAnimal.id, 'HEALTHY')).rejects.toThrow();
      expect(feed.events().length).toBe(0);
    });
  });

  describe('transfer', () => {
    it('publishes ANIMAL_TRANSFERRED event with fromEnclosureId', async () => {
      const fromEnclosure = healthyAnimal.enclosureId;
      const toEnclosure = ENCLOSURES[1].id;

      await api.transfer(healthyAnimal.id, toEnclosure);
      const events = feed.events();
      expect(events.length).toBe(1);
      expect(events[0].eventType).toBe('ANIMAL_TRANSFERRED');
      expect(events[0].fromEnclosureId).toBe(fromEnclosure);
      expect(events[0].animal.enclosureId).toBe(toEnclosure);
      expect(events[0].performedBy).toBe('admin.test');
    });

    it('does not publish event when transfer is forbidden', async () => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          MockAnimalApi,
          DemoEventFeed,
          { provide: AnimalApi, useClass: MockAnimalApi },
          { provide: Session, useValue: mockSession('zoo-vet') },
        ],
      });
      api = TestBed.inject(AnimalApi);
      feed = TestBed.inject(DemoEventFeed);

      await expect(api.transfer(healthyAnimal.id, ENCLOSURES[1].id)).rejects.toThrow();
      expect(feed.events().length).toBe(0);
    });

    it('does not publish event when animal is deceased', async () => {
      await expect(api.transfer(deceasedAnimal.id, ENCLOSURES[1].id)).rejects.toThrow();
      expect(feed.events().length).toBe(0);
    });
  });

  describe('register', () => {
    it('publishes ANIMAL_REGISTERED event', async () => {
      const result = await api.register({
        name: 'New Animal',
        species: 'New Species',
        dangerous: false,
        habitat: 'TERRESTRIAL',
        enclosureId: ENCLOSURES[0].id,
        arrivalDate: '2026-09-30',
      });

      const events = feed.events();
      expect(events.length).toBe(1);
      expect(events[0].eventType).toBe('ANIMAL_REGISTERED');
      expect(events[0].animal.id).toBe(result.id);
      expect(events[0].animal.name).toBe('New Animal');
      expect(events[0].performedBy).toBe('admin.test');
    });

    it('does not publish event when registration is forbidden', async () => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          MockAnimalApi,
          DemoEventFeed,
          { provide: AnimalApi, useClass: MockAnimalApi },
          { provide: Session, useValue: mockSession('zoo-keeper') },
        ],
      });
      api = TestBed.inject(AnimalApi);
      feed = TestBed.inject(DemoEventFeed);

      await expect(
        api.register({
          name: 'New Animal',
          species: 'New Species',
          dangerous: false,
          habitat: 'TERRESTRIAL',
          enclosureId: ENCLOSURES[0].id,
          arrivalDate: '2026-09-30',
        }),
      ).rejects.toThrow();

      expect(feed.events().length).toBe(0);
    });

    it('does not publish event when registration data is invalid', async () => {
      await expect(
        api.register({
          name: '',
          species: 'New Species',
          dangerous: false,
          habitat: 'TERRESTRIAL',
          enclosureId: ENCLOSURES[0].id,
          arrivalDate: '2026-09-30',
        }),
      ).rejects.toThrow();

      expect(feed.events().length).toBe(0);
    });
  });
});
