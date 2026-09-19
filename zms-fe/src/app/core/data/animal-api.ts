import { Animal, AnimalStatus, Enclosure } from '../models/animal';

/** Error shape mirroring the HTTP statuses `animal-service` returns. */
export class ApiError extends Error {
  constructor(
    readonly status: 400 | 403 | 404 | 422 | 500,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * Port for the `/animals` REST contract. The mock adapter implements it today;
 * an HttpClient + OIDC adapter can replace it without touching the UI.
 */
export abstract class AnimalApi {
  abstract listAll(): Promise<Animal[]>;
  abstract getById(id: string): Promise<Animal>;
  abstract updateStatus(id: string, status: AnimalStatus): Promise<Animal>;
  abstract transfer(id: string, targetEnclosureId: string): Promise<Animal>;
  abstract listEnclosures(): Promise<Enclosure[]>;
}
