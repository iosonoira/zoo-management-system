import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Animal, Enclosure } from '../models/animal';
import { AnimalApi } from './animal-api';
import { AnimalStore } from './animal-store';
import { ENCLOSURES } from './enclosure-directory';

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
