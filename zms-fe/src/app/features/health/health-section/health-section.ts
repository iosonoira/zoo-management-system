import {
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  linkedSignal,
  output,
  untracked,
  viewChild,
  viewChildren,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { LoadState } from '../../../core/data/animal-store';
import { HealthStore } from '../../../core/data/health-store';
import { Animal } from '../../../core/models/animal';
import {
  MedicalRecord,
  Treatment,
  TreatmentStatus,
  isTreatmentOpen,
} from '../../../core/models/health';
import { TREATMENT_STATUS_LABELS } from '../../../core/models/labels';
import { Session } from '../../../core/session/session';
import { Icon } from '../../../core/ui/icon/icon';
import { RecordSheet } from '../record-sheet/record-sheet';
import { TreatmentSheet } from '../treatment-sheet/treatment-sheet';
import { TreatmentStatusSheet } from '../treatment-status-sheet/treatment-status-sheet';

/** Open treatments come first, the one being given before the one still waiting. */
const RANK: Readonly<Record<TreatmentStatus, number>> = {
  ACTIVE: 0,
  PRESCRIBED: 1,
  COMPLETED: 2,
  CANCELLED: 2,
};

const SUMMARY_ORDER: readonly TreatmentStatus[] = [
  'ACTIVE',
  'PRESCRIBED',
  'COMPLETED',
  'CANCELLED',
];

/** A medical record as the section shows it. */
interface RecordView {
  readonly record: MedicalRecord;
  readonly open: boolean;
  readonly panelId: string;
  readonly detailState: LoadState;
  /** Null until the record’s treatments are known. */
  readonly treatments: readonly Treatment[] | null;
  /** Null until the record’s treatments are known. */
  readonly summary: string | null;
}

/**
 * The Health section of the animal page: the animal’s medical records, newest first, each
 * opening onto its diagnosis and treatments, and the sheet to change a treatment’s status.
 * It loads and fails on its own, so the rest of the page keeps working when health-service
 * is down.
 */
@Component({
  selector: 'app-health-section',
  imports: [DatePipe, Icon, RecordSheet, TreatmentSheet, TreatmentStatusSheet],
  templateUrl: './health-section.html',
  styleUrl: './health-section.scss',
})
export class HealthSection {
  protected readonly store = inject(HealthStore);
  protected readonly session = inject(Session);
  private readonly injector = inject(Injector);

  readonly animal = input.required<Animal>();
  /** A confirmation for the page’s live region. */
  readonly announce = output<string>();

  private readonly headers = viewChildren<ElementRef<HTMLButtonElement>>('header');
  private readonly statusSheets = viewChildren(TreatmentStatusSheet);
  private readonly prescribeSheets = viewChildren(TreatmentSheet);
  private readonly recordSheet = viewChild(RecordSheet);

  protected readonly statusLabels = TREATMENT_STATUS_LABELS;

  protected readonly deceased = computed(() => this.animal().status === 'DECEASED');
  protected readonly canCreate = computed(() => this.session.can('createMedicalRecord'));
  protected readonly canChange = computed(() => this.session.can('updateTreatmentStatus'));
  /** A record can still be added after death, for a post-mortem; a treatment can’t. */
  protected readonly canPrescribe = computed(
    () => this.session.can('prescribeTreatment') && !this.deceased(),
  );
  /** Explains the missing buttons of a role that can do none of it. */
  protected readonly readOnly = computed(
    () => !this.canCreate() && !this.session.can('prescribeTreatment') && !this.canChange(),
  );
  /** Explains why Prescribe is gone, and what is still possible. */
  protected readonly deceasedNote = computed(
    () =>
      this.deceased() &&
      this.canCreate() &&
      (this.session.can('prescribeTreatment') || this.canChange()),
  );

  /** The store keeps one animal at a time; only trust it once it holds this one. */
  private readonly forThisAnimal = computed(() => this.store.animalId() === this.animal().id);
  protected readonly ready = computed(() => this.forThisAnimal() && this.store.state() === 'ready');
  protected readonly failed = computed(
    () => this.forThisAnimal() && this.store.state() === 'error',
  );

  /** Newest first; records of the same day keep a fixed order. */
  private readonly records = computed(() =>
    [...this.store.records()].sort(
      (a, b) => b.examinedOn.localeCompare(a.examinedOn) || a.id.localeCompare(b.id),
    ),
  );

  /** What the reader has opened or closed; a record they have not touched is open if newest. */
  private readonly toggled = linkedSignal<string, ReadonlyMap<string, boolean>>({
    source: () => this.animal().id,
    computation: () => new Map(),
  });

  private readonly openIds = computed(() => {
    const newest = this.records()[0]?.id;
    return this.records()
      .filter((r) => this.toggled().get(r.id) ?? r.id === newest)
      .map((r) => r.id);
  });

  protected readonly views = computed<readonly RecordView[]>(() => {
    const open = new Set(this.openIds());
    return this.records().map((record) => {
      const detail = this.store.detail(record.id);
      const treatments = detail ? orderTreatments(detail.treatments) : null;
      return {
        record,
        open: open.has(record.id),
        panelId: `health-record-${record.id}`,
        detailState: this.store.detailState(record.id),
        treatments,
        summary: treatments ? summarise(treatments) : null,
      };
    });
  });

  constructor() {
    effect(() => {
      const id = this.animal().id;
      untracked(() => void this.store.load(id));
    });
    effect(() => {
      if (!this.ready()) {
        return;
      }
      for (const id of this.openIds()) {
        untracked(() => void this.store.loadDetail(id));
      }
    });
  }

  protected toggle(view: RecordView): void {
    this.toggled.update((map) => new Map(map).set(view.record.id, !view.open));
  }

  protected openStatus(treatment: Treatment): void {
    this.statusSheets()
      .find((sheet) => sheet.treatment().id === treatment.id)
      ?.open();
  }

  protected openRecord(): void {
    this.recordSheet()?.open();
  }

  protected openPrescribe(record: MedicalRecord): void {
    this.prescribeSheets()
      .find((sheet) => sheet.record().id === record.id)
      ?.open();
  }

  protected onRecordCreated(record: MedicalRecord): void {
    // The newest record opens by itself, but not one dated before the others.
    this.toggled.update((map) => new Map(map).set(record.id, true));
    this.announce.emit(`Medical record added for ${this.animal().name}.`);
    this.focusHeader(record.id);
  }

  protected onPrescribed(): void {
    this.announce.emit(`Treatment prescribed for ${this.animal().name}.`);
  }

  protected isOpen(treatment: Treatment): boolean {
    return isTreatmentOpen(treatment);
  }

  protected onChanged(recordId: string, treatment: Treatment): void {
    // Descriptions are sentences, often ending in a full stop of their own.
    const description = treatment.description.replace(/[.\s]+$/, '');
    this.announce.emit(
      `${description} is now ${TREATMENT_STATUS_LABELS[treatment.status].label.toLowerCase()}.`,
    );
    // A closed treatment loses its button, and with it the focus the sheet gave back.
    if (!isTreatmentOpen(treatment)) {
      this.focusHeader(recordId);
    }
  }

  private focusHeader(recordId: string): void {
    afterNextRender(
      () =>
        this.headers()
          .find((h) => h.nativeElement.dataset['record'] === recordId)
          ?.nativeElement.focus(),
      { injector: this.injector },
    );
  }
}

/** Open treatments first, then closed ones. Sorting is stable, so a group keeps the backend order. */
function orderTreatments(treatments: readonly Treatment[]): readonly Treatment[] {
  return [...treatments].sort((a, b) => RANK[a.status] - RANK[b.status]);
}

/** For example “1 active, 2 completed”. */
function summarise(treatments: readonly Treatment[]): string {
  if (treatments.length === 0) {
    return 'No treatments';
  }
  return SUMMARY_ORDER.map((status) => ({
    status,
    count: treatments.filter((t) => t.status === status).length,
  }))
    .filter(({ count }) => count > 0)
    .map(({ status, count }) => `${count} ${TREATMENT_STATUS_LABELS[status].label.toLowerCase()}`)
    .join(', ');
}
