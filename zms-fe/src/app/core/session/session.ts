import { Service, computed, signal } from '@angular/core';
import { ZooRole } from '../models/animal';
import { Permission, can } from '../models/permissions';

const DEMO_USERS: Record<ZooRole, string> = {
  'zoo-keeper': 'keeper.conti',
  'zoo-vet': 'vet.bianchi',
  'zoo-admin': 'admin.rossi',
};

const STORAGE_KEY = 'zms.demo-role';

/**
 * Demo stand-in for the Keycloak session: holds the active role and the
 * username that the backend would read from the token principal.
 */
@Service()
export class Session {
  readonly role = signal<ZooRole>('zoo-keeper');
  readonly username = computed(() => DEMO_USERS[this.role()]);

  can(permission: Permission): boolean {
    return can(this.role(), permission);
  }

  setRole(role: ZooRole): void {
    this.role.set(role);
    try {
      localStorage.setItem(STORAGE_KEY, role);
    } catch {
      // Storage unavailable (private mode, blocked site data): role stays in memory.
    }
  }

  /** Call from the browser only, after hydration. */
  restore(): void {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored && stored in DEMO_USERS) {
        this.role.set(stored as ZooRole);
      }
    } catch {
      // Ignore unavailable storage.
    }
  }
}
