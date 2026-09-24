import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Animal, AnimalStatus, Enclosure, NewAnimal } from '../models/animal';
import { AnimalApi, ApiError } from './animal-api';
import {
  conflict,
  deceasedTransfer,
  forbidden,
  invalidAnimal,
  notFound,
  sameStatus,
  serverError,
  sessionExpired,
} from './api-errors';

/** `ListAnimalsUseCase.MAX_PAGE_SIZE`; a larger value is rejected with 400. */
export const PAGE_SIZE = 100;

interface AnimalPageResponse {
  readonly items: readonly Animal[];
  readonly page: number;
  readonly size: number;
  readonly total: number;
}

/**
 * Live adapter for the `/animals` REST contract. `AnimalResponse` shares every field
 * name with the `Animal` interface and `arrivalDate` arrives as an ISO date string, so
 * responses are typed rather than mapped.
 *
 * Error bodies are discarded: the backend's `message` is either generic or technical,
 * so the status code is mapped to the copy in `api-errors.ts` instead.
 */
export class HttpAnimalApi extends AnimalApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/animals`;

  async listAll(): Promise<Animal[]> {
    const all: Animal[] = [];
    for (let page = 0; ; page++) {
      const response = await this.request<AnimalPageResponse>(() =>
        this.http.get<AnimalPageResponse>(`${this.base}?page=${page}&size=${PAGE_SIZE}`),
      );
      all.push(...response.items);
      // Stop on a short page as well as on a satisfied total: a roster that shrinks
      // between requests would otherwise loop forever.
      if (response.items.length < PAGE_SIZE || all.length >= response.total) {
        return all;
      }
    }
  }

  getById(id: string): Promise<Animal> {
    return this.request(() => this.http.get<Animal>(`${this.base}/${id}`));
  }

  updateStatus(id: string, status: AnimalStatus, name?: string): Promise<Animal> {
    return this.request(
      () => this.http.put<Animal>(`${this.base}/${id}/status`, { status }),
      { action: 'updateStatus', name },
    );
  }

  transfer(id: string, targetEnclosureId: string, name?: string): Promise<Animal> {
    return this.request(
      () => this.http.put<Animal>(`${this.base}/${id}/transfer`, { targetEnclosureId }),
      { action: 'transfer', name },
    );
  }

  register(input: NewAnimal): Promise<Animal> {
    return this.request(
      () => this.http.post<Animal>(this.base, input),
      { action: 'register' },
    );
  }

  listEnclosures(): Promise<Enclosure[]> {
    return this.request(() => this.http.get<Enclosure[]>(`${environment.apiBaseUrl}/enclosures`));
  }

  private async request<T>(
    send: () => import('rxjs').Observable<T>,
    context: { action?: 'updateStatus' | 'transfer' | 'register'; name?: string } = {},
  ): Promise<T> {
    try {
      return await firstValueFrom(send());
    } catch (error) {
      throw toApiError(error, context);
    }
  }
}

function toApiError(
  error: unknown,
  context: { action?: 'updateStatus' | 'transfer' | 'register'; name?: string },
): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return serverError();
  }
  const name = context.name ?? 'This animal';
  switch (error.status) {
    case 400:
      return context.action === 'register' ? invalidAnimal() : deceasedTransfer(name);
    case 401:
      return sessionExpired();
    case 403:
      return forbidden(context.action ?? 'updateStatus');
    case 404:
      return notFound();
    case 409:
      return conflict(name);
    case 422:
      return sameStatus(name);
    default:
      // Includes status 0, which is what a CORS rejection or an unreachable host looks like.
      return serverError();
  }
}
