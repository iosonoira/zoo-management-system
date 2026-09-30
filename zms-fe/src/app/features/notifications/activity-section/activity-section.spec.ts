import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ApiError } from '../../../core/data/api-error';
import { ENCLOSURES } from '../../../core/data/enclosure-directory';
import { NOTIFICATION_PAGE_SIZE, NotificationApi } from '../../../core/data/notification-api';
import { Page } from '../../../core/data/page';
import { Animal, ZooRole } from '../../../core/models/animal';
import { Notification, NotificationQuery, OpenCount } from '../../../core/models/notification';
import { can } from '../../../core/models/permissions';
import { Session } from '../../../core/session/session';
import { ActivitySection } from './activity-section';

const ANIMAL: Animal = {
  id: 'zuri',
  name: 'Zuri',
  species: 'African lion',
  dangerous: true,
  habitat: 'TERRESTRIAL',
  enclosureId: ENCLOSURES[1].id,
  arrivalDate: '2026-02-14',
  status: 'HEALTHY',
  createdBy: 'admin.rossi',
  updatedBy: 'vet.bianchi',
};

let sequence = 0;

function notification(overrides: Partial<Notification> = {}): Notification {
  sequence++;
  return {
    id: `n${sequence}`,
    animalId: ANIMAL.id,
    eventType: 'ANIMAL_TRANSFERRED',
    severity: 'WARNING',
    message: `Message ${sequence}`,
    occurredAt: new Date(2026, 8, 30, 10, 0, 60 - sequence).toISOString(),
    performedBy: 'keeper.conti',
    name: ANIMAL.name,
    species: ANIMAL.species,
    dangerous: true,
    previousStatus: null,
    newStatus: null,
    fromEnclosureId: ENCLOSURES[0].id,
    toEnclosureId: ENCLOSURES[1].id,
    acknowledgedBy: null,
    acknowledgedAt: null,
    ...overrides,
  };
}

const critical = () =>
  notification({
    severity: 'CRITICAL',
    eventType: 'ANIMAL_STATUS_CHANGED',
    previousStatus: 'IN_TREATMENT',
    newStatus: 'DECEASED',
    fromEnclosureId: null,
    toEnclosureId: null,
  });
const info = (overrides: Partial<Notification> = {}) =>
  notification({
    severity: 'INFO',
    eventType: 'ANIMAL_REGISTERED',
    fromEnclosureId: null,
    ...overrides,
  });
/** Acknowledged yesterday at 12:40, whatever day the test runs. */
const acknowledged = (n: Notification): Notification => {
  const at = new Date();
  at.setDate(at.getDate() - 1);
  at.setHours(12, 40, 0, 0);
  return { ...n, acknowledgedBy: 'admin.rossi', acknowledgedAt: at.toISOString() };
};

class FakeNotificationApi extends NotificationApi {
  data: Notification[] = [];
  countCalls = 0;
  failList = false;
  failAck: ApiError | null = null;
  ackedBy = 'keeper.conti';
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
        (!query.animalId || n.animalId === query.animalId) &&
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
    this.countCalls++;
    return { attention: 0, critical: 0 };
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
  allowed?: boolean;
  prepare?: (api: FakeNotificationApi) => void;
}

describe('ActivitySection', () => {
  beforeEach(() => {
    sequence = 0;
  });

  async function setUp(data: Notification[], options: Options = {}) {
    const api = new FakeNotificationApi();
    api.data = data;
    options.prepare?.(api);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ActivitySection],
      providers: [
        provideRouter([]),
        { provide: NotificationApi, useValue: api },
        { provide: Session, useValue: fakeSession('zoo-keeper', options.allowed) },
      ],
    });
    const fixture = TestBed.createComponent(ActivitySection);
    fixture.componentRef.setInput('animal', ANIMAL);
    const announced: string[] = [];
    fixture.componentInstance.announce.subscribe((m) => announced.push(m));
    const settle = async () => {
      fixture.detectChanges();
      await fixture.whenStable();
      await new Promise<void>((resolve) => setTimeout(resolve));
      fixture.detectChanges();
      await fixture.whenStable();
    };
    await settle();
    const root: HTMLElement = fixture.nativeElement;
    return {
      api,
      root,
      settle,
      announced,
      rows: () => Array.from(root.querySelectorAll('li')),
      folds: () => Array.from(root.querySelectorAll<HTMLButtonElement>('button.fold')),
      acks: () => Array.from(root.querySelectorAll<HTMLButtonElement>('button[data-ack]')),
      text: (el: Element) => el.textContent!.replace(/\s+/g, ' ').trim(),
    };
  }

  it('shows the loading state until the list answers', async () => {
    const { root } = await setUp([], { prepare: (api) => (api.hang = true) });
    const busy = root.querySelector('[aria-busy="true"]')!;
    expect(busy.getAttribute('aria-label')).toBe('Loading activity');
    expect(root.querySelector('li')).toBeNull();
  });

  it('loads the animal’s notifications and links to the notifications page', async () => {
    const { root, text } = await setUp([info()]);
    expect(text(root.querySelector('h2')!)).toBe('Activity');
    expect(root.querySelector('h2')!.getAttribute('tabindex')).toBe('-1');
    const link = root.querySelector('a')!;
    expect(link.getAttribute('href')).toBe('/notifications');
    expect(text(link)).toBe('All notifications');
  });

  it('expands open warnings and criticals with the action, and folds everything else', async () => {
    const c = critical();
    const w = notification();
    const i = info();
    const doneWarning = acknowledged(notification());
    const { rows, folds, acks, text, root } = await setUp([c, w, i, doneWarning]);

    expect(rows()).toHaveLength(4);
    expect(root.querySelector('.note')!.textContent!.trim()).toBe(
      '2 need attention. The rest is folded.',
    );

    // Open attention: sentence, severity word and a primary Acknowledge, no fold button.
    const open = rows().slice(0, 2);
    expect(open.every((li) => li.querySelector('button.fold') === null)).toBe(true);
    expect(text(open[0])).toContain('Marked deceased.');
    expect(text(open[0])).toContain('Critical');
    expect(text(open[1])).toContain(
      `Transferred from ${ENCLOSURES[0].name} to ${ENCLOSURES[1].name}.`,
    );
    // The animal’s name is not repeated: the page is about it.
    expect(text(open[1])).not.toContain('Zuri');
    expect(acks().slice(0, 2).every((b) => b.classList.contains('btn-primary'))).toBe(true);

    // Everything else: one folded line each, closed.
    expect(folds()).toHaveLength(2);
    expect(folds().map((b) => b.getAttribute('aria-expanded'))).toEqual(['false', 'false']);
    expect(text(folds()[0])).toContain(`Registered into ${ENCLOSURES[1].name}.`);
    expect(text(folds()[0])).toContain('Today · open');
    expect(text(folds()[1])).toContain('acknowledged');
    // A closed line has no Acknowledge to tab through.
    expect(acks()).toHaveLength(2);
  });

  it('unfolds a line on select, showing who and when and the action for an open one', async () => {
    const { folds, acks, settle, root, text } = await setUp([info(), acknowledged(info())]);
    const [openInfo, done] = folds();
    expect(openInfo.getAttribute('aria-controls')).toBe(root.querySelectorAll('.fold-body')[0].id);

    openInfo.click();
    await settle();
    expect(openInfo.getAttribute('aria-expanded')).toBe('true');
    expect(root.querySelectorAll('.fold-body')[0].hasAttribute('hidden')).toBe(false);
    expect(text(root.querySelectorAll('.fold-body')[0])).toContain('Info');
    expect(acks()).toHaveLength(1);
    expect(acks()[0].classList.contains('btn-secondary')).toBe(true);

    done.click();
    await settle();
    expect(text(root.querySelectorAll('.fold-body')[1])).toContain(
      'Acknowledged by admin.rossi · Yesterday, 12:40',
    );

    openInfo.click();
    await settle();
    expect(openInfo.getAttribute('aria-expanded')).toBe('false');
    expect(acks()).toHaveLength(0);
  });

  it('says nothing needs attention when nothing does', async () => {
    const { root } = await setUp([info()]);
    expect(root.querySelector('.note')!.textContent!.trim()).toBe(
      'Nothing needs attention. Select a line to unfold it.',
    );
  });

  it('names each Acknowledge for a screen reader', async () => {
    const { acks, text } = await setUp([critical()]);
    expect(text(acks()[0])).toBe('Acknowledge: critical, Marked deceased.');
  });

  it('shows the empty state', async () => {
    const { root, text } = await setUp([]);
    expect(text(root)).toContain('No activity yet');
    expect(text(root)).toContain('Registration, status changes and transfers of Zuri appear here.');
    expect(root.querySelector('ul')).toBeNull();
  });

  it('shows an error with “Try again” that stays inside the section, and loads again on retry', async () => {
    const { api, root, settle, rows, text } = await setUp([info()], {
      prepare: (fake) => (fake.failList = true),
    });
    expect(root.querySelector('[role="alert"]')).not.toBeNull();
    expect(text(root)).toContain('Activity didn’t load');
    expect(text(root)).toContain('The rest of this page still works.');
    // The heading and the link to all notifications are still there.
    expect(text(root.querySelector('h2')!)).toBe('Activity');

    api.failList = false;
    const retry = root.querySelector<HTMLButtonElement>('.notice button')!;
    expect(retry.textContent!.trim()).toBe('Try again');
    retry.click();
    await settle();
    expect(rows()).toHaveLength(1);
  });

  it('pages with “Show earlier”, and lands on the first new row when the button goes', async () => {
    const many = Array.from({ length: NOTIFICATION_PAGE_SIZE + 3 }, () => info());
    const { root, rows, settle } = await setUp(many);
    expect(rows()).toHaveLength(NOTIFICATION_PAGE_SIZE);
    const more = root.querySelector<HTMLButtonElement>('button.more')!;
    expect(more.textContent!.trim()).toBe('Show earlier');

    more.click();
    await settle();
    expect(rows()).toHaveLength(NOTIFICATION_PAGE_SIZE + 3);
    expect(root.querySelector('button.more')).toBeNull();
    expect(document.activeElement).toBe(rows()[NOTIFICATION_PAGE_SIZE].querySelector('button'));
  });

  describe('acknowledging', () => {
    it('updates the row, refreshes the bell and confirms in the page', async () => {
      const c = critical();
      const w = notification();
      const { api, acks, rows, settle, announced, root, text } = await setUp([c, w]);
      const before = api.countCalls;

      acks()[0].click();
      await settle();

      // The row stays, now a folded, acknowledged line; the other is still open.
      expect(rows()).toHaveLength(2);
      expect(rows()[0].querySelector('button.fold')).not.toBeNull();
      expect(text(rows()[0])).toContain('acknowledged');
      expect(acks()).toHaveLength(1);
      expect(root.querySelector('.note')!.textContent!.trim()).toBe(
        '1 needs attention. The rest is folded.',
      );
      expect(api.countCalls).toBeGreaterThan(before);
      expect(announced).toEqual(['Acknowledged by keeper.conti. Zuri is taken in charge.']);
    });

    it('moves focus to the next row’s action, or the row itself when none is left', async () => {
      const first = notification();
      const second = notification();
      const { acks, folds, settle } = await setUp([first, second]);

      acks()[0].click();
      await settle();
      expect(document.activeElement).toBe(acks()[0]);
      expect(document.activeElement!.getAttribute('data-ack')).toBe(second.id);

      acks()[0].click();
      await settle();
      // No open row is left: the row just acknowledged is a folded line now.
      expect(document.activeElement).toBe(folds()[1]);
    });

    it('uses the previous row’s action when the last row was acknowledged', async () => {
      const { acks, settle } = await setUp([notification(), notification()]);
      const [first, last] = acks();
      last.click();
      await settle();
      expect(document.activeElement).toBe(first);
    });

    it('says who got there first', async () => {
      const { api, acks, settle, announced } = await setUp([notification()]);
      api.ackedBy = 'vet.bianchi';
      acks()[0].click();
      await settle();
      expect(announced).toEqual(['Zuri was already acknowledged by vet.bianchi.']);
    });

    it('shows the API’s message in an alert when it fails, and keeps the row open', async () => {
      const { api, acks, settle, root, announced } = await setUp([notification()]);
      api.failAck = new ApiError(403, 'Only zoo staff can acknowledge notifications.');
      acks()[0].click();
      await settle();

      expect(root.querySelector('.form-error[role="alert"]')!.textContent!.trim()).toBe(
        'Only zoo staff can acknowledge notifications.',
      );
      expect(acks()).toHaveLength(1);
      expect(announced).toEqual([]);
    });
  });

  it('hides Acknowledge when the role cannot do it', async () => {
    const { acks, folds, root } = await setUp([notification(), info()], { allowed: false });
    expect(acks()).toHaveLength(0);
    expect(folds()).toHaveLength(1);
    expect(root.querySelectorAll('li')).toHaveLength(2);
  });
});
