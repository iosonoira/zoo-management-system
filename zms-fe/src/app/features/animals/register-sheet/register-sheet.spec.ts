import { By } from '@angular/platform-browser';
import { TestBed } from '@angular/core/testing';
import { AnimalStore } from '../../../core/data/animal-store';
import { ApiError } from '../../../core/data/animal-api';
import { Animal, Enclosure, NewAnimal } from '../../../core/models/animal';
import { RegisterSheet } from './register-sheet';

const ENCLOSURE: Enclosure = { id: 'e1', name: 'Savanna Paddock', habitat: 'TERRESTRIAL' };

function makeAnimal(input: NewAnimal): Animal {
  return {
    id: 'new-1',
    name: input.name,
    species: input.species,
    dangerous: input.dangerous,
    habitat: input.habitat,
    enclosureId: input.enclosureId,
    arrivalDate: input.arrivalDate,
    status: 'HEALTHY',
    createdBy: 'admin.rossi',
    updatedBy: 'admin.rossi',
  };
}

function fakeStore(register: (input: NewAnimal) => Promise<Animal>): AnimalStore {
  const enclosureById = new Map([[ENCLOSURE.id, ENCLOSURE]]);
  return {
    enclosures: () => [ENCLOSURE],
    animals: () => [],
    enclosureById: () => enclosureById,
    register,
  } as unknown as AnimalStore;
}

describe('RegisterSheet', () => {
  // jsdom does not implement <dialog>.showModal()/close(); stub them so open()/close() work.
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.removeAttribute('open');
    };
  });

  function setUp(register: (input: NewAnimal) => Promise<Animal>) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [RegisterSheet],
      providers: [{ provide: AnimalStore, useValue: fakeStore(register) }],
    });
    const fixture = TestBed.createComponent(RegisterSheet);
    const component = fixture.componentInstance;
    component.open();
    fixture.detectChanges();
    return { fixture, component };
  }

  function submitButton(fixture: ReturnType<typeof setUp>['fixture']): HTMLButtonElement {
    return fixture.nativeElement.querySelector('button[type="submit"]');
  }

  function fillValidForm(fixture: ReturnType<typeof setUp>['fixture']): void {
    const [name, species] = fixture.nativeElement.querySelectorAll('input[type="text"]');
    name.value = 'Simba';
    name.dispatchEvent(new Event('input'));
    species.value = 'African lion';
    species.dispatchEvent(new Event('input'));

    // The formField directive listens for the 'input' event (not 'change') to read
    // native control values, matching real radio/checkbox click behaviour in browsers.
    const radio: HTMLInputElement = fixture.nativeElement.querySelector('input[type="radio"]');
    radio.checked = true;
    radio.dispatchEvent(new Event('input'));
  }

  it('disables submit while required fields are empty', () => {
    const { fixture } = setUp(async (input) => makeAnimal(input));
    expect(submitButton(fixture).disabled).toBe(true);
  });

  it('enables submit once name, species and enclosure are filled in', () => {
    const { fixture } = setUp(async (input) => makeAnimal(input));
    fillValidForm(fixture);
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(false);
  });

  it('registers with trimmed values, the derived habitat and the chosen date, and emits registered', async () => {
    let received: NewAnimal | undefined;
    const { fixture, component } = setUp(async (input) => {
      received = input;
      return makeAnimal(input);
    });
    fillValidForm(fixture);
    fixture.detectChanges();

    // pad the model with whitespace to prove trimming, bypassing the DOM for brevity
    (component as unknown as { model: { update: (fn: (m: object) => object) => void } }).model.update(
      (m) => ({ ...m, name: '  Simba  ', species: ' African lion ' }),
    );
    fixture.detectChanges();

    let emitted: Animal | undefined;
    component.registered.subscribe((a) => (emitted = a));

    const form = fixture.debugElement.query(By.css('form'));
    await form.triggerEventHandler('submit', { preventDefault: () => {} });
    await fixture.whenStable();

    expect(received).toEqual({
      name: 'Simba',
      species: 'African lion',
      dangerous: false,
      habitat: 'TERRESTRIAL',
      enclosureId: 'e1',
      arrivalDate: (component as unknown as { today: string }).today,
    });
    expect(emitted?.name).toBe('Simba');
  });

  it('shows the ApiError message in the alert when registration fails', async () => {
    const { fixture } = setUp(async () => {
      throw new ApiError(403, 'Only admins can register animals.');
    });
    fillValidForm(fixture);
    fixture.detectChanges();

    const form = fixture.debugElement.query(By.css('form'));
    await form.triggerEventHandler('submit', { preventDefault: () => {} });
    await fixture.whenStable();
    fixture.detectChanges();

    const alert: HTMLElement = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert.textContent).toContain('Only admins can register animals.');
  });
});
