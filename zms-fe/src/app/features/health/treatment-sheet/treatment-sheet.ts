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
import { FormField, form, maxLength, validate } from '@angular/forms/signals';
import { errorMessage } from '../../../core/data/animal-store';
import { HealthStore } from '../../../core/data/health-store';
import { Animal } from '../../../core/models/animal';
import { MedicalRecord, Treatment } from '../../../core/models/health';
import { Icon } from '../../../core/ui/icon/icon';

interface TreatmentModel {
  readonly description: string;
}

/** Sheet for a vet or admin to prescribe a treatment under a medical record. */
@Component({
  selector: 'app-treatment-sheet',
  imports: [Icon, FormField],
  templateUrl: './treatment-sheet.html',
  styleUrl: './treatment-sheet.scss',
})
export class TreatmentSheet {
  private readonly store = inject(HealthStore);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly animal = input.required<Animal>();
  readonly record = input.required<MedicalRecord>();
  readonly prescribed = output<Treatment>();

  protected readonly pending = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly titleId = computed(() => `treatment-title-${this.record().id}`);
  protected readonly hintId = computed(() => `treatment-hint-${this.record().id}`);

  protected readonly model = signal<TreatmentModel>({ description: '' });
  protected readonly treatmentForm = form(this.model, (path) => {
    validate(path.description, ({ value }) =>
      value().trim() === ''
        ? { kind: 'required', message: 'Say what to give, how often and for how long.' }
        : null,
    );
    maxLength(path.description, 500, { message: 'Keep the description to 500 characters.' });
  });

  open(): void {
    this.treatmentForm().reset({ description: '' });
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
    if (this.treatmentForm().invalid() || this.pending()) {
      return;
    }
    this.pending.set(true);
    this.error.set(null);
    try {
      const treatment = await this.store.prescribe(
        this.record().id,
        this.model().description.trim(),
      );
      this.close();
      this.prescribed.emit(treatment);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.pending.set(false);
    }
  }
}
