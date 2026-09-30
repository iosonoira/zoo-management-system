import { PLATFORM_ID, Service, computed, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import {
  ATTENTION_SEVERITIES,
  Notification,
  NotificationQuery,
  OpenCount,
} from '../models/notification';
import { LoadState } from './animal-store';
import { NOTIFICATION_PAGE_SIZE, NotificationApi } from './notification-api';
import { Page } from './page';

/** What "Everything else" lists: what still waits (information only), or all of it. */
export type RestMode = 'open' | 'all';

/**
 * One paged, newest-first list. It drops a response that a newer load has outrun, and it
 * asks the server for the page the loaded items end in, so a row acknowledged out of an
 * open-only list does not make the next page skip one.
 */
export class NotificationList {
  private readonly loadedItems = signal<readonly Notification[]>([]);
  private readonly totalCount = signal(0);
  private generation = 0;

  readonly state = signal<LoadState>('idle');
  readonly loadingMore = signal(false);
  readonly moreFailed = signal(false);
  /** Everything fetched so far, in the order the server gave it. */
  readonly loaded = this.loadedItems.asReadonly();
  /** How many the server holds for this query. */
  readonly total = this.totalCount.asReadonly();
  readonly hasMore = computed(() => this.loadedItems().length < this.totalCount());

  constructor(
    private readonly fetchPage: (page: number) => Promise<Page<Notification>>,
    /** Whether the query only asks for open notifications. */
    private readonly openOnly: () => boolean,
  ) {}

  /**
   * Loads the first page. A list that is already showing keeps its rows until the new ones
   * arrive, and keeps them if the refresh fails; `fresh` drops them first (another query).
   */
  async reload(fresh = false): Promise<void> {
    const generation = ++this.generation;
    if (fresh) {
      this.loadedItems.set([]);
      this.totalCount.set(0);
      this.state.set('idle');
    }
    this.loadingMore.set(false);
    this.moreFailed.set(false);

    const showing = this.state() === 'ready';
    if (!showing) {
      this.state.set('loading');
    }
    try {
      const page = await this.fetchPage(0);
      if (generation !== this.generation) {
        return;
      }
      this.loadedItems.set(page.items);
      this.totalCount.set(page.total);
      this.state.set('ready');
    } catch {
      if (generation === this.generation && !showing) {
        this.state.set('error');
      }
    }
  }

  retry(): Promise<void> {
    this.state.set('idle');
    return this.reload();
  }

  async more(): Promise<void> {
    if (this.state() !== 'ready' || this.loadingMore() || !this.hasMore()) {
      return;
    }
    const generation = this.generation;
    this.loadingMore.set(true);
    this.moreFailed.set(false);
    try {
      const page = await this.fetchPage(Math.floor(this.loadedItems().length / NOTIFICATION_PAGE_SIZE));
      if (generation !== this.generation) {
        return;
      }
      const known = new Set(this.loadedItems().map((n) => n.id));
      this.loadedItems.update((items) => [...items, ...page.items.filter((n) => !known.has(n.id))]);
      this.totalCount.set(page.total);
    } catch {
      if (generation === this.generation) {
        this.moreFailed.set(true);
      }
    } finally {
      if (generation === this.generation) {
        this.loadingMore.set(false);
      }
    }
  }

  /**
   * Takes the server’s version of a notification in. A row this list holds is replaced; an
   * acknowledged row leaves a list that only asks for open ones. A row the list does not
   * hold is not added: it may sit on a page that was never loaded.
   */
  apply(updated: Notification): void {
    if (!this.loadedItems().some((n) => n.id === updated.id)) {
      return;
    }
    if (this.openOnly() && updated.acknowledgedBy !== null) {
      this.loadedItems.update((items) => items.filter((n) => n.id !== updated.id));
      this.totalCount.update((total) => Math.max(0, total - 1));
    } else {
      this.loadedItems.update((items) => items.map((n) => (n.id === updated.id ? updated : n)));
    }
  }
}

const NEEDS_ATTENTION: NotificationQuery = { severities: ATTENTION_SEVERITIES, openOnly: true };
const REST_OPEN: NotificationQuery = { severities: ['INFO'], openOnly: true };

/**
 * The notifications page and the bell. The bell’s count and the two lists load
 * separately, so a failing count never blanks the page and a failing list never blanks the bell.
 */
@Service()
export class NotificationStore {
  private readonly api = inject(NotificationApi);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  private readonly count = signal<OpenCount | null>(null);
  private counting = false;
  private countAgain = false;
  private readonly pending = signal<ReadonlySet<string>>(new Set());

  readonly mode = signal<RestMode>('open');

  /** Open WARNING and CRITICAL, and how many of them are CRITICAL; null until first known. */
  readonly openCount = this.count.asReadonly();

  /** Open WARNING and CRITICAL. */
  readonly attention = new NotificationList(
    (page) => this.api.list(NEEDS_ATTENTION, page),
    () => true,
  );

  /**
   * Everything else. In `open` mode the server only sends open INFO. In `all` mode it sends
   * everything, and the open WARNING and CRITICAL rows are hidden, since Needs attention has them.
   */
  readonly rest = new NotificationList(
    (page) => this.api.list(this.mode() === 'open' ? REST_OPEN : {}, page),
    () => this.mode() === 'open',
  );

  readonly restItems = computed(() => {
    const loaded = this.rest.loaded();
    return this.mode() === 'open' ? loaded : loaded.filter((n) => !isAttentionOpen(n));
  });

  /** Ids being acknowledged right now. */
  readonly acknowledging = this.pending.asReadonly();

  /** Loads both lists, on a first visit or a return to the page. */
  async load(): Promise<void> {
    if (!this.isBrowser) {
      return;
    }
    await Promise.all([this.attention.reload(), this.loadRest()]);
  }

  async setMode(mode: RestMode): Promise<void> {
    if (this.mode() === mode || !this.isBrowser) {
      return;
    }
    this.mode.set(mode);
    await this.loadRest(true);
  }

  retryAttention(): Promise<void> {
    return this.attention.retry();
  }

  async retryRest(): Promise<void> {
    this.rest.state.set('idle');
    await this.loadRest();
  }

  async loadMoreRest(): Promise<void> {
    await this.rest.more();
    await this.fillRest();
  }

  /**
   * Refreshes the bell. A failure is silent and keeps the last count: in live mode the call
   * can meet a 401 before sign-in, and a bell that cannot count is not worth an error.
   * A request that arrives while one is in flight does not start a second call; it makes the
   * running one go round once more, so a count taken before an acknowledge never sticks.
   */
  async refreshCount(): Promise<void> {
    if (!this.isBrowser) {
      return;
    }
    if (this.counting) {
      this.countAgain = true;
      return;
    }
    this.counting = true;
    try {
      do {
        this.countAgain = false;
        try {
          this.count.set(await this.api.countOpen());
        } catch {
          // Keep what the bell already shows.
        }
      } while (this.countAgain);
    } finally {
      this.counting = false;
    }
  }

  /**
   * First acknowledgement wins, so the notification that comes back is the truth: it may
   * carry someone else’s name. Errors are thrown for the caller to show.
   */
  async acknowledge(id: string): Promise<Notification> {
    this.pending.update((ids) => new Set(ids).add(id));
    try {
      const updated = await this.api.acknowledge(id);
      this.attention.apply(updated);
      this.rest.apply(updated);
      void this.refreshCount();
      return updated;
    } finally {
      this.pending.update((ids) => {
        const next = new Set(ids);
        next.delete(id);
        return next;
      });
    }
  }

  private async loadRest(fresh = false): Promise<void> {
    await this.rest.reload(fresh);
    await this.fillRest();
  }

  /** In `all` mode a whole page can be open WARNING and CRITICAL rows, hidden here: read on until something shows. */
  private async fillRest(): Promise<void> {
    let seen = -1;
    while (
      this.restItems().length === 0 &&
      this.rest.state() === 'ready' &&
      this.rest.hasMore() &&
      this.rest.loaded().length !== seen
    ) {
      seen = this.rest.loaded().length;
      await this.rest.more();
    }
  }
}

function isAttentionOpen(n: Notification): boolean {
  return n.acknowledgedBy === null && ATTENTION_SEVERITIES.includes(n.severity);
}
