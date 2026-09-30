import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { Notification, NotificationQuery } from '../models/notification';
import { NotificationApi } from './notification-api';
import { ApiError } from './api-error';
import {
  forbidden,
  notificationNotFound,
  serverError,
  sessionExpired,
} from './api-errors';
import { NOTIFICATION_PAGE_SIZE } from './notification-api';
import { HttpNotificationApi } from './http-notification-api';

const BASE = environment.api.notification;

function notification(overrides: Partial<Notification> = {}): Notification {
  return {
    id: 'notif-1',
    animalId: 'animal-1',
    eventType: 'ANIMAL_REGISTERED',
    severity: 'INFO',
    message: 'Test notification',
    occurredAt: '2026-09-30T10:00:00Z',
    performedBy: 'admin.rossi',
    name: 'Test Animal',
    species: 'Test Species',
    dangerous: false,
    previousStatus: null,
    newStatus: null,
    fromEnclosureId: null,
    toEnclosureId: 'enc-1',
    acknowledgedBy: null,
    acknowledgedAt: null,
    ...overrides,
  };
}

describe('HttpNotificationApi', () => {
  let api: NotificationApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NotificationApi, useClass: HttpNotificationApi },
      ],
    });
    api = TestBed.inject(NotificationApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('list', () => {
    it('builds the query string with empty query', async () => {
      const pending = api.list({}, 0);
      const request = http.expectOne(`${BASE}/notifications?page=0&size=${NOTIFICATION_PAGE_SIZE}`);
      expect(request.request.method).toBe('GET');
      request.flush({
        items: [notification()],
        page: 0,
        size: NOTIFICATION_PAGE_SIZE,
        total: 1,
      });
      await expect(pending).resolves.toEqual({
        items: [notification()],
        page: 0,
        size: NOTIFICATION_PAGE_SIZE,
        total: 1,
      });
    });

    it('includes animalId when set', async () => {
      const pending = api.list({ animalId: 'animal-1' }, 0);
      const request = http.expectOne(
        `${BASE}/notifications?animalId=animal-1&page=0&size=${NOTIFICATION_PAGE_SIZE}`,
      );
      expect(request.request.method).toBe('GET');
      request.flush({
        items: [notification()],
        page: 0,
        size: NOTIFICATION_PAGE_SIZE,
        total: 1,
      });
      await expect(pending).resolves.toBeTruthy();
    });

    it('includes severities with multiple values', async () => {
      const query: NotificationQuery = {
        severities: ['WARNING', 'CRITICAL'],
      };
      const pending = api.list(query, 0);
      const request = http.expectOne(
        `${BASE}/notifications?severity=WARNING&severity=CRITICAL&page=0&size=${NOTIFICATION_PAGE_SIZE}`,
      );
      expect(request.request.method).toBe('GET');
      request.flush({
        items: [notification()],
        page: 0,
        size: NOTIFICATION_PAGE_SIZE,
        total: 1,
      });
      await expect(pending).resolves.toBeTruthy();
    });

    it('includes open=true when openOnly is true', async () => {
      const pending = api.list({ openOnly: true }, 0);
      const request = http.expectOne(
        `${BASE}/notifications?open=true&page=0&size=${NOTIFICATION_PAGE_SIZE}`,
      );
      expect(request.request.method).toBe('GET');
      request.flush({
        items: [notification()],
        page: 0,
        size: NOTIFICATION_PAGE_SIZE,
        total: 1,
      });
      await expect(pending).resolves.toBeTruthy();
    });

    it('omits open parameter when openOnly is false', async () => {
      const pending = api.list({ openOnly: false }, 0);
      const request = http.expectOne(
        `${BASE}/notifications?page=0&size=${NOTIFICATION_PAGE_SIZE}`,
      );
      expect(request.request.method).toBe('GET');
      request.flush({
        items: [notification()],
        page: 0,
        size: NOTIFICATION_PAGE_SIZE,
        total: 1,
      });
      await expect(pending).resolves.toBeTruthy();
    });

    it('combines animalId, severities, and openOnly', async () => {
      const query: NotificationQuery = {
        animalId: 'animal-1',
        severities: ['WARNING', 'CRITICAL'],
        openOnly: true,
      };
      const pending = api.list(query, 1);
      const request = http.expectOne(
        `${BASE}/notifications?animalId=animal-1&severity=WARNING&severity=CRITICAL&open=true&page=1&size=${NOTIFICATION_PAGE_SIZE}`,
      );
      expect(request.request.method).toBe('GET');
      request.flush({
        items: [notification()],
        page: 1,
        size: NOTIFICATION_PAGE_SIZE,
        total: 5,
      });
      await expect(pending).resolves.toBeTruthy();
    });
  });

  describe('countOpen', () => {
    it('sends two size-1 queries in parallel and reads the total of each', async () => {
      const pending = api.countOpen();
      const attention = http.expectOne(
        `${BASE}/notifications?open=true&severity=WARNING&severity=CRITICAL&page=0&size=1`,
      );
      const critical = http.expectOne(
        `${BASE}/notifications?open=true&severity=CRITICAL&page=0&size=1`,
      );
      expect(attention.request.method).toBe('GET');
      expect(critical.request.method).toBe('GET');
      attention.flush({ items: [notification({ severity: 'WARNING' })], page: 0, size: 1, total: 42 });
      critical.flush({ items: [notification({ severity: 'CRITICAL' })], page: 0, size: 1, total: 5 });
      await expect(pending).resolves.toEqual({ attention: 42, critical: 5 });
    });

    it('counts zero when nothing is open', async () => {
      const pending = api.countOpen();
      const empty = { items: [], page: 0, size: 1, total: 0 };
      http
        .expectOne(`${BASE}/notifications?open=true&severity=WARNING&severity=CRITICAL&page=0&size=1`)
        .flush(empty);
      http.expectOne(`${BASE}/notifications?open=true&severity=CRITICAL&page=0&size=1`).flush(empty);
      await expect(pending).resolves.toEqual({ attention: 0, critical: 0 });
    });

    it('fails when either query fails', async () => {
      const pending = api.countOpen();
      http
        .expectOne(`${BASE}/notifications?open=true&severity=WARNING&severity=CRITICAL&page=0&size=1`)
        .flush({ items: [], page: 0, size: 1, total: 1 });
      http
        .expectOne(`${BASE}/notifications?open=true&severity=CRITICAL&page=0&size=1`)
        .flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });
      await expect(pending).rejects.toSatisfy((error: ApiError) => {
        expect(error.status).toBe(401);
        return true;
      });
    });
  });

  describe('acknowledge', () => {
    it('sends PUT to /notifications/:id/acknowledge with null body', async () => {
      const pending = api.acknowledge('notif-1');
      const request = http.expectOne(`${BASE}/notifications/notif-1/acknowledge`);
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toBeNull();
      request.flush(notification({ acknowledgedBy: 'keeper.conti', acknowledgedAt: '2026-09-30T10:05:00Z' }));
      await expect(pending).resolves.toEqual(
        notification({ acknowledgedBy: 'keeper.conti', acknowledgedAt: '2026-09-30T10:05:00Z' }),
      );
    });
  });

  describe('error mapping', () => {
    it('maps 401 to sessionExpired', async () => {
      const pending = api.list({}, 0);
      http.expectOne(`${BASE}/notifications?page=0&size=${NOTIFICATION_PAGE_SIZE}`).flush(
        { message: 'Unauthorized' },
        { status: 401, statusText: 'Unauthorized' },
      );
      await expect(pending).rejects.toSatisfy((error: ApiError) => {
        expect(error.status).toBe(401);
        expect(error.message).toBe(sessionExpired().message);
        return true;
      });
    });

    it('maps 403 to forbidden with acknowledgeNotification', async () => {
      const pending = api.acknowledge('notif-1');
      http.expectOne(`${BASE}/notifications/notif-1/acknowledge`).flush(
        { message: 'Forbidden' },
        { status: 403, statusText: 'Forbidden' },
      );
      await expect(pending).rejects.toSatisfy((error: ApiError) => {
        expect(error.status).toBe(403);
        expect(error.message).toBe(forbidden('acknowledgeNotification').message);
        return true;
      });
    });

    it('maps 404 to notificationNotFound', async () => {
      const pending = api.acknowledge('missing');
      http.expectOne(`${BASE}/notifications/missing/acknowledge`).flush(
        { message: 'Not Found' },
        { status: 404, statusText: 'Not Found' },
      );
      await expect(pending).rejects.toSatisfy((error: ApiError) => {
        expect(error.status).toBe(404);
        expect(error.message).toBe(notificationNotFound().message);
        return true;
      });
    });

    it('maps 400 to serverError', async () => {
      const pending = api.list({}, 0);
      http.expectOne(`${BASE}/notifications?page=0&size=${NOTIFICATION_PAGE_SIZE}`).flush(
        { message: 'Bad Request' },
        { status: 400, statusText: 'Bad Request' },
      );
      await expect(pending).rejects.toSatisfy((error: ApiError) => {
        expect(error.status).toBe(500);
        expect(error.message).toBe(serverError().message);
        return true;
      });
    });

    it('maps 500 to serverError', async () => {
      const pending = api.list({}, 0);
      http.expectOne(`${BASE}/notifications?page=0&size=${NOTIFICATION_PAGE_SIZE}`).flush(
        { message: 'Server Error' },
        { status: 500, statusText: 'Internal Server Error' },
      );
      await expect(pending).rejects.toSatisfy((error: ApiError) => {
        expect(error.status).toBe(500);
        expect(error.message).toBe(serverError().message);
        return true;
      });
    });

    it('maps status 0 to serverError', async () => {
      const pending = api.list({}, 0);
      http.expectOne(`${BASE}/notifications?page=0&size=${NOTIFICATION_PAGE_SIZE}`).error(
        new ProgressEvent('error'),
      );
      await expect(pending).rejects.toSatisfy((error: ApiError) => {
        expect(error.status).toBe(500);
        expect(error.message).toBe(serverError().message);
        return true;
      });
    });
  });
});
