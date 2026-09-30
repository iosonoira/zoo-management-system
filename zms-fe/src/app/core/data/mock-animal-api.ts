import { inject } from '@angular/core';
import { Animal, AnimalStatus, Enclosure, NewAnimal, canBeTransferred, canTransitionTo } from '../models/animal';
import { can } from '../models/permissions';
import { Session } from '../session/session';
import { AnimalApi } from './animal-api';
import {
  deceasedStatus,
  deceasedTransfer,
  forbidden,
  invalidAnimal,
  notFound,
  sameStatus,
  unknownEnclosure,
} from './api-errors';
import { DemoEventFeed } from './demo-event-feed';
import { ENCLOSURES } from './enclosure-directory';
import { DEMO_ANIMALS } from './demo-data';

const LATENCY_MS = 380;

/**
 * In-memory adapter that enforces the same rules and status codes as
 * `animal-service`: role checks (403), unknown ids (404), invalid data (400),
 * transfer of a deceased animal (400), and invalid status transitions (422).
 */
export class MockAnimalApi extends AnimalApi {
  private readonly session = inject(Session);
  private readonly events = inject(DemoEventFeed);
  private animals = new Map<string, Animal>(DEMO_ANIMALS.map((a) => [a.id, a]));

  async listAll(): Promise<Animal[]> {
    await delay();
    return [...this.animals.values()];
  }

  async getById(id: string): Promise<Animal> {
    await delay();
    return this.find(id);
  }

  async updateStatus(id: string, status: AnimalStatus, _name?: string): Promise<Animal> {
    await delay();
    if (!can(this.session.role(), 'updateStatus')) {
      throw forbidden('updateStatus');
    }
    const animal = this.find(id);
    if (!canTransitionTo(animal, status)) {
      throw animal.status === 'DECEASED' ? deceasedStatus(animal.name) : sameStatus(animal.name);
    }
    const updated = { ...animal, status, updatedBy: this.session.username() };
    const saved = this.save(updated);
    this.events.publish({
      eventType: 'ANIMAL_STATUS_CHANGED',
      animal: saved,
      performedBy: this.session.username(),
      previousStatus: animal.status,
      fromEnclosureId: null,
    });
    return saved;
  }

  async transfer(id: string, targetEnclosureId: string, _name?: string): Promise<Animal> {
    await delay();
    if (!can(this.session.role(), 'transfer')) {
      throw forbidden('transfer');
    }
    const animal = this.find(id);
    if (!canBeTransferred(animal)) {
      throw deceasedTransfer(animal.name);
    }
    if (!ENCLOSURES.some((e) => e.id === targetEnclosureId)) {
      throw unknownEnclosure();
    }
    const fromEnclosureId = animal.enclosureId;
    const updated = { ...animal, enclosureId: targetEnclosureId, updatedBy: this.session.username() };
    const saved = this.save(updated);
    this.events.publish({
      eventType: 'ANIMAL_TRANSFERRED',
      animal: saved,
      performedBy: this.session.username(),
      previousStatus: null,
      fromEnclosureId,
    });
    return saved;
  }

  async register(input: NewAnimal): Promise<Animal> {
    await delay();
    if (!can(this.session.role(), 'register')) {
      throw forbidden('register');
    }
    if (!input.name.trim() || !input.species.trim()) {
      throw invalidAnimal();
    }
    if (!ENCLOSURES.some((e) => e.id === input.enclosureId)) {
      throw unknownEnclosure();
    }
    const animal: Animal = {
      ...input,
      name: input.name.trim(),
      species: input.species.trim(),
      id: crypto.randomUUID(),
      status: 'HEALTHY',
      createdBy: this.session.username(),
      updatedBy: this.session.username(),
    };
    const saved = this.save(animal);
    this.events.publish({
      eventType: 'ANIMAL_REGISTERED',
      animal: saved,
      performedBy: this.session.username(),
      previousStatus: null,
      fromEnclosureId: null,
    });
    return saved;
  }

  async listEnclosures(): Promise<Enclosure[]> {
    return [...ENCLOSURES];
  }

  private find(id: string): Animal {
    const animal = this.animals.get(id);
    if (!animal) {
      throw notFound();
    }
    return animal;
  }

  private save(animal: Animal): Animal {
    this.animals = new Map(this.animals).set(animal.id, animal);
    return animal;
  }
}

function delay(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, LATENCY_MS));
}
