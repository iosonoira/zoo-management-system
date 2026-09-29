import { TREATMENT_TRANSITIONS, TreatmentStatus, canTreatmentTransitionTo } from './health';

describe('health', () => {
  describe('treatment transitions', () => {
    it('allows PRESCRIBED to transition to ACTIVE', () => {
      expect(canTreatmentTransitionTo({ status: 'PRESCRIBED' }, 'ACTIVE')).toBe(true);
    });

    it('allows PRESCRIBED to transition to CANCELLED', () => {
      expect(canTreatmentTransitionTo({ status: 'PRESCRIBED' }, 'CANCELLED')).toBe(true);
    });

    it('disallows PRESCRIBED to transition to COMPLETED', () => {
      expect(canTreatmentTransitionTo({ status: 'PRESCRIBED' }, 'COMPLETED')).toBe(false);
    });

    it('disallows PRESCRIBED to stay PRESCRIBED', () => {
      expect(canTreatmentTransitionTo({ status: 'PRESCRIBED' }, 'PRESCRIBED')).toBe(false);
    });

    it('allows ACTIVE to transition to COMPLETED', () => {
      expect(canTreatmentTransitionTo({ status: 'ACTIVE' }, 'COMPLETED')).toBe(true);
    });

    it('allows ACTIVE to transition to CANCELLED', () => {
      expect(canTreatmentTransitionTo({ status: 'ACTIVE' }, 'CANCELLED')).toBe(true);
    });

    it('disallows ACTIVE to transition to PRESCRIBED', () => {
      expect(canTreatmentTransitionTo({ status: 'ACTIVE' }, 'PRESCRIBED')).toBe(false);
    });

    it('disallows ACTIVE to stay ACTIVE', () => {
      expect(canTreatmentTransitionTo({ status: 'ACTIVE' }, 'ACTIVE')).toBe(false);
    });

    it('disallows COMPLETED to transition anywhere', () => {
      expect(canTreatmentTransitionTo({ status: 'COMPLETED' }, 'ACTIVE')).toBe(false);
      expect(canTreatmentTransitionTo({ status: 'COMPLETED' }, 'CANCELLED')).toBe(false);
      expect(canTreatmentTransitionTo({ status: 'COMPLETED' }, 'PRESCRIBED')).toBe(false);
      expect(canTreatmentTransitionTo({ status: 'COMPLETED' }, 'COMPLETED')).toBe(false);
    });

    it('disallows CANCELLED to transition anywhere', () => {
      expect(canTreatmentTransitionTo({ status: 'CANCELLED' }, 'ACTIVE')).toBe(false);
      expect(canTreatmentTransitionTo({ status: 'CANCELLED' }, 'COMPLETED')).toBe(false);
      expect(canTreatmentTransitionTo({ status: 'CANCELLED' }, 'PRESCRIBED')).toBe(false);
      expect(canTreatmentTransitionTo({ status: 'CANCELLED' }, 'CANCELLED')).toBe(false);
    });

    it('defines transitions for every status', () => {
      const statuses: TreatmentStatus[] = ['PRESCRIBED', 'ACTIVE', 'COMPLETED', 'CANCELLED'];
      for (const status of statuses) {
        expect(TREATMENT_TRANSITIONS).toHaveProperty(status);
      }
    });
  });
});
