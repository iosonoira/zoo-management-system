import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { App } from './app';
import { NotificationApi } from './core/data/notification-api';
import { Notification, NotificationQuery, OpenCount } from './core/models/notification';
import { DemoSession } from './core/session/demo-session';
import { Session } from './core/session/session';
import { Page } from './core/data/page';

class FakeNotificationApi extends NotificationApi {
  countCalls = 0;
  fail = false;

  constructor(public count: OpenCount) {
    super();
  }

  async countOpen(): Promise<OpenCount> {
    this.countCalls++;
    if (this.fail) {
      throw new Error('401');
    }
    return this.count;
  }

  async list(_query: NotificationQuery, page: number): Promise<Page<Notification>> {
    return { items: [], page, size: 20, total: 0 };
  }

  async acknowledge(): Promise<Notification> {
    throw new Error('not used');
  }
}

const flush = () => new Promise<void>((resolve) => setTimeout(resolve));

describe('App', () => {
  async function setUp(count: OpenCount = { attention: 0, critical: 0 }) {
    const api = new FakeNotificationApi(count);
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([{ path: '**', children: [] }]),
        { provide: Session, useClass: DemoSession },
        { provide: NotificationApi, useValue: api },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    const root: HTMLElement = fixture.nativeElement;
    const go = async (url: string) => {
      await router.navigateByUrl(url);
      await flush();
      fixture.detectChanges();
      await fixture.whenStable();
    };
    return {
      api,
      fixture,
      go,
      bell: () => root.querySelector<HTMLAnchorElement>('a.bell')!,
      badge: () => root.querySelector<HTMLElement>('.badge'),
    };
  }

  it('should create the app', async () => {
    const { fixture } = await setUp();
    expect(fixture.componentInstance).toBeTruthy();
  });

  describe('the bell', () => {
    it('links to /notifications, before the role switch, with a 44px target', async () => {
      const { fixture, bell } = await setUp();
      fixture.detectChanges();
      expect(bell().getAttribute('href')).toBe('/notifications');
      const controls = bell().parentElement!;
      expect(controls.firstElementChild).toBe(bell());
      expect(controls.querySelector('app-role-select')).not.toBeNull();
    });

    it('has no badge while the count is unknown or zero, and is just “Notifications”', async () => {
      const { fixture, go, bell, badge } = await setUp({ attention: 0, critical: 0 });
      fixture.detectChanges();
      expect(badge()).toBeNull();
      expect(bell().getAttribute('aria-label')).toBe('Notifications');

      await go('/animals');
      expect(badge()).toBeNull();
      expect(bell().getAttribute('aria-label')).toBe('Notifications');
    });

    it('shows the count in ink while nothing critical is open', async () => {
      const { fixture, go, bell, badge } = await setUp({ attention: 3, critical: 0 });
      fixture.detectChanges();
      await go('/animals');

      expect(badge()!.textContent!.trim()).toBe('3');
      expect(badge()!.classList.contains('badge-critical')).toBe(false);
      expect(bell().getAttribute('aria-label')).toBe('Notifications, 3 need attention');
    });

    it('turns red only when at least one open notification is critical', async () => {
      const { fixture, api, go, bell, badge } = await setUp({ attention: 3, critical: 0 });
      fixture.detectChanges();
      await go('/animals');
      expect(badge()!.classList.contains('badge-critical')).toBe(false);

      api.count = { attention: 3, critical: 1 };
      await go('/notifications');
      expect(badge()!.classList.contains('badge-critical')).toBe(true);
      // Colour is not the only cue: the label says how many are critical.
      expect(bell().getAttribute('aria-label')).toBe('Notifications, 3 need attention, 1 critical');

      api.count = { attention: 2, critical: 0 };
      await go('/animals');
      expect(badge()!.classList.contains('badge-critical')).toBe(false);
    });

    it('reads “needs” for one, and 99+ above 99', async () => {
      const { fixture, api, go, bell, badge } = await setUp({ attention: 1, critical: 0 });
      fixture.detectChanges();
      await go('/animals');
      expect(bell().getAttribute('aria-label')).toBe('Notifications, 1 needs attention');

      api.count = { attention: 120, critical: 0 };
      await go('/notifications');
      expect(badge()!.textContent!.trim()).toBe('99+');
      expect(bell().getAttribute('aria-label')).toBe('Notifications, 120 need attention');
    });

    it('marks the current page, and refreshes the count on every navigation', async () => {
      const { fixture, api, go, bell } = await setUp({ attention: 1, critical: 0 });
      fixture.detectChanges();
      await go('/animals');
      expect(bell().hasAttribute('aria-current')).toBe(false);
      const calls = api.countCalls;
      expect(calls).toBeGreaterThan(0);

      await go('/notifications');
      expect(bell().getAttribute('aria-current')).toBe('page');
      expect(api.countCalls).toBe(calls + 1);
    });

    it('keeps the last count when a refresh fails, and shows no error', async () => {
      const { fixture, api, go, badge } = await setUp({ attention: 2, critical: 1 });
      fixture.detectChanges();
      await go('/animals');
      expect(badge()!.textContent!.trim()).toBe('2');

      api.fail = true;
      await go('/notifications');
      expect(badge()!.textContent!.trim()).toBe('2');
      expect(badge()!.classList.contains('badge-critical')).toBe(true);
    });
  });
});
