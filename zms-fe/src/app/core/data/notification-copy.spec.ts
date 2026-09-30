import { Notification } from '../models/notification';
import { ENCLOSURES } from './enclosure-directory';
import {
  acknowledgedText,
  describeNotification,
  enclosureName,
  formatWhen,
  isOpen,
  notificationMeta,
  notificationSentence,
} from './notification-copy';

const SAVANNA = ENCLOSURES[0];
const BIG_CATS = ENCLOSURES[1];

/** Local time on purpose: the copy reads the reader’s clock, not UTC. */
function at(month: number, day: number, hour: number, minute: number, year = 2026): string {
  return new Date(year, month - 1, day, hour, minute).toISOString();
}

const NOW = new Date(2026, 8, 30, 11, 24);

function notification(overrides: Partial<Notification> = {}): Notification {
  return {
    id: 'n1',
    animalId: 'a1',
    eventType: 'ANIMAL_REGISTERED',
    severity: 'INFO',
    message: 'Lulu (Chimpanzee) was registered',
    occurredAt: at(9, 30, 9, 12),
    performedBy: 'admin.rossi',
    name: 'Lulu',
    species: 'Chimpanzee',
    dangerous: false,
    previousStatus: null,
    newStatus: null,
    fromEnclosureId: null,
    toEnclosureId: SAVANNA.id,
    acknowledgedBy: null,
    acknowledgedAt: null,
    ...overrides,
  };
}

const transfer = (overrides: Partial<Notification> = {}) =>
  notification({
    eventType: 'ANIMAL_TRANSFERRED',
    severity: 'WARNING',
    name: 'Zuri',
    fromEnclosureId: SAVANNA.id,
    toEnclosureId: BIG_CATS.id,
    ...overrides,
  });

const statusChange = (overrides: Partial<Notification> = {}) =>
  notification({
    eventType: 'ANIMAL_STATUS_CHANGED',
    severity: 'WARNING',
    name: 'Pepe',
    previousStatus: 'HEALTHY',
    newStatus: 'IN_TREATMENT',
    toEnclosureId: null,
    ...overrides,
  });

describe('enclosureName', () => {
  it('resolves an id from the enclosure directory', () => {
    expect(enclosureName(SAVANNA.id)).toBe(SAVANNA.name);
    expect(enclosureName(BIG_CATS.id)).toBe(BIG_CATS.name);
  });

  it('falls back to a plain stand-in for an id it does not know', () => {
    expect(enclosureName('not-in-the-directory')).toBe('an unknown enclosure');
  });
});

describe('notificationSentence', () => {
  it('says where a new animal was registered', () => {
    expect(notificationSentence(notification())).toBe(`Lulu was registered into ${SAVANNA.name}.`);
    expect(notificationSentence(notification(), { withName: false })).toBe(
      `Registered into ${SAVANNA.name}.`,
    );
  });

  it('says where an animal was transferred from and to', () => {
    expect(notificationSentence(transfer())).toBe(
      `Zuri was transferred from ${SAVANNA.name} to ${BIG_CATS.name}.`,
    );
    expect(notificationSentence(transfer(), { withName: false })).toBe(
      `Transferred from ${SAVANNA.name} to ${BIG_CATS.name}.`,
    );
  });

  it('names the enclosure it cannot resolve as an unknown one', () => {
    expect(notificationSentence(transfer({ toEnclosureId: 'gone' }))).toBe(
      `Zuri was transferred from ${SAVANNA.name} to an unknown enclosure.`,
    );
    expect(notificationSentence(notification({ toEnclosureId: 'gone' }))).toBe(
      'Lulu was registered into an unknown enclosure.',
    );
  });

  it('shows the status labels the app already uses', () => {
    expect(notificationSentence(statusChange())).toBe('Pepe’s status changed from Healthy to In treatment.');
    expect(
      notificationSentence(
        statusChange({ previousStatus: 'IN_TREATMENT', newStatus: 'UNDER_OBSERVATION' }),
        { withName: false },
      ),
    ).toBe('Status changed from In treatment to Under observation.');
  });

  it('states a death plainly, with no euphemism', () => {
    const deceased = statusChange({
      name: 'Bruno',
      severity: 'CRITICAL',
      previousStatus: 'IN_TREATMENT',
      newStatus: 'DECEASED',
    });
    expect(notificationSentence(deceased)).toBe('Bruno was marked deceased.');
    expect(notificationSentence(deceased, { withName: false })).toBe('Marked deceased.');
  });

  it('does not need the previous status to say an animal is deceased', () => {
    expect(
      notificationSentence(statusChange({ previousStatus: null, newStatus: 'DECEASED' })),
    ).toBe('Pepe was marked deceased.');
  });

  it('keeps the message unchanged when the fields a sentence needs are null', () => {
    const legacy = { message: 'Zuri (African lion) was transferred from enclosure a to enclosure b' };
    expect(notificationSentence(transfer({ ...legacy, fromEnclosureId: null }))).toBe(legacy.message);
    expect(notificationSentence(transfer({ ...legacy, toEnclosureId: null }))).toBe(legacy.message);
    expect(notificationSentence(notification({ message: 'Lulu was registered', toEnclosureId: null }))).toBe(
      'Lulu was registered',
    );
    expect(
      notificationSentence(statusChange({ message: 'Pepe changed', previousStatus: null })),
    ).toBe('Pepe changed');
    expect(notificationSentence(statusChange({ message: 'Pepe changed', newStatus: null }))).toBe(
      'Pepe changed',
    );
    // The name is only needed when the sentence leads with it.
    expect(notificationSentence(notification({ message: 'A row without a name', name: null }))).toBe(
      'A row without a name',
    );
    expect(
      notificationSentence(notification({ message: 'A row without a name', name: null }), {
        withName: false,
      }),
    ).toBe(`Registered into ${SAVANNA.name}.`);
  });

  it('marks the name and the statuses so the view can draw them', () => {
    expect(describeNotification(statusChange())).toEqual([
      { kind: 'name', text: 'Pepe' },
      { kind: 'text', text: '’s status changed from ' },
      { kind: 'status', text: 'Healthy', status: 'HEALTHY' },
      { kind: 'text', text: ' to ' },
      { kind: 'status', text: 'In treatment', status: 'IN_TREATMENT' },
      { kind: 'text', text: '.' },
    ]);
  });
});

describe('formatWhen', () => {
  it('reads today and yesterday as words, with the local time', () => {
    expect(formatWhen(at(9, 30, 10, 41), NOW)).toBe('Today, 10:41');
    expect(formatWhen(at(9, 29, 16, 20), NOW)).toBe('Yesterday, 16:20');
  });

  it('pads the time to two digits', () => {
    expect(formatWhen(at(9, 30, 8, 5), NOW)).toBe('Today, 08:05');
  });

  it('counts calendar days, not 24-hour spans', () => {
    // 23:50 last night is yesterday even though only 35 minutes passed.
    expect(formatWhen(new Date(2026, 8, 29, 23, 50).toISOString(), new Date(2026, 8, 30, 0, 25))).toBe(
      'Yesterday, 23:50',
    );
  });

  it('gives the date for anything older', () => {
    expect(formatWhen(at(9, 28, 11, 5), NOW)).toBe('28 Sep, 11:05');
    expect(formatWhen(at(2, 14, 15, 0), NOW)).toBe('14 Feb, 15:00');
  });

  it('adds the year when it is not this one', () => {
    expect(formatWhen(at(12, 31, 23, 59, 2025), NOW)).toBe('31 Dec 2025, 23:59');
  });

  it('reads a time ahead of the clock as today, and gives nothing for a value that is not a date', () => {
    expect(formatWhen(at(9, 30, 11, 30), NOW)).toBe('Today, 11:30');
    expect(formatWhen('not a date', NOW)).toBe('');
  });
});

describe('notificationMeta', () => {
  it('joins who and when', () => {
    expect(notificationMeta(transfer({ performedBy: 'keeper.conti', occurredAt: at(9, 30, 10, 41) }), NOW)).toBe(
      'keeper.conti · Today, 10:41',
    );
  });

  it('leaves out an author it does not have', () => {
    expect(notificationMeta(notification({ performedBy: null }), NOW)).toBe('Today, 09:12');
  });
});

describe('acknowledgedText', () => {
  it('names who took the notification in charge, and when', () => {
    expect(
      acknowledgedText(
        notification({ acknowledgedBy: 'vet.bianchi', acknowledgedAt: at(9, 29, 12, 40) }),
        NOW,
      ),
    ).toBe('Acknowledged by vet.bianchi · Yesterday, 12:40');
  });

  it('still names who when the time is missing', () => {
    expect(acknowledgedText(notification({ acknowledgedBy: 'vet.bianchi' }), NOW)).toBe(
      'Acknowledged by vet.bianchi',
    );
  });

  it('is null while the notification is open', () => {
    expect(acknowledgedText(notification(), NOW)).toBeNull();
    expect(isOpen(notification())).toBe(true);
    expect(isOpen(notification({ acknowledgedBy: 'vet.bianchi' }))).toBe(false);
  });
});
