import { Component, DOCUMENT, afterNextRender, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { NotificationStore } from './core/data/notification-store';
import { Session } from './core/session/session';
import { Icon } from './core/ui/icon/icon';
import { RoleSelect } from './core/ui/role-select/role-select';

type Theme = 'light' | 'dark';

const THEME_KEY = 'zms-theme';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, RoleSelect],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly session = inject(Session);
  protected readonly theme = signal<Theme>('light');
  protected readonly canSwitchRole = this.session.canSwitchRole;

  private readonly document = inject(DOCUMENT);
  private readonly notifications = inject(NotificationStore);

  /** The bell: the badge counts open warnings and criticals, and turns red while one is critical. */
  protected readonly bell = computed(() => {
    const count = this.notifications.openCount();
    const attention = count?.attention ?? 0;
    const critical = count?.critical ?? 0;
    let label = 'Notifications';
    if (attention > 0) {
      label += `, ${attention} ${attention === 1 ? 'needs' : 'need'} attention`;
      if (critical > 0) {
        label += `, ${critical} critical`;
      }
    }
    return { attention, critical, badge: attention > 99 ? '99+' : String(attention), label };
  });

  constructor() {
    // Every page change is a moment the count may have moved, and the first one is app start.
    // A count that cannot be read is not an error: the store keeps what the bell shows.
    inject(Router)
      .events.pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => void this.notifications.refreshCount());

    afterNextRender(() => {
      this.session.restore();
      this.theme.set(this.initialTheme());
    });
  }

  protected signOut(): void {
    this.session.signOut();
  }

  protected toggleTheme(): void {
    const next: Theme = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(next);
    this.document.documentElement.dataset['theme'] = next;
    try {
      localStorage.setItem(THEME_KEY, next);
    } catch {
      // Storage unavailable: the choice lasts for this page only.
    }
  }

  private initialTheme(): Theme {
    const set = this.document.documentElement.dataset['theme'];
    if (set === 'light' || set === 'dark') {
      return set;
    }
    return this.document.defaultView?.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
}
