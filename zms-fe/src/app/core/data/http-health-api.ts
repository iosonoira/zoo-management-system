import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  MedicalRecord,
  MedicalRecordDetail,
  NewMedicalRecord,
  Treatment,
  TreatmentStatus,
} from '../models/health';
import { HealthApi } from './health-api';
import { ApiError } from './api-error';
import { collectPages, Page } from './page';
import {
  conflict,
  forbidden,
  invalidRecord,
  invalidTreatment,
  recordNotFound,
  serverError,
  sessionExpired,
  treatmentChanged,
  treatmentNotFound,
} from './api-errors';

interface RequestContext {
  readonly action?: 'createMedicalRecord' | 'prescribeTreatment' | 'updateTreatmentStatus';
  readonly target?: 'record' | 'treatment';
}

/** `ListMedicalRecordsUseCase.MAX_PAGE_SIZE`; a larger value is rejected with 400. */
export const RECORD_PAGE_SIZE = 100;

/**
 * Live adapter for the health-service REST contract. Responses are typed, not mapped
 * (field names match the interfaces).
 *
 * Error bodies are discarded: the backend's `message` is either generic or technical,
 * so the status code is mapped to the copy in `api-errors.ts` instead.
 */
export class HttpHealthApi extends HealthApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.api.health}/medical-records`;

  async listRecords(animalId: string): Promise<MedicalRecord[]> {
    return collectPages(
      (page: number) =>
        this.request<Page<MedicalRecord>>(() =>
          this.http.get<Page<MedicalRecord>>(
            `${this.base}?animalId=${animalId}&page=${page}&size=${RECORD_PAGE_SIZE}`,
          ),
        ),
      RECORD_PAGE_SIZE,
    );
  }

  getRecord(id: string): Promise<MedicalRecordDetail> {
    return this.request(() => this.http.get<MedicalRecordDetail>(`${this.base}/${id}`), {
      target: 'record',
    });
  }

  createRecord(input: NewMedicalRecord): Promise<MedicalRecord> {
    return this.request(() => this.http.post<MedicalRecord>(this.base, input), {
      action: 'createMedicalRecord',
      target: 'record',
    });
  }

  prescribe(recordId: string, description: string): Promise<Treatment> {
    return this.request(
      () =>
        this.http.post<Treatment>(`${this.base}/${recordId}/treatments`, {
          description,
        }),
      { action: 'prescribeTreatment', target: 'record' },
    );
  }

  updateTreatmentStatus(id: string, status: TreatmentStatus): Promise<Treatment> {
    return this.request(
      () =>
        this.http.put<Treatment>(`${environment.api.health}/treatments/${id}/status`, { status }),
      {
        action: 'updateTreatmentStatus',
        target: 'treatment',
      },
    );
  }

  private async request<T>(
    send: () => import('rxjs').Observable<T>,
    context: RequestContext = {},
  ): Promise<T> {
    try {
      return await firstValueFrom(send());
    } catch (error) {
      throw toApiError(error, context);
    }
  }
}

function toApiError(error: unknown, context: RequestContext): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return serverError();
  }

  switch (error.status) {
    case 400:
      return context.action === 'createMedicalRecord' ? invalidRecord() : invalidTreatment();
    case 401:
      return sessionExpired();
    case 403:
      return forbidden(context.action ?? 'createMedicalRecord');
    case 404:
      return context.target === 'treatment' ? treatmentNotFound() : recordNotFound();
    case 409:
      return conflict(context.target === 'treatment' ? 'this treatment' : 'this medical record');
    case 422:
      return treatmentChanged();
    default:
      // Includes status 0, which is what a CORS rejection or an unreachable host looks like.
      return serverError();
  }
}
