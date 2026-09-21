/** Build-time mode flag. Replaced by `environment.live.ts` in the `live` configuration. */
export const environment = {
  live: false,
  apiBaseUrl: '',
  keycloak: null,
} as const satisfies Environment;

export interface Environment {
  /** True when the app talks to a running backend instead of the in-memory mock. */
  readonly live: boolean;
  /** Origin of `animal-service`. Empty in demo mode; the bearer token is scoped to it. */
  readonly apiBaseUrl: string;
  readonly keycloak: { readonly url: string; readonly realm: string; readonly clientId: string } | null;
}
