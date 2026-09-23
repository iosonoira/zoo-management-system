import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { Animal, NewAnimal } from '../models/animal';
import { AnimalApi, ApiError } from './animal-api';
import { HttpAnimalApi, PAGE_SIZE } from './http-animal-api';

// The suite builds with the demo `environment.ts`, so `apiBaseUrl` is empty and the
// adapter's paths are origin-relative. Reading it back keeps the expectations true of
// whichever environment a run is built with.
const BASE = environment.apiBaseUrl;

function animal(overrides: Partial<Animal> = {}): Animal {
  return {
    id: '7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41',
    name: 'Kibo',
    species: 'Reticulated giraffe',
    dangerous: false,
    habitat: 'TERRESTRIAL',
    enclosureId: '0b6e2f1a-3c4d-4e8f-9a1b-2c3d4e5f6a70',
    arrivalDate: '2019-04-11',
    status: 'HEALTHY',
    createdBy: 'admin.rossi',
    updatedBy: 'keeper.conti',
    ...overrides,
  };
}

function newAnimal(overrides: Partial<NewAnimal> = {}): NewAnimal {
  return {
    name: 'Kibo',
    species: 'Reticulated giraffe',
    dangerous: false,
    habitat: 'TERRESTRIAL',
    enclosureId: '0b6e2f1a-3c4d-4e8f-9a1b-2c3d4e5f6a70',
    arrivalDate: '2019-04-11',
    ...overrides,
  };
}

describe('HttpAnimalApi', () => {
  let api: AnimalApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AnimalApi, useClass: HttpAnimalApi },
      ],
    });
    api = TestBed.inject(AnimalApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for the first page at the server maximum', async () => {
    const pending = api.listAll();
    const request = http.expectOne(`${BASE}/animals?page=0&size=${PAGE_SIZE}`);
    expect(request.request.method).toBe('GET');
    request.flush({ items: [animal()], page: 0, size: PAGE_SIZE, total: 1 });
    await expect(pending).resolves.toEqual([animal()]);
  });

  it('keeps paging until it has the whole roster', async () => {
    const first = Array.from({ length: PAGE_SIZE }, (_, i) =>
      animal({ id: `page0-${i}`, name: `A${i}` }),
    );
    const pending = api.listAll();

    http
      .expectOne(`${BASE}/animals?page=0&size=${PAGE_SIZE}`)
      .flush({ items: first, page: 0, size: PAGE_SIZE, total: PAGE_SIZE + 2 });

    const second = [animal({ id: 'page1-0' }), animal({ id: 'page1-1' })];
    // The next page is only requested once the first response has been awaited.
    const request = await vi.waitFor(() =>
      http.expectOne(`${BASE}/animals?page=1&size=${PAGE_SIZE}`),
    );
    request.flush({ items: second, page: 1, size: PAGE_SIZE, total: PAGE_SIZE + 2 });

    await expect(pending).resolves.toHaveLength(PAGE_SIZE + 2);
  });

  it('stops paging when a page comes back short', async () => {
    const pending = api.listAll();
    http
      .expectOne(`${BASE}/animals?page=0&size=${PAGE_SIZE}`)
      .flush({ items: [animal()], page: 0, size: PAGE_SIZE, total: 999 });
    await expect(pending).resolves.toHaveLength(1);
  });

  it('reads one animal by id', async () => {
    const pending = api.getById('7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41');
    const request = http.expectOne(`${BASE}/animals/7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41`);
    expect(request.request.method).toBe('GET');
    request.flush(animal());
    await expect(pending).resolves.toEqual(animal());
  });

  it('sends the status as the backend expects it', async () => {
    const pending = api.updateStatus('7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41', 'IN_TREATMENT');
    const request = http.expectOne(
      `${BASE}/animals/7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41/status`,
    );
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ status: 'IN_TREATMENT' });
    request.flush(animal({ status: 'IN_TREATMENT' }));
    await expect(pending).resolves.toEqual(animal({ status: 'IN_TREATMENT' }));
  });

  it('sends the transfer target as the backend expects it', async () => {
    const target = '1c7f3a2b-4d5e-4f90-8b2c-3d4e5f6a7b81';
    const pending = api.transfer('7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41', target);
    const request = http.expectOne(
      `${BASE}/animals/7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41/transfer`,
    );
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ targetEnclosureId: target });
    request.flush(animal({ enclosureId: target }));
    await expect(pending).resolves.toEqual(animal({ enclosureId: target }));
  });

  it('issues GET /enclosures and returns the body', async () => {
    const pending = api.listEnclosures();
    const request = http.expectOne(`${BASE}/enclosures`);
    expect(request.request.method).toBe('GET');
    request.flush([
      {
        id: '0b6e2f1a-3c4d-4e8f-9a1b-2c3d4e5f6a70',
        name: 'Savanna Paddock',
        habitat: 'TERRESTRIAL',
      },
    ]);
    await expect(pending).resolves.toContainEqual({
      id: '0b6e2f1a-3c4d-4e8f-9a1b-2c3d4e5f6a70',
      name: 'Savanna Paddock',
      habitat: 'TERRESTRIAL',
    });
  });

  it('maps 403 to the role copy rather than the backend string', async () => {
    const pending = api.updateStatus('7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41', 'HEALTHY');
    http
      .expectOne(`${BASE}/animals/7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41/status`)
      .flush({ message: 'Insufficient role' }, { status: 403, statusText: 'Forbidden' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error).toBeInstanceOf(ApiError);
      expect(error.status).toBe(403);
      expect(error.message).toContain('vets');
      expect(error.message).not.toContain('Insufficient role');
      return true;
    });
  });

  it('maps 404 to the missing-animal copy', async () => {
    const pending = api.getById('missing');
    http
      .expectOne(`${BASE}/animals/missing`)
      .flush({ message: 'Animal not found: missing' }, { status: 404, statusText: 'Not Found' });
    await expect(pending).rejects.toMatchObject({ status: 404 });
  });

  it('maps 409 to the conflict copy and names the animal', async () => {
    const pending = api.transfer(
      '7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41',
      '1c7f3a2b-4d5e-4f90-8b2c-3d4e5f6a7b81',
      'Kibo',
    );
    http
      .expectOne(`${BASE}/animals/7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41/transfer`)
      .flush({ message: 'Concurrent update' }, { status: 409, statusText: 'Conflict' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(409);
      expect(error.message).toContain('Kibo');
      return true;
    });
  });

  it('maps 401 to the expired-session copy', async () => {
    const pending = api.getById('7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41');
    http
      .expectOne(`${BASE}/animals/7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41`)
      .flush({ message: 'Authentication required' }, { status: 401, statusText: 'Unauthorized' });
    await expect(pending).rejects.toMatchObject({ status: 401 });
  });

  it('maps 422 to the transition copy', async () => {
    const pending = api.updateStatus(
      '7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41',
      'HEALTHY',
      'Kibo',
    );
    http
      .expectOne(`${BASE}/animals/7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41/status`)
      .flush({ message: 'Same status' }, { status: 422, statusText: 'Unprocessable Entity' });
    await expect(pending).rejects.toMatchObject({ status: 422 });
  });

  it('maps an unreachable backend to the generic failure', async () => {
    const pending = api.listAll();
    http
      .expectOne(`${BASE}/animals?page=0&size=${PAGE_SIZE}`)
      .error(new ProgressEvent('network error'));
    await expect(pending).rejects.toMatchObject({ status: 500 });
  });

  it('sends register POST to /animals with the body', async () => {
    const input = newAnimal();
    const pending = api.register(input);
    const request = http.expectOne(`${BASE}/animals`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(input);
    const returned = animal({ name: 'Simba' });
    request.flush(returned);
    await expect(pending).resolves.toEqual(returned);
  });

  it('maps register 400 to the invalidAnimal message', async () => {
    const pending = api.register(newAnimal());
    http
      .expectOne(`${BASE}/animals`)
      .flush(
        { message: 'Invalid data' },
        { status: 400, statusText: 'Bad Request' },
      );
    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(400);
      expect(error.message).toContain('details');
      return true;
    });
  });

  it('maps register 403 to the admin-only copy', async () => {
    const pending = api.register(newAnimal());
    http
      .expectOne(`${BASE}/animals`)
      .flush(
        { message: 'Insufficient role' },
        { status: 403, statusText: 'Forbidden' },
      );
    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(403);
      expect(error.message).toContain('admins');
      return true;
    });
  });
});
