import { Component, computed, input } from '@angular/core';
import { acknowledgedText } from '../../../core/data/notification-copy';
import { Notification } from '../../../core/models/notification';
import { Icon } from '../../../core/ui/icon/icon';

/** “Acknowledged by vet.bianchi · Yesterday, 12:40”, where an acknowledged notification has no button. */
@Component({
  selector: 'app-acknowledged-note',
  imports: [Icon],
  templateUrl: './acknowledged-note.html',
  styleUrl: './acknowledged-note.scss',
})
export class AcknowledgedNote {
  readonly notification = input.required<Notification>();
  /** Read against this moment, passed in so the view never reads the clock. */
  readonly now = input.required<Date>();

  protected readonly text = computed(() => acknowledgedText(this.notification(), this.now()));
}
