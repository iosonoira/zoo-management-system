import { PLATFORM_ID, Service, computed, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { ATTENTION_SEVERITIES, Notification } from '../models/notification';
import { NotificationApi } from './notification-api';
import { NotificationList, NotificationStore } from './notification-store';

/**
 * The notifications of the animal on screen, newest first, for the Activity section of its
 * page. One animal at a time, like `HealthStore` and `FeedingStore`: opening another drops
 * the rows and any answer still on its way, so they never show under the wrong animal.
 */
@Service()
export class ActivityStore {
  private readonly api = inject(NotificationApi);
  private readonly notifications = inject(NotificationStore);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  readonly animalId = signal<string | null>(null);

  /** Every notification of the animal, open or not, so nothing is filtered out or dropped on acknowledge. */
  readonly list = new NotificationList(
    (page) => this.api.list({ animalId: this.animalId() ?? undefined }, page),
    () => false,
  );

  /** Ids being acknowledged right now, from any screen. */
  readonly acknowledging = this.notifications.acknowledging;

  /** How many of the loaded rows are open warnings or criticals: the ones that ask for action. */
  readonly needAttention = computed(
    () =>
      this.list
        .loaded()
        .filter((n) => n.acknowledgedBy === null && ATTENTION_SEVERITIES.includes(n.severity))
        .length,
  );

  /**
   * Loads the animal’s notifications. Coming back to the animal it already holds keeps its
   * rows on screen while the new ones arrive; another animal starts from nothing.
   */
  async load(animalId: string): Promise<void> {
    if (!this.isBrowser) {
      return;
    }
    if (this.animalId() === animalId) {
      if (this.list.state() === 'loading') {
        return;
      }
      return this.list.reload();
    }
    this.animalId.set(animalId);
    return this.list.reload(true);
  }

  retry(): Promise<void> {
    return this.list.retry();
  }

  /**
   * After this animal’s status or location changed on its own page: the service records a
   * notification for it, so read the list again, and the bell with it.
   */
  async refresh(): Promise<void> {
    void this.notifications.refreshCount();
    if (this.animalId() !== null && this.isBrowser) {
      await this.list.reload();
    }
  }

  /**
   * Acknowledges through `NotificationStore`, so the notifications page’s lists and the bell
   * see it too, then takes the notification that comes back (which may name someone else) in here.
   */
  async acknowledge(id: string): Promise<Notification> {
    const updated = await this.notifications.acknowledge(id);
    this.list.apply(updated);
    return updated;
  }
}
