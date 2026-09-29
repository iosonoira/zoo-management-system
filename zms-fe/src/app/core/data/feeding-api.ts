import { Feeding, FeedingPlan, NewFeeding, NewFeedingPlan, PlanStatus } from '../models/feeding';
import { Page } from './page';

/** The log shows the latest ten, then "Show earlier". */
export const FEEDING_PAGE_SIZE = 10;

/** Port for the feeding-service REST contract (`/feeding-plans`). */
export abstract class FeedingApi {
  /** Every plan of one animal, most recent start first. */
  abstract listPlans(animalId: string): Promise<FeedingPlan[]>;
  abstract createPlan(input: NewFeedingPlan): Promise<FeedingPlan>;
  abstract updatePlanStatus(id: string, status: PlanStatus): Promise<FeedingPlan>;
  /** One page of a plan's feedings, most recent first. */
  abstract listFeedings(planId: string, page: number): Promise<Page<Feeding>>;
  abstract recordFeeding(planId: string, input: NewFeeding): Promise<Feeding>;
}
