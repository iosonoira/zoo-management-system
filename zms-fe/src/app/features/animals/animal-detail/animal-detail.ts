import {
  ApplicationRef,
  Component,
  DOCUMENT,
  DestroyRef,
  PLATFORM_ID,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { DatePipe, isPlatformBrowser } from '@angular/common';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { AnimalStore } from '../../../core/data/animal-store';
import { Animal, canBeTransferred, tagCode } from '../../../core/models/animal';
import { HABITAT_LABELS, STATUS_LABELS } from '../../../core/models/labels';
import { Session } from '../../../core/session/session';
import { Icon } from '../../../core/ui/icon/icon';
import { EnclosureSign } from '../enclosure-sign/enclosure-sign';
import { StatusSheet } from '../status-sheet/status-sheet';
import { StatusTrack } from '../status-track/status-track';
import { TransferSheet } from '../transfer-sheet/transfer-sheet';

@Component({
  selector: 'app-animal-detail',
  imports: [RouterLink, DatePipe, Icon, EnclosureSign, StatusTrack, TransferSheet, StatusSheet],
  templateUrl: './animal-detail.html',
  styleUrl: './animal-detail.scss',
})
export class AnimalDetail {
  protected readonly store = inject(AnimalStore);
  protected readonly session = inject(Session);
  private readonly appRef = inject(ApplicationRef);
  private readonly document = inject(DOCUMENT);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly title = inject(Title);

  private readonly transferSheet = viewChild(TransferSheet);
  private readonly statusSheet = viewChild(StatusSheet);

  /** Bound from the `:id` route parameter. */
  readonly id = input.required<string>();

  protected readonly animal = computed(() => this.store.byId(this.id()));
  protected readonly code = computed(() => {
    const animal = this.animal();
    return animal ? tagCode(animal) : '';
  });
  protected readonly status = computed(() => {
    const animal = this.animal();
    return animal ? STATUS_LABELS[animal.status] : null;
  });
  protected readonly habitat = computed(() => {
    const animal = this.animal();
    return animal ? HABITAT_LABELS[animal.habitat] : null;
  });
  protected readonly enclosure = computed(() => {
    const animal = this.animal();
    return animal ? this.store.enclosureById().get(animal.enclosureId) : undefined;
  });
  protected readonly deceased = computed(() => this.animal()?.status === 'DECEASED');
  protected readonly canTransfer = computed(
    () => this.session.can('transfer') && !!this.animal() && canBeTransferred(this.animal()!),
  );
  protected readonly canUpdateStatus = computed(
    () => this.session.can('updateStatus') && !this.deceased(),
  );

  protected readonly announcement = signal('');
  private announceTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    void this.store.load();
    inject(DestroyRef).onDestroy(() => clearTimeout(this.announceTimer));
    effect(() => {
      const name = this.animal()?.name;
      if (name) {
        this.title.setTitle(`${name} · Zoo Management System`);
      }
    });
  }

  protected openTransfer(): void {
    this.transferSheet()?.open();
  }

  protected openStatus(): void {
    this.statusSheet()?.open();
  }

  /** Re-hangs the location sign: the old plate slides out, the new one slides in. */
  protected onMoved(animal: Animal): void {
    this.applyWithTransition(animal);
    this.announce(`${animal.name} moved to ${this.store.enclosureName(animal.enclosureId)}.`);
  }

  protected onStatusChanged(animal: Animal): void {
    this.applyWithTransition(animal);
    this.announce(
      animal.status === 'DECEASED'
        ? `${animal.name} is recorded as deceased.`
        : `${animal.name} is now ${STATUS_LABELS[animal.status].label.toLowerCase()}.`,
    );
  }

  private applyWithTransition(animal: Animal): void {
    const apply = () => {
      this.store.apply(animal);
      this.appRef.tick();
    };
    const doc = this.document as Document & {
      startViewTransition?: (cb: () => void) => unknown;
    };
    if (this.isBrowser && typeof doc.startViewTransition === 'function') {
      doc.startViewTransition(apply);
    } else {
      apply();
    }
  }

  private announce(message: string): void {
    this.announcement.set(message);
    clearTimeout(this.announceTimer);
    if (this.isBrowser) {
      this.announceTimer = setTimeout(() => this.announcement.set(''), 6000);
    }
  }
}
