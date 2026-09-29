import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Feeding, FeedingPlan, NewFeeding, NewFeedingPlan, PlanStatus } from '../models/feeding';
import { FeedingApi } from './feeding-api';
import { ApiError } from './api-error';
import { collectPages, Page } from './page';
import {
  conflict,
  deceasedPlan,
  forbidden,
  invalidFeeding,
  invalidPlan,
  planChanged,
  planNotActive,
  planNotFound,
  serverError,
  sessionExpired,
} from './api-errors';

interface RequestContext {
  readonly action?: 'createFeedingPlan' | 'updateFeedingPlanStatus' | 'recordFeeding';
}

/** The backend maximum; `listPlans` uses this to walk all pages. */
export const PLAN_PAGE_SIZE = 100;

/** The log shows the latest ten, then "Show earlier". */
export const FEEDING_PAGE_SIZE = 10;

/**
 * Live adapter for the feeding-service REST contract. Responses are typed, not mapped
 * (field names match the interfaces).
 *
 * Error bodies are discarded: the backend's `message` is either generic or technical,
 * so the status code is mapped to the copy in `api-errors.ts` instead.
 */
export class HttpFeedingApi extends FeedingApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.api.feeding}/feeding-plans`;

  async listPlans(animalId: string): Promise<FeedingPlan[]> {
    return collectPages(
      (page: number) =>
        this.request<Page<FeedingPlan>>(() =>
          this.http.get<Page<FeedingPlan>>(
            `${this.base}?animalId=${animalId}&page=${page}&size=${PLAN_PAGE_SIZE}`,
          ),
        ),
      PLAN_PAGE_SIZE,
    );
  }

  createPlan(input: NewFeedingPlan): Promise<FeedingPlan> {
    return this.request(() => this.http.post<FeedingPlan>(this.base, input), {
      action: 'createFeedingPlan',
    });
  }

  updatePlanStatus(id: string, status: PlanStatus): Promise<FeedingPlan> {
    return this.request(() => this.http.put<FeedingPlan>(`${this.base}/${id}/status`, { status }), {
      action: 'updateFeedingPlanStatus',
    });
  }

  listFeedings(planId: string, page: number): Promise<Page<Feeding>> {
    return this.request(() =>
      this.http.get<Page<Feeding>>(
        `${this.base}/${planId}/feedings?page=${page}&size=${FEEDING_PAGE_SIZE}`,
      ),
    );
  }

  recordFeeding(planId: string, input: NewFeeding): Promise<Feeding> {
    return this.request(() => this.http.post<Feeding>(`${this.base}/${planId}/feedings`, input), {
      action: 'recordFeeding',
    });
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
      return context.action === 'recordFeeding' ? invalidFeeding() : invalidPlan();
    case 401:
      return sessionExpired();
    case 403:
      return forbidden(context.action ?? 'createFeedingPlan');
    case 404:
      return planNotFound();
    case 409:
      return conflict('this feeding plan');
    case 422:
      if (context.action === 'createFeedingPlan') {
        return deceasedPlan();
      } else if (context.action === 'recordFeeding') {
        return planNotActive();
      } else {
        return planChanged();
      }
    default:
      // Includes status 0, which is what a CORS rejection or an unreachable host looks like.
      return serverError();
  }
}
