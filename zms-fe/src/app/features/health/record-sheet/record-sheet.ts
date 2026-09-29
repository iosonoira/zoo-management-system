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
import { FormField, SchemaPath, form, maxLength, required, validate } from '@angular/forms/signals';
import { errorMessage } from '../../../core/data/animal-store';
import { HealthStore } from '../../../core/data/health-store';
import { Animal } from '../../../core/models/animal';
import { MedicalRecord } from '../../../core/models/health';
import { Session } from '../../../core/session/session';
import { Icon } from '../../../core/ui/icon/icon';

interface RecordModel {
  readonly examinedOn: string;
  readonly reason: string;
  readonly diagnosis: string;
  readonly veterinarian: string;
}

/** Today as a local `YYYY-MM-DD` string. Computed here, never in the template. */
function todayLocal(): string {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Text that is only spaces counts as empty. */
function notBlank(path: SchemaPath<string>, message: string): void {
  validate(path, ({ value }) => (value().trim() === '' ? { kind: 'required', message } : null));
}

/** Sheet for a vet or admin to add a medical record for an animal. */
@Component({
  selector: 'app-record-sheet',
  imports: [Icon, FormField],
  templateUrl: './record-sheet.html',
  styleUrl: './record-sheet.scss',
})
export class RecordSheet {
  private readonly store = inject(HealthStore);
  private readonly session = inject(Session);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly animal = input.required<Animal>();
  readonly created = output<MedicalRecord>();

  protected readonly today = signal(todayLocal());
  protected readonly pending = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly deceased = computed(() => this.animal().status === 'DECEASED');

  protected readonly model = signal<RecordModel>(this.emptyModel());
  protected readonly recordForm = form(this.model, (path) => {
    required(path.examinedOn, { message: 'Enter the date of the examination.' });
    // The backend rejects an examination in the future.
    validate(path.examinedOn, ({ value }) =>
      value() > this.today()
        ? { kind: 'future', message: 'The examination can’t be in the future.' }
        : null,
    );

    notBlank(path.reason, 'Say why the animal was examined.');
    maxLength(path.reason, 200, { message: 'Keep the reason to 200 characters.' });

    notBlank(path.diagnosis, 'Write the diagnosis.');
    maxLength(path.diagnosis, 1000, { message: 'Keep the diagnosis to 1000 characters.' });

    notBlank(path.veterinarian, 'Enter the veterinarian’s name.');
    maxLength(path.veterinarian, 100, { message: 'Keep the name to 100 characters.' });
  });

  open(): void {
    this.today.set(todayLocal());
    this.recordForm().reset(this.emptyModel());
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
    if (this.recordForm().invalid() || this.pending()) {
      return;
    }
    const m = this.model();
    this.pending.set(true);
    this.error.set(null);
    try {
      const record = await this.store.createRecord({
        animalId: this.animal().id,
        examinedOn: m.examinedOn,
        reason: m.reason.trim(),
        diagnosis: m.diagnosis.trim(),
        veterinarian: m.veterinarian.trim(),
      });
      this.close();
      this.created.emit(record);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.pending.set(false);
    }
  }

  /** Today, and a vet’s own username as the examiner: they are usually the one writing it up. */
  private emptyModel(): RecordModel {
    return {
      examinedOn: todayLocal(),
      reason: '',
      diagnosis: '',
      veterinarian: this.session.role() === 'zoo-vet' ? this.session.username() : '',
    };
  }
}
