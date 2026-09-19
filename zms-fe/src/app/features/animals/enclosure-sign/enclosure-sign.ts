import { Component, computed, input } from '@angular/core';
import { Habitat } from '../../../core/models/animal';
import { HABITAT_LABELS } from '../../../core/models/labels';
import { Icon } from '../../../core/ui/icon/icon';

/** Bed marker naming an enclosure: habitat stake, name, count, arrow. */
@Component({
  selector: 'app-enclosure-sign',
  imports: [Icon],
  templateUrl: './enclosure-sign.html',
  styleUrl: './enclosure-sign.scss',
  host: { '[attr.data-size]': 'size()', '[attr.data-habitat]': 'habitat()' },
})
export class EnclosureSign {
  readonly name = input.required<string>();
  readonly habitat = input.required<Habitat>();
  readonly count = input<number>();
  readonly size = input<'md' | 'lg'>('md');
  /** Wayfinding arrow drawn on the plate, as on a real direction sign. */
  readonly arrow = input(false);
  /** Semantic level for the name, when the sign heads a section. */
  readonly headingLevel = input<2 | 3 | null>(null);

  protected readonly habitatLabel = computed(() => HABITAT_LABELS[this.habitat()]);
}
