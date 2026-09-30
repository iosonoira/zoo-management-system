import { AnimalStatus } from './animal';

/** Mirrors `it.zoo.notification.domain.enums.Severity`. */
export type Severity = 'INFO' | 'WARNING' | 'CRITICAL';

/** Mirrors `it.zoo.notification.domain.enums.AnimalEventType`. */
export type NotificationEventType = 'ANIMAL_REGISTERED' | 'ANIMAL_STATUS_CHANGED' | 'ANIMAL_TRANSFERRED';

/** Mirrors `NotificationResponse` from notification-service. Structured fields are null on rows stored before they existed; `message` is always set. */
export interface Notification {
  readonly id: string;
  readonly animalId: string;
  readonly eventType: NotificationEventType;
  readonly severity: Severity;
  readonly message: string;
  readonly occurredAt: string; // ISO-8601 instant
  readonly performedBy: string | null;
  readonly name: string | null;
  readonly species: string | null;
  readonly dangerous: boolean | null;
  readonly previousStatus: AnimalStatus | null;
  readonly newStatus: AnimalStatus | null;
  readonly fromEnclosureId: string | null;
  readonly toEnclosureId: string | null;
  readonly acknowledgedBy: string | null;
  readonly acknowledgedAt: string | null;
}

export interface NotificationQuery {
  readonly animalId?: string;
  readonly severities?: readonly Severity[];
  readonly openOnly?: boolean;
}

/** Severities that need someone to act: they light the open count. */
export const ATTENTION_SEVERITIES: readonly Severity[] = ['WARNING', 'CRITICAL'];
