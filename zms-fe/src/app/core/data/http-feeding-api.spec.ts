import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { Feeding, FeedingPlan, NewFeeding, NewFeedingPlan } from '../models/feeding';
import { FeedingApi } from './feeding-api';
import { ApiError } from './api-error';
import {
  deceasedPlan,
  forbidden,
  invalidFeeding,
  invalidPlan,
  planChanged,
  planNotActive,
  planNotFound,
} from './api-errors';
import { FEEDING_PAGE_SIZE, HttpFeedingApi, PLAN_PAGE_SIZE } from './http-feeding-api';

const BASE = environment.api.feeding;

function feedingPlan(overrides: Partial<FeedingPlan> = {}): FeedingPlan {
  return {
    id: 'plan-1',
    animalId: 'animal-1',
    food: 'Herbivore pellets',
    quantityGrams: 500,
    feedingTimes: ['08:00', '14:00', '18:00'],
    notes: 'Soak pellets before feeding',
    status: 'ACTIVE',
    startedOn: '2026-09-15',
    endedOn: null,
    createdBy: 'vet.bianchi',
    updatedBy: null,
    ...overrides,
  };
}

function feeding(overrides: Partial<Feeding> = {}): Feeding {
  return {
    id: 'feeding-1',
    planId: 'plan-1',
    fedAt: '2026-09-15T08:30:00Z',
    quantityGrams: 500,
    notes: 'All eaten',
    recordedBy: 'keeper.conti',
    ...overrides,
  };
}

function newFeedingPlan(overrides: Partial<NewFeedingPlan> = {}): NewFeedingPlan {
  return {
    animalId: 'animal-1',
    food: 'Herbivore pellets',
    quantityGrams: 500,
    feedingTimes: ['08:00', '14:00', '18:00'],
    notes: 'Soak pellets before feeding',
    ...overrides,
  };
}

function newFeeding(overrides: Partial<NewFeeding> = {}): NewFeeding {
  return {
    fedAt: '2026-09-15T08:30:00Z',
    quantityGrams: 500,
    notes: 'All eaten',
    ...overrides,
  };
}

describe('HttpFeedingApi', () => {
  let api: FeedingApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: FeedingApi, useClass: HttpFeedingApi },
      ],
    });
    api = TestBed.inject(FeedingApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for the first page with animalId in the URL', async () => {
    const pending = api.listPlans('animal-1');
    const request = http.expectOne(
      `${BASE}/feeding-plans?animalId=animal-1&page=0&size=${PLAN_PAGE_SIZE}`,
    );
    expect(request.request.method).toBe('GET');
    request.flush({ items: [feedingPlan()], page: 0, size: PLAN_PAGE_SIZE, total: 1 });
    await expect(pending).resolves.toEqual([feedingPlan()]);
  });

  it('keeps paging until it has all plans', async () => {
    const first = Array.from({ length: PLAN_PAGE_SIZE }, (_, i) =>
      feedingPlan({ id: `page0-${i}`, food: `Food${i}` }),
    );
    const pending = api.listPlans('animal-1');

    http
      .expectOne(`${BASE}/feeding-plans?animalId=animal-1&page=0&size=${PLAN_PAGE_SIZE}`)
      .flush({ items: first, page: 0, size: PLAN_PAGE_SIZE, total: PLAN_PAGE_SIZE + 2 });

    const second = [
      feedingPlan({ id: 'page1-0', food: 'Food100' }),
      feedingPlan({ id: 'page1-1', food: 'Food101' }),
    ];
    const request = await vi.waitFor(() =>
      http.expectOne(`${BASE}/feeding-plans?animalId=animal-1&page=1&size=${PLAN_PAGE_SIZE}`),
    );
    request.flush({ items: second, page: 1, size: PLAN_PAGE_SIZE, total: PLAN_PAGE_SIZE + 2 });

    await expect(pending).resolves.toHaveLength(PLAN_PAGE_SIZE + 2);
  });

  it('stops paging when a page comes back short', async () => {
    const pending = api.listPlans('animal-1');
    http
      .expectOne(`${BASE}/feeding-plans?animalId=animal-1&page=0&size=${PLAN_PAGE_SIZE}`)
      .flush({ items: [feedingPlan()], page: 0, size: PLAN_PAGE_SIZE, total: 999 });
    await expect(pending).resolves.toHaveLength(1);
  });

  it('sends createPlan POST to /feeding-plans with the body', async () => {
    const input = newFeedingPlan();
    const pending = api.createPlan(input);
    const request = http.expectOne(`${BASE}/feeding-plans`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(input);
    request.flush(feedingPlan());
    await expect(pending).resolves.toEqual(feedingPlan());
  });

  it('updates plan status with PUT to /feeding-plans/:id/status', async () => {
    const pending = api.updatePlanStatus('plan-1', 'SUSPENDED');
    const request = http.expectOne(`${BASE}/feeding-plans/plan-1/status`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ status: 'SUSPENDED' });
    request.flush(feedingPlan({ status: 'SUSPENDED' }));
    await expect(pending).resolves.toEqual(feedingPlan({ status: 'SUSPENDED' }));
  });

  it('lists feedings with page and size parameters', async () => {
    const pending = api.listFeedings('plan-1', 0);
    const request = http.expectOne(
      `${BASE}/feeding-plans/plan-1/feedings?page=0&size=${FEEDING_PAGE_SIZE}`,
    );
    expect(request.request.method).toBe('GET');
    request.flush({ items: [feeding()], page: 0, size: FEEDING_PAGE_SIZE, total: 1 });
    await expect(pending).resolves.toEqual({
      items: [feeding()],
      page: 0,
      size: FEEDING_PAGE_SIZE,
      total: 1,
    });
  });

  it('records a feeding with POST to /feeding-plans/:id/feedings', async () => {
    const input = newFeeding();
    const pending = api.recordFeeding('plan-1', input);
    const request = http.expectOne(`${BASE}/feeding-plans/plan-1/feedings`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(input);
    request.flush(feeding());
    await expect(pending).resolves.toEqual(feeding());
  });

  it('records a feeding with fedAt null', async () => {
    const input = newFeeding({ fedAt: null });
    const pending = api.recordFeeding('plan-1', input);
    const request = http.expectOne(`${BASE}/feeding-plans/plan-1/feedings`);
    expect(request.request.body).toEqual({ fedAt: null, quantityGrams: 500, notes: 'All eaten' });
    request.flush(feeding());
    await expect(pending).resolves.toEqual(feeding());
  });

  it('maps 400 on createPlan to invalidPlan copy', async () => {
    const pending = api.createPlan(newFeedingPlan());
    http
      .expectOne(`${BASE}/feeding-plans`)
      .flush({ message: 'Invalid data' }, { status: 400, statusText: 'Bad Request' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(400);
      expect(error.message).toBe(invalidPlan().message);
      return true;
    });
  });

  it('maps 400 on recordFeeding to invalidFeeding copy', async () => {
    const pending = api.recordFeeding('plan-1', newFeeding());
    http
      .expectOne(`${BASE}/feeding-plans/plan-1/feedings`)
      .flush({ message: 'Invalid data' }, { status: 400, statusText: 'Bad Request' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(400);
      expect(error.message).toBe(invalidFeeding().message);
      return true;
    });
  });

  it('maps 403 on recordFeeding to forbidden copy', async () => {
    const pending = api.recordFeeding('plan-1', newFeeding());
    http
      .expectOne(`${BASE}/feeding-plans/plan-1/feedings`)
      .flush({ message: 'Insufficient role' }, { status: 403, statusText: 'Forbidden' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(403);
      expect(error.message).toBe(forbidden('recordFeeding').message);
      return true;
    });
  });

  it('maps 404 to planNotFound', async () => {
    const pending = api.listFeedings('missing', 0);
    http
      .expectOne(`${BASE}/feeding-plans/missing/feedings?page=0&size=${FEEDING_PAGE_SIZE}`)
      .flush({ message: 'Plan not found' }, { status: 404, statusText: 'Not Found' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(404);
      expect(error.message).toBe(planNotFound().message);
      return true;
    });
  });

  it('maps 409 to conflict copy', async () => {
    const pending = api.updatePlanStatus('plan-1', 'SUSPENDED');
    http
      .expectOne(`${BASE}/feeding-plans/plan-1/status`)
      .flush({ message: 'Concurrent update' }, { status: 409, statusText: 'Conflict' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(409);
      expect(error.message).toContain('this feeding plan');
      return true;
    });
  });

  it('maps 422 on createPlan to deceasedPlan copy', async () => {
    const pending = api.createPlan(newFeedingPlan());
    http
      .expectOne(`${BASE}/feeding-plans`)
      .flush({ message: 'Deceased animal' }, { status: 422, statusText: 'Unprocessable Entity' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(422);
      expect(error.message).toBe(deceasedPlan().message);
      return true;
    });
  });

  it('maps 422 on recordFeeding to planNotActive copy', async () => {
    const pending = api.recordFeeding('plan-1', newFeeding());
    http
      .expectOne(`${BASE}/feeding-plans/plan-1/feedings`)
      .flush({ message: 'Plan not active' }, { status: 422, statusText: 'Unprocessable Entity' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(422);
      expect(error.message).toBe(planNotActive().message);
      return true;
    });
  });

  it('maps 422 on updatePlanStatus to planChanged copy', async () => {
    const pending = api.updatePlanStatus('plan-1', 'SUSPENDED');
    http
      .expectOne(`${BASE}/feeding-plans/plan-1/status`)
      .flush({ message: 'Plan changed' }, { status: 422, statusText: 'Unprocessable Entity' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(422);
      expect(error.message).toBe(planChanged().message);
      return true;
    });
  });

  it('maps network error to status 500', async () => {
    const pending = api.listPlans('animal-1');
    http
      .expectOne(`${BASE}/feeding-plans?animalId=animal-1&page=0&size=${PLAN_PAGE_SIZE}`)
      .error(new ProgressEvent('network error'));

    await expect(pending).rejects.toMatchObject({ status: 500 });
  });

  it('maps 401 to sessionExpired copy', async () => {
    const pending = api.createPlan(newFeedingPlan());
    http
      .expectOne(`${BASE}/feeding-plans`)
      .flush({ message: 'Authentication required' }, { status: 401, statusText: 'Unauthorized' });

    await expect(pending).rejects.toMatchObject({ status: 401 });
  });

  it('maps 403 on createPlan to createFeedingPlan copy', async () => {
    const pending = api.createPlan(newFeedingPlan());
    http
      .expectOne(`${BASE}/feeding-plans`)
      .flush({ message: 'Insufficient role' }, { status: 403, statusText: 'Forbidden' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(403);
      expect(error.message).toBe(forbidden('createFeedingPlan').message);
      return true;
    });
  });
});
