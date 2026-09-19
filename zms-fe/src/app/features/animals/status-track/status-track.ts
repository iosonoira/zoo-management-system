import { Component, input } from '@angular/core';
import { AnimalStatus, STATUS_ORDER } from '../../../core/models/animal';
import { STATUS_LABELS } from '../../../core/models/labels';
import { Icon } from '../../../core/ui/icon/icon';

/**
 * All four statuses shown at once: the current one lit in its safety colour,
 * the others drawn as unlit cells so position in the sequence reads at a glance.
 */
@Component({
  selector: 'app-status-track',
  imports: [Icon],
  templateUrl: './status-track.html',
  styleUrl: './status-track.scss',
})
export class StatusTrack {
  readonly status = input.required<AnimalStatus>();

  protected readonly order = STATUS_ORDER;
  protected readonly labels = STATUS_LABELS;
}
