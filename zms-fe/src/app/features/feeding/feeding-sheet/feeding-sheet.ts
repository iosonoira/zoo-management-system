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
import { form, FormField, maxLength, min, required, validate } from '@angular/forms/signals';
import { errorMessage } from '../../../core/data/animal-store';
import { FeedingStore } from '../../../core/data/feeding-store';
import { Animal } from '../../../core/models/animal';
import { Feeding, FeedingPlan, NewFeeding } from '../../../core/models/feeding';
import { Icon } from '../../../core/ui/icon/icon';

interface FeedingModel {
  readonly when: 'now' | 'earlier';
  /** `HH:mm` today, local. Used only when `when` is `earlier`. */
  readonly time: string;
  /** Null while the field is empty. */
  readonly grams: number | null;
  readonly notes: string;
}

/** The stepper moves the amount by this much. */
const STEP_GRAMS = 10;
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

function timeOf(date: Date): string {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Today's local date at `HH:mm`. */
function todayAt(time: string): Date {
  const [hours, minutes] = time.split(':').map(Number);
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate(), hours, minutes);
}

/** Sheet for a keeper or admin to record a meal against an active plan. */
@Component({
  selector: 'app-feeding-sheet',
  imports: [Icon, FormField],
  templateUrl: './feeding-sheet.html',
  styleUrl: './feeding-sheet.scss',
})
export class FeedingSheet {
  private readonly store = inject(FeedingStore);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly animal = input.required<Animal>();
  readonly plan = input.required<FeedingPlan>();
  readonly recorded = output<Feeding>();

  protected readonly pending = signal(false);
  protected readonly error = signal<string | null>(null);
  /** The time shown on the "Now" option, taken when the sheet opens. */
  protected readonly nowLabel = signal(timeOf(new Date()));

  protected readonly model = signal<FeedingModel>({
    when: 'now',
    time: this.nowLabel(),
    grams: null,
    notes: '',
  });
  protected readonly feedForm = form(this.model, (path) => {
    required(path.grams, { message: 'Enter the amount in grams.' });
    min(path.grams, 0, { message: 'The amount can’t be below 0 g.' });
    validate(path.grams, ({ value }) => {
      const grams = value();
      return grams === null || Number.isInteger(grams)
        ? null
        : { kind: 'integer', message: 'Use whole grams.' };
    });
    maxLength(path.notes, 500, { message: 'Keep the note to 500 characters.' });
    validate(path.time, ({ value, valueOf }) => {
      if (valueOf(path.when) !== 'earlier') {
        return null;
      }
      if (!TIME_PATTERN.test(value())) {
        return { kind: 'required', message: 'Pick the time it was fed.' };
      }
      return todayAt(value()).getTime() > Date.now()
        ? { kind: 'future', message: 'That time hasn’t happened yet.' }
        : null;
    });
  });

  protected readonly gramsId = computed(() => `feeding-grams-${this.plan().id}`);
  protected readonly titleId = computed(() => `feeding-title-${this.plan().id}`);
  protected readonly step = STEP_GRAMS;
  protected readonly timeLabel = computed(() => {
    const { when, time } = this.model();
    return when === 'earlier' ? time : this.nowLabel();
  });

  open(): void {
    const now = new Date();
    this.nowLabel.set(timeOf(now));
    this.feedForm().reset({
      when: 'now',
      time: timeOf(now),
      grams: this.plan().quantityGrams,
      notes: '',
    });
    this.error.set(null);
    this.dialog().nativeElement.showModal();
  }

  close(): void {
    this.dialog().nativeElement.close();
  }

  protected nudge(direction: 1 | -1): void {
    this.model.update((m) => ({
      ...m,
      grams: Math.max(0, (m.grams ?? 0) + direction * STEP_GRAMS),
    }));
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === this.dialog().nativeElement && !this.pending()) {
      this.close();
    }
  }

  protected async submit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const { when, time, grams, notes } = this.model();
    if (this.feedForm().invalid() || this.pending() || grams === null) {
      return;
    }
    const input: NewFeeding = {
      fedAt: when === 'now' ? null : todayAt(time).toISOString(),
      quantityGrams: grams,
      notes: notes.trim() || null,
    };
    this.pending.set(true);
    this.error.set(null);
    try {
      const feeding = await this.store.recordFeeding(this.plan().id, input);
      this.close();
      this.recorded.emit(feeding);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.pending.set(false);
    }
  }
}
