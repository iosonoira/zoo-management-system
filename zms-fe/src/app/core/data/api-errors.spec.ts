import { ApiError } from './animal-api';
import {
  conflict,
  deceasedStatus,
  deceasedTransfer,
  forbidden,
  notFound,
  sameStatus,
  serverError,
  sessionExpired,
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
});
