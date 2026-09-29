import { ZooRole } from './animal';

/**
 * Same role matrix as the `@RolesAllowed` annotations on the write endpoints of
 * `AnimalResource` (animal-service), `MedicalRecordResource` and `TreatmentResource`
 * (health-service) and `FeedingPlanResource` (feeding-service). Reads are open to every
 * zoo role in all three services, so they need no entry.
 */
export const PERMISSIONS = {
  updateStatus: ['zoo-vet', 'zoo-admin'],
  transfer: ['zoo-keeper', 'zoo-admin'],
  register: ['zoo-admin'],
  createMedicalRecord: ['zoo-vet', 'zoo-admin'],
  prescribeTreatment: ['zoo-vet', 'zoo-admin'],
  updateTreatmentStatus: ['zoo-vet', 'zoo-admin'],
  createFeedingPlan: ['zoo-vet', 'zoo-admin'],
  updateFeedingPlanStatus: ['zoo-vet', 'zoo-admin'],
  recordFeeding: ['zoo-keeper', 'zoo-admin'],
} as const satisfies Record<string, readonly ZooRole[]>;

export type Permission = keyof typeof PERMISSIONS;

export function can(role: ZooRole, permission: Permission): boolean {
  return (PERMISSIONS[permission] as readonly ZooRole[]).includes(role);
}
