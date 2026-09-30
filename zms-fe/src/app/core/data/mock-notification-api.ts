import { inject } from '@angular/core';
import {
  Notification,
  NotificationQuery,
  OpenCount,
  Severity,
  ATTENTION_SEVERITIES,
} from '../models/notification';
import { can } from '../models/permissions';
import { Session } from '../session/session';
import { NotificationApi, NOTIFICATION_PAGE_SIZE } from './notification-api';
import { Page } from './page';
import { forbidden, notificationNotFound } from './api-errors';
import { DemoEventFeed, DemoAnimalEvent } from './demo-event-feed';
import { DEMO_NOTIFICATIONS } from './demo-notifications';

const LATENCY_MS = 380;

/**
 * In-memory adapter for the notification-service. Notifications come from the seed
 * plus events published by MockAnimalApi. Acknowledges are kept in memory and merged
 * on read.
 */
export class MockNotificationApi extends NotificationApi {
  private readonly session = inject(Session);
  private readonly feed = inject(DemoEventFeed);
  private notifications = new Map<string, Notification>(
    DEMO_NOTIFICATIONS.map((n) => [n.id, n]),
  );

  async list(query: NotificationQuery, page: number): Promise<Page<Notification>> {
    await delay();
    this.syncEventsToNotifications();

    let items = [...this.notifications.values()];

    if (query.animalId) {
      items = items.filter((n) => n.animalId === query.animalId);
    }

    if (query.severities && query.severities.length > 0) {
      items = items.filter((n) => query.severities!.includes(n.severity));
    }

    if (query.openOnly) {
      items = items.filter((n) => !n.acknowledgedBy);
    }

    items.sort((a, b) => {
      if (a.occurredAt !== b.occurredAt) {
        return b.occurredAt.localeCompare(a.occurredAt);
      }
      return a.id.localeCompare(b.id);
    });

    const total = items.length;
    const start = page * NOTIFICATION_PAGE_SIZE;
    const pageItems = items.slice(start, start + NOTIFICATION_PAGE_SIZE);

    return {
      items: pageItems,
      page,
      size: NOTIFICATION_PAGE_SIZE,
      total,
    };
  }

  async countOpen(): Promise<OpenCount> {
    await delay();
    this.syncEventsToNotifications();

    const open = [...this.notifications.values()].filter(
      (n) => !n.acknowledgedBy && ATTENTION_SEVERITIES.includes(n.severity),
    );
    return {
      attention: open.length,
      critical: open.filter((n) => n.severity === 'CRITICAL').length,
    };
  }

  async acknowledge(id: string): Promise<Notification> {
    await delay();
    if (!can(this.session.role(), 'acknowledgeNotification')) {
      throw forbidden('acknowledgeNotification');
    }

    this.syncEventsToNotifications();
    const notification = this.notifications.get(id);
    if (!notification) {
      throw notificationNotFound();
    }

    if (notification.acknowledgedBy) {
      return notification;
    }

    const acknowledged: Notification = {
      ...notification,
      acknowledgedBy: this.session.username(),
      acknowledgedAt: new Date().toISOString(),
    };
    this.notifications.set(id, acknowledged);
    return acknowledged;
  }

  /** Adds a notification for every feed event not seen yet, as the Kafka consumer would. */
  private syncEventsToNotifications(): void {
    for (const event of this.feed.events()) {
      if (!this.notifications.has(event.id)) {
        this.notifications.set(event.id, toNotification(event));
      }
    }
  }
}

function severityOf(
  eventType: Notification['eventType'],
  dangerous: boolean | null,
  newStatus: Notification['newStatus'],
): Severity {
  if (eventType === 'ANIMAL_REGISTERED') {
    return 'INFO';
  }
  if (eventType === 'ANIMAL_STATUS_CHANGED') {
    if (newStatus === 'DECEASED') {
      return 'CRITICAL';
    }
    if (newStatus === 'UNDER_OBSERVATION' || newStatus === 'IN_TREATMENT') {
      return 'WARNING';
    }
    return 'INFO';
  }
  if (eventType === 'ANIMAL_TRANSFERRED') {
    return dangerous ? 'WARNING' : 'INFO';
  }
  return 'INFO';
}

function toNotification(event: DemoAnimalEvent): Notification {
  const { animal } = event;
  const severity = severityOf(
    event.eventType,
    animal.dangerous,
    event.eventType === 'ANIMAL_STATUS_CHANGED' ? animal.status : null,
  );

  let message: string;
  let toEnclosureId: string | null = null;

  if (event.eventType === 'ANIMAL_REGISTERED') {
    message = `${animal.name} (${animal.species}) was registered`;
    toEnclosureId = animal.enclosureId;
  } else if (event.eventType === 'ANIMAL_STATUS_CHANGED') {
    message = `${animal.name} (${animal.species}) status changed from ${event.previousStatus} to ${animal.status}`;
  } else if (event.eventType === 'ANIMAL_TRANSFERRED') {
    message = `${animal.name} (${animal.species}) was transferred from enclosure ${event.fromEnclosureId} to enclosure ${animal.enclosureId}`;
    toEnclosureId = animal.enclosureId;
  } else {
    message = 'Unknown event';
  }

  return {
    id: event.id,
    animalId: animal.id,
    eventType: event.eventType,
    severity,
    message,
    occurredAt: event.occurredAt,
    performedBy: event.performedBy,
    name: animal.name,
    species: animal.species,
    dangerous: animal.dangerous,
    previousStatus: event.eventType === 'ANIMAL_STATUS_CHANGED' ? event.previousStatus : null,
    newStatus: event.eventType === 'ANIMAL_STATUS_CHANGED' ? animal.status : null,
    fromEnclosureId: event.eventType === 'ANIMAL_TRANSFERRED' ? event.fromEnclosureId : null,
    toEnclosureId,
    acknowledgedBy: null,
    acknowledgedAt: null,
  };
}

function delay(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, LATENCY_MS));
}
