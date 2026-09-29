import { ApiError } from './api-error';
import {
  conflict,
  deceasedStatus,
  deceasedTransfer,
  forbidden,
  invalidRecord,
  invalidTreatment,
  notFound,
  recordNotFound,
  sameStatus,
  serverError,
  sessionExpired,
  treatmentChanged,
  treatmentNotFound,
  unknownEnclosure,
} from './api-errors';

describe('api-errors', () => {
  it('carries the status the backend returns', () => {
    expect(forbidden('transfer').status).toBe(403);
    expect(notFound().status).toBe(404);
    expect(sameStatus('Kibo').status).toBe(422);
    expect(deceasedStatus('Bruno').status).toBe(422);
    expect(deceasedTransfer('Bruno').status).toBe(400);
    expect(unknownEnclosure().status).toBe(400);
    expect(conflict('Kibo').status).toBe(409);
    expect(sessionExpired().status).toBe(401);
    expect(serverError().status).toBe(500);
  });

  it('is an ApiError so the store can read the message', () => {
    expect(notFound()).toBeInstanceOf(ApiError);
  });

  it('names the animal when the message is about one', () => {
    expect(conflict('Kibo').message).toContain('Kibo');
    expect(deceasedTransfer('Bruno').message).toContain('Bruno');
    expect(sameStatus('Kibo').message).toContain('Kibo');
  });

  it('tells each role what it may do', () => {
    expect(forbidden('updateStatus').message).toContain('vets');
    expect(forbidden('transfer').message).toContain('keepers');
  });

  it('carries the status for health-service errors', () => {
    expect(recordNotFound().status).toBe(404);
    expect(treatmentNotFound().status).toBe(404);
    expect(invalidRecord().status).toBe(400);
    expect(invalidTreatment().status).toBe(400);
    expect(treatmentChanged().status).toBe(422);
  });

  it('are ApiError instances', () => {
    expect(recordNotFound()).toBeInstanceOf(ApiError);
    expect(treatmentNotFound()).toBeInstanceOf(ApiError);
    expect(invalidRecord()).toBeInstanceOf(ApiError);
    expect(invalidTreatment()).toBeInstanceOf(ApiError);
    expect(treatmentChanged()).toBeInstanceOf(ApiError);
  });
});
