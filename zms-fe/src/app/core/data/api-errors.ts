import { ApiError } from './animal-api';

/**
 * The single source of user-facing copy for backend failures. `animal-service` returns
 * `{"message": "..."}` on every error, but those strings are either generic
 * ("Insufficient role") or technical ("Cannot transfer a deceased animal"), so the
 * adapters map the status code to the copy below instead of forwarding the body.
 */

export function forbidden(action: 'updateStatus' | 'transfer'): ApiError {
  return action === 'updateStatus'
    ? new ApiError(403, 'Only vets and admins can change an animal’s status.')
    : new ApiError(403, 'Only keepers and admins can transfer animals.');
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
