import { Component, ElementRef, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { AnimalStore, errorMessage } from '../../../core/data/animal-store';
import { Animal } from '../../../core/models/animal';
import { HABITAT_LABELS } from '../../../core/models/labels';
import { Icon } from '../../../core/ui/icon/icon';

/** Sheet for moving an animal to another enclosure. */
@Component({
  selector: 'app-transfer-sheet',
  imports: [Icon],
  templateUrl: './transfer-sheet.html',
  styleUrl: './transfer-sheet.scss',
})
export class TransferSheet {
  private readonly store = inject(AnimalStore);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly animal = input.required<Animal>();
  readonly moved = output<Animal>();

  protected readonly selected = signal<string | null>(null);
  protected readonly pending = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly habitats = HABITAT_LABELS;

  protected readonly enclosures = computed(() =>
    this.store.enclosures().map((enclosure) => {
      const current = enclosure.id === this.animal().enclosureId;
      const residents = this.store
        .animals()
        .filter((a) => a.enclosureId === enclosure.id && a.status !== 'DECEASED').length;
      const parts = [
        HABITAT_LABELS[enclosure.habitat].label,
        residents === 0 ? 'Empty' : `${residents} ${residents === 1 ? 'animal' : 'animals'}`,
      ];
      if (enclosure.habitat !== this.animal().habitat) {
        parts.push('Different habitat');
      }
      return { enclosure, current, detail: current ? 'Current enclosure' : parts.join(' · ') };
    }),
  );

  protected readonly currentName = computed(() => this.store.enclosureName(this.animal().enclosureId));

  protected readonly target = computed(() => {
    const id = this.selected();
    return id ? this.store.enclosureName(id) : null;
  });

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
    const target = this.selected();
    if (!target || this.pending()) {
      return;
    }
    this.pending.set(true);
    this.error.set(null);
    try {
      const updated = await this.store.transfer(this.animal().id, target);
      this.close();
      this.moved.emit(updated);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.pending.set(false);
    }
  }
}
