/** Every status the backend services can return, including the two the mocks never raise. */
export type ApiErrorStatus = 400 | 401 | 403 | 404 | 409 | 422 | 500;

/** Error shape mirroring the HTTP statuses the backend services return. */
export class ApiError extends Error {
  constructor(
    readonly status: ApiErrorStatus,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}
