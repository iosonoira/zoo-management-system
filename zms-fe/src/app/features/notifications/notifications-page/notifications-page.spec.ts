import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ApiError } from '../../../core/data/api-error';
import { ENCLOSURES } from '../../../core/data/enclosure-directory';
import { NOTIFICATION_PAGE_SIZE, NotificationApi } from '../../../core/data/notification-api';
import { NotificationStore } from '../../../core/data/notification-store';
import { Page } from '../../../core/data/page';
import { ZooRole } from '../../../core/models/animal';
import { Notification, NotificationQuery, OpenCount } from '../../../core/models/notification';
import { can } from '../../../core/models/permissions';
import { Session } from '../../../core/session/session';
import { NotificationsPage } from './notifications-page';

let sequence = 0;

function notification(overrides: Partial<Notification> = {}): Notification {
  sequence++;
  return {
    id: `n${sequence}`,
    animalId: `animal-${sequence}`,
    eventType: 'ANIMAL_STATUS_CHANGED',
    severity: 'WARNING',
    message: `Message ${sequence}`,
    occurredAt: new Date(2026, 8, 30, 10, 0, 60 - sequence).toISOString(),
    performedBy: 'vet.bianchi',
    name: `Animal${sequence}`,
    species: 'Test species',
    dangerous: false,
    previousStatus: 'HEALTHY',
    newStatus: 'IN_TREATMENT',
    fromEnclosureId: null,
    toEnclosureId: null,
    acknowledgedBy: null,
    acknowledgedAt: null,
    ...overrides,
  };
}

const warning = (overrides: Partial<Notification> = {}) => notification(overrides);
const critical = (overrides: Partial<Notification> = {}) =>
  notification({ severity: 'CRITICAL', previousStatus: 'IN_TREATMENT', newStatus: 'DECEASED', ...overrides });
const info = (overrides: Partial<Notification> = {}) =>
  notification({
    severity: 'INFO',
    eventType: 'ANIMAL_REGISTERED',
    previousStatus: null,
    newStatus: null,
    toEnclosureId: ENCLOSURES[2].id,
    ...overrides,
  });
/** Acknowledged yesterday at 12:40, whatever day the test runs. */
const acknowledged = (make: () => Notification) => {
  const at = new Date();
  at.setDate(at.getDate() - 1);
  at.setHours(12, 40, 0, 0);
  return { ...make(), acknowledgedBy: 'admin.rossi', acknowledgedAt: at.toISOString() };
};

class FakeNotificationApi extends NotificationApi {
  data: Notification[] = [];
  failList = false;
  failAck: ApiError | null = null;
  ackedBy = 'keeper.conti';
  /** While set, lists never answer: the page stays on its skeleton. */
  hang = false;

  async list(query: NotificationQuery, page: number): Promise<Page<Notification>> {
    if (this.hang) {
      return new Promise(() => {});
    }
    if (this.failList) {
      throw new ApiError(500, 'down');
    }
    const items = this.data.filter(
      (n) =>
        (!query.severities?.length || query.severities.includes(n.severity)) &&
        (!query.openOnly || n.acknowledgedBy === null),
    );
    return {
      items: items.slice(page * NOTIFICATION_PAGE_SIZE, (page + 1) * NOTIFICATION_PAGE_SIZE),
      page,
      size: NOTIFICATION_PAGE_SIZE,
      total: items.length,
    };
  }

  async countOpen(): Promise<OpenCount> {
    const open = this.data.filter(
      (n) => n.acknowledgedBy === null && n.severity !== 'INFO',
    );
    return { attention: open.length, critical: open.filter((n) => n.severity === 'CRITICAL').length };
  }

  async acknowledge(id: string): Promise<Notification> {
    if (this.failAck) {
      throw this.failAck;
    }
    const found = this.data.find((n) => n.id === id)!;
    const updated = found.acknowledgedBy
      ? found
      : { ...found, acknowledgedBy: this.ackedBy, acknowledgedAt: new Date().toISOString() };
    this.data = this.data.map((n) => (n.id === id ? updated : n));
    return updated;
  }
}

function fakeSession(role: ZooRole, allowed = true) {
  return {
    role: signal(role),
    username: signal('keeper.conti'),
    can: (permission: Parameters<typeof can>[1]) => allowed && can(role, permission),
  };
}

interface Options {
  role?: ZooRole;
  /** False simulates a role the matrix does not allow to acknowledge. */
  allowed?: boolean;
  prepare?: (api: FakeNotificationApi) => void;
}

describe('NotificationsPage', () => {
  beforeEach(() => {
    sequence = 0;
  });

  async function setUp(data: Notification[], options: Options = {}) {
    const api = new FakeNotificationApi();
    api.data = data;
    options.prepare?.(api);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [NotificationsPage],
      providers: [
        provideRouter([]),
        { provide: NotificationApi, useValue: api },
        { provide: Session, useValue: fakeSession(options.role ?? 'zoo-keeper', options.allowed) },
        NotificationStore,
      ],
    });
    const fixture = TestBed.createComponent(NotificationsPage);
    const root: HTMLElement = fixture.nativeElement;
    const settle = async () => {
      fixture.detectChanges();
      await fixture.whenStable();
      await new Promise<void>((resolve) => setTimeout(resolve));
      fixture.detectChanges();
      await fixture.whenStable();
    };
    await settle();
    const section = (heading: string) =>
      Array.from(root.querySelectorAll('section')).find(
        (s) => s.querySelector('h2')!.textContent!.includes(heading),
      )!;
    return {
      api,
      fixture,
      root,
      settle,
      store: TestBed.inject(NotificationStore),
      attention: () => section('Needs attention'),
      rest: () => section('Everything else'),
      rows: (s: HTMLElement) => Array.from(s.querySelectorAll('li')),
      text: (el: Element) => el.textContent!.replace(/\s+/g, ' ').trim(),
      ackButtons: (s: HTMLElement) =>
        Array.from(s.querySelectorAll<HTMLButtonElement>('button[data-ack]')),
      toast: () => Array.from(root.querySelectorAll('.toast.show')).map((t) => t.textContent!.trim()),
    };
  }

  it('shows the loading state until the lists answer', async () => {
    const api = new FakeNotificationApi();
    api.hang = true;
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [NotificationsPage],
      providers: [
        provideRouter([]),
        { provide: NotificationApi, useValue: api },
        { provide: Session, useValue: fakeSession('zoo-keeper') },
      ],
    });
    const fixture = TestBed.createComponent(NotificationsPage);
    fixture.detectChanges();
    const skeletons = fixture.nativeElement.querySelectorAll('[aria-busy="true"]');
    expect(skeletons).toHaveLength(2);
    expect(skeletons[0].getAttribute('aria-label')).toBe('Loading notifications that need attention');
  });

  it('splits Needs attention from Everything else, and says how many need attention', async () => {
    const c = critical({ name: 'Bruno' });
    const w = warning({ name: 'Zuri', dangerous: true, species: 'African lion' });
    const i = info({ name: 'Lulu' });
    const { root, attention, rest, rows, text, ackButtons } = await setUp([c, w, i]);

    expect(text(root.querySelector('h1')!)).toBe('Notifications');
    expect(text(root.querySelector('.lede')!)).toBe('2 need attention. Acknowledging takes one in charge for everyone.');
    expect(text(attention().querySelector('h2')!)).toBe('Needs attention 2');

    const needs = rows(attention());
    expect(needs).toHaveLength(2);
    expect(text(needs[0])).toContain('Bruno');
    expect(text(needs[0])).toContain('Marked deceased.');
    expect(text(needs[0])).toContain('Critical');
    expect(text(needs[1])).toContain('Zuri');
    expect(text(needs[1])).toContain('Danger');
    expect(text(needs[1])).toContain('African lion');
    expect(text(needs[1])).toContain('Status changed from Healthy to In treatment.');
    expect(ackButtons(attention())).toHaveLength(2);

    const log = rows(rest());
    expect(log).toHaveLength(1);
    expect(text(log[0])).toContain(`Lulu was registered into ${ENCLOSURES[2].name}.`);
    expect(text(log[0])).toContain('Info');
  });

  it('links each row to its animal', async () => {
    const w = warning();
    const i = info();
    const { attention, rest } = await setUp([w, i]);
    expect(attention().querySelector('a')!.getAttribute('href')).toBe(`/animals/${w.animalId}`);
    expect(rest().querySelector('a')!.getAttribute('href')).toBe(`/animals/${i.animalId}`);
  });

  it('names every Acknowledge button after its row for a screen reader', async () => {
    const { attention, text, ackButtons } = await setUp([critical({ name: 'Bruno' })]);
    expect(text(ackButtons(attention())[0])).toBe('Acknowledge: Bruno, critical notification');
  });

  it('shows the empty state of each section', async () => {
    const { attention, rest, text, root } = await setUp([]);
    expect(text(attention())).toContain('Nothing needs attention');
    expect(text(attention())).toContain(
      'Open warnings and critical notifications show up here until someone acknowledges them.',
    );
    expect(text(rest())).toContain('No open information notices');
    expect(text(rest())).toContain('Switch to All to see what has been acknowledged.');
    expect(text(root.querySelector('.lede')!)).toBe('Nothing needs attention.');
  });

  it('shows an error with “Try again” for each section, and loads again on retry', async () => {
    const { api, attention, rest, settle, text } = await setUp([warning(), info()], {
      prepare: (fake) => (fake.failList = true),
    });

    expect(text(attention())).toContain('Notifications that need attention didn’t load');
    expect(text(rest())).toContain('The rest of the notifications didn’t load');
    expect(attention().querySelector('[role="alert"]')).not.toBeNull();

    api.failList = false;
    const retry = attention().querySelector<HTMLButtonElement>('.notice button')!;
    expect(retry.textContent!.trim()).toBe('Try again');
    retry.click();
    await settle();
    expect(attention().querySelectorAll('li')).toHaveLength(1);
    expect(text(rest())).toContain('The rest of the notifications didn’t load');
  });

  describe('acknowledging', () => {
    it('takes the row out of Needs attention, updates the pill, and says so', async () => {
      const a = warning({ name: 'Zuri' });
      const b = warning({ name: 'Pepe' });
      const { attention, root, settle, text, ackButtons, toast, store } = await setUp([a, b]);

      ackButtons(attention())[0].click();
      await settle();

      expect(attention().querySelectorAll('li')).toHaveLength(1);
      expect(text(attention())).not.toContain('Zuri');
      expect(text(attention().querySelector('h2')!)).toBe('Needs attention 1');
      expect(text(root.querySelector('.lede')!)).toContain('1 needs attention.');
      expect(toast()).toEqual(['Acknowledged by keeper.conti. Zuri is taken in charge.']);
      expect(store.openCount()).toEqual({ attention: 1, critical: 0 });
    });

    it('moves focus to the next row’s action', async () => {
      const { attention, settle, ackButtons } = await setUp([warning(), warning(), warning()]);
      const [first, second] = ackButtons(attention());
      first.click();
      await settle();
      expect(document.activeElement).toBe(ackButtons(attention())[0]);
      expect(document.activeElement!.getAttribute('data-ack')).toBe(second.getAttribute('data-ack'));
    });

    it('moves focus to the previous row when the last one was acknowledged', async () => {
      const { attention, settle, ackButtons } = await setUp([warning(), warning()]);
      const [first, last] = ackButtons(attention());
      last.click();
      await settle();
      expect(document.activeElement).toBe(first);
    });

    it('moves focus to the section heading when no row is left', async () => {
      const { attention, settle, ackButtons } = await setUp([warning()]);
      ackButtons(attention())[0].click();
      await settle();
      expect(document.activeElement).toBe(attention().querySelector('h2'));
      expect(attention().querySelector('h2')!.getAttribute('tabindex')).toBe('-1');
    });

    it('shows whoever got there first, and says so', async () => {
      const w = warning({ name: 'Zuri' });
      const { api, attention, settle, ackButtons, toast } = await setUp([w]);
      api.ackedBy = 'vet.bianchi';
      ackButtons(attention())[0].click();
      await settle();
      expect(toast()).toEqual(['Zuri was already acknowledged by vet.bianchi.']);
    });

    it('shows the API’s message in an alert when it fails, and keeps the row', async () => {
      const { api, attention, settle, ackButtons, toast, root } = await setUp([warning()]);
      api.failAck = new ApiError(403, 'Only zoo staff can acknowledge notifications.');
      ackButtons(attention())[0].click();
      await settle();

      expect(toast()).toEqual(['Only zoo staff can acknowledge notifications.']);
      expect(root.querySelector('.toast[role="alert"]')!.classList.contains('show')).toBe(true);
      expect(attention().querySelectorAll('li')).toHaveLength(1);
    });

    it('acknowledges an information row: it leaves the open list', async () => {
      const { rest, settle, ackButtons, rows } = await setUp([info(), info()]);
      expect(rows(rest())).toHaveLength(2);
      ackButtons(rest())[0].click();
      await settle();
      expect(rows(rest())).toHaveLength(1);
      expect(document.activeElement).toBe(ackButtons(rest())[0]);
    });
  });

  describe('Everything else', () => {
    it('switches between Open and All, with the state in aria-pressed', async () => {
      const w = warning({ name: 'Zuri' });
      const ackedWarning = acknowledged(() => warning({ name: 'Pepe' }));
      const openInfo = info({ name: 'Lulu' });
      const ackedInfo = acknowledged(() => info({ name: 'Tobi' }));
      const { rest, root, settle, rows, text } = await setUp([w, ackedWarning, openInfo, ackedInfo]);

      const chips = () => Array.from(rest().querySelectorAll<HTMLButtonElement>('.chip'));
      expect(chips().map((c) => [c.textContent!.trim(), c.getAttribute('aria-pressed')])).toEqual([
        ['Open', 'true'],
        ['All', 'false'],
      ]);
      expect(rows(rest())).toHaveLength(1);

      chips()[1].click();
      await settle();

      expect(chips().map((c) => c.getAttribute('aria-pressed'))).toEqual(['false', 'true']);
      // The open warning already sits in Needs attention, so All does not repeat it.
      expect(rows(rest()).map((r) => text(r))).toEqual([
        expect.stringContaining('Pepe'),
        expect.stringContaining('Lulu'),
        expect.stringContaining('Tobi'),
      ]);
      expect(text(root)).toContain('Acknowledged by admin.rossi · Yesterday, 12:40');
    });

    it('offers “Show more” while the server holds more, and reads on when it is used', async () => {
      const many = Array.from({ length: NOTIFICATION_PAGE_SIZE + 3 }, () => info());
      const { rest, settle, rows, text } = await setUp(many);
      expect(rows(rest())).toHaveLength(NOTIFICATION_PAGE_SIZE);
      const more = rest().querySelector<HTMLButtonElement>('.more button')!;
      expect(more.textContent!.trim()).toBe('Show more');
      expect(text(rest().querySelector('.more')!)).toContain(`Showing ${NOTIFICATION_PAGE_SIZE} of 23.`);

      more.click();
      await settle();
      expect(rows(rest())).toHaveLength(23);
      expect(rest().querySelector('.more')).toBeNull();
      // The button is gone, so focus lands on the first row that arrived.
      expect(document.activeElement).toBe(rows(rest())[NOTIFICATION_PAGE_SIZE].querySelector('a'));
    });

    it('has the empty copy for All', async () => {
      const { rest, settle, text } = await setUp([]);
      rest().querySelectorAll<HTMLButtonElement>('.chip')[1].click();
      await settle();
      expect(text(rest())).toContain('No notifications yet');
      expect(text(rest())).toContain('Registrations, status changes and transfers of animals appear here.');
    });
  });

  it('gives every role the button, since the matrix allows them all', async () => {
    for (const role of ['zoo-keeper', 'zoo-vet', 'zoo-admin'] as const) {
      const { attention, ackButtons, root } = await setUp([warning()], { role });
      expect(ackButtons(attention())).toHaveLength(1);
      expect(root.querySelector('.note')).toBeNull();
    }
  });

  it('hides Acknowledge and says why when the role cannot do it', async () => {
    // Every role can today; the page still asks the session, like the other sections.
    const { attention, rest, ackButtons, text, root } = await setUp([warning(), info()], {
      allowed: false,
    });
    expect(ackButtons(attention())).toHaveLength(0);
    expect(ackButtons(rest())).toHaveLength(0);
    expect(text(root.querySelector('.note')!)).toBe('Only zoo staff can acknowledge notifications.');
  });
});
