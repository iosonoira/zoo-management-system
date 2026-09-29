import { Feeding } from '../../../core/models/feeding';

export type MealState = 'fed' | 'due' | 'missed' | 'later';

export interface MealSlot {
  /** `HH:mm`, as scheduled in the plan. */
  readonly time: string;
  readonly state: MealState;
  /** The feeding that covers this slot, when it is `fed`. */
  readonly feeding: Feeding | null;
}

/** A meal counts for a slot when it is recorded up to this long before the scheduled time. */
const EARLY_MS = 60 * 60 * 1000;

/** The scheduled time on the local day of `day`, as an epoch. */
function atTime(day: Date, time: string): number {
  const [hours, minutes] = time.split(':').map(Number);
  return new Date(day.getFullYear(), day.getMonth(), day.getDate(), hours, minutes).getTime();
}

/**
 * One slot per scheduled time of the plan, for the local day of `now`.
 *
 * Slot i owns the window from (time i − 60 min) up to (time i+1 − 60 min); the first window
 * opens at local midnight and the last one closes at the end of the day. A slot is `fed` with
 * the earliest feeding in its window. An unfed slot whose time has passed is `due` when it is the
 * latest such slot and `missed` otherwise; an unfed slot still ahead is `later`.
 */
export function mealSlots(
  times: readonly string[],
  feedings: readonly Feeding[],
  now: Date,
): MealSlot[] {
  const dayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const dayEnd = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1).getTime();
  const nowMs = now.getTime();

  const today = feedings
    .map((feeding) => ({ feeding, at: Date.parse(feeding.fedAt) }))
    .filter(({ at }) => at >= dayStart && at < dayEnd)
    .sort((a, b) => a.at - b.at);

  const scheduled = [...times].sort();
  const starts = scheduled.map((time, i) => (i === 0 ? dayStart : atTime(now, time) - EARLY_MS));
  const lastPast = scheduled.reduce((last, time, i) => (atTime(now, time) <= nowMs ? i : last), -1);

  return scheduled.map((time, i): MealSlot => {
    const end = i === scheduled.length - 1 ? dayEnd : starts[i + 1];
    const fed = today.find(({ at }) => at >= starts[i] && at < end)?.feeding ?? null;
    if (fed) {
      return { time, state: 'fed', feeding: fed };
    }
    if (atTime(now, time) > nowMs) {
      return { time, state: 'later', feeding: null };
    }
    return { time, state: i === lastPast ? 'due' : 'missed', feeding: null };
  });
}

/** Whether `at` falls on the local day of `now`, or the day before it. */
export function relativeDay(at: Date, now: Date): 'today' | 'yesterday' | null {
  const day = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const diff = Math.round((day(now) - day(at)) / 86_400_000);
  return diff === 0 ? 'today' : diff === 1 ? 'yesterday' : null;
}
