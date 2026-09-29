import { By } from '@angular/platform-browser';
import { TestBed } from '@angular/core/testing';
import { HealthStore } from '../../../core/data/health-store';
import { Animal } from '../../../core/models/animal';
import { Treatment, TreatmentStatus } from '../../../core/models/health';
import { TreatmentStatusSheet } from './treatment-status-sheet';

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

function makeTreatment(status: TreatmentStatus): Treatment {
  return {
    id: 't1',
    medicalRecordId: 'r1',
    description: 'Meloxicam once daily with food',
    status,
    startedOn: status === 'PRESCRIBED' ? null : '2026-09-19',
    endedOn: null,
    createdBy: 'vet.bianchi',
    updatedBy: null,
  };
}

describe('TreatmentStatusSheet', () => {
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
    status: TreatmentStatus,
    options: {
      animal?: Animal;
      update?: (recordId: string, treatmentId: string, s: TreatmentStatus) => Promise<Treatment>;
    } = {},
  ) {
    const update = vi.fn(
      options.update ?? (async (_r: string, _t: string, s: TreatmentStatus) => makeTreatment(s)),
    );
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [TreatmentStatusSheet],
      providers: [{ provide: HealthStore, useValue: { updateTreatmentStatus: update } }],
    });
    const fixture = TestBed.createComponent(TreatmentStatusSheet);
    fixture.componentRef.setInput('animal', options.animal ?? ANIMAL);
    fixture.componentRef.setInput('recordId', 'r1');
    fixture.componentRef.setInput('treatment', makeTreatment(status));
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

  function choose(f: Fixture, value: TreatmentStatus): void {
    const radios: HTMLInputElement[] = Array.from(
      f.nativeElement.querySelectorAll('input[type="radio"]'),
    );
    const radio = radios.find((r) => r.value === value)!;
    radio.checked = true;
    radio.dispatchEvent(new Event('change'));
    f.detectChanges();
  }

  async function submit(f: Fixture): Promise<void> {
    await f.debugElement
      .query(By.css('form'))
      .triggerEventHandler('submit', { preventDefault: () => {} });
    await f.whenStable();
    f.detectChanges();
  }

  it('offers Start and Cancel for a prescribed treatment', () => {
    const { fixture } = setUp('PRESCRIBED');
    expect(optionTitles(fixture)).toEqual(['Start', 'Cancel']);
  });

  it('offers Complete and Cancel for an active treatment', () => {
    const { fixture } = setUp('ACTIVE');
    expect(optionTitles(fixture)).toEqual(['Complete', 'Cancel']);
  });

  it('offers only Cancel for a prescribed treatment of a deceased animal, and says why', () => {
    const { fixture } = setUp('PRESCRIBED', {
      animal: { ...ANIMAL, name: 'Bruno', status: 'DECEASED' },
    });
    expect(optionTitles(fixture)).toEqual(['Cancel']);
    expect(fixture.nativeElement.querySelector('.note').textContent).toContain(
      'Bruno is deceased, so this treatment can no longer be started.',
    );
  });

  it('still offers Complete and Cancel for an active treatment of a deceased animal', () => {
    const { fixture } = setUp('ACTIVE', { animal: { ...ANIMAL, status: 'DECEASED' } });
    expect(optionTitles(fixture)).toEqual(['Complete', 'Cancel']);
    expect(fixture.nativeElement.querySelector('.note')).toBeNull();
  });

  it('keeps the submit button disabled until an option is chosen', () => {
    const { fixture } = setUp('PRESCRIBED');
    expect(submitButton(fixture).disabled).toBe(true);
    choose(fixture, 'ACTIVE');
    expect(submitButton(fixture).disabled).toBe(false);
    expect(submitButton(fixture).classList).toContain('btn-primary');
    expect(fixture.nativeElement.querySelector('.warning-note')).toBeNull();
  });

  it.each<TreatmentStatus>(['COMPLETED', 'CANCELLED'])(
    'uses the grave button and a warning for %s, which cannot be undone',
    (target) => {
      const { fixture } = setUp('ACTIVE');
      choose(fixture, target);
      expect(submitButton(fixture).classList).toContain('btn-grave');
      expect(submitButton(fixture).classList).not.toContain('btn-primary');
      expect(fixture.nativeElement.querySelector('.warning-note').textContent).toContain(
        'This can’t be undone. The treatment stays on record.',
      );
    },
  );

  it('updates the treatment and emits the confirmed treatment', async () => {
    const { fixture, update } = setUp('PRESCRIBED');
    let emitted: Treatment | undefined;
    fixture.componentInstance.changed.subscribe((t) => (emitted = t));
    choose(fixture, 'ACTIVE');
    await submit(fixture);
    expect(update).toHaveBeenCalledWith('r1', 't1', 'ACTIVE');
    expect(emitted?.status).toBe('ACTIVE');
    expect(fixture.nativeElement.querySelector('dialog').hasAttribute('open')).toBe(false);
  });

  it('shows the failure in an alert and keeps the sheet open', async () => {
    const { fixture } = setUp('ACTIVE', {
      update: async () => {
        throw new Error('Network down');
      },
    });
    let emitted = false;
    fixture.componentInstance.changed.subscribe(() => (emitted = true));
    choose(fixture, 'COMPLETED');
    await submit(fixture);
    const alert = fixture.nativeElement.querySelector('.form-error[role="alert"]');
    expect(alert).not.toBeNull();
    expect(alert.textContent.length).toBeGreaterThan(0);
    expect(emitted).toBe(false);
    expect(fixture.nativeElement.querySelector('dialog').hasAttribute('open')).toBe(true);
    expect(submitButton(fixture).disabled).toBe(false);
  });
});
