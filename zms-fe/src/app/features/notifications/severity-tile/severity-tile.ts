import { Component, computed, input } from '@angular/core';
import { SEVERITY_LABELS } from '../../../core/models/labels';
import { Severity } from '../../../core/models/notification';
import { Icon, IconName } from '../../../core/ui/icon/icon';

/**
 * A notification’s severity as a tile: critical is a solid ink tile, warning a two-pixel
 * ring, info a hairline ring. No hue, and never alone: the tile is decorative and the
 * severity word sits beside it (`app-notification-meta`).
 */
@Component({
  selector: 'app-severity-tile',
  imports: [Icon],
  templateUrl: './severity-tile.html',
  styleUrl: './severity-tile.scss',
  host: {
    'aria-hidden': 'true',
    '[attr.data-severity]': 'severity()',
    '[attr.data-size]': 'size()',
  },
})
export class SeverityTile {
  readonly severity = input.required<Severity>();
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  /** Replaces the severity’s own pictogram, for an empty state. */
  readonly icon = input<IconName | null>(null);

  protected readonly name = computed(() => this.icon() ?? SEVERITY_LABELS[this.severity()].icon);
}
