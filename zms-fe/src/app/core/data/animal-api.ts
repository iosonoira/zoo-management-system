import { Animal, AnimalStatus, Enclosure } from '../models/animal';

/** Every status `animal-service` can return, including the two the mock never raises. */
export type ApiErrorStatus = 400 | 401 | 403 | 404 | 409 | 422 | 500;

/** Error shape mirroring the HTTP statuses `animal-service` returns. */
export class ApiError extends Error {
  constructor(
    readonly status: ApiErrorStatus,
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
  /** `name` is used only to write error copy that names the animal. */
  abstract updateStatus(id: string, status: AnimalStatus, name?: string): Promise<Animal>;
  abstract transfer(id: string, targetEnclosureId: string, name?: string): Promise<Animal>;
  abstract listEnclosures(): Promise<Enclosure[]>;
}
