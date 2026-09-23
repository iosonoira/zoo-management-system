import { PLATFORM_ID } from '@angular/core';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Animal, Enclosure, NewAnimal } from '../models/animal';
import { AnimalApi, ApiError } from './animal-api';
import { AnimalStore } from './animal-store';
import { ENCLOSURES } from './enclosure-directory';
import { MockAnimalApi } from './mock-animal-api';
import { Session } from '../session/session';

class CountingApi extends AnimalApi {
  calls = 0;
  async listAll(): Promise<Animal[]> {
    this.calls++;
    return [];
  }
  async getById(): Promise<Animal> {
    throw new Error('not used');
  }
  async updateStatus(): Promise<Animal> {
    throw new Error('not used');
  }
  async transfer(): Promise<Animal> {
    throw new Error('not used');
  }
  async register(): Promise<Animal> {
    throw new Error('not used');
  }
  async listEnclosures(): Promise<Enclosure[]> {
    return [...ENCLOSURES];
  }
}

function storeOn(platform: string): { store: AnimalStore; api: CountingApi } {
  const api = new CountingApi();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: PLATFORM_ID, useValue: platform },
      { provide: AnimalApi, useValue: api },
      AnimalStore,
    ],
  });
  return { store: TestBed.inject(AnimalStore), api };
}

describe('AnimalStore.load', () => {
  it('does not call the API on the server', async () => {
    const { store, api } = storeOn('server');
    await store.load();
    expect(api.calls).toBe(0);
    expect(store.state()).toBe('idle');
  });

  it('loads in the browser', async () => {
    const { store, api } = storeOn('browser');
    await store.load();
    expect(api.calls).toBe(1);
    expect(store.state()).toBe('ready');
  });
});

describe('AnimalStore.register', () => {
  function storeWithMock(role: string): { store: AnimalStore; api: MockAnimalApi } {
    const mockSession: Partial<Session> = {
      role: signal(role as any),
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
        { provide: AnimalApi, useClass: MockAnimalApi },
        AnimalStore,
      ],
    });
    return {
      store: TestBed.inject(AnimalStore),
      api: TestBed.inject(AnimalApi) as MockAnimalApi,
    };
  }

  function newAnimal(overrides: Partial<NewAnimal> = {}): NewAnimal {
    return {
      name: 'Simba',
      species: 'African lion',
      dangerous: true,
      habitat: 'TERRESTRIAL',
      enclosureId: '1c7f3a2b-4d5e-4f90-8b2c-3d4e5f6a7b81',
      arrivalDate: '2020-06-15',
      ...overrides,
    };
  }

  it('adds the registered animal to the roster', async () => {
    const { store, api } = storeWithMock('zoo-admin');
    const input = newAnimal();
    const animal = await store.register(input);
    expect(animal.name).toBe('Simba');
    expect(animal.status).toBe('HEALTHY');
    expect(animal.createdBy).toBe('test.user');
    expect(store.animals()).toContainEqual(expect.objectContaining({ name: 'Simba' }));
  });

  it('rejects with 403 when keeper tries to register', async () => {
    const { store } = storeWithMock('zoo-keeper');
    const input = newAnimal();
    await expect(store.register(input)).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(403);
      expect(error.message).toContain('admins');
      return true;
    });
  });

  it('rejects with 400 when name is blank', async () => {
    const { store } = storeWithMock('zoo-admin');
    const input = newAnimal({ name: '  ' });
    await expect(store.register(input)).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(400);
      return true;
    });
  });

  it('rejects with 400 when species is blank', async () => {
    const { store } = storeWithMock('zoo-admin');
    const input = newAnimal({ species: '' });
    await expect(store.register(input)).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(400);
      return true;
    });
  });

  it('rejects with 400 when enclosure does not exist', async () => {
    const { store } = storeWithMock('zoo-admin');
    const input = newAnimal({ enclosureId: 'unknown' });
    await expect(store.register(input)).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(400);
      return true;
    });
  });
});
