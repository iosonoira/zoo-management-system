import { Animal, AnimalStatus, Enclosure, NewAnimal } from '../models/animal';

/**
 * Port for the `/animals` REST contract. The mock adapter implements it today;
 * an HttpClient + OIDC adapter can replace it without touching the UI.
 */
export abstract class AnimalApi {
  abstract listAll(): Promise<Animal[]>;
  abstract getById(id: string): Promise<Animal>;
  /** `name` is used only to write error copy that names the animal. */
  abstract updateStatus(id: string, status: AnimalStatus, name?: string): Promise<Animal>;
  abstract transfer(id: string, targetEnclosureId: string, name?: string): Promise<Animal>;
  abstract register(input: NewAnimal): Promise<Animal>;
  abstract listEnclosures(): Promise<Enclosure[]>;
}
