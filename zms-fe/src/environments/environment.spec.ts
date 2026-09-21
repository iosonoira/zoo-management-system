import { environment } from './environment';
import { ENCLOSURES } from '../app/core/data/enclosure-directory';

describe('environment', () => {
  it('defaults to demo mode', () => {
    expect(environment.live).toBe(false);
    expect(environment.keycloak).toBeNull();
  });
});

describe('ENCLOSURES', () => {
  it('lists every enclosure the demo roster references', () => {
    expect(ENCLOSURES.length).toBe(7);
    expect(ENCLOSURES.map((e) => e.name)).toContain('Quarantine Unit');
  });

  it('has unique ids', () => {
    expect(new Set(ENCLOSURES.map((e) => e.id)).size).toBe(ENCLOSURES.length);
  });
});
