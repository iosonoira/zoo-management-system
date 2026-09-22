import { computed, signal } from '@angular/core';
import { ZooRole } from '../models/animal';
import { Permission, can } from '../models/permissions';
import { Session } from './session';

const DEMO_USERS: Record<ZooRole, string> = {
  'zoo-keeper': 'keeper.conti',
  'zoo-vet': 'vet.bianchi',
  'zoo-admin': 'admin.rossi',
};

const STORAGE_KEY = 'zms.demo-role';

/**
 * Demo stand-in for the Keycloak session: holds the active role and the username
 * that the backend would read from the token principal.
 */
export class DemoSession extends Session {
  private readonly activeRole = signal<ZooRole>('zoo-keeper');

  readonly role = this.activeRole.asReadonly();
  readonly username = computed(() => DEMO_USERS[this.activeRole()]);
  readonly canSwitchRole = true;

  can(permission: Permission): boolean {
    return can(this.activeRole(), permission);
  }

  setRole(role: ZooRole): void {
    this.activeRole.set(role);
    try {
      localStorage.setItem(STORAGE_KEY, role);
    } catch {
      // Storage unavailable (private mode, blocked site data): role stays in memory.
    }
  }

  restore(): void {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored && stored in DEMO_USERS) {
        this.activeRole.set(stored as ZooRole);
      }
    } catch {
      // Ignore unavailable storage.
    }
  }

  /** Nothing to sign out of in demo mode. */
  signOut(): void {}
}
