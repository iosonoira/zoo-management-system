import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Animal, AnimalStatus, Enclosure, NewAnimal } from '../models/animal';
import { AnimalApi } from './animal-api';
import { ApiError } from './api-error';
import { collectPages, Page } from './page';
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
  private readonly base = `${environment.api.animal}/animals`;

  async listAll(): Promise<Animal[]> {
    return collectPages(
      (page: number) =>
        this.request<Page<Animal>>(() =>
          this.http.get<Page<Animal>>(`${this.base}?page=${page}&size=${PAGE_SIZE}`),
        ),
      PAGE_SIZE,
    );
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
    return this.request(() => this.http.get<Enclosure[]>(`${environment.api.animal}/enclosures`));
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
