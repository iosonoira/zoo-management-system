import { Notification, NotificationQuery, OpenCount } from '../models/notification';
import { Page } from './page';

/** Page size for the notification lists (notifications page and the animal's Activity). */
export const NOTIFICATION_PAGE_SIZE = 20;

/** Port for the notification-service REST contract (`/notifications`). */
export abstract class NotificationApi {
  /** One page, newest first. */
  abstract list(query: NotificationQuery, page: number): Promise<Page<Notification>>;

  /** How many open notifications need attention (WARNING or CRITICAL), and how many of those are CRITICAL. */
  abstract countOpen(): Promise<OpenCount>;

  /** First acknowledgement wins: an already acknowledged notification comes back unchanged. */
  abstract acknowledge(id: string): Promise<Notification>;
}
