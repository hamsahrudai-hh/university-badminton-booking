import assert from 'node:assert/strict';
import {
  fetchCourts,
  fetchCourtById,
  updateCourtStatus,
  setStoredAuthToken,
  setStoredAuthUser,
  clearStoredAuth,
  ApiError,
  type CourtResponseDto,
  type UpdateCourtStatusRequestDto,
} from '../src/api/client.ts';

console.log('--- Running Admin Court Management Workflow Tests ---');

// 1. Verify UpdateCourtStatusRequestDto typing
{
  const testPayload: UpdateCourtStatusRequestDto = {
    active: false,
  };
  assert.equal(testPayload.active, false);
  console.log('✓ UpdateCourtStatusRequestDto payload structure validated');
}

// 2. Verify fetchCourts queries /api/courts and parses response
{
  const originalFetch = globalThis.fetch;
  const mockCourts: CourtResponseDto[] = [
    { id: 1, courtNumber: 1, active: true },
    { id: 2, courtNumber: 2, active: true },
    { id: 3, courtNumber: 3, active: false },
    { id: 4, courtNumber: 4, active: true },
  ];

  let capturedUrl = '';

  globalThis.fetch = (async (url: string | URL | Request) => {
    capturedUrl = String(url);
    return {
      ok: true,
      status: 200,
      json: async () => mockCourts,
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const result = await fetchCourts();
    assert.equal(capturedUrl, '/api/courts');
    assert.equal(result.length, 4);
    assert.equal(result[0].courtNumber, 1);
    assert.equal(result[0].active, true);
    assert.equal(result[2].active, false);
    console.log('✓ fetchCourts() dispatches GET /api/courts successfully');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

// 3. Verify fetchCourts(true) includes activeOnly=true parameter
{
  const originalFetch = globalThis.fetch;
  const mockActiveCourts: CourtResponseDto[] = [
    { id: 1, courtNumber: 1, active: true },
    { id: 2, courtNumber: 2, active: true },
  ];

  let capturedUrl = '';

  globalThis.fetch = (async (url: string | URL | Request) => {
    capturedUrl = String(url);
    return {
      ok: true,
      status: 200,
      json: async () => mockActiveCourts,
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const result = await fetchCourts(true);
    assert.equal(capturedUrl, '/api/courts?activeOnly=true');
    assert.equal(result.length, 2);
    assert.ok(result.every((c) => c.active));
    console.log('✓ fetchCourts(true) queries /api/courts?activeOnly=true');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

// 4. Verify fetchCourtById queries /api/courts/{id}
{
  const originalFetch = globalThis.fetch;
  const mockCourt: CourtResponseDto = { id: 3, courtNumber: 3, active: true };

  let capturedUrl = '';

  globalThis.fetch = (async (url: string | URL | Request) => {
    capturedUrl = String(url);
    return {
      ok: true,
      status: 200,
      json: async () => mockCourt,
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const result = await fetchCourtById(3);
    assert.equal(capturedUrl, '/api/courts/3');
    assert.equal(result.id, 3);
    assert.equal(result.courtNumber, 3);
    console.log('✓ fetchCourtById(3) queries /api/courts/3');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

// 5. Verify updateCourtStatus performs PATCH with Bearer authorization and payload
{
  const originalFetch = globalThis.fetch;
  clearStoredAuth();
  setStoredAuthToken('ADMIN_STAFF_JWT_TOKEN');
  setStoredAuthUser({
    token: 'ADMIN_STAFF_JWT_TOKEN',
    tokenType: 'Bearer',
    expiresInSeconds: 3600,
    id: 99,
    studentId: 99,
    fullName: 'Admin Marshal',
    email: 'admin@vitapstudent.ac.in',
    universityEmail: 'admin@vitapstudent.ac.in',
    admissionNumber: 'ADMIN001',
    role: 'ADMIN',
  });

  let capturedUrl = '';
  let capturedMethod = '';
  let capturedHeaders: Record<string, string> = {};
  let capturedBody = '';

  const mockUpdated: CourtResponseDto = { id: 2, courtNumber: 2, active: false };

  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    capturedUrl = String(url);
    capturedMethod = init?.method || 'GET';
    capturedHeaders = (init?.headers || {}) as Record<string, string>;
    capturedBody = String(init?.body || '');
    return {
      ok: true,
      status: 200,
      json: async () => mockUpdated,
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const result = await updateCourtStatus(2, false);
    assert.equal(capturedUrl, '/api/courts/2/status');
    assert.equal(capturedMethod, 'PATCH');
    assert.equal(capturedHeaders['Authorization'], 'Bearer ADMIN_STAFF_JWT_TOKEN');
    assert.equal(capturedHeaders['Content-Type'], 'application/json');
    assert.deepEqual(JSON.parse(capturedBody), { active: false });
    assert.equal(result.id, 2);
    assert.equal(result.active, false);
    console.log('✓ updateCourtStatus(2, false) sends PATCH with JWT and payload');
  } finally {
    clearStoredAuth();
    globalThis.fetch = originalFetch;
  }
}

// 6. Verify error propagation for 404 Not Found (invalid court ID)
{
  const originalFetch = globalThis.fetch;

  globalThis.fetch = (async () => {
    return {
      ok: false,
      status: 404,
      statusText: 'Not Found',
      json: async () => ({ error: 'Court not found with ID: 999' }),
    } as unknown as Response;
  }) as typeof fetch;

  try {
    let thrownError: ApiError | null = null;
    try {
      await updateCourtStatus(999, false);
    } catch (err) {
      if (err instanceof ApiError) {
        thrownError = err;
      }
    }
    assert.ok(thrownError, 'ApiError should be thrown');
    assert.equal(thrownError.status, 404);
    assert.equal(thrownError.message, 'Court not found with ID: 999');
    console.log('✓ updateCourtStatus correctly handles 404 Not Found for non-existent court');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

// 7. Verify error propagation for 409 Conflict (invalid state transition)
{
  const originalFetch = globalThis.fetch;

  globalThis.fetch = (async () => {
    return {
      ok: false,
      status: 409,
      statusText: 'Conflict',
      json: async () => ({ error: 'Court 1 is already active.' }),
    } as unknown as Response;
  }) as typeof fetch;

  try {
    let thrownError: ApiError | null = null;
    try {
      await updateCourtStatus(1, true);
    } catch (err) {
      if (err instanceof ApiError) {
        thrownError = err;
      }
    }
    assert.ok(thrownError, 'ApiError should be thrown on duplicate state');
    assert.equal(thrownError.status, 409);
    assert.equal(thrownError.message, 'Court 1 is already active.');
    console.log('✓ updateCourtStatus correctly handles 409 Conflict for duplicate state');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

// 8. Verify error propagation for 403 Forbidden (student or non-staff)
{
  const originalFetch = globalThis.fetch;

  globalThis.fetch = (async () => {
    return {
      ok: false,
      status: 403,
      statusText: 'Forbidden',
      json: async () => ({ error: 'Access denied: insufficient permissions.' }),
    } as unknown as Response;
  }) as typeof fetch;

  try {
    let thrownError: ApiError | null = null;
    try {
      await updateCourtStatus(1, false);
    } catch (err) {
      if (err instanceof ApiError) {
        thrownError = err;
      }
    }
    assert.ok(thrownError, 'ApiError should be thrown on forbidden action');
    assert.equal(thrownError.status, 403);
    assert.equal(thrownError.message, 'Access denied: insufficient permissions.');
    console.log('✓ updateCourtStatus correctly handles 403 Forbidden for unauthorized roles');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

console.log('--- All Admin Court Management Workflow Tests Passed! ---');
