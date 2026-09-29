import {
  Component,
  DestroyRef,
  LOCALE_ID,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChildren,
} from '@angular/core';
import { DatePipe, formatDate } from '@angular/common';
import { LoadState } from '../../../core/data/animal-store';
import { FeedingStore } from '../../../core/data/feeding-store';
import { Animal } from '../../../core/models/animal';
import { Feeding, FeedingPlan } from '../../../core/models/feeding';
import { PLAN_STATUS_LABELS } from '../../../core/models/labels';
import { Session } from '../../../core/session/session';
import { Icon } from '../../../core/ui/icon/icon';
import { FeedingSheet } from '../feeding-sheet/feeding-sheet';
import { MealSlot, mealSlots, relativeDay } from '../meal-track/meal-slots';
import { MealTrack } from '../meal-track/meal-track';
import { PlanStatusSheet } from '../plan-status-sheet/plan-status-sheet';

/** A plan that is still in force: it can be fed against, or is only paused. */
interface OpenPlan {
  readonly plan: FeedingPlan;
  /** Whether today's meals are shown: an active plan of a living animal. */
  readonly track: boolean;
  /** Null until the plan's feedings are known, and on the server. */
  readonly slots: readonly MealSlot[] | null;
  readonly feedings: readonly Feeding[];
  readonly feedingsState: LoadState;
  readonly hasMore: boolean;
}

/**
 * The Feeding section of the animal page: today's meals, the plan behind them, the log of
 * recent feedings, and the sheets to record one or change the plan. It loads and fails on
 * its own, so the rest of the page keeps working when feeding-service is down.
 */
@Component({
  selector: 'app-feeding-section',
  imports: [DatePipe, Icon, MealTrack, FeedingSheet, PlanStatusSheet],
  templateUrl: './feeding-section.html',
  styleUrl: './feeding-section.scss',
})
export class FeedingSection {
  protected readonly store = inject(FeedingStore);
  protected readonly session = inject(Session);
  private readonly locale = inject(LOCALE_ID);

  readonly animal = input.required<Animal>();
  /** A confirmation for the page's live region. */
  readonly announce = output<string>();

  private readonly recordSheets = viewChildren(FeedingSheet);
  private readonly statusSheets = viewChildren(PlanStatusSheet);

  protected readonly statusLabels = PLAN_STATUS_LABELS;

  /** The current time, set in the browser and refreshed every minute. Null on the server. */
  protected readonly now = signal<Date | null>(null);

  protected readonly deceased = computed(() => this.animal().status === 'DECEASED');
  protected readonly canRecord = computed(
    () => this.session.can('recordFeeding') && !this.deceased(),
  );
  protected readonly canChangePlan = computed(() => this.session.can('updateFeedingPlanStatus'));
  protected readonly canCreatePlan = computed(() => this.session.can('createFeedingPlan'));

  /** The store keeps one animal at a time; only trust it once it holds this one. */
  private readonly forThisAnimal = computed(() => this.store.animalId() === this.animal().id);
  protected readonly ready = computed(() => this.forThisAnimal() && this.store.state() === 'ready');
  protected readonly failed = computed(
    () => this.forThisAnimal() && this.store.state() === 'error',
  );

  private readonly openPlans = computed(() =>
    this.store.plans().filter((p) => p.status === 'ACTIVE' || p.status === 'SUSPENDED'),
  );
  protected readonly ended = computed(() => this.store.plans().filter((p) => p.status === 'ENDED'));

  protected readonly open = computed<readonly OpenPlan[]>(() => {
    const now = this.now();
    return this.openPlans().map((plan) => {
      const feedings = this.store.feedings(plan.id);
      const feedingsState = this.store.feedingsState(plan.id);
      const track = plan.status === 'ACTIVE' && !this.deceased();
      // Meal states from a half-loaded log would be wrong, so wait for it.
      const known = feedingsState === 'ready' || feedings.length > 0;
      return {
        plan,
        track,
        slots: track && now && known ? mealSlots(plan.feedingTimes, feedings, now) : null,
        feedings,
        feedingsState,
        hasMore: this.store.hasMoreFeedings(plan.id),
      };
    });
  });

  constructor() {
    effect(() => {
      const id = this.animal().id;
      untracked(() => void this.store.load(id));
    });
    effect(() => {
      if (!this.ready()) {
        return;
      }
      for (const { id } of this.openPlans()) {
        untracked(() => void this.store.loadFeedings(id));
      }
    });

    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      this.now.set(new Date());
      const timer = setInterval(() => this.now.set(new Date()), 60_000);
      destroyRef.onDestroy(() => clearInterval(timer));
    });
  }

  protected openRecord(plan: FeedingPlan): void {
    this.recordSheets()
      .find((sheet) => sheet.plan().id === plan.id)
      ?.open();
  }

  protected openStatus(plan: FeedingPlan): void {
    this.statusSheets()
      .find((sheet) => sheet.plan().id === plan.id)
      ?.open();
  }

  /** Tries the log again: the next page if there is one on screen, else the first. */
  protected retryFeedings(view: OpenPlan): void {
    if (view.feedings.length > 0) {
      void this.store.loadMoreFeedings(view.plan.id);
    } else {
      void this.store.loadFeedings(view.plan.id);
    }
  }

  protected whenLabel(iso: string): string {
    const at = new Date(iso);
    const now = this.now();
    const time = formatDate(at, 'HH:mm', this.locale);
    switch (now ? relativeDay(at, now) : null) {
      case 'today':
        return `Today, ${time}`;
      case 'yesterday':
        return `Yesterday, ${time}`;
      default: {
        const sameYear = at.getFullYear() === (now ?? new Date()).getFullYear();
        return `${formatDate(at, sameYear ? 'd MMM' : 'd MMM y', this.locale)}, ${time}`;
      }
    }
  }

  protected onRecorded(feeding: Feeding): void {
    this.announce.emit(
      `Feeding recorded for ${this.animal().name} at ${formatDate(feeding.fedAt, 'HH:mm', this.locale)}.`,
    );
  }

  protected onPlanChanged(plan: FeedingPlan): void {
    this.announce.emit(
      `${plan.food} plan is now ${PLAN_STATUS_LABELS[plan.status].label.toLowerCase()}.`,
    );
  }
}
