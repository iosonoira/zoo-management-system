import { rolesFrom } from './keycloak-session';

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
