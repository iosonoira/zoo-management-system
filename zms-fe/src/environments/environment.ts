/** Build-time mode flag. Replaced by `environment.live.ts` in the `live` configuration. */
export const environment = {
  live: false,
  api: { animal: '', health: '', feeding: '' },
  keycloak: null,
} as const satisfies Environment;

export interface Environment {
  /** True when the app talks to a running backend instead of the in-memory mock. */
  readonly live: boolean;
  /**
   * Origin of each backend service. Empty in demo mode. The bearer token is attached to
   * these origins and to nothing else.
   */
  readonly api: { readonly animal: string; readonly health: string; readonly feeding: string };
  readonly keycloak: { readonly url: string; readonly realm: string; readonly clientId: string } | null;
}
