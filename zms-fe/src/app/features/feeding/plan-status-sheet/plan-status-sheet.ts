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
import { FeedingStore } from '../../../core/data/feeding-store';
import { Animal } from '../../../core/models/animal';
import { FeedingPlan, PlanStatus, canPlanTransitionTo } from '../../../core/models/feeding';
import { PLAN_STATUS_LABELS } from '../../../core/models/labels';
import { Icon } from '../../../core/ui/icon/icon';

/** What each target status is called as an action. */
const ACTIONS: Readonly<Record<PlanStatus, string>> = {
  ACTIVE: 'Resume',
  SUSPENDED: 'Suspend',
  ENDED: 'End',
};

const TARGETS: readonly PlanStatus[] = ['SUSPENDED', 'ACTIVE', 'ENDED'];

/** Sheet for a vet or admin to suspend, resume or end a feeding plan. */
@Component({
  selector: 'app-plan-status-sheet',
  imports: [Icon],
  templateUrl: './plan-status-sheet.html',
  styleUrl: './plan-status-sheet.scss',
})
export class PlanStatusSheet {
  private readonly store = inject(FeedingStore);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly animal = input.required<Animal>();
  readonly plan = input.required<FeedingPlan>();
  readonly changed = output<FeedingPlan>();

  protected readonly labels = PLAN_STATUS_LABELS;
  protected readonly actions = ACTIONS;
  protected readonly selected = signal<PlanStatus | null>(null);
  protected readonly pending = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Only the moves the backend allows from the plan's current status. */
  protected readonly options = computed(() =>
    TARGETS.filter((target) => canPlanTransitionTo(this.plan(), target)),
  );
  protected readonly grave = computed(() => this.selected() === 'ENDED');
  protected readonly titleId = computed(() => `plan-status-title-${this.plan().id}`);

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
      const updated = await this.store.updatePlanStatus(this.plan().id, status);
      this.close();
      this.changed.emit(updated);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.pending.set(false);
    }
  }
}
