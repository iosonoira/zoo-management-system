import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Animal, tagCode } from '../../../core/models/animal';
import { STATUS_LABELS } from '../../../core/models/labels';
import { Icon } from '../../../core/ui/icon/icon';

/** One animal as a white sign plate: status symbol, name, species, tag code. */
@Component({
  selector: 'app-animal-plate',
  imports: [RouterLink, Icon],
  templateUrl: './animal-plate.html',
  styleUrl: './animal-plate.scss',
})
export class AnimalPlate {
  readonly animal = input.required<Animal>();

  protected readonly code = computed(() => tagCode(this.animal()));
  protected readonly status = computed(() => STATUS_LABELS[this.animal().status]);
  protected readonly deceased = computed(() => this.animal().status === 'DECEASED');
}
