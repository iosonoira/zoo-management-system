import { computed, inject } from '@angular/core';
import Keycloak from 'keycloak-js';
import { KEYCLOAK_EVENT_SIGNAL } from 'keycloak-angular';
import { ZooRole } from '../models/animal';
import { Permission, can } from '../models/permissions';
import { Session } from './session';

/** Widest first: the UI shows one role, a Keycloak user may hold several. */
const ROLE_PRECEDENCE: readonly ZooRole[] = ['zoo-admin', 'zoo-vet', 'zoo-keeper'];

/**
 * Reduces `realm_access.roles` to the one role the UI works with. A user with no zoo
 * role at all is treated as a keeper, the least privileged: the backend rejects
 * anything they are not entitled to anyway, and the permission matrix only decides
 * which controls are offered.
 */
export function rolesFrom(roles: readonly string[] | undefined): ZooRole {
  return ROLE_PRECEDENCE.find((role) => roles?.includes(role)) ?? 'zoo-keeper';
}

interface ZooToken {
  readonly preferred_username?: string;
  readonly realm_access?: { readonly roles?: readonly string[] };
}

/** Live session: role and username come from the access token, never from the UI. */
export class KeycloakSession extends Session {
  private readonly keycloak = inject(Keycloak);
  /** Re-reads the token whenever keycloak-angular emits (ready, refresh, logout). */
  private readonly events = inject(KEYCLOAK_EVENT_SIGNAL);

  private readonly token = computed<ZooToken>(() => {
    this.events();
    return (this.keycloak.tokenParsed ?? {}) as ZooToken;
  });

  readonly role = computed(() => rolesFrom(this.token().realm_access?.roles));
  readonly username = computed(() => this.token().preferred_username ?? 'unknown');
  readonly canSwitchRole = false;

  can(permission: Permission): boolean {
    return can(this.role(), permission);
  }

  setRole(): void {
    throw new Error('The role comes from the access token and cannot be switched.');
  }

  /** Nothing to restore: the token is the source of truth. */
  restore(): void {}

  signOut(): void {
    void this.keycloak.logout({ redirectUri: window.location.origin });
  }
}
