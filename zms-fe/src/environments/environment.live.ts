// Fallback if the type import cannot survive file replacement — duplicate the shape.
export interface Environment {
  readonly live: boolean;
  readonly apiBaseUrl: string;
  readonly keycloak: { readonly url: string; readonly realm: string; readonly clientId: string } | null;
}

export const environment = {
  live: true,
  apiBaseUrl: 'http://localhost:8080',
  keycloak: {
    url: 'http://localhost:8081',
    realm: 'zoo',
    clientId: 'zms-fe',
  },
} as const satisfies Environment;
