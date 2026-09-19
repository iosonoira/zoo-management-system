/** Mirrors `it.zoo.animal.domain.enums.AnimalStatus`. */
export type AnimalStatus = 'HEALTHY' | 'UNDER_OBSERVATION' | 'IN_TREATMENT' | 'DECEASED';

/** Mirrors `it.zoo.animal.domain.enums.Habitat`. */
export type Habitat = 'TERRESTRIAL' | 'AQUATIC' | 'AMPHIBIOUS';

/** Keycloak realm roles enforced by `AnimalResource`. */
export type ZooRole = 'zoo-keeper' | 'zoo-vet' | 'zoo-admin';

/** Mirrors `AnimalResponse` from animal-service. */
export interface Animal {
  readonly id: string;
  readonly name: string;
  readonly species: string;
  readonly dangerous: boolean;
  readonly habitat: Habitat;
  readonly enclosureId: string;
  readonly arrivalDate: string;
  readonly status: AnimalStatus;
  readonly createdBy: string | null;
  readonly updatedBy: string | null;
}

/**
 * Enclosures exist only as UUIDs in the backend. The name and habitat live
 * in the frontend directory until an enclosure resource exists.
 */
export interface Enclosure {
  readonly id: string;
  readonly name: string;
  readonly habitat: Habitat;
}

export const STATUS_ORDER: readonly AnimalStatus[] = [
  'HEALTHY',
  'UNDER_OBSERVATION',
  'IN_TREATMENT',
  'DECEASED',
];

/** Same rule as `Animal.canTransitionTo` in the domain model. */
export function canTransitionTo(animal: Animal, target: AnimalStatus): boolean {
  return animal.status !== 'DECEASED' && animal.status !== target;
}

/** Same rule as `Animal.canBeTransferred` in the domain model. */
export function canBeTransferred(animal: Animal): boolean {
  return animal.status !== 'DECEASED';
}

/** Short, human-readable tag code derived from the animal's UUID. */
export function tagCode(animal: Pick<Animal, 'id'>): string {
  return animal.id.slice(0, 4).toUpperCase();
}
