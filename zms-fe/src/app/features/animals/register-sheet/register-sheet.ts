import { Component, ElementRef, computed, inject, output, signal, viewChild } from '@angular/core';
import { form, FormField, maxLength, required } from '@angular/forms/signals';
import { AnimalStore, errorMessage } from '../../../core/data/animal-store';
import { Animal } from '../../../core/models/animal';
import { HABITAT_LABELS } from '../../../core/models/labels';
import { Icon } from '../../../core/ui/icon/icon';

interface RegisterModel {
  readonly name: string;
  readonly species: string;
  readonly dangerous: boolean;
  readonly enclosureId: string;
  readonly arrivalDate: string;
}

/** Today as a local `YYYY-MM-DD` string. Computed here, never in the template. */
function todayLocal(): string {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function emptyModel(arrivalDate: string): RegisterModel {
  return { name: '', species: '', dangerous: false, enclosureId: '', arrivalDate };
}

/** Sheet for an admin to add a new animal to the roster. */
@Component({
  selector: 'app-register-sheet',
  imports: [Icon, FormField],
  templateUrl: './register-sheet.html',
  styleUrl: './register-sheet.scss',
})
export class RegisterSheet {
  private readonly store = inject(AnimalStore);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly registered = output<Animal>();

  protected readonly habitats = HABITAT_LABELS;
  protected readonly pending = signal(false);
  protected readonly error = signal<string | null>(null);
  protected today = todayLocal();

  protected readonly model = signal<RegisterModel>(emptyModel(this.today));
  protected readonly registerForm = form(this.model, (schemaPath) => {
    required(schemaPath.name, { message: 'Name is required.' });
    maxLength(schemaPath.name, 100);
    required(schemaPath.species, { message: 'Species is required.' });
    maxLength(schemaPath.species, 100);
    required(schemaPath.enclosureId, { message: 'Choose an enclosure.' });
    required(schemaPath.arrivalDate, { message: 'Arrival date is required.' });
  });

  protected readonly enclosures = computed(() =>
    this.store.enclosures().map((enclosure) => {
      const residents = this.store
        .animals()
        .filter((a) => a.enclosureId === enclosure.id && a.status !== 'DECEASED').length;
      const detail = [
        HABITAT_LABELS[enclosure.habitat].label,
        residents === 0 ? 'Empty' : `${residents} ${residents === 1 ? 'animal' : 'animals'}`,
      ].join(' · ');
      return { enclosure, detail };
    }),
  );

  protected readonly selectedEnclosure = computed(() =>
    this.store.enclosureById().get(this.model().enclosureId),
  );

  open(): void {
    this.today = todayLocal();
    this.model.set(emptyModel(this.today));
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
    if (this.registerForm().invalid() || this.pending()) {
      return;
    }
    const enclosure = this.selectedEnclosure();
    if (!enclosure) {
      return;
    }
    this.pending.set(true);
    this.error.set(null);
    try {
      const m = this.model();
      const animal = await this.store.register({
        name: m.name.trim(),
        species: m.species.trim(),
        dangerous: m.dangerous,
        habitat: enclosure.habitat,
        enclosureId: m.enclosureId,
        arrivalDate: m.arrivalDate,
      });
      this.close();
      this.registered.emit(animal);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.pending.set(false);
    }
  }
}
