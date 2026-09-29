import { Feeding } from '../../../core/models/feeding';
import { mealSlots, relativeDay } from './meal-slots';

/** Local wall-clock time on 29 Sep 2026, so the tests pass in any time zone. */
function at(hours: number, minutes = 0, day = 29): Date {
  return new Date(2026, 8, day, hours, minutes);
}

let nextId = 0;

function fed(when: Date, quantityGrams = 250): Feeding {
  return {
    id: `f${nextId++}`,
    planId: 'p1',
    fedAt: when.toISOString(),
    quantityGrams,
    notes: null,
    recordedBy: 'keeper.conti',
  };
}

function states(times: string[], feedings: Feeding[], now: Date): string[] {
  return mealSlots(times, feedings, now).map((s) => s.state);
}

describe('mealSlots', () => {
  it('returns one slot per scheduled time, in order', () => {
    const slots = mealSlots(['08:30', '15:00'], [], at(6));
    expect(slots.map((s) => s.time)).toEqual(['08:30', '15:00']);
  });

  it('marks every slot as later before the first meal', () => {
    expect(states(['08:30', '15:00'], [], at(6))).toEqual(['later', 'later']);
  });

  it('marks an unfed slot whose time has passed as due', () => {
    expect(states(['08:30', '15:00'], [], at(8, 30))).toEqual(['due', 'later']);
  });

  it('marks the latest passed unfed slot as due and the earlier ones as missed', () => {
    expect(states(['07:30', '12:30', '17:00'], [], at(13))).toEqual(['missed', 'due', 'later']);
  });

  it('keeps an unfed slot due for less than an hour after its time', () => {
    expect(states(['08:30', '15:00'], [], at(9, 29))).toEqual(['due', 'later']);
  });

  it('marks an unfed slot as missed from an hour after its time', () => {
    expect(states(['08:30', '15:00'], [], at(9, 30))).toEqual(['missed', 'later']);
    expect(states(['08:30', '15:00'], [], at(14, 54))).toEqual(['missed', 'later']);
  });

  it('keeps an earlier unfed slot missed when a later slot is fed', () => {
    const feedings = [fed(at(12, 40))];
    expect(states(['07:30', '12:30', '17:00'], feedings, at(13))).toEqual([
      'missed',
      'fed',
      'later',
    ]);
  });

  it('marks a slot as fed with the feeding inside its window', () => {
    const meal = fed(at(8, 34));
    const [first, second] = mealSlots(['08:30', '15:00'], [meal], at(15, 10));
    expect(first).toEqual({ time: '08:30', state: 'fed', feeding: meal });
    expect(second.state).toBe('due');
  });

  it('counts a feeding up to 60 minutes before the scheduled time', () => {
    const early = fed(at(14, 0));
    // The unfed 08:30 slot is six hours past, so it reads as missed.
    expect(states(['08:30', '15:00'], [early], at(14, 30))).toEqual(['missed', 'fed']);
  });

  it('does not count a feeding more than 60 minutes before the scheduled time', () => {
    const tooEarly = fed(at(13, 59));
    const slots = mealSlots(['08:30', '15:00'], [tooEarly], at(14, 30));
    // 13:59 still belongs to the 08:30 slot, whose window runs until 14:00
    expect(slots.map((s) => s.state)).toEqual(['fed', 'later']);
    expect(slots[0].feeding).toBe(tooEarly);
  });

  it('opens the first window at local midnight', () => {
    const night = fed(at(0, 5));
    expect(states(['08:30', '15:00'], [night], at(9))).toEqual(['fed', 'later']);
  });

  it('closes the last window at the end of the day', () => {
    const late = fed(at(23, 59));
    expect(states(['08:30', '15:00'], [late], at(23, 59))).toEqual(['missed', 'fed']);
  });

  it('takes the earliest feeding when a window holds several', () => {
    const later = fed(at(9, 10), 300);
    const earlier = fed(at(8, 40), 200);
    const [first] = mealSlots(['08:30'], [later, earlier], at(10));
    expect(first.feeding).toBe(earlier);
  });

  it('ignores feedings from other days', () => {
    const yesterday = fed(at(8, 34, 28));
    const tomorrow = fed(at(8, 34, 30));
    expect(states(['08:30', '15:00'], [yesterday, tomorrow], at(9))).toEqual(['due', 'later']);
  });

  it('gives an unfed slot in the future the later state even with feedings recorded', () => {
    const morning = fed(at(8, 34));
    expect(states(['08:30', '15:00'], [morning], at(9))).toEqual(['fed', 'later']);
  });

  it('returns no slots for a plan without times', () => {
    expect(mealSlots([], [fed(at(8))], at(9))).toEqual([]);
  });
});

describe('relativeDay', () => {
  it('recognises today and yesterday on the local calendar', () => {
    const now = at(0, 10);
    expect(relativeDay(at(23, 50), now)).toBe('today');
    expect(relativeDay(at(23, 50, 28), now)).toBe('yesterday');
    expect(relativeDay(at(8, 0, 27), now)).toBeNull();
  });
});
