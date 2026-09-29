import {
  Component,
  ElementRef,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { errorMessage } from '../../../core/data/animal-store';
import { HealthStore } from '../../../core/data/health-store';
import { Animal } from '../../../core/models/animal';
import { Treatment, TreatmentStatus, canTreatmentTransitionTo } from '../../../core/models/health';
import { TREATMENT_STATUS_LABELS } from '../../../core/models/labels';
import { Icon } from '../../../core/ui/icon/icon';

/** What each target status is called as an action. */
const ACTIONS: Readonly<Record<TreatmentStatus, string>> = {
  PRESCRIBED: 'Prescribe',
  ACTIVE: 'Start',
  COMPLETED: 'Complete',
  CANCELLED: 'Cancel',
};

const TARGETS: readonly TreatmentStatus[] = ['ACTIVE', 'COMPLETED', 'CANCELLED'];

/** Sheet for a vet or admin to start, complete or cancel a treatment. */
@Component({
  selector: 'app-treatment-status-sheet',
  imports: [Icon],
  templateUrl: './treatment-status-sheet.html',
  styleUrl: './treatment-status-sheet.scss',
})
export class TreatmentStatusSheet {
  private readonly store = inject(HealthStore);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly animal = input.required<Animal>();
  /** The medical record the treatment belongs to. */
  readonly recordId = input.required<string>();
  readonly treatment = input.required<Treatment>();
  readonly changed = output<Treatment>();

  protected readonly labels = TREATMENT_STATUS_LABELS;
  protected readonly actions = ACTIONS;
  protected readonly selected = signal<TreatmentStatus | null>(null);
  protected readonly pending = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly deceased = computed(() => this.animal().status === 'DECEASED');
  /** Only the moves the backend allows, and no start for an animal that has died. */
  protected readonly options = computed(() =>
    TARGETS.filter(
      (target) =>
        canTreatmentTransitionTo(this.treatment(), target) &&
        !(target === 'ACTIVE' && this.deceased()),
    ),
  );
  /** True when the animal’s death is the only reason the treatment can’t be started. */
  protected readonly startBlocked = computed(
    () => this.deceased() && canTreatmentTransitionTo(this.treatment(), 'ACTIVE'),
  );
  /** Completing and cancelling are final. */
  protected readonly grave = computed(() => {
    const status = this.selected();
    return status === 'COMPLETED' || status === 'CANCELLED';
  });
  protected readonly titleId = computed(() => `treatment-status-title-${this.treatment().id}`);

  open(): void {
    this.selected.set(null);
    this.error.set(null);
    this.dialog().nativeElement.showModal();
  }

  close(): void {
    this.dialog().nativeElement.close();
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === this.dialog().nativeElement && !this.pending()) {
      this.close();
    }
  }

  protected async submit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const status = this.selected();
    if (!status || this.pending()) {
      return;
    }
    this.pending.set(true);
    this.error.set(null);
    try {
      const updated = await this.store.updateTreatmentStatus(
        this.recordId(),
        this.treatment().id,
        status,
      );
      this.close();
      this.changed.emit(updated);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.pending.set(false);
    }
  }
}
