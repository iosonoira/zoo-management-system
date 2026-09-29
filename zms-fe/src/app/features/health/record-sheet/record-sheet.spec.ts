import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ApiError } from '../../../core/data/api-error';
import { HealthStore } from '../../../core/data/health-store';
import { Animal, ZooRole } from '../../../core/models/animal';
import { MedicalRecord, NewMedicalRecord } from '../../../core/models/health';
import { Session } from '../../../core/session/session';
import { RecordSheet } from './record-sheet';

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

function created(input: NewMedicalRecord): MedicalRecord {
  return { id: 'new-record', ...input, createdBy: 'vet.bianchi', updatedBy: null };
}

/** Today as `YYYY-MM-DD` in local time, plus an offset in days. */
function isoDay(offset = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

describe('RecordSheet', () => {
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
      role?: ZooRole;
      animal?: Animal;
      create?: (input: NewMedicalRecord) => Promise<MedicalRecord>;
    } = {},
  ) {
    const create = vi.fn(options.create ?? (async (input: NewMedicalRecord) => created(input)));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [RecordSheet],
      providers: [
        { provide: HealthStore, useValue: { createRecord: create } },
        {
          provide: Session,
          useValue: { role: signal(options.role ?? 'zoo-vet'), username: signal('vet.bianchi') },
        },
      ],
    });
    const fixture = TestBed.createComponent(RecordSheet);
    fixture.componentRef.setInput('animal', options.animal ?? ANIMAL);
    fixture.componentInstance.open();
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, create };
  }

  type Fixture = ReturnType<typeof setUp>['fixture'];

  const root = (f: Fixture): HTMLElement => f.nativeElement;
  const dateInput = (f: Fixture): HTMLInputElement =>
    root(f).querySelector('input[type="date"]') as HTMLInputElement;
  const textInputs = (f: Fixture): HTMLInputElement[] =>
    Array.from(root(f).querySelectorAll('input[type="text"]'));
  const reasonInput = (f: Fixture) => textInputs(f)[0];
  const vetInput = (f: Fixture) => textInputs(f)[1];
  const diagnosisInput = (f: Fixture): HTMLTextAreaElement =>
    root(f).querySelector('textarea') as HTMLTextAreaElement;
  const submitButton = (f: Fixture): HTMLButtonElement =>
    root(f).querySelector('button[type="submit"]') as HTMLButtonElement;

  function typeInto(input: HTMLInputElement | HTMLTextAreaElement, value: string): void {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function fillValid(f: Fixture): void {
    typeInto(reasonInput(f), 'Limping on the left hind leg');
    typeInto(diagnosisInput(f), 'Soft-tissue strain of the left hock.');
    typeInto(vetInput(f), 'Dr. Bianchi');
    f.detectChanges();
  }

  async function submit(f: Fixture): Promise<void> {
    await f.debugElement
      .query(By.css('form'))
      .triggerEventHandler('submit', { preventDefault: () => {} });
    await f.whenStable();
    f.detectChanges();
  }

  it('starts on today, capped at today, with a disabled submit', () => {
    const { fixture } = setUp({ role: 'zoo-admin' });
    expect(dateInput(fixture).value).toBe(isoDay());
    expect(dateInput(fixture).getAttribute('max')).toBe(isoDay());
    expect(submitButton(fixture).disabled).toBe(true);
  });

  it('prefills the veterinarian with a vet’s username, and leaves it empty for an admin', () => {
    expect(vetInput(setUp({ role: 'zoo-vet' }).fixture).value).toBe('vet.bianchi');
    expect(vetInput(setUp({ role: 'zoo-admin' }).fixture).value).toBe('');
  });

  it('enables submit only once reason, diagnosis and veterinarian are filled', () => {
    const { fixture } = setUp({ role: 'zoo-admin' });
    typeInto(reasonInput(fixture), 'Annual health check');
    typeInto(diagnosisInput(fixture), 'Good body condition.');
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(true);

    typeInto(vetInput(fixture), 'Dr. Marino');
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(false);
  });

  it('rejects a future date and a blank date, and accepts today and the past', () => {
    const { fixture } = setUp();
    fillValid(fixture);
    expect(submitButton(fixture).disabled).toBe(false);

    typeInto(dateInput(fixture), isoDay(1));
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(true);

    typeInto(dateInput(fixture), '');
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(true);

    typeInto(dateInput(fixture), isoDay(-30));
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(false);
  });

  it('rejects text that is only spaces', () => {
    const { fixture } = setUp();
    fillValid(fixture);
    typeInto(diagnosisInput(fixture), '   ');
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(true);
  });

  it('enforces the length limits of 200, 1000 and 100 characters', () => {
    const { fixture } = setUp();
    fillValid(fixture);

    typeInto(reasonInput(fixture), 'x'.repeat(201));
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(true);
    typeInto(reasonInput(fixture), 'x'.repeat(200));

    typeInto(diagnosisInput(fixture), 'x'.repeat(1001));
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(true);
    typeInto(diagnosisInput(fixture), 'x'.repeat(1000));

    typeInto(vetInput(fixture), 'x'.repeat(101));
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(true);
    typeInto(vetInput(fixture), 'x'.repeat(100));
    fixture.detectChanges();
    expect(submitButton(fixture).disabled).toBe(false);
  });

  it('shows a message only after the field has been touched', () => {
    const { fixture } = setUp({ role: 'zoo-admin' });
    expect(root(fixture).querySelector('.field-hint')).toBeNull();

    reasonInput(fixture).dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(root(fixture).querySelector('.field-hint')!.textContent).toContain(
      'Say why the animal was examined.',
    );
  });

  it('sends the trimmed values for the animal and emits the new record', async () => {
    const { fixture, component, create } = setUp();
    typeInto(dateInput(fixture), '2026-09-18');
    typeInto(reasonInput(fixture), '  Limping on the left hind leg  ');
    typeInto(diagnosisInput(fixture), '  Soft-tissue strain of the left hock.\n');
    typeInto(vetInput(fixture), '  Dr. Bianchi ');
    fixture.detectChanges();

    let emitted: MedicalRecord | undefined;
    component.created.subscribe((r) => (emitted = r));
    await submit(fixture);

    expect(create).toHaveBeenCalledWith({
      animalId: 'a1',
      examinedOn: '2026-09-18',
      reason: 'Limping on the left hind leg',
      diagnosis: 'Soft-tissue strain of the left hock.',
      veterinarian: 'Dr. Bianchi',
    });
    expect(emitted?.id).toBe('new-record');
    expect(root(fixture).querySelector('dialog')!.hasAttribute('open')).toBe(false);
  });

  it('shows the ApiError message in the alert and keeps the sheet open', async () => {
    const { fixture } = setUp({
      create: async () => {
        throw new ApiError(422, 'The examination date is in the future.');
      },
    });
    fillValid(fixture);
    await submit(fixture);
    expect(root(fixture).querySelector('[role="alert"]')!.textContent).toContain(
      'The examination date is in the future.',
    );
    expect(root(fixture).querySelector('dialog')!.hasAttribute('open')).toBe(true);
  });

  it('says a deceased animal’s record is for a post-mortem, and only then', () => {
    const living = setUp();
    expect(root(living.fixture).querySelector('.note')).toBeNull();

    const { fixture } = setUp({ animal: { ...ANIMAL, name: 'Bruno', status: 'DECEASED' } });
    expect(root(fixture).querySelector('.note')!.textContent).toContain(
      'Bruno is deceased. Use this for a post-mortem examination.',
    );
  });
});
