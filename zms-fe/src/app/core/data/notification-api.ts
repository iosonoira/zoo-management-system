import { Notification, NotificationQuery } from '../models/notification';
import { Page } from './page';

/** Page size for the notification lists (notifications page and the animal's Activity). */
export const NOTIFICATION_PAGE_SIZE = 20;

/** Port for the notification-service REST contract (`/notifications`). */
export abstract class NotificationApi {
  /** One page, newest first. */
  abstract list(query: NotificationQuery, page: number): Promise<Page<Notification>>;

  /** How many open notifications need attention (severity WARNING or CRITICAL). */
  abstract countOpen(): Promise<number>;

  /** First acknowledgement wins: an already acknowledged notification comes back unchanged. */
  abstract acknowledge(id: string): Promise<Notification>;
}
