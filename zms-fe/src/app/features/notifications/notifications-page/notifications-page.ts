import {
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../../core/data/animal-store';
import { NotificationStore, RestMode } from '../../../core/data/notification-store';
import { isOpen } from '../../../core/data/notification-copy';
import { SEVERITY_LABELS } from '../../../core/models/labels';
import { Notification } from '../../../core/models/notification';
import { Session } from '../../../core/session/session';
import { Icon } from '../../../core/ui/icon/icon';
import { AcknowledgedNote } from '../acknowledged-note/acknowledged-note';
import { NotificationMeta } from '../notification-meta/notification-meta';
import { NotificationText } from '../notification-text/notification-text';
import { SeverityTile } from '../severity-tile/severity-tile';

type Section = 'attention' | 'rest';

/**
 * `/notifications`, triaged: what needs someone first, as large rows with the action, then
 * a quieter log of everything else. Each list loads and fails on its own.
 */
@Component({
  selector: 'app-notifications-page',
  imports: [RouterLink, Icon, SeverityTile, NotificationText, NotificationMeta, AcknowledgedNote],
  templateUrl: './notifications-page.html',
  styleUrl: './notifications-page.scss',
})
export class NotificationsPage {
  protected readonly store = inject(NotificationStore);
  protected readonly session = inject(Session);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  private readonly attentionHeading = viewChild<ElementRef<HTMLElement>>('attentionHeading');
  private readonly restHeading = viewChild<ElementRef<HTMLElement>>('restHeading');

  /** Read once per load and per action, so the rows never call the clock themselves. */
  protected readonly now = signal(new Date());
  protected readonly announcement = signal('');
  protected readonly failure = signal('');
  private toastTimer: ReturnType<typeof setTimeout> | undefined;

  protected readonly canAcknowledge = computed(() => this.session.can('acknowledgeNotification'));
  protected readonly restModes: readonly { readonly value: RestMode; readonly label: string }[] = [
    { value: 'open', label: 'Open' },
    { value: 'all', label: 'All' },
  ];
  protected readonly skeleton = [1, 2, 3];

  protected readonly attentionTotal = computed(() => this.store.attention.total());
  protected readonly needsWord = computed(() => (this.attentionTotal() === 1 ? 'needs' : 'need'));
  /** What the reader sees of Everything else, for a screen reader that cannot see the list change. */
  protected readonly restSummary = computed(() =>
    this.store.rest.state() === 'ready'
      ? `Everything else: ${this.store.restItems().length} shown${this.store.rest.hasMore() ? ', more available' : ''}.`
      : '',
  );

  /** In all mode the total counts rows this list hides, so it is not offered as a denominator. */
  protected readonly restCaption = computed(() => {
    const shown = this.store.restItems().length;
    return this.store.mode() === 'open'
      ? `Showing ${shown} of ${this.store.rest.total()}.`
      : `Showing ${shown} so far.`;
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.toastTimer));
    void this.store.load().then(() => this.now.set(new Date()));
  }

  protected isBusy(id: string): boolean {
    return this.store.acknowledging().has(id);
  }

  /** Names the row for a screen reader, since every row has the same visible “Acknowledge”. */
  protected acknowledgeLabel(n: Notification): string {
    return `: ${n.name ?? 'animal'}, ${SEVERITY_LABELS[n.severity].label.toLowerCase()} notification`;
  }

  protected setMode(mode: RestMode): void {
    void this.store.setMode(mode).then(() => this.now.set(new Date()));
  }

  protected async acknowledge(n: Notification, section: Section): Promise<void> {
    if (this.isBusy(n.id)) {
      return;
    }
    // Worked out before the list changes: the row that will be under the reader’s finger next.
    const next = this.neighbourWithAction(n.id, section);
    try {
      const updated = await this.store.acknowledge(n.id);
      this.now.set(new Date());
      const name = n.name ?? 'The animal';
      // First acknowledgement wins: the notification that came back says who won.
      this.announce(
        updated.acknowledgedBy === this.session.username()
          ? `Acknowledged by ${updated.acknowledgedBy}. ${name} is taken in charge.`
          : `${name} was already acknowledged by ${updated.acknowledgedBy}.`,
      );
      this.focusAfterAcknowledge(n.id, next, section);
    } catch (error) {
      this.fail(errorMessage(error));
    }
  }

  protected async showMore(section: Section): Promise<void> {
    const shown = new Set(this.rowIds(section));
    await (section === 'attention' ? this.store.attention.more() : this.store.loadMoreRest());
    this.now.set(new Date());
    const list = section === 'attention' ? this.store.attention : this.store.rest;
    if (list.hasMore() || list.moreFailed()) {
      return; // The button is still there, and so is the focus.
    }
    // The button is gone: land on the first row that arrived.
    const first = this.rowIds(section).find((id) => !shown.has(id));
    if (first) {
      this.focusAfterRender(() => this.rowLink(first));
    }
  }

  private rowIds(section: Section): string[] {
    const items = section === 'attention' ? this.store.attention.loaded() : this.store.restItems();
    return items.map((n) => n.id);
  }

  /** The next row with an Acknowledge button, or the previous one when this is the last. */
  private neighbourWithAction(id: string, section: Section): string | null {
    const items = section === 'attention' ? this.store.attention.loaded() : this.store.restItems();
    const ids = items.filter(isOpen).map((n) => n.id);
    const at = ids.indexOf(id);
    return ids[at + 1] ?? ids[at - 1] ?? null;
  }

  /** To the next row’s action, or, with none left, to the row itself if it stayed, else the heading. */
  private focusAfterAcknowledge(id: string, next: string | null, section: Section): void {
    this.focusAfterRender(
      () =>
        (next ? this.action(next) : null) ??
        (section === 'rest' ? this.rowLink(id) : null) ??
        (section === 'attention' ? this.attentionHeading() : this.restHeading())?.nativeElement ??
        null,
    );
  }

  private focusAfterRender(target: () => HTMLElement | null): void {
    afterNextRender(() => target()?.focus(), { injector: this.injector });
  }

  private action(id: string): HTMLElement | null {
    return this.find('data-ack', id);
  }

  private rowLink(id: string): HTMLElement | null {
    return this.find('data-link', id);
  }

  private find(attribute: string, value: string): HTMLElement | null {
    const all = this.host.nativeElement.querySelectorAll<HTMLElement>(`[${attribute}]`);
    return Array.from(all).find((el) => el.getAttribute(attribute) === value) ?? null;
  }

  private announce(message: string): void {
    this.failure.set('');
    this.announcement.set(message);
    this.armTimer();
  }

  private fail(message: string): void {
    this.announcement.set('');
    this.failure.set(message);
    this.armTimer();
  }

  private armTimer(): void {
    clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => {
      this.announcement.set('');
      this.failure.set('');
    }, 6000);
  }
}
