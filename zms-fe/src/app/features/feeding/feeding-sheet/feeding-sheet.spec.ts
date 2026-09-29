import { By } from '@angular/platform-browser';
import { TestBed } from '@angular/core/testing';
import { ApiError } from '../../../core/data/api-error';
import { FeedingStore } from '../../../core/data/feeding-store';
import { Animal } from '../../../core/models/animal';
import { Feeding, FeedingPlan, NewFeeding } from '../../../core/models/feeding';
import { FeedingSheet } from './feeding-sheet';

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

const PLAN: FeedingPlan = {
  id: 'p1',
  animalId: ANIMAL.id,
  food: 'Chopped vegetables and fruit',
  quantityGrams: 250,
  feedingTimes: ['08:30', '15:00'],
  notes: null,
  status: 'ACTIVE',
  startedOn: '2026-09-18',
  endedOn: null,
  createdBy: 'vet.bianchi',
  updatedBy: 'vet.bianchi',
};

function makeFeeding(input: NewFeeding): Feeding {
  return {
    id: 'f1',
    planId: PLAN.id,
    fedAt: input.fedAt ?? new Date().toISOString(),
    quantityGrams: input.quantityGrams,
    notes: input.notes,
    recordedBy: 'keeper.conti',
  };
}

describe('FeedingSheet', () => {
  // jsdom does not implement <dialog>.showModal()/close(); stub them so open()/close() work.
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.removeAttribute('open');
    };
  });

  function setUp(record: (planId: string, input: NewFeeding) => Promise<Feeding>) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [FeedingSheet],
      providers: [{ provide: FeedingStore, useValue: { recordFeeding: record } }],
    });
    const fixture = TestBed.createComponent(FeedingSheet);
    fixture.componentRef.setInput('animal', ANIMAL);
    fixture.componentRef.setInput('plan', PLAN);
    fixture.componentInstance.open();
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance };
  }

  type Fixture = ReturnType<typeof setUp>['fixture'];

  const gramsInput = (f: Fixture): HTMLInputElement =>
    f.nativeElement.querySelector('input[type="number"]');
  const stepButton = (f: Fixture, label: string): HTMLButtonElement =>
    f.nativeElement.querySelector(`button[aria-label="${label}"]`);
  const submitButton = (f: Fixture): HTMLButtonElement =>
    f.nativeElement.querySelector('button[type="submit"]');

  function typeInto(input: HTMLInputElement | HTMLTextAreaElement, value: string): void {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function chooseEarlier(f: Fixture): void {
    // The formField directive reads radios on the 'input' event, like a real click.
    const earlier: HTMLInputElement = f.nativeElement.querySelector('input[value="earlier"]');
    earlier.checked = true;
    earlier.dispatchEvent(new Event('input'));
    f.detectChanges();
  }

  async function submit(f: Fixture): Promise<void> {
    const form = f.debugElement.query(By.css('form'));
    await form.triggerEventHandler('submit', { preventDefault: () => {} });
    await f.whenStable();
    f.detectChanges();
  }

  it('prefills the amount from the plan and names the animal and food', () => {
    const { fixture } = setUp(async (_, input) => makeFeeding(input));
    expect(gramsInput(fixture).value).toBe('250');
    expect(fixture.nativeElement.textContent).toContain(
      'Pepe · Chopped vegetables and fruit. The feeding is recorded under your name.',
    );
    expect(submitButton(fixture).textContent).toMatch(/Record\s+250 g\s+at\s+\d\d:\d\d/);
  });

  it('steps the amount by 10 g and never goes below 0', () => {
    const { fixture } = setUp(async (_, input) => makeFeeding(input));
    typeInto(gramsInput(fixture), '5');
    fixture.detectChanges();

    stepButton(fixture, '10 grams less').click();
    fixture.detectChanges();
    expect(gramsInput(fixture).value).toBe('0');
    expect(stepButton(fixture, '10 grams less').disabled).toBe(true);

    stepButton(fixture, '10 grams more').click();
    fixture.detectChanges();
    expect(gramsInput(fixture).value).toBe('10');
  });

  it('sends fedAt null for "now", the plan’s amount and a null note', async () => {
    let received: { planId: string; input: NewFeeding } | undefined;
    const { fixture } = setUp(async (planId, input) => {
      received = { planId, input };
      return makeFeeding(input);
    });
    await submit(fixture);
    expect(received).toEqual({
      planId: 'p1',
      input: { fedAt: null, quantityGrams: 250, notes: null },
    });
  });

  it('sends an ISO instant for today when the meal was earlier, with a trimmed note', async () => {
    let received: NewFeeding | undefined;
    const { fixture, component } = setUp(async (_, input) => {
      received = input;
      return makeFeeding(input);
    });
    chooseEarlier(fixture);
    typeInto(fixture.nativeElement.querySelector('input[type="time"]'), '00:00');
    typeInto(gramsInput(fixture), '230');
    typeInto(fixture.nativeElement.querySelector('textarea'), '  Left some pellets.  ');
    fixture.detectChanges();

    let emitted: Feeding | undefined;
    component.recorded.subscribe((f) => (emitted = f));
    await submit(fixture);

    const today = new Date();
    expect(received).toEqual({
      fedAt: new Date(today.getFullYear(), today.getMonth(), today.getDate(), 0, 0).toISOString(),
      quantityGrams: 230,
      notes: 'Left some pellets.',
    });
    expect(emitted?.quantityGrams).toBe(230);
  });

  it('blocks submitting an earlier meal without a time', () => {
    const { fixture } = setUp(async (_, input) => makeFeeding(input));
    chooseEarlier(fixture);
    typeInto(fixture.nativeElement.querySelector('input[type="time"]'), '');
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(true);
  });

  it('blocks submitting when the amount is empty', () => {
    const { fixture } = setUp(async (_, input) => makeFeeding(input));
    typeInto(gramsInput(fixture), '');
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(true);
  });

  it('shows the ApiError message in the alert when recording fails', async () => {
    const { fixture } = setUp(async () => {
      throw new ApiError(422, 'This plan isn’t active, so no feedings can be recorded.');
    });
    await submit(fixture);
    const alert: HTMLElement = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert.textContent).toContain('This plan isn’t active');
  });
});
