import { Component, ElementRef, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { AnimalStore, errorMessage } from '../../../core/data/animal-store';
import { Animal, AnimalStatus, STATUS_ORDER } from '../../../core/models/animal';
import { STATUS_LABELS } from '../../../core/models/labels';
import { Icon } from '../../../core/ui/icon/icon';

/** Sheet for a vet or admin to record a new clinical status. */
@Component({
  selector: 'app-status-sheet',
  imports: [Icon],
  templateUrl: './status-sheet.html',
  styleUrl: './status-sheet.scss',
})
export class StatusSheet {
  private readonly store = inject(AnimalStore);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly animal = input.required<Animal>();
  readonly changed = output<Animal>();

  protected readonly order = STATUS_ORDER;
  protected readonly labels = STATUS_LABELS;
  protected readonly selected = signal<AnimalStatus | null>(null);
  protected readonly pending = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly grave = computed(() => this.selected() === 'DECEASED');

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
      const updated = await this.store.updateStatus(this.animal().id, status);
      this.close();
      this.changed.emit(updated);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.pending.set(false);
    }
  }
}
