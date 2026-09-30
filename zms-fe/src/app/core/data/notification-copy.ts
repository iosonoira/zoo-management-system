import { AnimalStatus } from '../models/animal';
import { STATUS_LABELS } from '../models/labels';
import { Notification } from '../models/notification';
import { ENCLOSURES } from './enclosure-directory';

/**
 * Display text for notifications, kept pure so each sentence can be tested and the
 * same words serve the notifications page and an animal's Activity section.
 * Nothing here reads the clock: callers pass `now`.
 */

/** One run of a sentence. Status parts are drawn with their icon and ink; the name in bold. */
export interface TextPart {
  readonly kind: 'text' | 'name' | 'status';
  readonly text: string;
  /** Only on `status` parts. */
  readonly status?: AnimalStatus;
}

const UNKNOWN_ENCLOSURE = 'an unknown enclosure';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** The enclosure’s name, or a plain stand-in when the id is not in the directory. */
export function enclosureName(id: string): string {
  return ENCLOSURES.find((e) => e.id === id)?.name ?? UNKNOWN_ENCLOSURE;
}

/**
 * The sentence for a notification, as parts. With `withName: false` the animal is left
 * out, for rows that already lead with its name ("Transferred from A to B.").
 *
 * Rows stored before the structured fields existed have nulls where the sentence needs
 * them; those keep the message the service wrote, unchanged.
 */
export function describeNotification(
  n: Notification,
  options: { readonly withName?: boolean } = {},
): readonly TextPart[] {
  const withName = options.withName ?? true;
  // Without the name there is nothing to lead with, so a name that is missing only matters with it.
  if (withName && n.name === null) {
    return fallback(n);
  }
  const subject = (rest: string, bare: string): TextPart[] =>
    withName ? [{ kind: 'name', text: n.name ?? '' }, text(rest)] : [text(bare)];

  switch (n.eventType) {
    case 'ANIMAL_REGISTERED': {
      if (n.toEnclosureId === null) {
        return fallback(n);
      }
      const to = enclosureName(n.toEnclosureId);
      return subject(` was registered into ${to}.`, `Registered into ${to}.`);
    }

    case 'ANIMAL_TRANSFERRED': {
      if (n.fromEnclosureId === null || n.toEnclosureId === null) {
        return fallback(n);
      }
      const from = enclosureName(n.fromEnclosureId);
      const to = enclosureName(n.toEnclosureId);
      return subject(
        ` was transferred from ${from} to ${to}.`,
        `Transferred from ${from} to ${to}.`,
      );
    }

    case 'ANIMAL_STATUS_CHANGED': {
      if (n.newStatus === null) {
        return fallback(n);
      }
      if (n.newStatus === 'DECEASED') {
        // Plain and unhurried: the word itself, with its own pictogram, and no euphemism.
        return [
          ...subject(' was marked ', 'Marked '),
          status('DECEASED', STATUS_LABELS.DECEASED.label.toLowerCase()),
          text('.'),
        ];
      }
      if (n.previousStatus === null) {
        return fallback(n);
      }
      return [
        ...subject('’s status changed from ', 'Status changed from '),
        status(n.previousStatus),
        text(' to '),
        status(n.newStatus),
        text('.'),
      ];
    }

    default:
      return fallback(n);
  }
}

/** The same sentence as plain text, for tests, titles and screen-reader labels. */
export function notificationSentence(
  n: Notification,
  options: { readonly withName?: boolean } = {},
): string {
  return describeNotification(n, options)
    .map((part) => part.text)
    .join('');
}

/**
 * When something happened, relative to `now` in the reader’s local time: "Today, 10:41",
 * "Yesterday, 16:20", "28 Sep, 11:05", and with the year once it is not this one.
 */
export function formatWhen(iso: string, now: Date): string {
  const day = formatDay(iso, now);
  if (!day) {
    return '';
  }
  const at = new Date(iso);
  return `${day}, ${pad(at.getHours())}:${pad(at.getMinutes())}`;
}

/** The day part alone: "Today", "Yesterday", "28 Sep", or "31 Dec 2025". Empty for a value that is not a date. */
export function formatDay(iso: string, now: Date): string {
  const at = new Date(iso);
  if (Number.isNaN(at.getTime())) {
    return '';
  }
  const daysAgo = calendarDays(now) - calendarDays(at);

  // A clock a little ahead of the server still reads as today.
  if (daysAgo <= 0) {
    return 'Today';
  }
  if (daysAgo === 1) {
    return 'Yesterday';
  }
  const date = `${at.getDate()} ${MONTHS[at.getMonth()]}`;
  return at.getFullYear() === now.getFullYear() ? date : `${date} ${at.getFullYear()}`;
}

/** Who did it and when: "keeper.conti · Today, 10:41". A missing author is left out. */
export function notificationMeta(n: Notification, now: Date): string {
  return [n.performedBy, formatWhen(n.occurredAt, now)].filter(Boolean).join(' · ');
}

/** "Acknowledged by vet.bianchi · Yesterday, 12:40", or null while the notification is open. */
export function acknowledgedText(n: Notification, now: Date): string | null {
  if (n.acknowledgedBy === null) {
    return null;
  }
  const when = n.acknowledgedAt === null ? '' : formatWhen(n.acknowledgedAt, now);
  return when ? `Acknowledged by ${n.acknowledgedBy} · ${when}` : `Acknowledged by ${n.acknowledgedBy}`;
}

/** Whether the notification still waits for someone. */
export function isOpen(n: Notification): boolean {
  return n.acknowledgedBy === null;
}

function text(value: string): TextPart {
  return { kind: 'text', text: value };
}

function status(code: AnimalStatus, label = STATUS_LABELS[code].label): TextPart {
  return { kind: 'status', text: label, status: code };
}

function fallback(n: Notification): readonly TextPart[] {
  return [text(n.message)];
}

function pad(value: number): string {
  return value.toString().padStart(2, '0');
}

/** Whole local calendar days since an arbitrary origin, so DST does not skew the difference. */
function calendarDays(date: Date): number {
  return Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86_400_000);
}
