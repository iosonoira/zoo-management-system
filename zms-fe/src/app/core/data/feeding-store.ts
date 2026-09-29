import { PLATFORM_ID, Service, computed, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { FeedingPlan, Feeding, NewFeedingPlan, NewFeeding, PlanStatus } from '../models/feeding';
import { FeedingApi, FEEDING_PAGE_SIZE } from './feeding-api';
import { LoadState } from './animal-store';

/** Feeding plans of the animal on screen, with each plan's feedings loaded on demand. */
@Service()
export class FeedingStore {
  private readonly api = inject(FeedingApi);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  private readonly planMap = signal<ReadonlyMap<string, FeedingPlan>>(new Map());
  private readonly feedingMap = signal<ReadonlyMap<string, readonly Feeding[]>>(new Map());
  private readonly feedingStates = signal<ReadonlyMap<string, LoadState>>(new Map());
  private readonly feedingPageCounts = signal<ReadonlyMap<string, number>>(new Map());
  private readonly feedingTotals = signal<ReadonlyMap<string, number>>(new Map());

  readonly animalId = signal<string | null>(null);
  readonly state = signal<LoadState>('idle');
  readonly plans = computed(() => [...this.planMap().values()]);

  async load(animalId: string): Promise<void> {
    if (!this.isBrowser) {
      return;
    }
    if (this.animalId() === animalId && (this.state() === 'loading' || this.state() === 'ready')) {
      return;
    }
    // Another animal: drop everything, so its plans never show under this one.
    if (this.animalId() !== animalId) {
      this.animalId.set(animalId);
      this.planMap.set(new Map());
      this.feedingMap.set(new Map());
      this.feedingStates.set(new Map());
      this.feedingPageCounts.set(new Map());
      this.feedingTotals.set(new Map());
    }
    this.state.set('loading');
    try {
      const plans = await this.api.listPlans(animalId);
      // A newer load for another animal has started: this response is stale.
      if (this.animalId() !== animalId) {
        return;
      }
      this.planMap.set(new Map(plans.map((p) => [p.id, p])));
      this.state.set('ready');
    } catch {
      if (this.animalId() === animalId) {
        this.state.set('error');
      }
    }
  }

  retry(): Promise<void> {
    const current = this.animalId();
    if (!current) {
      return Promise.resolve();
    }
    this.state.set('idle');
    return this.load(current);
  }

  feedings(planId: string): readonly Feeding[] {
    return this.feedingMap().get(planId) ?? [];
  }

  feedingsState(planId: string): LoadState {
    return this.feedingStates().get(planId) ?? 'idle';
  }

  hasMoreFeedings(planId: string): boolean {
    const loaded = this.feedings(planId).length;
    const total = this.feedingTotals().get(planId) ?? 0;
    return loaded < total;
  }

  async loadFeedings(planId: string): Promise<void> {
    if (!this.isBrowser) {
      return;
    }
    const state = this.feedingsState(planId);
    if (state === 'loading' || state === 'ready') {
      return;
    }
    const animalId = this.animalId();
    this.feedingStates.update((m) => new Map(m).set(planId, 'loading'));
    try {
      const page = await this.api.listFeedings(planId, 0);
      if (this.animalId() !== animalId) {
        return;
      }
      this.feedingMap.update((m) => new Map(m).set(planId, page.items));
      this.feedingPageCounts.update((m) => new Map(m).set(planId, 1));
      this.feedingTotals.update((m) => new Map(m).set(planId, page.total));
      this.feedingStates.update((m) => new Map(m).set(planId, 'ready'));
    } catch {
      if (this.animalId() === animalId) {
        this.feedingStates.update((m) => new Map(m).set(planId, 'error'));
      }
    }
  }

  async loadMoreFeedings(planId: string): Promise<void> {
    if (!this.isBrowser) {
      return;
    }
    const state = this.feedingsState(planId);
    if (state === 'loading' || !this.hasMoreFeedings(planId)) {
      return;
    }
    const animalId = this.animalId();
    this.feedingStates.update((m) => new Map(m).set(planId, 'loading'));
    try {
      const pageCount = this.feedingPageCounts().get(planId) ?? 0;
      const page = await this.api.listFeedings(planId, pageCount);
      if (this.animalId() !== animalId) {
        return;
      }

      // Skip items already loaded (in case feedings were recorded meanwhile)
      const existing = new Set(this.feedings(planId).map((f) => f.id));
      const newFeedings = page.items.filter((f) => !existing.has(f.id));

      this.feedingMap.update((m) => {
        const current = m.get(planId) ?? [];
        return new Map(m).set(planId, [...current, ...newFeedings]);
      });
      this.feedingPageCounts.update((m) => new Map(m).set(planId, pageCount + 1));
      this.feedingTotals.update((m) => new Map(m).set(planId, page.total));
      this.feedingStates.update((m) => new Map(m).set(planId, 'ready'));
    } catch {
      if (this.animalId() === animalId) {
        this.feedingStates.update((m) => new Map(m).set(planId, 'error'));
      }
    }
  }

  async createPlan(input: NewFeedingPlan): Promise<FeedingPlan> {
    const plan = await this.api.createPlan(input);
    // The user may have opened another animal while the request was in flight.
    if (plan.animalId !== this.animalId()) {
      return plan;
    }
    this.applyPlan(plan);
    // Mark its feedings as ready with none and total 0
    this.feedingMap.update((m) => new Map(m).set(plan.id, []));
    this.feedingStates.update((m) => new Map(m).set(plan.id, 'ready'));
    this.feedingPageCounts.update((m) => new Map(m).set(plan.id, 0));
    this.feedingTotals.update((m) => new Map(m).set(plan.id, 0));
    return plan;
  }

  async updatePlanStatus(id: string, status: PlanStatus): Promise<FeedingPlan> {
    const plan = await this.api.updatePlanStatus(id, status);
    if (plan.animalId === this.animalId()) {
      this.applyPlan(plan);
    }
    return plan;
  }

  async recordFeeding(planId: string, input: NewFeeding): Promise<Feeding> {
    const feeding = await this.api.recordFeeding(planId, input);
    // Insert in fedAt descending order
    this.feedingMap.update((m) => {
      const current = m.get(planId);
      if (!current) {
        return m;
      }
      const updated = [...current, feeding].sort((a, b) => b.fedAt.localeCompare(a.fedAt));
      return new Map(m).set(planId, updated);
    });
    // Increment the total
    this.feedingTotals.update((m) => {
      const current = m.get(planId) ?? 0;
      return new Map(m).set(planId, current + 1);
    });
    return feeding;
  }

  private applyPlan(plan: FeedingPlan): void {
    this.planMap.update((map) => {
      const newMap = new Map(map);
      newMap.set(plan.id, plan);
      // Re-sort by startedOn descending, then id ascending
      const sorted = [...newMap.values()].sort((a, b) => {
        if (a.startedOn !== b.startedOn) {
          return b.startedOn.localeCompare(a.startedOn);
        }
        return a.id.localeCompare(b.id);
      });
      return new Map(sorted.map((p) => [p.id, p]));
    });
  }
}
