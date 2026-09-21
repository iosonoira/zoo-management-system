import { EnvironmentProviders, Provider, inject } from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { CanActivateFn, UrlTree } from '@angular/router';
import Keycloak from 'keycloak-js';
import {
  AuthGuardData,
  AutoRefreshTokenService,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  IncludeBearerTokenCondition,
  UserActivityService,
  createAuthGuard,
  createInterceptorCondition,
  includeBearerTokenInterceptor,
  provideKeycloak,
  withAutoRefreshToken,
} from 'keycloak-angular';
import { environment } from '../../../environments/environment';
import { KeycloakSession } from '../session/keycloak-session';
import { Session } from '../session/session';

/**
 * Attach the bearer token to `animal-service` and to nothing else. Anchored at both
 * ends so a host merely starting with the API origin cannot match.
 */
const apiCondition = createInterceptorCondition<IncludeBearerTokenCondition>({
  urlPattern: new RegExp(`^${escapeForRegExp(environment.apiBaseUrl)}(/.*)?$`, 'i'),
  bearerPrefix: 'Bearer',
});

function escapeForRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Browser-only providers. They must not reach `app.config.ts`, because
 * `app.config.server.ts` merges it and `keycloak-js` touches `window`.
 */
export function keycloakProviders(): (Provider | EnvironmentProviders)[] {
  const config = environment.keycloak;
  if (!config) {
    throw new Error('keycloakProviders() called without a keycloak configuration.');
  }
  return [
    provideKeycloak({
      config,
      initOptions: {
        // check-sso, not login-required: an anonymous visitor reaches the shell and the
        // route guard is what asks them to sign in.
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        redirectUri: `${window.location.origin}/`,
      },
      features: [
        // Refreshes the token before it expires while the user is active, so a keeper
        // mid-shift does not meet a 401. The 401 copy remains the terminal fallback for
        // a session that ended server-side.
        withAutoRefreshToken({ onInactivityTimeout: 'logout', sessionTimeout: 300000 }),
      ],
      providers: [AutoRefreshTokenService, UserActivityService],
    }),
    { provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG, useValue: [apiCondition] },
    provideHttpClient(withFetch(), withInterceptors([includeBearerTokenInterceptor])),
    { provide: Session, useClass: KeycloakSession },
  ];
}

const isSignedIn = async (
  _route: unknown,
  _state: unknown,
  authData: AuthGuardData,
): Promise<boolean | UrlTree> => {
  if (authData.authenticated) {
    return true;
  }
  // Any zoo role may read the roster; the backend enforces the rest per endpoint.
  await inject(Keycloak).login({ redirectUri: window.location.href });
  return false;
};

export const animalRouteGuard = createAuthGuard<CanActivateFn>(isSignedIn);
