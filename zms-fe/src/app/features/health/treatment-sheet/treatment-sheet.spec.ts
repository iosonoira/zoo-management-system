import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ApiError } from '../../../core/data/api-error';
import { HealthStore } from '../../../core/data/health-store';
import { Animal } from '../../../core/models/animal';
import { MedicalRecord, Treatment } from '../../../core/models/health';
import { TreatmentSheet } from './treatment-sheet';

const ANIMAL: Animal = {
  id: 'a1',
  name: 'Pepe',
  species: 'Ring-tailed lemur',
  dangerous: false,
  habitat: 'TERRESTRIAL',
  enclosureId: 'e1',
  arrivalDate: '2023-02-14',
  status: 'IN_TREATMENT',
  createdBy: 'admin.rossi',
  updatedBy: 'vet.bianchi',
};

const RECORD: MedicalRecord = {
  id: 'r1',
  animalId: ANIMAL.id,
  reason: 'Limping on the left hind leg',
  diagnosis: 'Soft-tissue strain of the left hock.',
  examinedOn: '2026-09-18',
  veterinarian: 'Dr. Bianchi',
  createdBy: 'vet.bianchi',
  updatedBy: null,
};

function prescribed(description: string): Treatment {
  return {
    id: 't-new',
    medicalRecordId: RECORD.id,
    description,
    status: 'PRESCRIBED',
    startedOn: null,
    endedOn: null,
    createdBy: 'vet.bianchi',
    updatedBy: null,
  };
}

describe('TreatmentSheet', () => {
  // jsdom does not implement <dialog>.showModal()/close(); stub them so open()/close() work.
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.removeAttribute('open');
    };
  });

  function setUp(prescribe?: (recordId: string, description: string) => Promise<Treatment>) {
    const spy = vi.fn(prescribe ?? (async (_r: string, d: string) => prescribed(d)));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [TreatmentSheet],
      providers: [{ provide: HealthStore, useValue: { prescribe: spy } }],
    });
    const fixture = TestBed.createComponent(TreatmentSheet);
    fixture.componentRef.setInput('animal', ANIMAL);
    fixture.componentRef.setInput('record', RECORD);
    fixture.componentInstance.open();
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, prescribe: spy };
  }

  type Fixture = ReturnType<typeof setUp>['fixture'];

  const root = (f: Fixture): HTMLElement => f.nativeElement;
  const textarea = (f: Fixture): HTMLTextAreaElement =>
    root(f).querySelector('textarea') as HTMLTextAreaElement;
  const submitButton = (f: Fixture): HTMLButtonElement =>
    root(f).querySelector('button[type="submit"]') as HTMLButtonElement;

  function typeInto(f: Fixture, value: string): void {
    textarea(f).value = value;
    textarea(f).dispatchEvent(new Event('input'));
    f.detectChanges();
  }

  async function submit(f: Fixture): Promise<void> {
    await f.debugElement
      .query(By.css('form'))
      .triggerEventHandler('submit', { preventDefault: () => {} });
    await f.whenStable();
    f.detectChanges();
  }

  it('names the animal and the record, and explains the starting status', () => {
    const { fixture } = setUp();
    const head = root(fixture).querySelector('.sheet-head')!.textContent!.replace(/\s+/g, ' ');
    expect(head).toContain('Prescribe a treatment');
    expect(head).toContain(
      'Pepe · Limping on the left hind leg. It starts as prescribed; start it when it is first given.',
    );
    expect(root(fixture).textContent).toContain('What to give, how often, for how long.');
  });

  it('keeps submit disabled while the description is empty or only spaces', () => {
    const { fixture } = setUp();
    expect(submitButton(fixture).disabled).toBe(true);
    typeInto(fixture, '    ');
    expect(submitButton(fixture).disabled).toBe(true);
    typeInto(fixture, 'Meloxicam once daily with food for 7 days.');
    expect(submitButton(fixture).disabled).toBe(false);
  });

  it('accepts 500 characters and rejects 501', () => {
    const { fixture } = setUp();
    typeInto(fixture, 'x'.repeat(500));
    expect(submitButton(fixture).disabled).toBe(false);
    typeInto(fixture, 'x'.repeat(501));
    expect(submitButton(fixture).disabled).toBe(true);
  });

  it('shows the message only after the field has been touched', () => {
    const { fixture } = setUp();
    expect(root(fixture).querySelectorAll('.field-hint')).toHaveLength(1);
    textarea(fixture).dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(root(fixture).textContent).toContain('Say what to give, how often and for how long.');
  });

  it('sends the trimmed description for the record and emits the treatment', async () => {
    const { fixture, component, prescribe } = setUp();
    let emitted: Treatment | undefined;
    component.prescribed.subscribe((t) => (emitted = t));
    typeInto(fixture, '  Meloxicam once daily with food for 7 days.\n');
    await submit(fixture);
    expect(prescribe).toHaveBeenCalledWith('r1', 'Meloxicam once daily with food for 7 days.');
    expect(emitted?.status).toBe('PRESCRIBED');
    expect(root(fixture).querySelector('dialog')!.hasAttribute('open')).toBe(false);
  });

  it('shows the ApiError message in the alert and keeps the sheet open', async () => {
    const { fixture } = setUp(async () => {
      throw new ApiError(404, 'This medical record no longer exists.');
    });
    typeInto(fixture, 'Meloxicam once daily.');
    await submit(fixture);
    expect(root(fixture).querySelector('[role="alert"]')!.textContent).toContain(
      'This medical record no longer exists.',
    );
    expect(root(fixture).querySelector('dialog')!.hasAttribute('open')).toBe(true);
  });
});
