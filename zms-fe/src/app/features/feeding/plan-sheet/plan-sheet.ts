import {
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';
import {
  applyEach,
  form,
  FormField,
  maxLength,
  minLength,
  required,
  min,
  validate,
} from '@angular/forms/signals';
import { errorMessage } from '../../../core/data/animal-store';
import { FeedingStore } from '../../../core/data/feeding-store';
import { Animal } from '../../../core/models/animal';
import { FeedingPlan, MAX_FEEDING_TIMES, NewFeedingPlan } from '../../../core/models/feeding';
import { Icon } from '../../../core/ui/icon/icon';

interface PlanModel {
  readonly food: string;
  /** Null while the field is empty. */
  readonly grams: number | null;
  /** `HH:mm` per meal; an empty string is a row not filled in yet. */
  readonly times: string[];
  readonly notes: string;
}

function emptyModel(): PlanModel {
  return { food: '', grams: null, times: [''], notes: '' };
}

/** Sheet for a vet or admin to start a feeding plan for an animal. */
@Component({
  selector: 'app-plan-sheet',
  imports: [Icon, FormField],
  templateUrl: './plan-sheet.html',
  styleUrl: './plan-sheet.scss',
})
export class PlanSheet {
  private readonly store = inject(FeedingStore);
  private readonly injector = inject(Injector);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly timeInputs = viewChildren<ElementRef<HTMLInputElement>>('timeInput');

  readonly animal = input.required<Animal>();
  readonly created = output<FeedingPlan>();

  protected readonly maxTimes = MAX_FEEDING_TIMES;
  protected readonly pending = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly model = signal<PlanModel>(emptyModel());
  protected readonly planForm = form(this.model, (path) => {
    validate(path.food, ({ value }) =>
      value().trim() === '' ? { kind: 'required', message: 'Say what the animal is fed.' } : null,
    );
    maxLength(path.food, 100, { message: 'Keep the food to 100 characters.' });

    required(path.grams, { message: 'Enter the grams per meal.' });
    min(path.grams, 1, { message: 'A meal needs at least 1 g.' });
    validate(path.grams, ({ value }) => {
      const grams = value();
      return grams === null || Number.isInteger(grams)
        ? null
        : { kind: 'integer', message: 'Use whole grams.' };
    });

    // Each row must be filled in; the list as a whole has a size and no repeats.
    applyEach(path.times, (time) => {
      required(time, { message: 'Pick a time.' });
    });
    minLength(path.times, 1, { message: 'Add at least one time.' });
    maxLength(path.times, MAX_FEEDING_TIMES, {
      message: `A plan has at most ${MAX_FEEDING_TIMES} times a day.`,
    });
    validate(path.times, ({ value }) => {
      const filled = value().filter((t) => t !== '');
      return new Set(filled).size === filled.length
        ? null
        : { kind: 'duplicate', message: 'Each time can only be used once.' };
    });

    maxLength(path.notes, 500, { message: 'Keep the note to 500 characters.' });
  });

  /** The backend allows several open plans, so this warns instead of blocking. */
  protected readonly hasOpenPlan = computed(() =>
    this.store
      .plans()
      .some(
        (p) =>
          p.animalId === this.animal().id && (p.status === 'ACTIVE' || p.status === 'SUSPENDED'),
      ),
  );
  protected readonly canAddTime = computed(() => this.model().times.length < MAX_FEEDING_TIMES);
  protected readonly canRemoveTime = computed(() => this.model().times.length > 1);

  /** What is wrong with the list of times, if anything. */
  protected readonly timesHint = computed(() => {
    const times = this.planForm.times;
    const own = times().errors()[0]?.message;
    if (own) {
      return own;
    }
    const blank = [...times].some((t) => t().invalid() && t().touched());
    return blank ? 'Pick a time for every row, or remove the empty one.' : null;
  });

  open(): void {
    this.planForm().reset(emptyModel());
    this.error.set(null);
    this.dialog().nativeElement.showModal();
  }

  close(): void {
    this.dialog().nativeElement.close();
  }

  protected removeLabel(time: string): string {
    return time ? `Remove ${time}` : 'Remove this time';
  }

  protected addTime(): void {
    if (!this.canAddTime()) {
      return;
    }
    const index = this.model().times.length;
    this.model.update((m) => ({ ...m, times: [...m.times, ''] }));
    this.focusTime(index);
  }

  protected removeTime(index: number): void {
    if (!this.canRemoveTime()) {
      return;
    }
    this.model.update((m) => ({ ...m, times: m.times.filter((_, i) => i !== index) }));
    // Keep the user in the list: the row above, or the new first row.
    this.focusTime(Math.max(0, index - 1));
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === this.dialog().nativeElement && !this.pending()) {
      this.close();
    }
  }

  protected async submit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const { food, grams, times, notes } = this.model();
    if (this.planForm().invalid() || this.pending() || grams === null) {
      return;
    }
    const input: NewFeedingPlan = {
      animalId: this.animal().id,
      food: food.trim(),
      quantityGrams: grams,
      feedingTimes: [...times],
      notes: notes.trim() || null,
    };
    this.pending.set(true);
    this.error.set(null);
    try {
      const plan = await this.store.createPlan(input);
      this.close();
      this.created.emit(plan);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.pending.set(false);
    }
  }

  private focusTime(index: number): void {
    afterNextRender(() => this.timeInputs()[index]?.nativeElement.focus(), {
      injector: this.injector,
    });
  }
}
