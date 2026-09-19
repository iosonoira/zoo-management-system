import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { Combobox, ComboboxPopup, ComboboxWidget } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { ConnectedPosition, OverlayModule } from '@angular/cdk/overlay';
import { ZooRole } from '../../models/animal';
import { ROLE_LABELS } from '../../models/labels';
import { Session } from '../../session/session';
import { Icon } from '../icon/icon';

interface RoleOption {
  readonly value: ZooRole;
  readonly label: string;
  readonly job: string;
}

const ROLE_JOBS: Record<ZooRole, string> = {
  'zoo-keeper': 'Transfers animals between enclosures',
  'zoo-vet': 'Changes clinical status',
  'zoo-admin': 'Registers animals, full access',
};

/** Demo role switch standing in for Keycloak sign-in, built on the Angular Aria select pattern. */
@Component({
  selector: 'app-role-select',
  imports: [Combobox, ComboboxPopup, ComboboxWidget, Listbox, Option, OverlayModule, Icon],
  templateUrl: './role-select.html',
  styleUrl: './role-select.scss',
})
export class RoleSelect {
  protected readonly session = inject(Session);

  protected readonly options: RoleOption[] = (Object.keys(ROLE_LABELS) as ZooRole[]).map((value) => ({
    value,
    label: ROLE_LABELS[value],
    job: ROLE_JOBS[value],
  }));

  protected readonly expanded = signal(false);
  protected readonly selected = linkedSignal<ZooRole[]>(() => [this.session.role()]);
  protected readonly label = computed(() => ROLE_LABELS[this.session.role()]);

  /** Drops below the trigger, right edges aligned; flips above when there is no room. */
  protected readonly positions: ConnectedPosition[] = [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 6 },
    { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -6 },
  ];

  /** Clicks on the trigger toggle it themselves; anything else outside closes the menu. */
  protected closeFromOutside(event: MouseEvent, trigger: HTMLElement): void {
    if (!trigger.contains(event.target as Node)) {
      this.expanded.set(false);
    }
  }

  protected commit(): void {
    const role = this.selected()[0];
    if (role && role !== this.session.role()) {
      this.session.setRole(role);
    }
    this.expanded.set(false);
  }
}
