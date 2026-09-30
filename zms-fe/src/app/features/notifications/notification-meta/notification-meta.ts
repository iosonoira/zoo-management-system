import { Component, computed, input } from '@angular/core';
import { notificationMeta } from '../../../core/data/notification-copy';
import { SEVERITY_LABELS } from '../../../core/models/labels';
import { Notification } from '../../../core/models/notification';
import { Icon } from '../../../core/ui/icon/icon';

/**
 * The line under a notification: the severity word, the species (with the danger tab), who
 * did it and when. The word is what says the severity; the tile beside it only echoes it.
 */
@Component({
  selector: 'app-notification-meta',
  imports: [Icon],
  templateUrl: './notification-meta.html',
  styleUrl: './notification-meta.scss',
})
export class NotificationMeta {
  readonly notification = input.required<Notification>();
  /** Read against this moment, passed in so the view never reads the clock. */
  readonly now = input.required<Date>();
  /** False when the row already shows the species beside the name. */
  readonly showSpecies = input(false);

  protected readonly severity = computed(() => SEVERITY_LABELS[this.notification().severity]);
  protected readonly meta = computed(() => notificationMeta(this.notification(), this.now()));
}
