import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import Keycloak from 'keycloak-js';
import {
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  includeBearerTokenInterceptor,
} from 'keycloak-angular';
import { firstValueFrom } from 'rxjs';
import { environment as live } from '../../../environments/environment.live';
import { bearerTokenConditions, signedInGuard } from './keycloak-providers';

// The live origins, read from the file the `live` build actually ships.
const ORIGINS = Object.values(live.api);

function matches(url: string): boolean {
  return bearerTokenConditions(ORIGINS).some((condition) => condition.urlPattern.test(url));
}

describe('bearerTokenConditions', () => {
  it('covers every backend service of the live build', () => {
    expect(ORIGINS).toEqual([
      'http://localhost:8080',
      'http://localhost:8082',
      'http://localhost:8084',
      'http://localhost:8083',
    ]);
  });

  it('matches each service origin and any path below it', () => {
    expect(matches('http://localhost:8080/animals?page=0&size=100')).toBe(true);
    expect(matches('http://localhost:8080/enclosures')).toBe(true);
    expect(matches('http://localhost:8082/medical-records?animalId=7f3a')).toBe(true);
    expect(matches('http://localhost:8082/treatments/1/status')).toBe(true);
    expect(matches('http://localhost:8084/feeding-plans/1/feedings')).toBe(true);
    expect(matches('http://localhost:8084')).toBe(true);
    expect(matches('http://localhost:8083/notifications?open=true')).toBe(true);
    expect(matches('http://localhost:8083/notifications/1/acknowledge')).toBe(true);
  });

  it('never matches Keycloak, the app itself or another port', () => {
    expect(matches(`${live.keycloak.url}/realms/zoo/protocol/openid-connect/token`)).toBe(false);
    expect(matches('http://localhost:4200/animals')).toBe(false);
    expect(matches('http://localhost:8085/notifications')).toBe(false);
    expect(matches('/animals')).toBe(false);
  });

  it('never matches a host that merely starts with a service origin', () => {
    expect(matches('http://localhost:80800/animals')).toBe(false);
    expect(matches('http://localhost:8080.evil.test/animals')).toBe(false);
    expect(matches('http://localhost:8082@evil.test/medical-records')).toBe(false);
  });

  it('refuses an empty origin, which would match every relative URL', () => {
    expect(() => bearerTokenConditions(['http://localhost:8080', ''])).toThrow();
  });
});

describe('includeBearerTokenInterceptor with the live conditions', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    const keycloak = { authenticated: true, token: 'test-token', updateToken: async () => true };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([includeBearerTokenInterceptor])),
        provideHttpClientTesting(),
        { provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG, useValue: bearerTokenConditions(ORIGINS) },
        { provide: Keycloak, useValue: keycloak },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it.each([
    'http://localhost:8080/animals',
    'http://localhost:8082/medical-records',
    'http://localhost:8084/feeding-plans',
    'http://localhost:8083/notifications',
  ])('sends the bearer token to %s', async (url) => {
    const pending = firstValueFrom(http.get(url));
    // The interceptor refreshes the token asynchronously before sending.
    const request = await vi.waitFor(() => backend.expectOne(url));
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
    request.flush({});
    await pending;
  });

  it('sends nothing to Keycloak', async () => {
    const url = `${live.keycloak.url}/realms/zoo/account`;
    const pending = firstValueFrom(http.get(url));
    const request = backend.expectOne(url);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
    await pending;
  });
});

describe('signedInGuard', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;

  function runGuard(keycloak: Partial<Keycloak>): Promise<unknown> {
    TestBed.configureTestingModule({ providers: [{ provide: Keycloak, useValue: keycloak }] });
    return TestBed.runInInjectionContext(() => signedInGuard(route, state) as Promise<unknown>);
  }

  it('lets a signed-in user through without asking to sign in', async () => {
    const login = vi.fn();
    await expect(runGuard({ authenticated: true, login })).resolves.toBe(true);
    expect(login).not.toHaveBeenCalled();
  });

  it('sends an anonymous visitor to Keycloak and back to the same page', async () => {
    const login = vi.fn(async () => undefined);
    await expect(runGuard({ authenticated: false, login })).resolves.toBe(false);
    expect(login).toHaveBeenCalledWith({ redirectUri: window.location.href });
  });
});
