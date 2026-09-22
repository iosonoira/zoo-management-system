import { TestBed } from '@angular/core/testing';
import { DemoSession } from './demo-session';
import { Session } from './session';

describe('DemoSession', () => {
  let session: Session;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [{ provide: Session, useClass: DemoSession }],
    });
    session = TestBed.inject(Session);
  });

  it('starts as a keeper', () => {
    expect(session.role()).toBe('zoo-keeper');
    expect(session.username()).toBe('keeper.conti');
  });

  it('allows switching roles', () => {
    expect(session.canSwitchRole).toBe(true);
    session.setRole('zoo-vet');
    expect(session.role()).toBe('zoo-vet');
    expect(session.username()).toBe('vet.bianchi');
  });

  it('applies the same permission matrix as the backend', () => {
    session.setRole('zoo-vet');
    expect(session.can('updateStatus')).toBe(true);
    expect(session.can('transfer')).toBe(false);
  });

  it('restores a stored role', () => {
    session.setRole('zoo-admin');
    const fresh = TestBed.runInInjectionContext(() => new DemoSession());
    fresh.restore();
    expect(fresh.role()).toBe('zoo-admin');
  });
});
