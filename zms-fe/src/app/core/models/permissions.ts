import { ZooRole } from './animal';

/** Same role matrix as the `@RolesAllowed` annotations on `AnimalResource`. */
export const PERMISSIONS = {
  updateStatus: ['zoo-vet', 'zoo-admin'],
  transfer: ['zoo-keeper', 'zoo-admin'],
  register: ['zoo-admin'],
} as const satisfies Record<string, readonly ZooRole[]>;

export type Permission = keyof typeof PERMISSIONS;

export function can(role: ZooRole, permission: Permission): boolean {
  return (PERMISSIONS[permission] as readonly ZooRole[]).includes(role);
}
