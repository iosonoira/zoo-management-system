import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Notification, NotificationQuery, OpenCount, Severity } from '../models/notification';
import { NotificationApi, NOTIFICATION_PAGE_SIZE } from './notification-api';
import { ApiError } from './api-error';
import { Page } from './page';
import { forbidden, notificationNotFound, serverError, sessionExpired } from './api-errors';

/**
 * Live adapter for the notification-service REST contract. Responses are typed, not mapped
 * (field names match the interfaces).
 *
 * Error bodies are discarded: the backend's `message` is either generic or technical,
 * so the status code is mapped to the copy in `api-errors.ts` instead.
 */
export class HttpNotificationApi extends NotificationApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.api.notification}/notifications`;

  async list(query: NotificationQuery, page: number): Promise<Page<Notification>> {
    let params = new HttpParams();

    if (query.animalId) {
      params = params.set('animalId', query.animalId);
    }

    if (query.severities && query.severities.length > 0) {
      for (const severity of query.severities) {
        params = params.append('severity', severity);
      }
    }

    if (query.openOnly) {
      params = params.set('open', 'true');
    }

    params = params.set('page', page.toString()).set('size', NOTIFICATION_PAGE_SIZE.toString());

    return this.request(() => this.http.get<Page<Notification>>(this.base, { params }));
  }

  async countOpen(): Promise<OpenCount> {
    const [attention, critical] = await Promise.all([
      this.countOpenWith(['WARNING', 'CRITICAL']),
      this.countOpenWith(['CRITICAL']),
    ]);
    return { attention, critical };
  }

  async acknowledge(id: string): Promise<Notification> {
    return this.request(() => this.http.put<Notification>(`${this.base}/${id}/acknowledge`, null));
  }

  /** A page of one item is enough: only its `total` is read. */
  private async countOpenWith(severities: readonly Severity[]): Promise<number> {
    let params = new HttpParams().set('open', 'true');
    for (const severity of severities) {
      params = params.append('severity', severity);
    }
    params = params.set('page', '0').set('size', '1');

    const page = await this.request(() => this.http.get<Page<Notification>>(this.base, { params }));
    return page.total;
  }

  private async request<T>(
    send: () => import('rxjs').Observable<T>,
  ): Promise<T> {
    try {
      return await firstValueFrom(send());
    } catch (error) {
      throw toApiError(error);
    }
  }
}

function toApiError(error: unknown): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return serverError();
  }

  switch (error.status) {
    case 401:
      return sessionExpired();
    case 403:
      return forbidden('acknowledgeNotification');
    case 404:
      return notificationNotFound();
    default:
      // Includes status 0, which is what a CORS rejection or an unreachable host looks like.
      return serverError();
  }
}
