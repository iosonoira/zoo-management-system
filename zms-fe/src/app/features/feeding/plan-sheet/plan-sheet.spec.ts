import { By } from '@angular/platform-browser';
import { TestBed } from '@angular/core/testing';
import { ApiError } from '../../../core/data/api-error';
import { FeedingStore } from '../../../core/data/feeding-store';
import { Animal } from '../../../core/models/animal';
import { FeedingPlan, NewFeedingPlan, PlanStatus } from '../../../core/models/feeding';
import { PlanSheet } from './plan-sheet';

const ANIMAL: Animal = {
  id: 'a1',
  name: 'Pepe',
  species: 'Ring-tailed lemur',
  dangerous: false,
  habitat: 'TERRESTRIAL',
  enclosureId: 'e1',
  arrivalDate: '2023-02-14',
  status: 'HEALTHY',
  createdBy: 'admin.rossi',
  updatedBy: 'vet.bianchi',
};

function makePlan(status: PlanStatus, animalId = ANIMAL.id): FeedingPlan {
  return {
    id: `plan-${status}-${animalId}`,
    animalId,
    food: 'Chopped vegetables and fruit',
    quantityGrams: 250,
    feedingTimes: ['08:30'],
    notes: null,
    status,
    startedOn: '2026-09-18',
    endedOn: status === 'ENDED' ? '2026-09-25' : null,
    createdBy: 'vet.bianchi',
    updatedBy: 'vet.bianchi',
  };
}

function created(input: NewFeedingPlan): FeedingPlan {
  return {
    id: 'new-plan',
    animalId: input.animalId,
    food: input.food,
    quantityGrams: input.quantityGrams,
    feedingTimes: [...input.feedingTimes].sort(),
    notes: input.notes,
    status: 'ACTIVE',
    startedOn: '2026-09-29',
    endedOn: null,
    createdBy: 'vet.bianchi',
    updatedBy: 'vet.bianchi',
  };
}

describe('PlanSheet', () => {
  // jsdom does not implement <dialog>.showModal()/close(); stub them so open()/close() work.
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.removeAttribute('open');
    };
  });

  function setUp(
    options: {
      plans?: FeedingPlan[];
      create?: (input: NewFeedingPlan) => Promise<FeedingPlan>;
    } = {},
  ) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [PlanSheet],
      providers: [
        {
          provide: FeedingStore,
          useValue: {
            plans: () => options.plans ?? [],
            createPlan: options.create ?? (async (input: NewFeedingPlan) => created(input)),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(PlanSheet);
    fixture.componentRef.setInput('animal', ANIMAL);
    fixture.componentInstance.open();
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance };
  }

  type Fixture = ReturnType<typeof setUp>['fixture'];

  const root = (f: Fixture): HTMLElement => f.nativeElement;
  const foodInput = (f: Fixture): HTMLInputElement =>
    root(f).querySelector('input[type="text"]') as HTMLInputElement;
  const gramsInput = (f: Fixture): HTMLInputElement =>
    root(f).querySelector('input[type="number"]') as HTMLInputElement;
  const timeInputs = (f: Fixture): HTMLInputElement[] =>
    Array.from(root(f).querySelectorAll('input[type="time"]'));
  const submitButton = (f: Fixture): HTMLButtonElement =>
    root(f).querySelector('button[type="submit"]') as HTMLButtonElement;
  const addButton = (f: Fixture): HTMLButtonElement =>
    Array.from(root(f).querySelectorAll('button')).find((b) =>
      b.textContent!.includes('Add a time'),
    )!;
  const removeButtons = (f: Fixture): HTMLButtonElement[] =>
    Array.from(root(f).querySelectorAll('button[aria-label^="Remove"]'));

  function typeInto(input: HTMLInputElement | HTMLTextAreaElement, value: string): void {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function fillValid(f: Fixture): void {
    typeInto(foodInput(f), 'Chopped vegetables and fruit');
    typeInto(gramsInput(f), '250');
    typeInto(timeInputs(f)[0], '08:30');
    f.detectChanges();
  }

  async function submit(f: Fixture): Promise<void> {
    await f.debugElement
      .query(By.css('form'))
      .triggerEventHandler('submit', { preventDefault: () => {} });
    await f.whenStable();
    f.detectChanges();
  }

  it('starts with one empty time and a disabled submit', () => {
    const { fixture } = setUp();
    expect(timeInputs(fixture)).toHaveLength(1);
    expect(submitButton(fixture).disabled).toBe(true);
  });

  it('enables submit only once food, grams and one time are valid', () => {
    const { fixture } = setUp();
    typeInto(foodInput(fixture), 'Chopped vegetables');
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(true);

    typeInto(gramsInput(fixture), '250');
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(true);

    typeInto(timeInputs(fixture)[0], '08:30');
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(false);
  });

  it('rejects blank food, zero and fractional grams', () => {
    const { fixture } = setUp();
    fillValid(fixture);

    typeInto(foodInput(fixture), '   ');
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(true);

    typeInto(foodInput(fixture), 'Hay');
    typeInto(gramsInput(fixture), '0');
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(true);

    typeInto(gramsInput(fixture), '2.5');
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(true);
  });

  it('adds times up to the limit of six, then disables the add button', () => {
    const { fixture } = setUp();
    for (let i = 1; i < 6; i++) {
      addButton(fixture).click();
      fixture.detectChanges();
    }
    expect(timeInputs(fixture)).toHaveLength(6);
    expect(addButton(fixture).disabled).toBe(true);
  });

  it('moves focus to the new time input after adding one', () => {
    const { fixture } = setUp();
    document.body.appendChild(fixture.nativeElement);
    addButton(fixture).click();
    fixture.detectChanges();
    expect(document.activeElement).toBe(timeInputs(fixture)[1]);
    fixture.nativeElement.remove();
  });

  it('removes a time, keeps the last row, and names the remove buttons after the time', () => {
    const { fixture } = setUp();
    expect(removeButtons(fixture)[0].disabled).toBe(true);

    addButton(fixture).click();
    fixture.detectChanges();
    typeInto(timeInputs(fixture)[0], '08:00');
    fixture.detectChanges();
    expect(removeButtons(fixture).map((b) => b.getAttribute('aria-label'))).toEqual([
      'Remove 08:00',
      'Remove this time',
    ]);

    removeButtons(fixture)[1].click();
    fixture.detectChanges();
    expect(timeInputs(fixture)).toHaveLength(1);
    expect(timeInputs(fixture)[0].value).toBe('08:00');
    expect(removeButtons(fixture)[0].disabled).toBe(true);
  });

  it('keeps submit disabled and shows a hint for a duplicate time', () => {
    const { fixture } = setUp();
    fillValid(fixture);
    addButton(fixture).click();
    fixture.detectChanges();
    typeInto(timeInputs(fixture)[1], '08:30');
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(true);
    expect(root(fixture).textContent).toContain('Each time can only be used once.');

    typeInto(timeInputs(fixture)[1], '15:00');
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(false);
  });

  it('keeps submit disabled while a time row is blank', () => {
    const { fixture } = setUp();
    fillValid(fixture);
    addButton(fixture).click();
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(true);
  });

  it('sends the trimmed food, the grams, the times as entered and a null note', async () => {
    let received: NewFeedingPlan | undefined;
    const { fixture, component } = setUp({
      create: async (input) => {
        received = input;
        return created(input);
      },
    });
    typeInto(foodInput(fixture), '  Chopped vegetables and fruit  ');
    typeInto(gramsInput(fixture), '250');
    typeInto(timeInputs(fixture)[0], '15:00');
    addButton(fixture).click();
    fixture.detectChanges();
    typeInto(timeInputs(fixture)[1], '08:30');
    typeInto(root(fixture).querySelector('textarea')!, '   ');
    fixture.detectChanges();

    let emitted: FeedingPlan | undefined;
    component.created.subscribe((p) => (emitted = p));
    await submit(fixture);

    expect(received).toEqual({
      animalId: 'a1',
      food: 'Chopped vegetables and fruit',
      quantityGrams: 250,
      feedingTimes: ['15:00', '08:30'],
      notes: null,
    });
    expect(emitted?.id).toBe('new-plan');
  });

  it('sends a trimmed note', async () => {
    let received: NewFeedingPlan | undefined;
    const { fixture } = setUp({
      create: async (input) => {
        received = input;
        return created(input);
      },
    });
    fillValid(fixture);
    typeInto(root(fixture).querySelector('textarea')!, '  Give the medicine with the meal.  ');
    fixture.detectChanges();
    await submit(fixture);
    expect(received?.notes).toBe('Give the medicine with the meal.');
  });

  it('warns about an open plan only when the animal has one', () => {
    const warning = (f: Fixture) => root(f).querySelector('.warning-note');

    expect(warning(setUp().fixture)).toBeNull();
    expect(warning(setUp({ plans: [makePlan('ENDED')] }).fixture)).toBeNull();
    expect(warning(setUp({ plans: [makePlan('ACTIVE', 'other')] }).fixture)).toBeNull();

    for (const status of ['ACTIVE', 'SUSPENDED'] as const) {
      const { fixture } = setUp({ plans: [makePlan(status)] });
      expect(warning(fixture)!.textContent).toContain(
        'Pepe already has an open plan. It stays as it is: suspend or end it if this one replaces it.',
      );
    }
  });

  it('shows the ApiError message in the alert when starting the plan fails', async () => {
    const { fixture } = setUp({
      create: async () => {
        throw new ApiError(422, 'Pepe is deceased, so no plan can be started.');
      },
    });
    fillValid(fixture);
    await submit(fixture);
    const alert = root(fixture).querySelector('[role="alert"]')!;
    expect(alert.textContent).toContain('Pepe is deceased');
  });
});
