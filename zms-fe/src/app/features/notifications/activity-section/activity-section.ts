import {
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  linkedSignal,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { ActivityStore } from '../../../core/data/activity-store';
import { errorMessage } from '../../../core/data/animal-store';
import { formatDay, notificationSentence } from '../../../core/data/notification-copy';
import { Animal } from '../../../core/models/animal';
import { SEVERITY_LABELS } from '../../../core/models/labels';
import { ATTENTION_SEVERITIES, Notification } from '../../../core/models/notification';
import { Session } from '../../../core/session/session';
import { Icon } from '../../../core/ui/icon/icon';
import { AcknowledgedNote } from '../acknowledged-note/acknowledged-note';
import { NotificationMeta } from '../notification-meta/notification-meta';
import { NotificationText } from '../notification-text/notification-text';
import { SeverityTile } from '../severity-tile/severity-tile';

/** A notification as the section shows it. */
interface RowView {
  readonly n: Notification;
  readonly open: boolean;
  /** Open warning or critical: an expanded row with the action. Everything else folds. */
  readonly attention: boolean;
  readonly expanded: boolean;
  readonly panelId: string;
}

/**
 * The Activity section of the animal page: what happened to this animal, newest first.
 * Open warnings and criticals are expanded rows with the action; everything else is one
 * folded line that opens on select. Like Health and Feeding it loads and fails on its own,
 * so the rest of the page keeps working when notification-service is down.
 */
@Component({
  selector: 'app-activity-section',
  imports: [RouterLink, Icon, SeverityTile, NotificationText, NotificationMeta, AcknowledgedNote],
  templateUrl: './activity-section.html',
  styleUrl: './activity-section.scss',
})
export class ActivitySection {
  protected readonly store = inject(ActivityStore);
  protected readonly session = inject(Session);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  readonly animal = input.required<Animal>();
  /** A confirmation for the page’s live region. */
  readonly announce = output<string>();

  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');

  /** Read once per load and per action, so the rows never call the clock themselves. */
  protected readonly now = signal(new Date());
  protected readonly failure = signal('');
  protected readonly canAcknowledge = computed(() => this.session.can('acknowledgeNotification'));

  /** What the reader has unfolded. */
  private readonly unfolded = linkedSignal<string, ReadonlySet<string>>({
    source: () => this.animal().id,
    computation: () => new Set(),
  });

  /** The store keeps one animal at a time; only trust it once it holds this one. */
  private readonly forThisAnimal = computed(() => this.store.animalId() === this.animal().id);
  protected readonly ready = computed(
    () => this.forThisAnimal() && this.store.list.state() === 'ready',
  );
  protected readonly failed = computed(
    () => this.forThisAnimal() && this.store.list.state() === 'error',
  );

  protected readonly rows = computed<readonly RowView[]>(() => {
    const unfolded = this.unfolded();
    return this.store.list.loaded().map((n) => {
      const open = n.acknowledgedBy === null;
      return {
        n,
        open,
        attention: open && ATTENTION_SEVERITIES.includes(n.severity),
        expanded: unfolded.has(n.id),
        panelId: `activity-${n.id}`,
      };
    });
  });

  protected readonly note = computed(() => {
    const count = this.store.needAttention();
    return count > 0
      ? `${count} ${count === 1 ? 'needs' : 'need'} attention. The rest is folded.`
      : 'Nothing needs attention. Select a line to unfold it.';
  });

  constructor() {
    effect(() => {
      const id = this.animal().id;
      untracked(() => void this.store.load(id).then(() => this.now.set(new Date())));
    });
  }

  protected isBusy(id: string): boolean {
    return this.store.acknowledging().has(id);
  }

  protected day(n: Notification): string {
    return formatDay(n.occurredAt, this.now());
  }

  /** Names the row for a screen reader: the visible “Acknowledge” is the same on every row. */
  protected acknowledgeLabel(n: Notification): string {
    const severity = SEVERITY_LABELS[n.severity].label.toLowerCase();
    return `: ${severity}, ${notificationSentence(n, { withName: false })}`;
  }

  protected toggle(row: RowView): void {
    this.unfolded.update((set) => {
      const next = new Set(set);
      if (!next.delete(row.n.id)) {
        next.add(row.n.id);
      }
      return next;
    });
  }

  protected async acknowledge(n: Notification): Promise<void> {
    if (this.isBusy(n.id)) {
      return;
    }
    this.failure.set('');
    // Worked out before the list changes: the action under the reader’s finger next.
    const next = this.neighbourAction(n.id);
    try {
      const updated = await this.store.acknowledge(n.id);
      this.now.set(new Date());
      const name = this.animal().name;
      // First acknowledgement wins: the notification that came back says who won.
      this.announce.emit(
        updated.acknowledgedBy === this.session.username()
          ? `Acknowledged by ${updated.acknowledgedBy}. ${name} is taken in charge.`
          : `${name} was already acknowledged by ${updated.acknowledgedBy}.`,
      );
      // To the next action, or, with none left, to the row itself, which is now a folded line.
      this.focusAfterRender(
        () =>
          (next ? this.find('data-ack', next) : null) ??
          this.find('data-fold', n.id) ??
          this.heading()?.nativeElement ??
          null,
      );
    } catch (error) {
      this.failure.set(errorMessage(error));
    }
  }

  protected async showEarlier(): Promise<void> {
    const shown = new Set(this.rows().map((r) => r.n.id));
    await this.store.list.more();
    this.now.set(new Date());
    if (this.store.list.hasMore() || this.store.list.moreFailed()) {
      return; // The button is still there, and so is the focus.
    }
    // The button is gone: land on the first row that arrived.
    const first = this.rows().find((r) => !shown.has(r.n.id));
    if (first) {
      this.focusAfterRender(
        () => this.find('data-row', first.n.id)?.querySelector<HTMLElement>('button, a') ?? null,
      );
    }
  }

  /** The next Acknowledge button on screen, or the previous one when this is the last. */
  private neighbourAction(id: string): string | null {
    const ids = Array.from(this.host.nativeElement.querySelectorAll<HTMLElement>('[data-ack]')).map(
      (el) => el.getAttribute('data-ack'),
    );
    const at = ids.indexOf(id);
    return ids[at + 1] ?? ids[at - 1] ?? null;
  }

  private focusAfterRender(target: () => HTMLElement | null): void {
    afterNextRender(() => target()?.focus(), { injector: this.injector });
  }

  private find(attribute: string, value: string): HTMLElement | null {
    const all = this.host.nativeElement.querySelectorAll<HTMLElement>(`[${attribute}]`);
    return Array.from(all).find((el) => el.getAttribute(attribute) === value) ?? null;
  }
}
