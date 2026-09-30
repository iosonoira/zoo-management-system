import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Notification, NotificationQuery, OpenCount } from '../models/notification';
import { ActivityStore } from './activity-store';
import { ApiError } from './api-error';
import { NOTIFICATION_PAGE_SIZE, NotificationApi } from './notification-api';
import { NotificationStore } from './notification-store';
import { Page } from './page';

let sequence = 0;

function notification(animalId: string, overrides: Partial<Notification> = {}): Notification {
  sequence++;
  return {
    id: `n${sequence}`,
    animalId,
    eventType: 'ANIMAL_STATUS_CHANGED',
    severity: 'WARNING',
    message: `Message ${sequence}`,
    occurredAt: new Date(2026, 8, 30, 10, 0, 60 - sequence).toISOString(),
    performedBy: 'vet.bianchi',
    name: 'Zuri',
    species: 'African lion',
    dangerous: true,
    previousStatus: 'HEALTHY',
    newStatus: 'IN_TREATMENT',
    fromEnclosureId: null,
    toEnclosureId: null,
    acknowledgedBy: null,
    acknowledgedAt: null,
    ...overrides,
  };
}

class FakeNotificationApi extends NotificationApi {
  data: Notification[] = [];
  listCalls: { query: NotificationQuery; page: number }[] = [];
  countCalls = 0;
  failList = false;
  ackedBy = 'keeper.conti';
  hold = false;
  private held: (() => void)[] = [];

  async list(query: NotificationQuery, page: number): Promise<Page<Notification>> {
    this.listCalls.push({ query, page });
    if (this.failList) {
      throw new ApiError(500, 'down');
    }
    const items = this.data.filter(
      (n) =>
        (!query.animalId || n.animalId === query.animalId) &&
        (!query.severities?.length || query.severities.includes(n.severity)) &&
        (!query.openOnly || n.acknowledgedBy === null),
    );
    const result = {
      items: items.slice(page * NOTIFICATION_PAGE_SIZE, (page + 1) * NOTIFICATION_PAGE_SIZE),
      page,
      size: NOTIFICATION_PAGE_SIZE,
      total: items.length,
    };
    if (this.hold) {
      await new Promise<void>((resolve) => this.held.push(resolve));
    }
    return result;
  }

  async countOpen(): Promise<OpenCount> {
    this.countCalls++;
    return { attention: 0, critical: 0 };
  }

  async acknowledge(id: string): Promise<Notification> {
    const found = this.data.find((n) => n.id === id)!;
    const updated = found.acknowledgedBy
      ? found
      : { ...found, acknowledgedBy: this.ackedBy, acknowledgedAt: '2026-09-30T11:00:00Z' };
    this.data = this.data.map((n) => (n.id === id ? updated : n));
    return updated;
  }

  release(index: number): void {
    this.held[index]();
  }
}

function storeOn(platform = 'browser') {
  const api = new FakeNotificationApi();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: PLATFORM_ID, useValue: platform },
      { provide: NotificationApi, useValue: api },
    ],
  });
  return {
    api,
    store: TestBed.inject(ActivityStore),
    notifications: TestBed.inject(NotificationStore),
  };
}

const flush = () => new Promise<void>((resolve) => setTimeout(resolve));

describe('ActivityStore', () => {
  beforeEach(() => {
    sequence = 0;
  });

  it('loads the notifications of one animal, newest first, whatever their severity or state', async () => {
    const { store, api } = storeOn();
    const mine = [
      notification('zuri', { severity: 'CRITICAL' }),
      notification('zuri', { severity: 'INFO' }),
      notification('zuri', { acknowledgedBy: 'admin.rossi' }),
    ];
    api.data = [mine[0], notification('pepe'), mine[1], mine[2]];
    await store.load('zuri');

    expect(api.listCalls).toEqual([{ query: { animalId: 'zuri' }, page: 0 }]);
    expect(store.animalId()).toBe('zuri');
    expect(store.list.state()).toBe('ready');
    expect(store.list.loaded().map((n) => n.id)).toEqual(mine.map((n) => n.id));
    expect(store.needAttention()).toBe(1);
  });

  it('does nothing on the server, where there is no token', async () => {
    const { store, api } = storeOn('server');
    await store.load('zuri');
    await store.refresh();
    expect(api.listCalls).toEqual([]);
    expect(api.countCalls).toBe(0);
    expect(store.list.state()).toBe('idle');
  });

  it('pages by NOTIFICATION_PAGE_SIZE', async () => {
    const { store, api } = storeOn();
    api.data = Array.from({ length: NOTIFICATION_PAGE_SIZE + 5 }, () => notification('zuri'));
    await store.load('zuri');
    expect(store.list.loaded()).toHaveLength(NOTIFICATION_PAGE_SIZE);
    expect(store.list.hasMore()).toBe(true);

    await store.list.more();
    expect(store.list.loaded()).toHaveLength(NOTIFICATION_PAGE_SIZE + 5);
    expect(store.list.hasMore()).toBe(false);
  });

  it('drops the rows of the animal it held when another one opens', async () => {
    const { store, api } = storeOn();
    api.data = [notification('zuri'), notification('pepe')];
    await store.load('zuri');
    expect(store.list.loaded().map((n) => n.animalId)).toEqual(['zuri']);

    await store.load('pepe');
    expect(store.animalId()).toBe('pepe');
    expect(store.list.loaded().map((n) => n.animalId)).toEqual(['pepe']);
  });

  it('empties at once when another animal opens, so its rows never show under the new one', async () => {
    const { store, api } = storeOn();
    api.data = [notification('zuri'), notification('pepe')];
    await store.load('zuri');

    api.hold = true;
    const opening = store.load('pepe');
    expect(store.list.loaded()).toEqual([]);
    expect(store.list.state()).toBe('loading');
    api.release(0);
    await opening;
  });

  it('drops an answer that arrives after another animal opened', async () => {
    const { store, api } = storeOn();
    api.data = [notification('zuri'), notification('pepe')];
    api.hold = true;

    const first = store.load('zuri');
    await flush();
    const second = store.load('pepe');
    await flush();

    // The newer answer arrives first, the stale one after it.
    api.release(1);
    await flush();
    api.release(0);
    await Promise.all([first, second]);

    expect(store.animalId()).toBe('pepe');
    expect(store.list.loaded().map((n) => n.animalId)).toEqual(['pepe']);
  });

  it('keeps the rows while it reads the same animal again', async () => {
    const { store, api } = storeOn();
    api.data = [notification('zuri')];
    await store.load('zuri');

    api.data = [notification('zuri'), ...api.data];
    api.hold = true;
    const again = store.load('zuri');
    expect(store.list.state()).toBe('ready');
    expect(store.list.loaded()).toHaveLength(1);
    api.release(0);
    await again;
    expect(store.list.loaded()).toHaveLength(2);
  });

  it('shows an error when the first load fails, and loads again on retry', async () => {
    const { store, api } = storeOn();
    api.data = [notification('zuri')];
    api.failList = true;
    await store.load('zuri');
    expect(store.list.state()).toBe('error');

    api.failList = false;
    await store.retry();
    expect(store.list.state()).toBe('ready');
    expect(store.list.loaded()).toHaveLength(1);
  });

  describe('refresh', () => {
    it('reads the list again, so a new event shows, and refreshes the bell', async () => {
      const { store, api, notifications } = storeOn();
      api.data = [notification('zuri')];
      await store.load('zuri');
      const calls = api.countCalls;

      api.data = [notification('zuri', { severity: 'CRITICAL' }), ...api.data];
      await store.refresh();
      await flush();

      expect(store.list.loaded()).toHaveLength(2);
      expect(store.list.loaded()[0].severity).toBe('CRITICAL');
      expect(api.countCalls).toBe(calls + 1);
      expect(notifications.openCount()).toEqual({ attention: 0, critical: 0 });
    });

    it('refreshes only the bell when no animal is open', async () => {
      const { store, api } = storeOn();
      await store.refresh();
      expect(api.listCalls).toEqual([]);
      expect(api.countCalls).toBe(1);
    });
  });

  describe('acknowledge', () => {
    it('updates the row in place, refreshes the bell and updates the notifications page lists', async () => {
      const { store, api, notifications } = storeOn();
      const open = notification('zuri', { severity: 'CRITICAL' });
      const other = notification('pepe');
      api.data = [open, other];
      await notifications.load();
      await store.load('zuri');
      expect(notifications.attention.loaded().map((n) => n.id)).toEqual([open.id, other.id]);
      const calls = api.countCalls;

      const updated = await store.acknowledge(open.id);
      await flush();

      expect(updated.acknowledgedBy).toBe('keeper.conti');
      // The row stays in Activity, now acknowledged, and no longer asks for action.
      expect(store.list.loaded()[0].acknowledgedBy).toBe('keeper.conti');
      expect(store.needAttention()).toBe(0);
      // The page’s list dropped it, so the two views agree.
      expect(notifications.attention.loaded().map((n) => n.id)).toEqual([other.id]);
      expect(api.countCalls).toBe(calls + 1);
    });

    it('shows whoever won the race', async () => {
      const { store, api } = storeOn();
      const open = notification('zuri');
      api.data = [open];
      api.ackedBy = 'vet.bianchi';
      await store.load('zuri');

      const updated = await store.acknowledge(open.id);
      expect(updated.acknowledgedBy).toBe('vet.bianchi');
      expect(store.list.loaded()[0].acknowledgedBy).toBe('vet.bianchi');
    });
  });
});
