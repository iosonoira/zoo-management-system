import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import Keycloak from 'keycloak-js';
import { KEYCLOAK_EVENT_SIGNAL, KeycloakEvent, KeycloakEventType } from 'keycloak-angular';
import { KeycloakSession, rolesFrom } from './keycloak-session';

describe('rolesFrom', () => {
  it('reads the single zoo role from the realm roles', () => {
    expect(rolesFrom(['zoo-keeper'])).toBe('zoo-keeper');
    expect(rolesFrom(['zoo-vet'])).toBe('zoo-vet');
    expect(rolesFrom(['zoo-admin'])).toBe('zoo-admin');
  });

  it('ignores realm roles that are not zoo roles', () => {
    expect(rolesFrom(['offline_access', 'default-roles-zoo', 'zoo-vet'])).toBe('zoo-vet');
  });

  it('prefers the widest role when a user holds several', () => {
    expect(rolesFrom(['zoo-keeper', 'zoo-admin'])).toBe('zoo-admin');
    expect(rolesFrom(['zoo-keeper', 'zoo-vet'])).toBe('zoo-vet');
  });

  it('falls back to the narrowest role when none is present', () => {
    expect(rolesFrom([])).toBe('zoo-keeper');
    expect(rolesFrom(undefined)).toBe('zoo-keeper');
  });
});

describe('KeycloakSession', () => {
  interface FakeKeycloak {
    tokenParsed?: { preferred_username?: string; realm_access?: { roles?: string[] } };
    logout: ReturnType<typeof vi.fn>;
  }

  function setUp(tokenParsed: FakeKeycloak['tokenParsed']) {
    const keycloak: FakeKeycloak = { tokenParsed, logout: vi.fn(async () => undefined) };
    const events = signal<KeycloakEvent>({ type: KeycloakEventType.Ready });
    TestBed.configureTestingModule({
      providers: [
        { provide: Keycloak, useValue: keycloak },
        { provide: KEYCLOAK_EVENT_SIGNAL, useValue: events },
      ],
    });
    const session = TestBed.runInInjectionContext(() => new KeycloakSession());
    return { session, keycloak, events };
  }

  it('takes the username and the role from the access token', () => {
    const { session } = setUp({
      preferred_username: 'vet.bianchi',
      realm_access: { roles: ['default-roles-zoo', 'zoo-vet'] },
    });
    expect(session.username()).toBe('vet.bianchi');
    expect(session.role()).toBe('zoo-vet');
    expect(session.canSwitchRole).toBe(false);
  });

  it('answers permissions from the token role', () => {
    const { session } = setUp({ realm_access: { roles: ['zoo-keeper'] } });
    expect(session.can('recordFeeding')).toBe(true);
    expect(session.can('transfer')).toBe(true);
    expect(session.can('prescribeTreatment')).toBe(false);
    expect(session.can('createFeedingPlan')).toBe(false);
  });

  it('re-reads the token when keycloak-angular emits an event', () => {
    const { session, keycloak, events } = setUp({
      preferred_username: 'keeper.conti',
      realm_access: { roles: ['zoo-keeper'] },
    });
    expect(session.role()).toBe('zoo-keeper');

    keycloak.tokenParsed = { preferred_username: 'keeper.conti', realm_access: { roles: ['zoo-admin'] } };
    events.set({ type: KeycloakEventType.AuthRefreshSuccess });

    expect(session.role()).toBe('zoo-admin');
  });

  it('treats a missing token as an unknown keeper', () => {
    const { session } = setUp(undefined);
    expect(session.username()).toBe('unknown');
    expect(session.role()).toBe('zoo-keeper');
  });

  it('never lets the UI switch the role', () => {
    const { session } = setUp({ realm_access: { roles: ['zoo-keeper'] } });
    expect(() => session.setRole()).toThrow();
  });

  it('signs out through Keycloak and returns to the app origin', () => {
    const { session, keycloak } = setUp({ realm_access: { roles: ['zoo-vet'] } });
    session.signOut();
    expect(keycloak.logout).toHaveBeenCalledWith({ redirectUri: window.location.origin });
  });
});
