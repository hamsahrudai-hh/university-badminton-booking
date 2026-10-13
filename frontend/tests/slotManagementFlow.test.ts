import assert from 'node:assert/strict';
import {
  fetchSlots,
  generateSlots,
  setStoredAuthToken,
  setStoredAuthUser,
  clearStoredAuth,
  ApiError,
  type TimeSlotResponseDto,
  type SlotGenerationResponseDto,
} from '../src/api/client.ts';

console.log('--- Running Admin Slot Management & Generation Workflow Tests ---');

// 1. Verify fetchSlots queries /api/slots?date=... and parses response
{
  const originalFetch = globalThis.fetch;
  const mockSlots: TimeSlotResponseDto[] = [
    {
      id: 1,
      courtId: 1,
      courtNumber: 1,
      sessionType: 'MORNING',
      slotDate: '2026-10-15',
      startTime: '2026-10-15T06:00:00',
      endTime: '2026-10-15T06:30:00',
      status: 'AVAILABLE',
    },
    {
      id: 2,
      courtId: 1,
      courtNumber: 1,
      sessionType: 'MORNING',
      slotDate: '2026-10-15',
      startTime: '2026-10-15T06:30:00',
      endTime: '2026-10-15T07:00:00',
      status: 'BOOKED',
    },
    {
      id: 3,
      courtId: 2,
      courtNumber: 2,
      sessionType: 'EVENING',
      slotDate: '2026-10-15',
      startTime: '2026-10-15T15:30:00',
      endTime: '2026-10-15T16:00:00',
      status: 'BLOCKED_TOURNAMENT',
    },
  ];

  let capturedUrl = '';

  globalThis.fetch = (async (url: string | URL | Request) => {
    capturedUrl = String(url);
    return {
      ok: true,
      status: 200,
      json: async () => mockSlots,
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const result = await fetchSlots('2026-10-15');
    assert.equal(capturedUrl, '/api/slots?date=2026-10-15');
    assert.equal(result.length, 3);
    assert.equal(result[0].status, 'AVAILABLE');
    assert.equal(result[1].status, 'BOOKED');
    assert.equal(result[2].status, 'BLOCKED_TOURNAMENT');
    console.log('✓ fetchSlots(2026-10-15) queries /api/slots with exact date param');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

// 2. Verify generateSlots performs POST /api/slots/generate with Bearer token and JSON payload
{
  const originalFetch = globalThis.fetch;
  clearStoredAuth();
  setStoredAuthToken('STAFF_JWT_TOKEN');
  setStoredAuthUser({
    token: 'STAFF_JWT_TOKEN',
    tokenType: 'Bearer',
    expiresInSeconds: 3600,
    id: 88,
    studentId: 88,
    fullName: 'Sports Marshal',
    email: 'staff@vitapstudent.ac.in',
    universityEmail: 'staff@vitapstudent.ac.in',
    admissionNumber: 'STAFF001',
    role: 'STAFF',
  });

  let capturedUrl = '';
  let capturedMethod = '';
  let capturedHeaders: Record<string, string> = {};
  let capturedBody = '';

  const mockGenerationResponse: SlotGenerationResponseDto = {
    date: '2026-10-16',
    totalSlots: 48,
    createdSlots: 48,
    existingSlots: 0,
    message: 'Successfully generated 48 time slots for 2026-10-16 across 4 courts (0 pre-existing slots preserved).',
  };

  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    capturedUrl = String(url);
    capturedMethod = init?.method || 'GET';
    capturedHeaders = (init?.headers || {}) as Record<string, string>;
    capturedBody = String(init?.body || '');
    return {
      ok: true,
      status: 201,
      json: async () => mockGenerationResponse,
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const result = await generateSlots('2026-10-16');
    assert.equal(capturedUrl, '/api/slots/generate');
    assert.equal(capturedMethod, 'POST');
    assert.equal(capturedHeaders['Authorization'], 'Bearer STAFF_JWT_TOKEN');
    assert.equal(capturedHeaders['Content-Type'], 'application/json');
    assert.deepEqual(JSON.parse(capturedBody), { date: '2026-10-16' });
    assert.equal(result.totalSlots, 48);
    assert.equal(result.createdSlots, 48);
    assert.equal(result.existingSlots, 0);
    console.log('✓ generateSlots(2026-10-16) dispatches POST with STAFF token and creates 48 slots');
  } finally {
    clearStoredAuth();
    globalThis.fetch = originalFetch;
  }
}

// 3. Verify idempotent repeated generation preserves existing slots
{
  const originalFetch = globalThis.fetch;
  clearStoredAuth();
  setStoredAuthToken('ADMIN_JWT_TOKEN');

  const mockRepeatResponse: SlotGenerationResponseDto = {
    date: '2026-10-16',
    totalSlots: 48,
    createdSlots: 0,
    existingSlots: 48,
    message: 'Slots already exist for 2026-10-16. 0 new slots created, 48 existing slots preserved.',
  };

  globalThis.fetch = (async () => {
    return {
      ok: true,
      status: 200,
      json: async () => mockRepeatResponse,
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const result = await generateSlots('2026-10-16');
    assert.equal(result.createdSlots, 0);
    assert.equal(result.existingSlots, 48);
    assert.equal(result.totalSlots, 48);
    console.log('✓ Repeated slot generation reports 0 created and 48 preserved (idempotent)');
  } finally {
    clearStoredAuth();
    globalThis.fetch = originalFetch;
  }
}

// 4. Verify error handling for invalid date (400 Bad Request)
{
  const originalFetch = globalThis.fetch;

  globalThis.fetch = (async () => {
    return {
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      json: async () => ({ error: 'Invalid date format. Expected ISO-8601 date (YYYY-MM-DD).' }),
    } as unknown as Response;
  }) as typeof fetch;

  try {
    let thrownError: ApiError | null = null;
    try {
      await generateSlots('invalid-date');
    } catch (err) {
      if (err instanceof ApiError) {
        thrownError = err;
      }
    }
    assert.ok(thrownError, 'ApiError should be thrown for invalid date');
    assert.equal(thrownError.status, 400);
    assert.equal(thrownError.message, 'Invalid date format. Expected ISO-8601 date (YYYY-MM-DD).');
    console.log('✓ generateSlots correctly propagates 400 Bad Request on invalid date format');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

// 5. Verify error handling for unauthorized student role (403 Forbidden)
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
      await generateSlots('2026-10-17');
    } catch (err) {
      if (err instanceof ApiError) {
        thrownError = err;
      }
    }
    assert.ok(thrownError, 'ApiError should be thrown for forbidden access');
    assert.equal(thrownError.status, 403);
    assert.equal(thrownError.message, 'Access denied: insufficient permissions.');
    console.log('✓ generateSlots correctly propagates 403 Forbidden for non-staff accounts');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

// 6. Verify error handling for configuration conflict (409 Conflict)
{
  const originalFetch = globalThis.fetch;

  globalThis.fetch = (async () => {
    return {
      ok: false,
      status: 409,
      statusText: 'Conflict',
      json: async () => ({ error: 'No active courts found. At least one active court is required to generate time slots.' }),
    } as unknown as Response;
  }) as typeof fetch;

  try {
    let thrownError: ApiError | null = null;
    try {
      await generateSlots('2026-10-18');
    } catch (err) {
      if (err instanceof ApiError) {
        thrownError = err;
      }
    }
    assert.ok(thrownError, 'ApiError should be thrown for missing courts');
    assert.equal(thrownError.status, 409);
    assert.ok(thrownError.message.includes('No active courts found'));
    console.log('✓ generateSlots correctly propagates 409 Conflict when configuration is missing');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

console.log('--- All Admin Slot Management Workflow Tests Passed! ---');
