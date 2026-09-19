import { Component, afterNextRender, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { ZooRole } from './core/models/animal';
import { ROLE_LABELS } from './core/models/labels';
import { Session } from './core/session/session';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly session = inject(Session);
  protected readonly roles = Object.entries(ROLE_LABELS) as [ZooRole, string][];

  constructor() {
    afterNextRender(() => this.session.restore());
  }

  protected onRoleChange(event: Event): void {
    this.session.setRole((event.target as HTMLSelectElement).value as ZooRole);
  }
}
