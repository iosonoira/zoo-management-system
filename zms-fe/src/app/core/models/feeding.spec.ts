import { PLAN_TRANSITIONS, PlanStatus, canPlanTransitionTo } from './feeding';

describe('feeding', () => {
  describe('plan transitions', () => {
    it('allows ACTIVE to transition to SUSPENDED', () => {
      expect(canPlanTransitionTo({ status: 'ACTIVE' }, 'SUSPENDED')).toBe(true);
    });

    it('allows ACTIVE to transition to ENDED', () => {
      expect(canPlanTransitionTo({ status: 'ACTIVE' }, 'ENDED')).toBe(true);
    });

    it('disallows ACTIVE to transition to ACTIVE', () => {
      expect(canPlanTransitionTo({ status: 'ACTIVE' }, 'ACTIVE')).toBe(false);
    });

    it('disallows ACTIVE to stay ACTIVE', () => {
      expect(canPlanTransitionTo({ status: 'ACTIVE' }, 'ACTIVE')).toBe(false);
    });

    it('allows SUSPENDED to transition to ACTIVE', () => {
      expect(canPlanTransitionTo({ status: 'SUSPENDED' }, 'ACTIVE')).toBe(true);
    });

    it('allows SUSPENDED to transition to ENDED', () => {
      expect(canPlanTransitionTo({ status: 'SUSPENDED' }, 'ENDED')).toBe(true);
    });

    it('disallows SUSPENDED to transition to SUSPENDED', () => {
      expect(canPlanTransitionTo({ status: 'SUSPENDED' }, 'SUSPENDED')).toBe(false);
    });

    it('disallows SUSPENDED to stay SUSPENDED', () => {
      expect(canPlanTransitionTo({ status: 'SUSPENDED' }, 'SUSPENDED')).toBe(false);
    });

    it('disallows ENDED to transition anywhere', () => {
      expect(canPlanTransitionTo({ status: 'ENDED' }, 'ACTIVE')).toBe(false);
      expect(canPlanTransitionTo({ status: 'ENDED' }, 'SUSPENDED')).toBe(false);
      expect(canPlanTransitionTo({ status: 'ENDED' }, 'ENDED')).toBe(false);
    });

    it('defines transitions for every status', () => {
      const statuses: PlanStatus[] = ['ACTIVE', 'SUSPENDED', 'ENDED'];
      for (const status of statuses) {
        expect(PLAN_TRANSITIONS).toHaveProperty(status);
      }
    });
  });
});
