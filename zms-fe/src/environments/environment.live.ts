// Fallback if the type import cannot survive file replacement — duplicate the shape.
export interface Environment {
  readonly live: boolean;
  readonly api: { readonly animal: string; readonly health: string; readonly feeding: string };
  readonly keycloak: { readonly url: string; readonly realm: string; readonly clientId: string } | null;
}

export const environment = {
  live: true,
  api: {
    animal: 'http://localhost:8080',
    health: 'http://localhost:8082',
    feeding: 'http://localhost:8084',
  },
  keycloak: {
    url: 'http://localhost:8081',
    realm: 'zoo',
    clientId: 'zms-fe',
  },
} as const satisfies Environment;
