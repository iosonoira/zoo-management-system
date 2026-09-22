import { Signal } from '@angular/core';
import { ZooRole } from '../models/animal';
import { Permission } from '../models/permissions';

/**
 * Port for the signed-in user. `DemoSession` holds a role picked from the header
 * switcher; `KeycloakSession` reads it from the access token. Components depend on
 * this shape only.
 */
export abstract class Session {
  /** The active role. The UI is built around one role at a time. */
  abstract readonly role: Signal<ZooRole>;
  /** What the backend records in `createdBy` / `updatedBy`. */
  abstract readonly username: Signal<string>;
  /** False when the role comes from a token and the header shows an account control. */
  abstract readonly canSwitchRole: boolean;

  abstract can(permission: Permission): boolean;
  /** Throws when `canSwitchRole` is false. */
  abstract setRole(role: ZooRole): void;
  /** Call from the browser only, after hydration. */
  abstract restore(): void;
  abstract signOut(): void;
}
