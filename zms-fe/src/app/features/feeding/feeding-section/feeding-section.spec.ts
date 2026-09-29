import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { LoadState } from '../../../core/data/animal-store';
import { FeedingStore } from '../../../core/data/feeding-store';
import { Animal, ZooRole } from '../../../core/models/animal';
import { Feeding, FeedingPlan, PlanStatus } from '../../../core/models/feeding';
import { can } from '../../../core/models/permissions';
import { Session } from '../../../core/session/session';
import { FeedingSection } from './feeding-section';

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

function makePlan(status: PlanStatus, id = 'p1'): FeedingPlan {
  return {
    id,
    animalId: ANIMAL.id,
    food: 'Chopped vegetables and fruit',
    quantityGrams: 250,
    feedingTimes: ['08:30', '15:00'],
    notes: 'Give the anti-inflammatory with the morning meal.',
    status,
    startedOn: '2026-09-18',
    endedOn: status === 'ENDED' ? '2026-09-25' : null,
    createdBy: 'vet.bianchi',
    updatedBy: 'vet.bianchi',
  };
}

interface Options {
  role?: ZooRole;
  animal?: Animal;
  plans?: FeedingPlan[];
  state?: LoadState;
  feedings?: Feeding[];
}

function fakeStore(animal: Animal, options: Options) {
  return {
    animalId: signal(animal.id),
    state: signal<LoadState>(options.state ?? 'ready'),
    plans: signal(options.plans ?? []),
    feedings: () => options.feedings ?? [],
    feedingsState: (): LoadState => 'ready',
    hasMoreFeedings: () => false,
    load: vi.fn(async () => {}),
    loadFeedings: vi.fn(async () => {}),
    loadMoreFeedings: vi.fn(async () => {}),
    retry: vi.fn(async () => {}),
  };
}

function fakeSession(role: ZooRole) {
  return {
    role: signal(role),
    can: (permission: Parameters<typeof can>[1]) => can(role, permission),
  };
}

describe('FeedingSection', () => {
  // jsdom does not implement <dialog>.showModal()/close(); the sheets never open here.
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.removeAttribute('open');
    };
  });

  async function setUp(options: Options = {}) {
    const animal = options.animal ?? ANIMAL;
    const store = fakeStore(animal, options);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [FeedingSection],
      providers: [
        { provide: FeedingStore, useValue: store },
        { provide: Session, useValue: fakeSession(options.role ?? 'zoo-keeper') },
      ],
    });
    const fixture = TestBed.createComponent(FeedingSection);
    fixture.componentRef.setInput('animal', animal);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    return {
      fixture,
      store,
      root,
      /** Text of the buttons on the page itself, not those inside the closed sheets. */
      buttons: () =>
        Array.from(root.querySelectorAll('button'))
          .filter((b) => !b.closest('dialog'))
          .map((b) => b.textContent!.replace(/\s+/g, ' ').trim()),
      text: () => root.textContent!.replace(/\s+/g, ' '),
    };
  }

  it('loads the animal’s plans and the feedings of each open plan', async () => {
    const { store } = await setUp({
      plans: [makePlan('ACTIVE', 'p1'), makePlan('SUSPENDED', 'p2'), makePlan('ENDED', 'p3')],
    });
    expect(store.load).toHaveBeenCalledWith(ANIMAL.id);
    expect(store.loadFeedings).toHaveBeenCalledWith('p1');
    expect(store.loadFeedings).toHaveBeenCalledWith('p2');
    expect(store.loadFeedings).not.toHaveBeenCalledWith('p3');
  });

  it('shows a keeper Record feeding and the meals, but not Change plan status', async () => {
    const { buttons, text, root } = await setUp({
      role: 'zoo-keeper',
      plans: [makePlan('ACTIVE')],
    });
    expect(buttons().some((b) => b.startsWith('Record feeding'))).toBe(true);
    expect(buttons().some((b) => b.startsWith('Change plan status'))).toBe(false);
    expect(text()).toContain('Only vets and admins can change the plan.');
    expect(text()).not.toContain('Only keepers and admins can record feedings.');
    expect(root.querySelectorAll('app-meal-track li')).toHaveLength(2);
  });

  it('shows a vet Change plan status and the lock note, but not Record feeding', async () => {
    const { buttons, text } = await setUp({ role: 'zoo-vet', plans: [makePlan('ACTIVE')] });
    expect(buttons().some((b) => b.startsWith('Change plan status'))).toBe(true);
    expect(buttons().some((b) => b.startsWith('Record feeding'))).toBe(false);
    expect(text()).toContain('Only keepers and admins can record feedings.');
    expect(text()).not.toContain('Only vets and admins can change the plan.');
  });

  it('shows an admin both actions and no lock notes', async () => {
    const { buttons, text } = await setUp({ role: 'zoo-admin', plans: [makePlan('ACTIVE')] });
    expect(buttons().some((b) => b.startsWith('Record feeding'))).toBe(true);
    expect(buttons().some((b) => b.startsWith('Change plan status'))).toBe(true);
    expect(text()).not.toContain('Only keepers and admins');
    expect(text()).not.toContain('Only vets and admins');
  });

  it('replaces the meals with the suspended line and hides Record feeding', async () => {
    const { buttons, text, root } = await setUp({
      role: 'zoo-admin',
      plans: [makePlan('SUSPENDED')],
    });
    expect(text()).toContain('Plan suspended. No feedings are recorded until it resumes.');
    expect(root.querySelector('app-meal-track')).toBeNull();
    expect(buttons().some((b) => b.startsWith('Record feeding'))).toBe(false);
    expect(buttons().some((b) => b.startsWith('Change plan status'))).toBe(true);
  });

  it('says a deceased animal’s plans have ended and hides Record feeding', async () => {
    const { buttons, text } = await setUp({
      role: 'zoo-admin',
      animal: { ...ANIMAL, name: 'Bruno', status: 'DECEASED' },
      plans: [makePlan('ENDED')],
    });
    expect(text()).toContain('Bruno is recorded as deceased. Its feeding plans have ended.');
    expect(buttons().some((b) => b.startsWith('Record feeding'))).toBe(false);
  });

  it('keeps ended plans under a collapsed Earlier plans disclosure', async () => {
    const { root, text } = await setUp({
      plans: [makePlan('ACTIVE', 'p1'), makePlan('ENDED', 'p2')],
    });
    const details = root.querySelector('details')!;
    expect(details.open).toBe(false);
    expect(details.querySelector('summary')!.textContent).toContain('Earlier plans');
    expect(text()).toContain('ended 25 Sep 2026');
  });

  it('tells a keeper there is no plan yet and who can start one', async () => {
    const { text } = await setUp({ role: 'zoo-keeper', plans: [] });
    expect(text()).toContain('No feeding plan yet.');
    expect(text()).toContain('A vet or admin needs to start a plan.');
  });

  it('does not show the plan lock note to a vet with no plan', async () => {
    const { text } = await setUp({ role: 'zoo-vet', plans: [] });
    expect(text()).toContain('No feeding plan yet.');
    expect(text()).not.toContain('A vet or admin needs to start a plan.');
  });

  it('shows a vet New plan next to Change plan status, and a keeper no New plan', async () => {
    const vet = await setUp({ role: 'zoo-vet', plans: [makePlan('ACTIVE')] });
    expect(vet.buttons()).toContain('New plan');
    expect(vet.buttons().some((b) => b.startsWith('Change plan status'))).toBe(true);

    const keeper = await setUp({ role: 'zoo-keeper', plans: [makePlan('ACTIVE')] });
    expect(keeper.buttons()).not.toContain('New plan');
  });

  it('offers a vet New plan as the primary action when there is no plan', async () => {
    const { root, buttons } = await setUp({ role: 'zoo-vet', plans: [] });
    expect(buttons()).toContain('New plan');
    const button = Array.from(root.querySelectorAll('button')).find(
      (b) => b.textContent!.trim() === 'New plan',
    )!;
    expect(button.classList).toContain('btn-primary');
  });

  it('gives a keeper no New plan button in the empty state, only the lock note', async () => {
    const { buttons, text } = await setUp({ role: 'zoo-keeper', plans: [] });
    expect(buttons()).not.toContain('New plan');
    expect(text()).toContain('A vet or admin needs to start a plan.');
  });

  it('hides New plan for a deceased animal', async () => {
    const { buttons } = await setUp({
      role: 'zoo-admin',
      animal: { ...ANIMAL, name: 'Bruno', status: 'DECEASED' },
      plans: [makePlan('ENDED')],
    });
    expect(buttons()).not.toContain('New plan');
  });

  it('shows a Try again button when the plans fail to load, and retries', async () => {
    const { buttons, store, root } = await setUp({ state: 'error' });
    expect(root.querySelector('[role="alert"]')).not.toBeNull();
    expect(buttons()).toContain('Try again');
    Array.from(root.querySelectorAll('button'))
      .find((b) => b.textContent!.trim() === 'Try again')!
      .click();
    expect(store.retry).toHaveBeenCalled();
  });

  it('shows a loading placeholder while the plans load', async () => {
    const { root } = await setUp({ state: 'loading' });
    expect(root.querySelector('[aria-busy="true"]')).not.toBeNull();
    expect(root.querySelector('app-meal-track')).toBeNull();
  });
});
