import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { LoadState } from '../../../core/data/animal-store';
import { HealthStore } from '../../../core/data/health-store';
import { Animal, ZooRole } from '../../../core/models/animal';
import { MedicalRecord, MedicalRecordDetail, Treatment } from '../../../core/models/health';
import { can } from '../../../core/models/permissions';
import { Session } from '../../../core/session/session';
import { RecordSheet } from '../record-sheet/record-sheet';
import { HealthSection } from './health-section';

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

const NEWEST: MedicalRecord = {
  id: 'r-new',
  animalId: ANIMAL.id,
  reason: 'Limping on the left hind leg',
  diagnosis: 'Soft-tissue strain of the left hock.',
  examinedOn: '2026-09-18',
  veterinarian: 'Dr. Bianchi',
  createdBy: 'vet.bianchi',
  updatedBy: null,
};

const OLDER: MedicalRecord = {
  ...NEWEST,
  id: 'r-old',
  reason: 'Annual health check',
  diagnosis: 'Good body condition.',
  examinedOn: '2026-03-04',
};

function treatment(
  status: Treatment['status'],
  description: string,
  recordId = NEWEST.id,
): Treatment {
  return {
    id: `t-${description}`,
    medicalRecordId: recordId,
    description,
    status,
    startedOn: status === 'PRESCRIBED' ? null : '2026-09-19',
    endedOn: status === 'COMPLETED' || status === 'CANCELLED' ? '2026-09-25' : null,
    createdBy: 'vet.bianchi',
    updatedBy: null,
  };
}

interface Options {
  role?: ZooRole;
  animal?: Animal;
  /** Given in any order: the section sorts them newest first. */
  records?: MedicalRecord[];
  state?: LoadState;
  treatments?: Treatment[];
}

function fakeStore(animal: Animal, options: Options) {
  const treatments = options.treatments ?? [];
  const details = new Map<string, MedicalRecordDetail>(
    [NEWEST, OLDER].map((r) => [
      r.id,
      { ...r, treatments: treatments.filter((t) => t.medicalRecordId === r.id) },
    ]),
  );
  const known = signal<ReadonlySet<string>>(new Set());
  return {
    animalId: signal(animal.id),
    state: signal<LoadState>(options.state ?? 'ready'),
    records: signal(options.records ?? [NEWEST, OLDER]),
    detail: (id: string) => (known().has(id) ? details.get(id) : undefined),
    detailState: (id: string): LoadState => (known().has(id) ? 'ready' : 'idle'),
    load: vi.fn(async () => {}),
    loadDetail: vi.fn(async (id: string) => known.update((s) => new Set(s).add(id))),
    retry: vi.fn(async () => {}),
  };
}

function fakeSession(role: ZooRole) {
  return {
    role: signal(role),
    username: signal('vet.bianchi'),
    can: (permission: Parameters<typeof can>[1]) => can(role, permission),
  };
}

describe('HealthSection', () => {
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
      imports: [HealthSection],
      providers: [
        { provide: HealthStore, useValue: store },
        { provide: Session, useValue: fakeSession(options.role ?? 'zoo-keeper') },
      ],
    });
    const fixture = TestBed.createComponent(HealthSection);
    fixture.componentRef.setInput('animal', animal);
    const settle = async () => {
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    };
    await settle();
    const root: HTMLElement = fixture.nativeElement;
    return {
      fixture,
      store,
      root,
      settle,
      /** Text of the buttons on the page itself, not those inside the closed sheets. */
      buttons: () =>
        Array.from(root.querySelectorAll('button'))
          .filter((b) => !b.closest('dialog'))
          .map((b) => b.textContent!.replace(/\s+/g, ' ').trim()),
      headers: () => Array.from(root.querySelectorAll<HTMLButtonElement>('.rec-head')),
      text: () => root.textContent!.replace(/\s+/g, ' '),
    };
  }

  const TREATMENTS = [
    treatment('ACTIVE', 'Meloxicam once daily'),
    treatment('COMPLETED', 'Dental scaling', OLDER.id),
  ];

  it('loads the animal’s records and the newest record’s treatments', async () => {
    const { store } = await setUp({ treatments: TREATMENTS });
    expect(store.load).toHaveBeenCalledWith(ANIMAL.id);
    expect(store.loadDetail).toHaveBeenCalledTimes(1);
    expect(store.loadDetail).toHaveBeenCalledWith(NEWEST.id);
  });

  it('lists records newest first and starts only the newest one expanded', async () => {
    const { headers, text } = await setUp({
      records: [OLDER, NEWEST],
      treatments: TREATMENTS,
    });
    expect(headers().map((h) => h.textContent)).toEqual([
      expect.stringContaining('Limping on the left hind leg'),
      expect.stringContaining('Annual health check'),
    ]);
    expect(headers().map((h) => h.getAttribute('aria-expanded'))).toEqual(['true', 'false']);
    expect(headers()[0].getAttribute('aria-controls')).toBe(`health-record-${NEWEST.id}`);
    expect(text()).toContain('Soft-tissue strain of the left hock.');
    expect(text()).toContain('Recorded by vet.bianchi');
    expect(text()).not.toContain('Good body condition.');
  });

  it('shows the date, the reason, the vet and a summary of the treatments in the header', async () => {
    const { headers } = await setUp({ treatments: TREATMENTS });
    const header = headers()[0].textContent!.replace(/\s+/g, ' ');
    expect(header).toContain('18 Sep 2026');
    expect(header).toContain('Dr. Bianchi');
    expect(header).toContain('Limping on the left hind leg');
    expect(header).toContain('1 active');
    expect(headers()[1].textContent).not.toContain('active');
  });

  it('opens an older record on click, loads its treatments and shows their summary', async () => {
    const { headers, store, settle, text } = await setUp({ treatments: TREATMENTS });
    headers()[1].click();
    await settle();
    expect(headers()[1].getAttribute('aria-expanded')).toBe('true');
    expect(store.loadDetail).toHaveBeenCalledWith(OLDER.id);
    expect(text()).toContain('Good body condition.');
    expect(text()).toContain('Dental scaling');
    expect(headers()[1].textContent).toContain('1 completed');

    headers()[1].click();
    await settle();
    expect(headers()[1].getAttribute('aria-expanded')).toBe('false');
    expect(text()).not.toContain('Good body condition.');
  });

  it('orders open treatments first, keeping the given order inside each group', async () => {
    const { root } = await setUp({
      treatments: [
        treatment('CANCELLED', 'Second closed'),
        treatment('COMPLETED', 'First closed'),
        treatment('PRESCRIBED', 'Waiting'),
        treatment('ACTIVE', 'Being given'),
      ],
    });
    const descriptions = Array.from(root.querySelectorAll('.tx-desc')).map((e) => e.textContent);
    expect(descriptions).toEqual(['Being given', 'Waiting', 'Second closed', 'First closed']);
    const states = Array.from(root.querySelectorAll('.tx')).map((e) =>
      e.getAttribute('data-treatment'),
    );
    expect(states).toEqual(['ACTIVE', 'PRESCRIBED', 'CANCELLED', 'COMPLETED']);
  });

  it('gives each treatment its status as a word, next to the icon', async () => {
    const { root } = await setUp({ treatments: [treatment('ACTIVE', 'Meloxicam once daily')] });
    const meta = root.querySelector('.tx-meta')!.textContent!.replace(/\s+/g, ' ');
    expect(meta).toContain('Active');
    expect(meta).toContain('started 19 Sep 2026');
    expect(root.querySelector('.tx .status-tile app-icon')).not.toBeNull();
  });

  it('shows a keeper the records and the lock note, but no Change status', async () => {
    const { buttons, text } = await setUp({ role: 'zoo-keeper', treatments: TREATMENTS });
    expect(text()).toContain('Only vets and admins can add records or change treatments.');
    expect(text()).toContain('Meloxicam once daily');
    expect(buttons().some((b) => b.startsWith('Change status'))).toBe(false);
  });

  it('shows a vet Change status only on open treatments, and no lock note', async () => {
    const { buttons, text, headers, settle } = await setUp({
      role: 'zoo-vet',
      treatments: [
        treatment('ACTIVE', 'Meloxicam once daily'),
        treatment('PRESCRIBED', 'Physiotherapy'),
        treatment('COMPLETED', 'Wound dressing'),
        treatment('CANCELLED', 'Antibiotic course'),
      ],
    });
    const change = () => buttons().filter((b) => b.startsWith('Change status'));
    expect(change()).toEqual([
      'Change status of Meloxicam once daily',
      'Change status of Physiotherapy',
    ]);
    expect(text()).not.toContain('Only vets and admins');
    expect(text()).not.toContain('is deceased');
    headers()[1].click();
    await settle();
    expect(change()).toHaveLength(2);
  });

  it('shows an admin Change status too', async () => {
    const { buttons } = await setUp({ role: 'zoo-admin', treatments: TREATMENTS });
    expect(buttons().some((b) => b.startsWith('Change status'))).toBe(true);
  });

  it('tells a vet what a deceased animal can still get, and hides nothing else', async () => {
    const { text, buttons } = await setUp({
      role: 'zoo-vet',
      animal: { ...ANIMAL, name: 'Bruno', status: 'DECEASED' },
      treatments: [treatment('PRESCRIBED', 'Physiotherapy')],
    });
    expect(text()).toContain(
      'Bruno is deceased. You can still add a post-mortem record. Treatments can no longer be prescribed or started.',
    );
    expect(text()).not.toContain('Only vets and admins');
    // Cancel stays possible, so the button is still there.
    expect(buttons().some((b) => b.startsWith('Change status'))).toBe(true);
  });

  it('shows a keeper only the keeper note for a deceased animal', async () => {
    const { text } = await setUp({
      role: 'zoo-keeper',
      animal: { ...ANIMAL, name: 'Bruno', status: 'DECEASED' },
    });
    expect(text()).toContain('Only vets and admins can add records or change treatments.');
    expect(text()).not.toContain('post-mortem');
  });

  it('says when a record has no treatments', async () => {
    const { text, headers } = await setUp({ treatments: [] });
    expect(text()).toContain('No treatments for this record.');
    expect(headers()[0].textContent).toContain('No treatments');
  });

  it('says there are no records yet', async () => {
    const { text, root } = await setUp({ records: [] });
    expect(text()).toContain('No medical records yet.');
    expect(root.querySelector('.rec-head')).toBeNull();
  });

  it('shows a Try again button when the records fail to load, and retries', async () => {
    const { buttons, store, root } = await setUp({ state: 'error' });
    expect(root.querySelector('[role="alert"]')).not.toBeNull();
    expect(buttons()).toContain('Try again');
    Array.from(root.querySelectorAll('button'))
      .find((b) => b.textContent!.trim() === 'Try again')!
      .click();
    expect(store.retry).toHaveBeenCalled();
  });

  it('shows a loading placeholder while the records load', async () => {
    const { root } = await setUp({ state: 'loading' });
    expect(root.querySelector('[aria-busy="true"]')).not.toBeNull();
    expect(root.querySelector('.rec-head')).toBeNull();
  });

  describe('adding records and prescribing', () => {
    const DECEASED: Animal = { ...ANIMAL, name: 'Bruno', status: 'DECEASED' };
    const newRecord = (t: { buttons: () => string[] }) => t.buttons().includes('New record');
    const prescribe = (t: { buttons: () => string[] }) =>
      t.buttons().filter((b) => b.startsWith('Prescribe'));

    it('shows vets and admins New record, and a keeper nothing to add', async () => {
      for (const role of ['zoo-vet', 'zoo-admin'] as const) {
        expect(newRecord(await setUp({ role }))).toBe(true);
      }
      expect(newRecord(await setUp({ role: 'zoo-keeper' }))).toBe(false);
    });

    it('keeps New record for a deceased animal, for a post-mortem', async () => {
      const vet = await setUp({ role: 'zoo-vet', animal: DECEASED });
      expect(newRecord(vet)).toBe(true);
      expect(vet.text()).toContain('You can still add a post-mortem record.');
    });

    it('offers New record as the primary action when there are no records', async () => {
      const { root, buttons } = await setUp({ role: 'zoo-vet', records: [] });
      expect(buttons().filter((b) => b === 'New record')).toHaveLength(1);
      const button = Array.from(root.querySelectorAll('button')).find(
        (b) => b.textContent!.trim() === 'New record',
      )!;
      expect(button.classList).toContain('btn-primary');
      expect(newRecord(await setUp({ role: 'zoo-keeper', records: [] }))).toBe(false);
    });

    it('offers Prescribe inside each expanded record of a living animal', async () => {
      const { buttons, headers, settle } = await setUp({
        role: 'zoo-vet',
        treatments: TREATMENTS,
      });
      expect(buttons().filter((b) => b.startsWith('Prescribe'))).toEqual([
        'Prescribe for the 18 Sep 2026 record',
      ]);
      headers()[1].click();
      await settle();
      expect(buttons().filter((b) => b.startsWith('Prescribe'))).toEqual([
        'Prescribe for the 18 Sep 2026 record',
        'Prescribe for the 4 Mar 2026 record',
      ]);
    });

    it('offers an admin Prescribe too', async () => {
      expect(prescribe(await setUp({ role: 'zoo-admin' }))).toHaveLength(1);
    });

    it('hides Prescribe from keepers and for a deceased animal', async () => {
      expect(prescribe(await setUp({ role: 'zoo-keeper' }))).toEqual([]);
      expect(prescribe(await setUp({ role: 'zoo-vet', animal: DECEASED }))).toEqual([]);
      expect(prescribe(await setUp({ role: 'zoo-admin', animal: DECEASED }))).toEqual([]);
    });

    it('opens a record that was just added, even one dated before the others', async () => {
      const backdated: MedicalRecord = { ...OLDER, id: 'r-back', examinedOn: '2025-01-10' };
      const { fixture, store, headers, settle, root } = await setUp({ role: 'zoo-vet' });
      store.records.set([NEWEST, OLDER, backdated]);
      await settle();
      expect(headers()[2].getAttribute('aria-expanded')).toBe('false');

      fixture.debugElement
        .query(By.directive(RecordSheet))
        .componentInstance.created.emit(backdated);
      await settle();
      expect(headers()[2].getAttribute('aria-expanded')).toBe('true');
      expect(store.loadDetail).toHaveBeenCalledWith('r-back');
      expect(root.textContent).toContain('Recorded by');
    });
  });
});
