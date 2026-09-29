import { Permission } from '../models/permissions';
import { ApiError } from './api-error';

/**
 * The single source of user-facing copy for backend failures. The backend services return
 * `{"message": "..."}` on their own errors, but those strings are either generic
 * ("Insufficient role") or technical ("Cannot transfer a deceased animal"), so the
 * adapters map the status code to the copy below instead of forwarding the body.
 */

export function forbidden(permission: Permission): ApiError {
  switch (permission) {
    case 'updateStatus':
      return new ApiError(403, 'Only vets and admins can change an animal’s status.');
    case 'transfer':
      return new ApiError(403, 'Only keepers and admins can transfer animals.');
    case 'register':
      return new ApiError(403, 'Only admins can register animals.');
    case 'createMedicalRecord':
      return new ApiError(403, 'Only vets and admins can add medical records.');
    case 'prescribeTreatment':
      return new ApiError(403, 'Only vets and admins can prescribe treatments.');
    case 'updateTreatmentStatus':
      return new ApiError(403, 'Only vets and admins can change a treatment’s status.');
    case 'createFeedingPlan':
      return new ApiError(403, 'Only vets and admins can create feeding plans.');
    case 'updateFeedingPlanStatus':
      return new ApiError(403, 'Only vets and admins can change a feeding plan’s status.');
    case 'recordFeeding':
      return new ApiError(403, 'Only keepers and admins can record feedings.');
  }
}

export function notFound(): ApiError {
  return new ApiError(404, 'No animal with this tag exists.');
}

export function sameStatus(name: string): ApiError {
  return new ApiError(422, `${name} already has this status.`);
}

export function deceasedStatus(name: string): ApiError {
  return new ApiError(422, `${name} is recorded as deceased. The status can no longer change.`);
}

export function deceasedTransfer(name: string): ApiError {
  return new ApiError(400, `${name} is recorded as deceased and can’t be transferred.`);
}

export function unknownEnclosure(): ApiError {
  return new ApiError(400, 'That enclosure doesn’t exist.');
}

export function invalidAnimal(): ApiError {
  return new ApiError(400, 'Some details aren’t valid. Check the name, species and enclosure, then try again.');
}

/** Optimistic locking lost: another writer saved first. The mock cannot produce this. */
export function conflict(name: string): ApiError {
  return new ApiError(409, `Someone else updated ${name} first. Reload to see the latest record.`);
}

export function sessionExpired(): ApiError {
  return new ApiError(401, 'Your session expired. Sign in again to continue.');
}

export function serverError(): ApiError {
  return new ApiError(500, 'Something went wrong on our side. Check your connection and try again.');
}

// ---- health-service ----

export function recordNotFound(): ApiError {
  return new ApiError(404, 'This medical record no longer exists.');
}

export function treatmentNotFound(): ApiError {
  return new ApiError(404, 'This treatment no longer exists.');
}

export function invalidRecord(): ApiError {
  return new ApiError(
    400,
    'Some details aren’t valid. Check the reason, diagnosis, examination date and vet, then try again.',
  );
}

export function invalidTreatment(): ApiError {
  return new ApiError(400, 'Describe the treatment in 500 characters or fewer, then try again.');
}

export function treatmentChanged(): ApiError {
  return new ApiError(422, 'This treatment changed since you opened it. Reload to see where it stands.');
}
