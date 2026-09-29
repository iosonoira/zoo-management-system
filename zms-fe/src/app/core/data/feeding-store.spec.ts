import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FeedingPlan, Feeding, NewFeedingPlan, NewFeeding, PlanStatus } from '../models/feeding';
import { FeedingApi } from './feeding-api';
import { FeedingStore } from './feeding-store';
import { LoadState } from './animal-store';
import { Page } from './page';

class FakeFeedingApi extends FeedingApi {
  calls = 0;
  listPlansResult: FeedingPlan[] = [];
  listFeedingsResult: Page<Feeding> = {
    items: [],
    page: 0,
    size: 10,
    total: 0,
  };
  deferred = new Map<string, { resolve: Function; reject: Function }>();

  async listPlans(): Promise<FeedingPlan[]> {
    this.calls++;
    const def = this.deferred.get('listPlans');
    if (def) {
      return new Promise((resolve, reject) => {
        this.deferred.set('listPlans', { resolve, reject });
      });
    }
    return this.listPlansResult;
  }

  async createPlan(input: NewFeedingPlan): Promise<FeedingPlan> {
    const plan: FeedingPlan = {
      id: 'created-' + crypto.randomUUID(),
      animalId: input.animalId,
      food: input.food,
      quantityGrams: input.quantityGrams,
      feedingTimes: input.feedingTimes,
      notes: input.notes,
      status: 'ACTIVE',
      startedOn: new Date().toISOString().split('T')[0],
      endedOn: null,
      createdBy: 'test.user',
      updatedBy: 'test.user',
    };
    return plan;
  }

  async updatePlanStatus(id: string, status: PlanStatus): Promise<FeedingPlan> {
    throw new Error('not used');
  }

  async listFeedings(planId: string, page: number): Promise<Page<Feeding>> {
    const def = this.deferred.get(`listFeedings-${planId}`);
    if (def) {
      return new Promise((resolve, reject) => {
        this.deferred.set(`listFeedings-${planId}`, { resolve, reject });
      });
    }
    return this.listFeedingsResult;
  }

  async recordFeeding(planId: string, input: NewFeeding): Promise<Feeding> {
    const feeding: Feeding = {
      id: 'created-' + crypto.randomUUID(),
      planId,
      fedAt: input.fedAt || new Date().toISOString(),
      quantityGrams: input.quantityGrams,
      notes: input.notes,
      recordedBy: 'test.user',
    };
    return feeding;
  }

  resolveDeferred(key: string, value: unknown): void {
    const def = this.deferred.get(key);
    if (def) {
      def.resolve(value);
      this.deferred.delete(key);
    }
  }

  rejectDeferred(key: string, error: unknown): void {
    const def = this.deferred.get(key);
    if (def) {
      def.reject(error);
      this.deferred.delete(key);
    }
  }
}

describe('FeedingStore', () => {
  let store: FeedingStore;
  let api: FakeFeedingApi;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        FeedingStore,
        { provide: FeedingApi, useClass: FakeFeedingApi },
        { provide: PLATFORM_ID, useValue: 'browser' },
      ],
    });
    store = TestBed.inject(FeedingStore);
    api = TestBed.inject(FeedingApi) as FakeFeedingApi;
  });

  describe('loading', () => {
    it('does not call api on server', async () => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          FeedingStore,
          { provide: FeedingApi, useClass: FakeFeedingApi },
          { provide: PLATFORM_ID, useValue: 'server' },
        ],
      });
      store = TestBed.inject(FeedingStore);
      api = TestBed.inject(FeedingApi) as FakeFeedingApi;

      await store.load('animal-1');
      expect(api.calls).toBe(0);
    });

    it('transitions idle → loading → ready', async () => {
      api.listPlansResult = [];
      expect(store.state()).toBe('idle');

      const load = store.load('animal-1');
      expect(store.state()).toBe('loading');

      await load;
      expect(store.state()).toBe('ready');
    });

    it('does not reload if same animal is already ready', async () => {
      api.listPlansResult = [];
      await store.load('animal-1');
      const calls = api.calls;

      await store.load('animal-1');
      expect(api.calls).toBe(calls);
    });

    it('resets on another animal', async () => {
      const plan1 = {
        id: 'plan-1',
        animalId: 'animal-1',
        food: 'Test',
        quantityGrams: 1000,
        feedingTimes: ['08:00'],
        notes: null,
        status: 'ACTIVE' as const,
        startedOn: '2026-09-15',
        endedOn: null,
        createdBy: 'test',
        updatedBy: null,
      };
      api.listPlansResult = [plan1];

      await store.load('animal-1');
      expect(store.plans().length).toBe(1);

      api.listPlansResult = [];
      await store.load('animal-2');
      expect(store.plans().length).toBe(0);
    });

    it('drops stale response', async () => {
      const plan1 = {
        id: 'plan-1',
        animalId: 'animal-1',
        food: 'Test',
        quantityGrams: 1000,
        feedingTimes: ['08:00'],
        notes: null,
        status: 'ACTIVE' as const,
        startedOn: '2026-09-15',
        endedOn: null,
        createdBy: 'test',
        updatedBy: null,
      };
      const plan2 = { ...plan1, id: 'plan-2', animalId: 'animal-2' };

      api.deferred.set('listPlans', { resolve: () => {}, reject: () => {} });
      const load1 = store.load('animal-1');
      api.resolveDeferred('listPlans', [plan1]);

      api.listPlansResult = [plan2];
      await store.load('animal-2');

      expect(store.plans()[0].animalId).toBe('animal-2');
    });
  });

  describe('retry', () => {
    it('reloads the current animal', async () => {
      api.listPlansResult = [];
      await store.load('animal-1');
      const calls = api.calls;

      api.listPlansResult = [];
      await store.retry();
      expect(api.calls).toBe(calls + 1);
    });

    it('returns resolved promise if no animal loaded', async () => {
      const result = await store.retry();
      expect(result).toBeUndefined();
    });
  });

  describe('feedings loading', () => {
    it('loads first page and marks ready', async () => {
      const feeding = {
        id: 'f-1',
        planId: 'plan-1',
        fedAt: '2026-09-15T08:30:00Z',
        quantityGrams: 1000,
        notes: null,
        recordedBy: 'test',
      };
      api.listFeedingsResult = {
        items: [feeding],
        page: 0,
        size: 10,
        total: 1,
      };

      await store.loadFeedings('plan-1');
      expect(store.feedingsState('plan-1')).toBe('ready');
      expect(store.feedings('plan-1')).toEqual([feeding]);
    });

    it('does not reload if already ready', async () => {
      api.listFeedingsResult = {
        items: [],
        page: 0,
        size: 10,
        total: 0,
      };

      await store.loadFeedings('plan-1');
      const calls = api.calls;

      await store.loadFeedings('plan-1');
      expect(api.calls).toBe(calls);
    });

    it('hasMoreFeedings returns false when all loaded', async () => {
      api.listFeedingsResult = {
        items: [
          {
            id: 'f-1',
            planId: 'plan-1',
            fedAt: '2026-09-15T08:30:00Z',
            quantityGrams: 1000,
            notes: null,
            recordedBy: 'test',
          },
        ],
        page: 0,
        size: 10,
        total: 1,
      };

      await store.loadFeedings('plan-1');
      expect(store.hasMoreFeedings('plan-1')).toBe(false);
    });

    it('hasMoreFeedings returns true when more exist', async () => {
      api.listFeedingsResult = {
        items: [
          {
            id: 'f-1',
            planId: 'plan-1',
            fedAt: '2026-09-15T08:30:00Z',
            quantityGrams: 1000,
            notes: null,
            recordedBy: 'test',
          },
        ],
        page: 0,
        size: 10,
        total: 15,
      };

      await store.loadFeedings('plan-1');
      expect(store.hasMoreFeedings('plan-1')).toBe(true);
    });
  });

  describe('feedings pagination', () => {
    it('appends next page', async () => {
      const f1 = {
        id: 'f-1',
        planId: 'plan-1',
        fedAt: '2026-09-15T16:00:00Z',
        quantityGrams: 1000,
        notes: null,
        recordedBy: 'test',
      };
      const f2 = {
        id: 'f-2',
        planId: 'plan-1',
        fedAt: '2026-09-15T14:00:00Z',
        quantityGrams: 1000,
        notes: null,
        recordedBy: 'test',
      };

      api.listFeedingsResult = {
        items: [f1],
        page: 0,
        size: 10,
        total: 2,
      };
      await store.loadFeedings('plan-1');

      api.listFeedingsResult = {
        items: [f2],
        page: 1,
        size: 10,
        total: 2,
      };
      await store.loadMoreFeedings('plan-1');

      expect(store.feedings('plan-1')).toEqual([f1, f2]);
    });

    it('skips duplicate ids when paging', async () => {
      const f1 = {
        id: 'f-1',
        planId: 'plan-1',
        fedAt: '2026-09-15T16:00:00Z',
        quantityGrams: 1000,
        notes: null,
        recordedBy: 'test',
      };
      const f2 = {
        id: 'f-2',
        planId: 'plan-1',
        fedAt: '2026-09-15T14:00:00Z',
        quantityGrams: 1000,
        notes: null,
        recordedBy: 'test',
      };

      api.listFeedingsResult = {
        items: [f1],
        page: 0,
        size: 10,
        total: 2,
      };
      await store.loadFeedings('plan-1');

      api.listFeedingsResult = {
        items: [f1, f2],
        page: 1,
        size: 10,
        total: 2,
      };
      await store.loadMoreFeedings('plan-1');

      const feedings = store.feedings('plan-1');
      expect(feedings.length).toBe(2);
      expect(feedings[0].id).toBe('f-1');
      expect(feedings[1].id).toBe('f-2');
    });
  });

  describe('writes', () => {
    it('createPlan inserts and marks feedings ready with empty total', async () => {
      const input: NewFeedingPlan = {
        animalId: 'animal-1',
        food: 'Test',
        quantityGrams: 1000,
        feedingTimes: ['08:00'],
        notes: null,
      };
      const plan = await store.createPlan(input);

      expect(store.feedings(plan.id)).toEqual([]);
      expect(store.feedingsState(plan.id)).toBe('ready');
    });

    it('recordFeeding inserts in fedAt order', async () => {
      const f1 = {
        id: 'f-1',
        planId: 'plan-1',
        fedAt: '2026-09-15T16:00:00Z',
        quantityGrams: 1000,
        notes: null,
        recordedBy: 'test',
      };
      const f2 = {
        id: 'f-2',
        planId: 'plan-1',
        fedAt: '2026-09-15T14:00:00Z',
        quantityGrams: 1000,
        notes: null,
        recordedBy: 'test',
      };

      api.listFeedingsResult = {
        items: [f1],
        page: 0,
        size: 10,
        total: 1,
      };
      await store.loadFeedings('plan-1');

      const recorded = await store.recordFeeding('plan-1', {
        fedAt: f2.fedAt,
        quantityGrams: 1000,
        notes: null,
      });

      const feedings = store.feedings('plan-1');
      expect(feedings[0].fedAt).toBe(f1.fedAt);
      expect(feedings[1].fedAt).toBe(f2.fedAt);
    });
  });

  describe('error handling', () => {
    it('sets error on failed load', async () => {
      api.deferred.set('listPlans', { resolve: () => {}, reject: () => {} });
      const load = store.load('animal-1');
      api.rejectDeferred('listPlans', new Error('Network error'));

      await load.catch(() => {});
      expect(store.state()).toBe('error');
    });

    it('retry resets state before reloading', async () => {
      api.deferred.set('listPlans', { resolve: () => {}, reject: () => {} });
      const load = store.load('animal-1');
      api.rejectDeferred('listPlans', new Error('Network error'));

      await load.catch(() => {});
      expect(store.state()).toBe('error');

      api.listPlansResult = [];
      await store.retry();
      expect(store.state()).toBe('ready');
    });
  });
});
