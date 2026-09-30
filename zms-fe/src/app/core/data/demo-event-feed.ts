import { Service, signal } from '@angular/core';
import { Animal, AnimalStatus } from '../models/animal';
import { NotificationEventType } from '../models/notification';

export interface DemoAnimalEvent {
  readonly id: string;              // crypto.randomUUID()
  readonly eventType: NotificationEventType;
  readonly occurredAt: string;      // ISO-8601 instant
  readonly animal: Animal;          // the animal AFTER the change
  readonly performedBy: string;
  readonly previousStatus: AnimalStatus | null;   // status changes only
  readonly fromEnclosureId: string | null;        // transfers only
}

/** Demo-only stand-in for the Kafka topic: MockAnimalApi publishes, MockNotificationApi reads. */
@Service()
export class DemoEventFeed {
  private readonly log = signal<readonly DemoAnimalEvent[]>([]);
  readonly events = this.log.asReadonly();

  publish(event: Omit<DemoAnimalEvent, 'id' | 'occurredAt'>): void {
    const newEvent: DemoAnimalEvent = {
      id: crypto.randomUUID(),
      occurredAt: new Date().toISOString(),
      ...event,
    };
    this.log.update((prev) => [...prev, newEvent]);
  }
}
