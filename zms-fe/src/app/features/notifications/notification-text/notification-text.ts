import { Component, computed, input } from '@angular/core';
import { TextPart, describeNotification } from '../../../core/data/notification-copy';
import { STATUS_LABELS } from '../../../core/models/labels';
import { Notification } from '../../../core/models/notification';
import { Icon, IconName } from '../../../core/ui/icon/icon';

interface PartView extends TextPart {
  /** Only on status parts. */
  readonly icon: IconName | null;
}

/**
 * The sentence of a notification, with the animal’s name in bold and each status drawn
 * with its icon and ink. The words come from `core/data/notification-copy`.
 */
@Component({
  selector: 'app-notification-text',
  imports: [Icon],
  templateUrl: './notification-text.html',
  styleUrl: './notification-text.scss',
})
export class NotificationText {
  readonly notification = input.required<Notification>();
  /** False when the row already leads with the name. */
  readonly withName = input(true);

  protected readonly parts = computed<readonly PartView[]>(() =>
    describeNotification(this.notification(), { withName: this.withName() }).map((part) => ({
      ...part,
      icon: part.status ? STATUS_LABELS[part.status].icon : null,
    })),
  );
}
