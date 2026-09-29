export type PlanStatus = 'ACTIVE' | 'SUSPENDED' | 'ENDED';

export interface FeedingPlan {
  readonly id: string;
  readonly animalId: string;
  readonly food: string;
  readonly quantityGrams: number;
  /** `HH:mm`, ascending. */
  readonly feedingTimes: readonly string[];
  readonly notes: string | null;
  readonly status: PlanStatus;
  readonly startedOn: string;
  readonly endedOn: string | null;
  readonly createdBy: string | null;
  readonly updatedBy: string | null;
}

export interface Feeding {
  readonly id: string;
  readonly planId: string;
  /** ISO instant. */
  readonly fedAt: string;
  readonly quantityGrams: number;
  readonly notes: string | null;
  readonly recordedBy: string | null;
}

export interface NewFeedingPlan {
  readonly animalId: string;
  readonly food: string;
  readonly quantityGrams: number;
  readonly feedingTimes: readonly string[];
  readonly notes: string | null;
}

export interface NewFeeding {
  /** ISO instant; null means "now" on the server. */
  readonly fedAt: string | null;
  readonly quantityGrams: number;
  readonly notes: string | null;
}

/** Same rules as `FeedingPlan.canTransitionTo` in feeding-service. */
export const PLAN_TRANSITIONS: Readonly<Record<PlanStatus, readonly PlanStatus[]>> = {
  ACTIVE: ['SUSPENDED', 'ENDED'],
  SUSPENDED: ['ACTIVE', 'ENDED'],
  ENDED: [],
};

export function canPlanTransitionTo(
  plan: Pick<FeedingPlan, 'status'>,
  target: PlanStatus,
): boolean {
  return plan.status !== target && PLAN_TRANSITIONS[plan.status].includes(target);
}

export const MAX_FEEDING_TIMES = 6;
