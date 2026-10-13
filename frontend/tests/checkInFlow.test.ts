import assert from 'node:assert/strict';
import {
  checkInPlayer,
  processNoShows,
  type CheckInResponseDto,
  type NoShowProcessingResponseDto,
  type CheckInRequestDto,
  ApiError,
} from '../src/api/client.ts';

console.log('--- Running Staff QR Check-in Workflow Tests ---');

// 1. Verify CheckInRequestDto payload requirements and default assignment
{
  const testPayload: CheckInRequestDto = {
    qrToken: 'QR_ABCD1234EFGH',
    staffIdentifier: 'STAFF_MARSHAL_1',
  };
  assert.equal(testPayload.qrToken, 'QR_ABCD1234EFGH');
  assert.equal(testPayload.staffIdentifier, 'STAFF_MARSHAL_1');
  console.log('✓ CheckInRequestDto types and payload structure validated');
}

// 2. Verify API client checkInPlayer dispatches proper JSON and parses response
{
  const originalFetch = globalThis.fetch;
  const mockResponse: CheckInResponseDto = {
    success: true,
    message: 'Player checked in successfully.',
    alreadyCheckedIn: false,
    bookingId: 10,
    bookingPlayerId: 20,
    studentId: 1,
    studentName: 'Hamsa Hrudai',
    admissionNumber: '23MIC7333',
    spotNumber: 1,
    courtId: 1,
    courtNumber: 1,
    slotStartTime: '2026-10-15T06:00:00',
    slotEndTime: '2026-10-15T06:30:00',
    checkedInAt: '2026-10-15T06:05:00Z',
    checkedInBy: 'STAFF_MARSHAL_1',
  };

  let capturedUrl = '';
  let capturedBody = '';

  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    capturedUrl = String(url);
    capturedBody = String(init?.body || '');
    return {
      ok: true,
      status: 200,
      json: async () => mockResponse,
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const result = await checkInPlayer({
      qrToken: 'QR_TEST_TOKEN_123',
      staffIdentifier: 'STAFF_MARSHAL_1',
    });

    assert.equal(capturedUrl, '/api/check-in');
    assert.ok(capturedBody.includes('QR_TEST_TOKEN_123'), 'Request body must contain the scanned token');
    assert.ok(capturedBody.includes('STAFF_MARSHAL_1'), 'Request body must contain the staff identifier');
    assert.equal(result.studentName, 'Hamsa Hrudai');
    assert.equal(result.success, true);
    assert.equal(result.alreadyCheckedIn, false);
    console.log('✓ checkInPlayer API client properly formats payload and deserializes response');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

// 3. Verify API client checkInPlayer propagates 409 Conflict (out of grace period) as ApiError
{
  const originalFetch = globalThis.fetch;

  globalThis.fetch = (async () => {
    return {
      ok: false,
      status: 409,
      statusText: 'Conflict',
      json: async () => ({ error: 'Check-in grace period expired at 06:10 for slot at 06:00.' }),
    } as unknown as Response;
  }) as typeof fetch;

  try {
    await checkInPlayer({ qrToken: 'QR_EXPIRED' });
    assert.fail('Expected checkInPlayer to throw ApiError');
  } catch (err) {
    assert.ok(err instanceof ApiError, 'Must throw ApiError');
    assert.equal(err.status, 409);
    assert.ok(err.message.includes('Check-in grace period expired'), 'Must propagate backend error message');
    console.log('✓ checkInPlayer API client cleanly propagates backend grace-period violations');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

// 4. Verify API client processNoShows dispatches and receives penalty summary
{
  const originalFetch = globalThis.fetch;
  const mockNoShowResponse: NoShowProcessingResponseDto = {
    processedCount: 1,
    penalizedPlayers: [
      {
        bookingPlayerId: 30,
        bookingId: 10,
        studentId: 2,
        studentName: 'Bomba',
        admissionNumber: '23MIC9999',
        spotNumber: 2,
        courtNumber: 1,
        slotStartTime: '2026-10-15T06:00:00',
        forfeitedCredits: 5,
        updatedReliabilityRating: 90.0,
      },
    ],
    message: 'Processed 1 no-show player(s).',
  };

  let capturedUrl = '';
  let capturedBody = '';

  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    capturedUrl = String(url);
    capturedBody = String(init?.body || '');
    return {
      ok: true,
      status: 200,
      json: async () => mockNoShowResponse,
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const result = await processNoShows({ timeSlotId: 100 });
    assert.equal(capturedUrl, '/api/check-in/process-no-shows');
    assert.ok(capturedBody.includes('100'), 'Must pass slot ID filter if provided');
    assert.equal(result.processedCount, 1);
    assert.equal(result.penalizedPlayers[0].studentName, 'Bomba');
    assert.equal(result.penalizedPlayers[0].forfeitedCredits, 5);
    console.log('✓ processNoShows API client properly dispatches and processes no-show responses');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

// 5. Verify API client login sets stored auth and dispatches credentials
{
  const originalFetch = globalThis.fetch;
  const mockLoginResponse = {
    token: 'jwt.header.signature123',
    tokenType: 'Bearer',
    studentId: 5,
    fullName: 'Court Marshall',
    universityEmail: 'marshall@vitapstudent.ac.in',
    admissionNumber: '24STAFF001',
    role: 'STAFF',
    emailVerified: true,
    expiresInSeconds: 86400,
  };

  let capturedBody = '';
  globalThis.fetch = (async (_url: string | URL | Request, init?: RequestInit) => {
    capturedBody = String(init?.body || '');
    return {
      ok: true,
      status: 200,
      json: async () => mockLoginResponse,
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const { login, getStoredAuthToken, clearStoredAuth } = await import('../src/api/client.ts');
    clearStoredAuth();
    const loginResult = await login({
      usernameOrEmail: 'marshall@vitapstudent.ac.in',
      password: 'SecurePassword123!',
    });

    assert.ok(capturedBody.includes('marshall@vitapstudent.ac.in'));
    assert.equal(loginResult.token, 'jwt.header.signature123');
    assert.equal(loginResult.role, 'STAFF');
    assert.equal(getStoredAuthToken(), 'jwt.header.signature123');
    console.log('✓ login API client authenticates staff credentials and caches Bearer token');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

// 6. Verify checkInPlayer and processNoShows include Authorization Bearer header
{
  const originalFetch = globalThis.fetch;
  let capturedHeaders: Record<string, string> = {};

  globalThis.fetch = (async (_url: string | URL | Request, init?: RequestInit) => {
    capturedHeaders = (init?.headers || {}) as Record<string, string>;
    return {
      ok: true,
      status: 200,
      json: async () => ({
        success: true,
        message: 'Checked in',
        alreadyCheckedIn: false,
        bookingId: 1,
        bookingPlayerId: 1,
        studentId: 1,
        studentName: 'Test',
        admissionNumber: '1',
        spotNumber: 1,
        courtId: 1,
        courtNumber: 1,
        slotStartTime: '2026-10-15T06:00:00',
        slotEndTime: '2026-10-15T06:30:00',
        checkedInAt: '2026-10-15T06:01:00Z',
        checkedInBy: 'Court Marshall',
      }),
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const { checkInPlayer, clearStoredAuth } = await import('../src/api/client.ts');
    await checkInPlayer({ qrToken: 'QR_AUTH_TEST' });
    assert.equal(capturedHeaders['Authorization'], 'Bearer jwt.header.signature123');
    clearStoredAuth();
    console.log('✓ checkInPlayer automatically attaches Authorization Bearer header when logged in');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

console.log('--- All Staff Check-in Tests Passed Successfully! ---');
