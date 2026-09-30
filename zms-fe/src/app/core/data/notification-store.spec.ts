import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Notification, NotificationQuery, OpenCount, Severity } from '../models/notification';
import { ApiError } from './api-error';
import { NOTIFICATION_PAGE_SIZE, NotificationApi } from './notification-api';
import { NotificationStore } from './notification-store';
import { Page } from './page';

let sequence = 0;

/** Newest first: the fake serves rows in the order they are given, like the server. */
function notification(severity: Severity, overrides: Partial<Notification> = {}): Notification {
  sequence++;
  return {
    id: `n${sequence}`,
    animalId: `a${sequence}`,
    eventType: 'ANIMAL_STATUS_CHANGED',
    severity,
    message: `Message ${sequence}`,
    occurredAt: new Date(2026, 8, 30, 10, 0, 60 - sequence).toISOString(),
    performedBy: 'vet.bianchi',
    name: `Animal ${sequence}`,
    species: 'Test species',
    dangerous: false,
    previousStatus: 'HEALTHY',
    newStatus: 'IN_TREATMENT',
    fromEnclosureId: null,
    toEnclosureId: null,
    acknowledgedBy: null,
    acknowledgedAt: null,
    ...overrides,
  };
}

const acknowledged = (severity: Severity, by = 'keeper.conti') =>
  notification(severity, { acknowledgedBy: by, acknowledgedAt: '2026-09-30T09:00:00Z' });

class FakeNotificationApi extends NotificationApi {
  data: Notification[] = [];
  listCalls: { query: NotificationQuery; page: number }[] = [];
  countCalls = 0;
  ackCalls: string[] = [];

  count: OpenCount = { attention: 0, critical: 0 };
  failCount = false;
  failList = false;
  failAck: ApiError | null = null;
  /** Who the server says took a notification in charge; someone else’s name simulates losing the race. */
  ackedBy = 'keeper.conti';

  /** While set, list and count results are held until released. */
  holdList = false;
  holdCount = false;
  private heldList: (() => void)[] = [];
  private heldCount: (() => void)[] = [];

  async list(query: NotificationQuery, page: number): Promise<Page<Notification>> {
    this.listCalls.push({ query, page });
    if (this.failList) {
      throw new ApiError(500, 'down');
    }
    const items = this.data.filter(
      (n) =>
        (!query.severities?.length || query.severities.includes(n.severity)) &&
        (!query.openOnly || n.acknowledgedBy === null),
    );
    const result = {
      items: items.slice(page * NOTIFICATION_PAGE_SIZE, (page + 1) * NOTIFICATION_PAGE_SIZE),
      page,
      size: NOTIFICATION_PAGE_SIZE,
      total: items.length,
    };
    if (this.holdList) {
      await new Promise<void>((resolve) => this.heldList.push(resolve));
    }
    return result;
  }

  async countOpen(): Promise<OpenCount> {
    this.countCalls++;
    const result = this.count;
    if (this.holdCount) {
      await new Promise<void>((resolve) => this.heldCount.push(resolve));
    }
    if (this.failCount) {
      throw new ApiError(401, 'Sign in again.');
    }
    return result;
  }

  async acknowledge(id: string): Promise<Notification> {
    this.ackCalls.push(id);
    if (this.failAck) {
      throw this.failAck;
    }
    const found = this.data.find((n) => n.id === id);
    if (!found) {
      throw new ApiError(404, 'This notification no longer exists.');
    }
    if (found.acknowledgedBy) {
      return found;
    }
    const updated = { ...found, acknowledgedBy: this.ackedBy, acknowledgedAt: '2026-09-30T11:00:00Z' };
    this.data = this.data.map((n) => (n.id === id ? updated : n));
    return updated;
  }

  releaseList(index: number): void {
    this.heldList[index]();
  }

  releaseCount(index: number): void {
    this.heldCount[index]();
  }
}

function storeOn(platform = 'browser') {
  const api = new FakeNotificationApi();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: PLATFORM_ID, useValue: platform },
      { provide: NotificationApi, useValue: api },
      NotificationStore,
    ],
  });
  return { store: TestBed.inject(NotificationStore), api };
}

const flush = () => new Promise<void>((resolve) => setTimeout(resolve));

describe('NotificationStore', () => {
  beforeEach(() => {
    sequence = 0;
  });

  describe('open count', () => {
    it('is unknown until it is fetched, then holds attention and critical', async () => {
      const { store, api } = storeOn();
      api.count = { attention: 3, critical: 1 };
      expect(store.openCount()).toBeNull();
      await store.refreshCount();
      expect(store.openCount()).toEqual({ attention: 3, critical: 1 });
    });

    it('keeps critical at zero when nothing critical is open', async () => {
      const { store, api } = storeOn();
      api.count = { attention: 2, critical: 0 };
      await store.refreshCount();
      expect(store.openCount()?.critical).toBe(0);
    });

    it('fails silently: it never throws and keeps the last value', async () => {
      const { store, api } = storeOn();
      api.failCount = true;
      await expect(store.refreshCount()).resolves.toBeUndefined();
      expect(store.openCount()).toBeNull();

      api.failCount = false;
      api.count = { attention: 4, critical: 2 };
      await store.refreshCount();

      api.failCount = true;
      await expect(store.refreshCount()).resolves.toBeUndefined();
      expect(store.openCount()).toEqual({ attention: 4, critical: 2 });
    });

    it('does not start a second call while one is in flight, and goes round once more instead', async () => {
      const { store, api } = storeOn();
      api.holdCount = true;
      api.count = { attention: 1, critical: 0 };
      const first = store.refreshCount();
      await flush();
      expect(api.countCalls).toBe(1);

      // Two more requests while the first is out: they fold into a single follow-up call.
      api.count = { attention: 2, critical: 1 };
      void store.refreshCount();
      void store.refreshCount();
      await flush();
      expect(api.countCalls).toBe(1);

      api.releaseCount(0);
      await flush();
      expect(api.countCalls).toBe(2);
      api.releaseCount(1);
      await first;
      expect(api.countCalls).toBe(2);
      expect(store.openCount()).toEqual({ attention: 2, critical: 1 });
    });

    it('does nothing on the server, where there is no token', async () => {
      const { store, api } = storeOn('server');
      await store.refreshCount();
      await store.load();
      expect(api.countCalls).toBe(0);
      expect(api.listCalls).toEqual([]);
    });
  });

  describe('lists', () => {
    it('asks for open WARNING and CRITICAL, and for open INFO', async () => {
      const { store, api } = storeOn();
      api.data = [notification('CRITICAL'), notification('WARNING'), notification('INFO')];
      await store.load();

      expect(api.listCalls).toContainEqual({
        query: { severities: ['WARNING', 'CRITICAL'], openOnly: true },
        page: 0,
      });
      expect(api.listCalls).toContainEqual({ query: { severities: ['INFO'], openOnly: true }, page: 0 });
      expect(store.attention.state()).toBe('ready');
      expect(store.attention.loaded().map((n) => n.severity)).toEqual(['CRITICAL', 'WARNING']);
      expect(store.rest.state()).toBe('ready');
      expect(store.restItems().map((n) => n.severity)).toEqual(['INFO']);
    });

    it('loads the next page when asked, without repeating a row', async () => {
      const { store, api } = storeOn();
      api.data = Array.from({ length: 25 }, () => notification('WARNING'));
      await store.load();
      expect(store.attention.loaded()).toHaveLength(NOTIFICATION_PAGE_SIZE);
      expect(store.attention.total()).toBe(25);
      expect(store.attention.hasMore()).toBe(true);

      await store.attention.more();
      expect(store.attention.loaded()).toHaveLength(25);
      expect(new Set(store.attention.loaded().map((n) => n.id)).size).toBe(25);
      expect(store.attention.hasMore()).toBe(false);
    });

    it('does not skip a row on the next page after one was acknowledged out of the list', async () => {
      const { store, api } = storeOn();
      api.data = Array.from({ length: 25 }, () => notification('WARNING'));
      await store.load();

      await store.acknowledge(store.attention.loaded()[0].id);
      expect(store.attention.loaded()).toHaveLength(19);

      while (store.attention.hasMore()) {
        await store.attention.more();
      }
      const ids = store.attention.loaded().map((n) => n.id);
      expect(ids).toHaveLength(24);
      expect(new Set(ids).size).toBe(24);
    });

    it('shows an error when a list fails, and loads it again on retry', async () => {
      const { store, api } = storeOn();
      api.data = [notification('WARNING')];
      api.failList = true;
      await store.load();
      expect(store.attention.state()).toBe('error');
      expect(store.rest.state()).toBe('error');

      api.failList = false;
      await store.retryAttention();
      expect(store.attention.state()).toBe('ready');
      expect(store.attention.loaded()).toHaveLength(1);
      expect(store.rest.state()).toBe('error');

      await store.retryRest();
      expect(store.rest.state()).toBe('ready');
    });

    it('keeps the rows on screen when a refresh of a loaded list fails', async () => {
      const { store, api } = storeOn();
      api.data = [notification('WARNING')];
      await store.load();

      api.failList = true;
      await store.load();
      expect(store.attention.state()).toBe('ready');
      expect(store.attention.loaded()).toHaveLength(1);
    });

    it('marks a failed “show more” without losing what is shown', async () => {
      const { store, api } = storeOn();
      api.data = Array.from({ length: 25 }, () => notification('WARNING'));
      await store.load();

      api.failList = true;
      await store.attention.more();
      expect(store.attention.moreFailed()).toBe(true);
      expect(store.attention.loaded()).toHaveLength(NOTIFICATION_PAGE_SIZE);
      expect(store.attention.state()).toBe('ready');

      api.failList = false;
      await store.attention.more();
      expect(store.attention.moreFailed()).toBe(false);
      expect(store.attention.loaded()).toHaveLength(25);
    });
  });

  describe('Everything else, in all mode', () => {
    it('asks for everything and hides the open WARNING and CRITICAL rows', async () => {
      const { store, api } = storeOn();
      const openWarning = notification('WARNING');
      const openCritical = notification('CRITICAL');
      const openInfo = notification('INFO');
      const ackedWarning = acknowledged('WARNING');
      const ackedCritical = acknowledged('CRITICAL');
      const ackedInfo = acknowledged('INFO');
      api.data = [openWarning, openCritical, openInfo, ackedWarning, ackedCritical, ackedInfo];
      await store.load();
      await store.setMode('all');

      expect(api.listCalls.at(-1)).toEqual({ query: {}, page: 0 });
      expect(store.rest.loaded()).toHaveLength(6);
      expect(store.restItems().map((n) => n.id)).toEqual([
        openInfo.id,
        ackedWarning.id,
        ackedCritical.id,
        ackedInfo.id,
      ]);
    });

    it('bases “show more” on the rows fetched, not on the rows shown', async () => {
      const { store, api } = storeOn();
      // 20 open WARNING rows fill the first page, and none of them may show; 3 more follow.
      api.data = [
        ...Array.from({ length: NOTIFICATION_PAGE_SIZE }, () => notification('WARNING')),
        ...Array.from({ length: 3 }, () => acknowledged('INFO')),
      ];
      await store.load();
      await store.setMode('all');

      // The first page shows nothing, so the store reads on by itself until something does.
      expect(api.listCalls.filter((c) => c.page === 1)).toHaveLength(1);
      expect(store.restItems()).toHaveLength(3);
      expect(store.rest.total()).toBe(23);
      expect(store.rest.loaded()).toHaveLength(23);
      expect(store.rest.hasMore()).toBe(false);
    });

    it('offers more while raw rows remain, even when the shown ones are fewer', async () => {
      const { store, api } = storeOn();
      api.data = [
        ...Array.from({ length: 5 }, () => notification('WARNING')),
        ...Array.from({ length: 20 }, () => acknowledged('INFO')),
      ];
      await store.load();
      await store.setMode('all');
      expect(store.restItems()).toHaveLength(15);
      expect(store.rest.hasMore()).toBe(true);

      await store.loadMoreRest();
      expect(store.restItems()).toHaveLength(20);
      expect(store.rest.hasMore()).toBe(false);
    });

    it('drops a response that a newer mode has outrun', async () => {
      const { store, api } = storeOn();
      api.data = [notification('INFO'), acknowledged('INFO')];
      api.holdList = true;

      const opening = store.load();
      await flush();
      const switching = store.setMode('all');
      await flush();
      // Calls so far: attention, rest (open), rest (all).
      const restOpen = api.listCalls.findIndex((c) => c.query.severities?.[0] === 'INFO');
      const restAll = api.listCalls.findIndex((c) => Object.keys(c.query).length === 0);

      // The answer to the newer question arrives first, the stale one after it.
      api.releaseList(restAll);
      await flush();
      api.releaseList(restOpen);
      api.releaseList(0);
      await Promise.all([opening, switching]);

      expect(store.mode()).toBe('all');
      expect(store.rest.loaded()).toHaveLength(2);
    });
  });

  describe('acknowledge', () => {
    it('takes the row out of Needs attention and refreshes the count', async () => {
      const { store, api } = storeOn();
      const critical = notification('CRITICAL');
      const warning = notification('WARNING');
      api.data = [critical, warning, notification('INFO')];
      api.count = { attention: 2, critical: 1 };
      await store.load();
      await store.refreshCount();
      expect(store.openCount()).toEqual({ attention: 2, critical: 1 });
      const countsBefore = api.countCalls;

      api.count = { attention: 1, critical: 0 };
      const updated = await store.acknowledge(critical.id);
      await flush();

      expect(updated.acknowledgedBy).toBe('keeper.conti');
      expect(api.ackCalls).toEqual([critical.id]);
      expect(store.attention.loaded().map((n) => n.id)).toEqual([warning.id]);
      expect(store.attention.total()).toBe(1);
      expect(api.countCalls).toBe(countsBefore + 1);
      expect(store.openCount()).toEqual({ attention: 1, critical: 0 });
    });

    it('does not add the row to Everything else when that list has not loaded it', async () => {
      const { store, api } = storeOn();
      // A full page of acknowledged INFO rows comes first, so in all mode the critical one is on page 2.
      const critical = notification('CRITICAL');
      api.data = [
        ...Array.from({ length: NOTIFICATION_PAGE_SIZE }, () => acknowledged('INFO')),
        critical,
      ];
      await store.load();
      await store.setMode('all');
      expect(store.rest.loaded().map((n) => n.id)).not.toContain(critical.id);
      const shown = store.restItems().length;

      await store.acknowledge(critical.id);

      expect(store.attention.loaded()).toEqual([]);
      expect(store.restItems()).toHaveLength(shown);
      expect(store.restItems().map((n) => n.id)).not.toContain(critical.id);
      expect(store.rest.total()).toBe(NOTIFICATION_PAGE_SIZE + 1);
    });

    it('replaces the row where Everything else already holds it', async () => {
      const { store, api } = storeOn();
      const warning = notification('WARNING');
      api.data = [warning, notification('INFO')];
      await store.load();
      await store.setMode('all');
      expect(store.restItems().map((n) => n.id)).not.toContain(warning.id);
      expect(store.rest.loaded().map((n) => n.id)).toContain(warning.id);

      await store.acknowledge(warning.id);

      const row = store.restItems().find((n) => n.id === warning.id);
      expect(row?.acknowledgedBy).toBe('keeper.conti');
      expect(store.attention.loaded()).toEqual([]);
    });

    it('takes an acknowledged INFO row out of the open list, and keeps it in the full one', async () => {
      const { store, api } = storeOn();
      const info = notification('INFO');
      api.data = [info];
      await store.load();
      await store.acknowledge(info.id);
      expect(store.restItems()).toEqual([]);
      expect(store.rest.total()).toBe(0);

      await store.setMode('all');
      expect(store.restItems().map((n) => n.id)).toEqual([info.id]);
      await store.acknowledge(info.id);
      expect(store.restItems()[0].acknowledgedBy).toBe('keeper.conti');
    });

    it('shows whoever won the race: the notification that comes back is the truth', async () => {
      const { store, api } = storeOn();
      const warning = notification('WARNING');
      api.data = [warning];
      api.ackedBy = 'vet.bianchi';
      await store.load();
      await store.setMode('all');

      const updated = await store.acknowledge(warning.id);

      expect(updated.acknowledgedBy).toBe('vet.bianchi');
      expect(store.restItems().find((n) => n.id === warning.id)?.acknowledgedBy).toBe('vet.bianchi');
      expect(store.attention.loaded()).toEqual([]);
    });

    it('throws the ApiError, leaves the lists as they were and is free to try again', async () => {
      const { store, api } = storeOn();
      const warning = notification('WARNING');
      api.data = [warning];
      await store.load();
      const countsBefore = api.countCalls;

      api.failAck = new ApiError(403, 'Only zoo staff can acknowledge notifications.');
      const attempt = store.acknowledge(warning.id);
      expect(store.acknowledging().has(warning.id)).toBe(true);
      await expect(attempt).rejects.toMatchObject({
        status: 403,
        message: 'Only zoo staff can acknowledge notifications.',
      });

      expect(store.acknowledging().has(warning.id)).toBe(false);
      expect(store.attention.loaded()).toEqual([warning]);
      expect(api.countCalls).toBe(countsBefore);

      api.failAck = null;
      await store.acknowledge(warning.id);
      expect(store.attention.loaded()).toEqual([]);
    });
  });
});
