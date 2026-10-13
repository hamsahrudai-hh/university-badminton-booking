import assert from 'node:assert/strict';
import {
  fetchSessions,
  fetchSessionById,
  updateSessionConfig,
  setStoredAuthToken,
  setStoredAuthUser,
  clearStoredAuth,
  ApiError,
  type SessionConfigResponseDto,
  type UpdateSessionConfigRequestDto,
} from '../src/api/client.ts';
import {
  computeHeaderNavState,
  getVisibleMoreSections,
  isStaffOrAdminUser,
} from '../src/utils/navigation.ts';

console.log('--- Running Admin Session Rules & Timing Workflow Tests ---');

// 1. Verify UpdateSessionConfigRequestDto typing and structure
{
  const updatePayload: UpdateSessionConfigRequestDto = {
    startTime: '06:30:00',
    endTime: '09:00:00',
    slotDurationMinutes: 45,
    queueOpenTime: '20:00:00',
    bookingStartTime: '20:10:00',
    queueDaysInAdvance: 1,
    active: true,
  };

  assert.equal(updatePayload.slotDurationMinutes, 45);
  assert.equal(updatePayload.startTime, '06:30:00');
  assert.equal(updatePayload.active, true);
  console.log('✓ UpdateSessionConfigRequestDto payload structure validated');
}

// 2. Verify fetchSessions queries GET /api/sessions and deserializes response
{
  const originalFetch = globalThis.fetch;
  const mockSessions: SessionConfigResponseDto[] = [
    {
      id: 1,
      sessionType: 'MORNING',
      startTime: '06:00:00',
      endTime: '08:30:00',
      slotDurationMinutes: 30,
      queueOpenTime: '21:00:00',
      bookingStartTime: '21:05:00',
      queueDaysInAdvance: 1,
      active: true,
    },
    {
      id: 2,
      sessionType: 'EVENING',
      startTime: '15:30:00',
      endTime: '19:00:00',
      slotDurationMinutes: 30,
      queueOpenTime: '12:00:00',
      bookingStartTime: '12:05:00',
      queueDaysInAdvance: 0,
      active: true,
    },
  ];

  let capturedUrl = '';

  globalThis.fetch = (async (url: string | URL | Request) => {
    capturedUrl = String(url);
    return {
      ok: true,
      status: 200,
      json: async () => mockSessions,
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const result = await fetchSessions();
    assert.equal(capturedUrl, '/api/sessions');
    assert.equal(result.length, 2);
    assert.equal(result[0].sessionType, 'MORNING');
    assert.equal(result[0].slotDurationMinutes, 30);
    assert.equal(result[1].sessionType, 'EVENING');
    console.log('✓ fetchSessions() queries GET /api/sessions successfully');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

// 3. Verify fetchSessionById queries GET /api/sessions/{id}
{
  const originalFetch = globalThis.fetch;
  let capturedUrl = '';

  globalThis.fetch = (async (url: string | URL | Request) => {
    capturedUrl = String(url);
    return {
      ok: true,
      status: 200,
      json: async () => ({
        id: 1,
        sessionType: 'MORNING',
        startTime: '06:00:00',
        endTime: '08:30:00',
        slotDurationMinutes: 30,
        queueOpenTime: '21:00:00',
        bookingStartTime: '21:05:00',
        queueDaysInAdvance: 1,
        active: true,
      }),
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const result = await fetchSessionById(1);
    assert.equal(capturedUrl, '/api/sessions/1');
    assert.equal(result.id, 1);
    assert.equal(result.sessionType, 'MORNING');
    console.log('✓ fetchSessionById(1) queries GET /api/sessions/1 successfully');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

// 4. Verify updateSessionConfig sends PUT /api/sessions/{id} with Authorization Bearer header
{
  const originalFetch = globalThis.fetch;
  const staffToken = 'eyJhbGciOiJIUzI1NiJ9.staff_session_token';
  setStoredAuthToken(staffToken);
  setStoredAuthUser({
    token: staffToken,
    id: 10,
    fullName: 'Marshal Staff',
    email: 'staff@vitapstudent.ac.in',
    admissionNumber: 'STAFF001',
    role: 'STAFF',
    emailVerified: true,
  });

  let capturedMethod = '';
  let capturedUrl = '';
  let capturedHeaders: Record<string, string> = {};
  let capturedBody = '';

  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    capturedUrl = String(url);
    capturedMethod = init?.method || 'GET';
    capturedHeaders = (init?.headers as Record<string, string>) || {};
    capturedBody = String(init?.body || '');

    return {
      ok: true,
      status: 200,
      json: async () => ({
        id: 1,
        sessionType: 'MORNING',
        startTime: '06:30:00',
        endTime: '09:00:00',
        slotDurationMinutes: 45,
        queueOpenTime: '20:00:00',
        bookingStartTime: '20:10:00',
        queueDaysInAdvance: 1,
        active: true,
      }),
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const payload: UpdateSessionConfigRequestDto = {
      startTime: '06:30:00',
      endTime: '09:00:00',
      slotDurationMinutes: 45,
      queueOpenTime: '20:00:00',
      bookingStartTime: '20:10:00',
      queueDaysInAdvance: 1,
      active: true,
    };

    const updated = await updateSessionConfig(1, payload);
    assert.equal(capturedMethod, 'PUT');
    assert.equal(capturedUrl, '/api/sessions/1');
    assert.equal(capturedHeaders['Authorization'], `Bearer ${staffToken}`);
    assert.ok(capturedBody.includes('"slotDurationMinutes":45'));
    assert.equal(updated.slotDurationMinutes, 45);
    assert.equal(updated.startTime, '06:30:00');
    console.log('✓ updateSessionConfig(1) dispatches PUT with STAFF token and serialized payload');
  } finally {
    clearStoredAuth();
    globalThis.fetch = originalFetch;
  }
}

// 5. Verify updateSessionConfig error handling (400 Bad Request, 403 Forbidden, 404 Not Found)
{
  const originalFetch = globalThis.fetch;

  // 5a. Validation error (400)
  globalThis.fetch = (async () => {
    return {
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      json: async () => ({
        error: 'Session start time (09:00:00) must be strictly before end time (06:00:00).',
      }),
    } as unknown as Response;
  }) as typeof fetch;

  try {
    await updateSessionConfig(1, { startTime: '09:00:00', endTime: '06:00:00' });
    assert.fail('Expected ApiError for invalid time window');
  } catch (err) {
    assert.ok(err instanceof ApiError);
    assert.equal(err.status, 400);
    assert.ok(err.message.includes('strictly before end time'));
    console.log('✓ updateSessionConfig cleanly propagates 400 Bad Request for invalid time window');
  }

  // 5b. Unauthorized error (403)
  globalThis.fetch = (async () => {
    return {
      ok: false,
      status: 403,
      statusText: 'Forbidden',
      json: async () => ({
        error: 'Access denied: insufficient permissions.',
      }),
    } as unknown as Response;
  }) as typeof fetch;

  try {
    await updateSessionConfig(1, { active: false });
    assert.fail('Expected ApiError for unauthorized access');
  } catch (err) {
    assert.ok(err instanceof ApiError);
    assert.equal(err.status, 403);
    assert.ok(err.message.includes('insufficient permissions'));
    console.log('✓ updateSessionConfig cleanly propagates 403 Forbidden for unauthorized accounts');
  }

  // 5c. Not found error (404)
  globalThis.fetch = (async () => {
    return {
      ok: false,
      status: 404,
      statusText: 'Not Found',
      json: async () => ({
        error: 'Session configuration not found with ID: 999',
      }),
    } as unknown as Response;
  }) as typeof fetch;

  try {
    await updateSessionConfig(999, { active: false });
    assert.fail('Expected ApiError for unknown session ID');
  } catch (err) {
    assert.ok(err instanceof ApiError);
    assert.equal(err.status, 404);
    assert.ok(err.message.includes('not found with ID: 999'));
    console.log('✓ updateSessionConfig cleanly propagates 404 Not Found for non-existent session ID');
  }

  globalThis.fetch = originalFetch;
}

// 6. Verify Navigation integration and Role Security
{
  // 6a. Active navigation computation when Session Rules screen is active
  const navState = computeHeaderNavState({
    isWizardActive: false,
    currentAuthScreen: 'none',
    isStaffCheckInActive: false,
    isCourtManagementActive: false,
    isSlotManagementActive: false,
    isSessionRulesActive: true,
    userRole: 'STAFF',
  });
  assert.equal(navState.isDashboardActive, false, 'Dashboard must not be highlighted when Session Rules is active');
  assert.equal(navState.isMoreMenuActive, true, 'More menu must be highlighted when Session Rules is active');
  assert.equal(navState.isStaffOrAdmin, true, 'STAFF role is properly recognized');

  // 6b. More menu contains Session Rules for Staff/Admin, but hides for Student/Guest
  const staffSections = getVisibleMoreSections('STAFF');
  assert.equal(staffSections.hasStaffAndAdmin, true);
  const sessionItem = staffSections.staffItems.find((i) => i.id === 'sessions');
  assert.ok(sessionItem, 'Session Rules must exist in staff destinations');
  assert.equal(sessionItem?.label, 'Session Rules');

  const studentSections = getVisibleMoreSections('STUDENT');
  assert.equal(studentSections.hasStaffAndAdmin, false);
  assert.equal(studentSections.staffItems.length, 0, 'Students must never see staff session management controls');

  const guestSections = getVisibleMoreSections(undefined);
  assert.equal(guestSections.hasStaffAndAdmin, false);
  assert.equal(guestSections.staffItems.length, 0, 'Guests must never see staff session management controls');

  // 6c. Role check helper
  assert.equal(isStaffOrAdminUser('ADMIN'), true);
  assert.equal(isStaffOrAdminUser('STAFF'), true);
  assert.equal(isStaffOrAdminUser('STUDENT'), false);
  assert.equal(isStaffOrAdminUser(undefined), false);

  console.log('✓ Navigation and role permissions: Session Rules cleanly integrated and role-guarded');
}

console.log('--- All Admin Session Rules Workflow Tests Passed! ---');
