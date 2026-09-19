import { Component, DOCUMENT, afterNextRender, inject, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { Session } from './core/session/session';
import { Icon } from './core/ui/icon/icon';
import { RoleSelect } from './core/ui/role-select/role-select';

type Theme = 'light' | 'dark';

const THEME_KEY = 'zms-theme';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, Icon, RoleSelect],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly session = inject(Session);
  protected readonly theme = signal<Theme>('light');

  private readonly document = inject(DOCUMENT);

  constructor() {
    afterNextRender(() => {
      this.session.restore();
      this.theme.set(this.initialTheme());
    });
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
