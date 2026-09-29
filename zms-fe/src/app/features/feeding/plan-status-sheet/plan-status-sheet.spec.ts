import { By } from '@angular/platform-browser';
import { TestBed } from '@angular/core/testing';
import { FeedingStore } from '../../../core/data/feeding-store';
import { Animal } from '../../../core/models/animal';
import { FeedingPlan, PlanStatus } from '../../../core/models/feeding';
import { PlanStatusSheet } from './plan-status-sheet';

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

function makePlan(status: PlanStatus): FeedingPlan {
  return {
    id: 'p1',
    animalId: ANIMAL.id,
    food: 'Chopped vegetables and fruit',
    quantityGrams: 250,
    feedingTimes: ['08:30', '15:00'],
    notes: null,
    status,
    startedOn: '2026-09-18',
    endedOn: null,
    createdBy: 'vet.bianchi',
    updatedBy: 'vet.bianchi',
  };
}

describe('PlanStatusSheet', () => {
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
    status: PlanStatus,
    update = vi.fn(async (_id: string, s: PlanStatus) => makePlan(s)),
  ) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [PlanStatusSheet],
      providers: [{ provide: FeedingStore, useValue: { updatePlanStatus: update } }],
    });
    const fixture = TestBed.createComponent(PlanStatusSheet);
    fixture.componentRef.setInput('animal', ANIMAL);
    fixture.componentRef.setInput('plan', makePlan(status));
    fixture.componentInstance.open();
    fixture.detectChanges();
    return { fixture, update };
  }

  type Fixture = ReturnType<typeof setUp>['fixture'];

  const optionTitles = (f: Fixture): string[] =>
    Array.from(f.nativeElement.querySelectorAll('.option-text strong')).map((e) =>
      (e as HTMLElement).textContent!.trim(),
    );
  const submitButton = (f: Fixture): HTMLButtonElement =>
    f.nativeElement.querySelector('button[type="submit"]');

  function choose(f: Fixture, value: PlanStatus): void {
    const radios: HTMLInputElement[] = Array.from(
      f.nativeElement.querySelectorAll('input[type="radio"]'),
    );
    const radio = radios.find((r) => r.value === value)!;
    radio.checked = true;
    radio.dispatchEvent(new Event('change'));
    f.detectChanges();
  }

  it('offers Suspend and End for an active plan', () => {
    const { fixture } = setUp('ACTIVE');
    expect(optionTitles(fixture)).toEqual(['Suspend', 'End']);
  });

  it('offers Resume and End for a suspended plan', () => {
    const { fixture } = setUp('SUSPENDED');
    expect(optionTitles(fixture)).toEqual(['Resume', 'End']);
  });

  it('keeps the submit button disabled until an option is chosen', () => {
    const { fixture } = setUp('ACTIVE');
    expect(submitButton(fixture).disabled).toBe(true);
    choose(fixture, 'SUSPENDED');
    expect(submitButton(fixture).disabled).toBe(false);
    expect(submitButton(fixture).classList).toContain('btn-primary');
  });

  it('uses the grave button and a warning for End, which cannot be undone', () => {
    const { fixture } = setUp('ACTIVE');
    choose(fixture, 'ENDED');
    expect(submitButton(fixture).classList).toContain('btn-grave');
    expect(submitButton(fixture).classList).not.toContain('btn-primary');
    expect(fixture.nativeElement.querySelector('.warning-note').textContent).toContain(
      'This can’t be undone.',
    );
  });

  it('updates the plan and emits the confirmed plan', async () => {
    const { fixture, update } = setUp('ACTIVE');
    let emitted: FeedingPlan | undefined;
    fixture.componentInstance.changed.subscribe((p) => (emitted = p));
    choose(fixture, 'SUSPENDED');
    await fixture.debugElement
      .query(By.css('form'))
      .triggerEventHandler('submit', { preventDefault: () => {} });
    await fixture.whenStable();
    expect(update).toHaveBeenCalledWith('p1', 'SUSPENDED');
    expect(emitted?.status).toBe('SUSPENDED');
  });
});
