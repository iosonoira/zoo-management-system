import { inject } from '@angular/core';
import {
  FeedingPlan,
  NewFeedingPlan,
  Feeding,
  NewFeeding,
  PlanStatus,
  canPlanTransitionTo,
  MAX_FEEDING_TIMES,
} from '../models/feeding';
import { can } from '../models/permissions';
import { Session } from '../session/session';
import { AnimalApi } from './animal-api';
import { FeedingApi, FEEDING_PAGE_SIZE } from './feeding-api';
import { Page } from './page';
import {
  conflict,
  deceasedPlan,
  forbidden,
  invalidFeeding,
  invalidPlan,
  planChanged,
  planNotActive,
  planNotFound,
} from './api-errors';
import { DEMO_FEEDING_PLANS, DEMO_FEEDINGS } from './demo-feeding';

const LATENCY_MS = 380;

/**
 * In-memory adapter that enforces the same rules and status codes as feeding-service:
 * role checks (403), unknown ids (404), invalid data (400), invalid status transitions (422),
 * and deceased animals (422).
 *
 * Deceased animals: the mock imitates the Kafka consumer that the live feeding-service uses.
 * When a plan is created for a deceased animal, or when an animal becomes deceased, its
 * ACTIVE and SUSPENDED plans are ended with `endedOn = today` (but never before `startedOn`).
 */
export class MockFeedingApi extends FeedingApi {
  private readonly session = inject(Session);
  private readonly animals = inject(AnimalApi);
  private plans = new Map<string, FeedingPlan>(DEMO_FEEDING_PLANS.map((p) => [p.id, p]));
  private feedings = new Map<string, Feeding>(DEMO_FEEDINGS.map((f) => [f.id, f]));

  async listPlans(animalId: string): Promise<FeedingPlan[]> {
    await delay();
    await this.settleDeceased(animalId);
    const planList = [...this.plans.values()].filter((p) => p.animalId === animalId);
    // Sort by startedOn descending, then id ascending
    return planList.sort((a, b) => {
      if (a.startedOn !== b.startedOn) {
        return b.startedOn.localeCompare(a.startedOn);
      }
      return a.id.localeCompare(b.id);
    });
  }

  async createPlan(input: NewFeedingPlan): Promise<FeedingPlan> {
    await delay();
    if (!can(this.session.role(), 'createFeedingPlan')) {
      throw forbidden('createFeedingPlan');
    }

    // Validate data before checking if animal is deceased
    const food = input.food.trim();
    if (!food || food.length > 100) {
      throw invalidPlan();
    }

    if (!Number.isInteger(input.quantityGrams) || input.quantityGrams <= 0) {
      throw invalidPlan();
    }

    if (input.feedingTimes.length === 0 || input.feedingTimes.length > MAX_FEEDING_TIMES) {
      throw invalidPlan();
    }

    // Check time format and duplicates
    const timeRegex = /^([01]\d|2[0-3]):[0-5]\d$/;
    const times = new Set<string>();
    for (const time of input.feedingTimes) {
      if (!timeRegex.test(time)) {
        throw invalidPlan();
      }
      if (times.has(time)) {
        throw invalidPlan();
      }
      times.add(time);
    }

    const notes = input.notes ? input.notes.trim() : null;
    if (notes && notes.length > 500) {
      throw invalidPlan();
    }

    // Check if animal is deceased
    if (await this.settleDeceased(input.animalId)) {
      throw deceasedPlan();
    }

    const plan: FeedingPlan = {
      id: crypto.randomUUID(),
      animalId: input.animalId,
      food,
      quantityGrams: input.quantityGrams,
      feedingTimes: [...input.feedingTimes].sort(),
      notes: notes || null,
      status: 'ACTIVE',
      startedOn: this.today(),
      endedOn: null,
      createdBy: this.session.username(),
      updatedBy: this.session.username(),
    };

    this.plans.set(plan.id, plan);
    return plan;
  }

  async updatePlanStatus(id: string, status: PlanStatus): Promise<FeedingPlan> {
    await delay();
    if (!can(this.session.role(), 'updateFeedingPlanStatus')) {
      throw forbidden('updateFeedingPlanStatus');
    }

    const plan = this.plans.get(id);
    if (!plan) {
      throw planNotFound();
    }

    if (!canPlanTransitionTo(plan, status)) {
      throw planChanged();
    }

    const today = this.today();
    const updated: FeedingPlan = {
      ...plan,
      status,
      endedOn:
        status === 'ENDED' && !plan.endedOn
          ? today >= plan.startedOn
            ? today
            : plan.startedOn
          : plan.endedOn,
      updatedBy: this.session.username(),
    };

    this.plans.set(id, updated);
    return updated;
  }

  async listFeedings(planId: string, page: number): Promise<Page<Feeding>> {
    await delay();
    const plan = this.plans.get(planId);
    if (!plan) {
      throw planNotFound();
    }

    const planFeedings = [...this.feedings.values()].filter((f) => f.planId === planId);
    // Sort by fedAt descending, then id ascending
    planFeedings.sort((a, b) => {
      if (a.fedAt !== b.fedAt) {
        return b.fedAt.localeCompare(a.fedAt);
      }
      return b.id.localeCompare(a.id);
    });

    const start = page * FEEDING_PAGE_SIZE;
    const items = planFeedings.slice(start, start + FEEDING_PAGE_SIZE);

    return {
      items,
      page,
      size: FEEDING_PAGE_SIZE,
      total: planFeedings.length,
    };
  }

  async recordFeeding(planId: string, input: NewFeeding): Promise<Feeding> {
    await delay();
    if (!can(this.session.role(), 'recordFeeding')) {
      throw forbidden('recordFeeding');
    }

    const plan = this.plans.get(planId);
    if (!plan) {
      throw planNotFound();
    }

    if (plan.status !== 'ACTIVE') {
      throw planNotActive();
    }

    if (!Number.isInteger(input.quantityGrams) || input.quantityGrams < 0) {
      throw invalidFeeding();
    }

    const notes = input.notes ? input.notes.trim() : null;
    if (notes && notes.length > 500) {
      throw invalidFeeding();
    }

    // Normalised to UTC ISO so the log sorts correctly by string comparison.
    const fedTime = input.fedAt === null ? Date.now() : new Date(input.fedAt).getTime();
    if (Number.isNaN(fedTime) || fedTime > Date.now() + 60_000) {
      throw invalidFeeding();
    }
    const fedAt = new Date(fedTime).toISOString();

    const feeding: Feeding = {
      id: crypto.randomUUID(),
      planId,
      fedAt,
      quantityGrams: input.quantityGrams,
      notes: notes || null,
      recordedBy: this.session.username(),
    };

    this.feedings.set(feeding.id, feeding);
    return feeding;
  }

  /**
   * Private helper: settles deceased animals by ending their active/suspended plans.
   * Returns true if the animal is deceased, false otherwise. When an animal dies, the
   * live feeding-service consumes that event from Kafka and ends its plans; this mock
   * does the same so demo and live modes show the same outcome.
   */
  private async settleDeceased(animalId: string): Promise<boolean> {
    try {
      const animal = await this.animals.getById(animalId);
      if (animal.status !== 'DECEASED') {
        return false;
      }

      const today = this.today();
      for (const plan of this.plans.values()) {
        if (
          plan.animalId === animalId &&
          (plan.status === 'ACTIVE' || plan.status === 'SUSPENDED')
        ) {
          const updated: FeedingPlan = {
            ...plan,
            status: 'ENDED',
            endedOn: today >= plan.startedOn ? today : plan.startedOn,
            updatedBy: animal.updatedBy ?? 'animal-service',
          };
          this.plans.set(plan.id, updated);
        }
      }

      return true;
    } catch {
      return false;
    }
  }

  private today(): string {
    const now = new Date();
    return (
      now.getFullYear() +
      '-' +
      String(now.getMonth() + 1).padStart(2, '0') +
      '-' +
      String(now.getDate()).padStart(2, '0')
    );
  }
}

function delay(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, LATENCY_MS));
}
