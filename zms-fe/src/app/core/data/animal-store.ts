import { Service, computed, inject, signal } from '@angular/core';
import { Animal, AnimalStatus, Enclosure } from '../models/animal';
import { AnimalApi, ApiError } from './animal-api';

export type LoadState = 'idle' | 'loading' | 'ready' | 'error';

/** Shared roster state for the animal screens. */
@Service()
export class AnimalStore {
  private readonly api = inject(AnimalApi);

  private readonly animalMap = signal<ReadonlyMap<string, Animal>>(new Map());
  private readonly enclosureList = signal<readonly Enclosure[]>([]);

  readonly state = signal<LoadState>('idle');
  readonly animals = computed(() => [...this.animalMap().values()]);
  readonly enclosures = this.enclosureList.asReadonly();
  readonly enclosureById = computed(
    () => new Map(this.enclosureList().map((e) => [e.id, e] as const)),
  );

  async load(): Promise<void> {
    if (this.state() === 'loading' || this.state() === 'ready') {
      return;
    }
    this.state.set('loading');
    try {
      const [animals, enclosures] = await Promise.all([
        this.api.listAll(),
        this.api.listEnclosures(),
      ]);
      this.animalMap.set(new Map(animals.map((a) => [a.id, a])));
      this.enclosureList.set(enclosures);
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  retry(): Promise<void> {
    this.state.set('idle');
    return this.load();
  }

  byId(id: string): Animal | undefined {
    return this.animalMap().get(id);
  }

  enclosureName(id: string): string {
    return this.enclosureById().get(id)?.name ?? `Enclosure ${id.slice(0, 4).toUpperCase()}`;
  }

  updateStatus(id: string, status: AnimalStatus): Promise<Animal> {
    return this.api.updateStatus(id, status, this.byId(id)?.name);
  }

  transfer(id: string, enclosureId: string): Promise<Animal> {
    return this.api.transfer(id, enclosureId, this.byId(id)?.name);
  }

  /** Applies a server-confirmed animal to local state. */
  apply(animal: Animal): void {
    this.animalMap.update((map) => new Map(map).set(animal.id, animal));
  }
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return 'Something went wrong on our side. Check your connection and try again.';
}
