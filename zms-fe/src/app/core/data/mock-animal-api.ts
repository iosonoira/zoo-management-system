import { inject } from '@angular/core';
import { Animal, AnimalStatus, Enclosure, canBeTransferred, canTransitionTo } from '../models/animal';
import { can } from '../models/permissions';
import { Session } from '../session/session';
import { AnimalApi, ApiError } from './animal-api';
import { DEMO_ANIMALS } from './demo-data';
import { ENCLOSURES } from './enclosure-directory';

const LATENCY_MS = 380;

/**
 * In-memory adapter that enforces the same rules and status codes as
 * `animal-service`: role checks (403), unknown ids (404), transfer of a
 * deceased animal (400) and invalid status transitions (422).
 */
export class MockAnimalApi extends AnimalApi {
  private readonly session = inject(Session);
  private animals = new Map<string, Animal>(DEMO_ANIMALS.map((a) => [a.id, a]));

  async listAll(): Promise<Animal[]> {
    await delay();
    return [...this.animals.values()];
  }

  async getById(id: string): Promise<Animal> {
    await delay();
    return this.find(id);
  }

  async updateStatus(id: string, status: AnimalStatus): Promise<Animal> {
    await delay();
    if (!can(this.session.role(), 'updateStatus')) {
      throw new ApiError(403, 'Only vets and admins can change an animal’s status.');
    }
    const animal = this.find(id);
    if (!canTransitionTo(animal, status)) {
      throw new ApiError(
        422,
        animal.status === 'DECEASED'
          ? `${animal.name} is recorded as deceased. The status can no longer change.`
          : `${animal.name} already has this status.`,
      );
    }
    return this.save({ ...animal, status, updatedBy: this.session.username() });
  }

  async transfer(id: string, targetEnclosureId: string): Promise<Animal> {
    await delay();
    if (!can(this.session.role(), 'transfer')) {
      throw new ApiError(403, 'Only keepers and admins can transfer animals.');
    }
    const animal = this.find(id);
    if (!canBeTransferred(animal)) {
      throw new ApiError(400, `${animal.name} is recorded as deceased and can’t be transferred.`);
    }
    if (!ENCLOSURES.some((e) => e.id === targetEnclosureId)) {
      throw new ApiError(400, 'That enclosure doesn’t exist.');
    }
    return this.save({ ...animal, enclosureId: targetEnclosureId, updatedBy: this.session.username() });
  }

  async listEnclosures(): Promise<Enclosure[]> {
    return [...ENCLOSURES];
  }

  private find(id: string): Animal {
    const animal = this.animals.get(id);
    if (!animal) {
      throw new ApiError(404, 'No animal with this tag exists.');
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
