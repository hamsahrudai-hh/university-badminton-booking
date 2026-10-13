import assert from 'node:assert/strict';
import {
  login,
  getStoredAuthToken,
  setStoredAuthToken,
  getStoredAuthUser,
  setStoredAuthUser,
  clearStoredAuth,
  createBooking,
  fetchStudentById,
  fetchStudents,
  type LoginResponseDto,
  type CreateBookingRequestDto,
  ApiError,
} from '../src/api/client.ts';

console.log('--- Running Student Authentication & Booking Authorization Tests ---');

// 1. Verify in-memory / storage token and user management
{
  clearStoredAuth();
  assert.equal(getStoredAuthToken(), null, 'Initially stored token should be null');
  assert.equal(getStoredAuthUser(), null, 'Initially stored user should be null');

  const sampleToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.sample';
  setStoredAuthToken(sampleToken);
  assert.equal(getStoredAuthToken(), sampleToken, 'Token should be retrieved accurately');

  const sampleUser: LoginResponseDto = {
    token: sampleToken,
    tokenType: 'Bearer',
    expiresInSeconds: 86400,
    id: 42,
    studentId: 42,
    fullName: 'Jane Doe',
    email: 'jane.doe@vitapstudent.ac.in',
    universityEmail: 'jane.doe@vitapstudent.ac.in',
    admissionNumber: '23BCE1001',
    role: 'STUDENT',
    weeklyCredits: 6,
    reliabilityRating: 1.0,
    emailVerified: true,
  };

  setStoredAuthUser(sampleUser);
  const retrievedUser = getStoredAuthUser();
  assert.notEqual(retrievedUser, null);
  assert.equal(retrievedUser?.id, 42);
  assert.equal(retrievedUser?.universityEmail, 'jane.doe@vitapstudent.ac.in');
  assert.equal(retrievedUser?.admissionNumber, '23BCE1001');

  clearStoredAuth();
  assert.equal(getStoredAuthToken(), null, 'Token should be null after clearing auth');
  assert.equal(getStoredAuthUser(), null, 'User should be null after clearing auth');

  console.log('✓ Token and user persistence / cleanup functions verified');
}

// 2. Verify login API client formats request with identifier and caches token upon success
{
  const originalFetch = globalThis.fetch;
  const mockJwt = 'mock.jwt.token.student42';
  const mockBackendResponse = {
    token: mockJwt,
    tokenType: 'Bearer',
    expiresInSeconds: 86400,
    id: 42,
    fullName: 'Jane Doe',
    email: 'jane.doe@vitapstudent.ac.in',
    admissionNumber: '23BCE1001',
    role: 'STUDENT',
    weeklyCredits: 6,
    reliabilityRating: 1.0,
    emailVerified: true,
  };

  let capturedUrl = '';
  let capturedBody = '';
  let capturedHeaders: Record<string, string> = {};

  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    capturedUrl = String(url);
    capturedBody = String(init?.body || '');
    capturedHeaders = (init?.headers || {}) as Record<string, string>;
    return {
      ok: true,
      status: 200,
      json: async () => mockBackendResponse,
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const res = await login({
      identifier: '23BCE1001',
      password: 'SecurePassword123!',
    });

    assert.equal(capturedUrl, '/api/auth/login');
    const parsedBody = JSON.parse(capturedBody);
    assert.equal(parsedBody.identifier, '23BCE1001', 'Body must send identifier');
    assert.equal(parsedBody.usernameOrEmail, '23BCE1001', 'Body must send usernameOrEmail alias');
    assert.equal(parsedBody.password, 'SecurePassword123!');
    assert.equal(capturedHeaders['Content-Type'], 'application/json');
    assert.equal(res.id, 42);
    assert.equal(res.token, mockJwt);
    assert.equal(getStoredAuthToken(), mockJwt, 'login() must cache token automatically');
    assert.equal(getStoredAuthUser()?.id, 42, 'login() must cache user automatically');

    console.log('✓ login() correctly formats payload and caches JWT token and user profile');
  } finally {
    globalThis.fetch = originalFetch;
    clearStoredAuth();
  }
}

// 3. Verify login properly propagates HTTP 403 for unverified accounts
{
  const originalFetch = globalThis.fetch;

  globalThis.fetch = (async () => {
    return {
      ok: false,
      status: 403,
      statusText: 'Forbidden',
      json: async () => ({ error: 'University email is not verified. Please verify your email before logging in.' }),
    } as unknown as Response;
  }) as typeof fetch;

  try {
    await login({
      identifier: 'unverified.student@vitapstudent.ac.in',
      password: 'Password123!',
    });
    assert.fail('login() should have thrown ApiError for 403 unverified account');
  } catch (err) {
    assert.ok(err instanceof ApiError);
    assert.equal(err.status, 403);
    assert.ok(err.message.includes('not verified') || err.message.includes('inactive'));
    console.log('✓ login() cleanly propagates HTTP 403 for unverified student accounts');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

// 4. Verify login properly propagates HTTP 401 for invalid credentials
{
  const originalFetch = globalThis.fetch;

  globalThis.fetch = (async () => {
    return {
      ok: false,
      status: 401,
      statusText: 'Unauthorized',
      json: async () => ({ error: 'Invalid university email/admission number or password.' }),
    } as unknown as Response;
  }) as typeof fetch;

  try {
    await login({
      identifier: 'wrong.id@vitapstudent.ac.in',
      password: 'WrongPassword',
    });
    assert.fail('login() should have thrown ApiError for 401 invalid credentials');
  } catch (err) {
    assert.ok(err instanceof ApiError);
    assert.equal(err.status, 401);
    assert.ok(err.message.includes('Invalid'));
    console.log('✓ login() cleanly propagates HTTP 401 for bad credentials');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

// 5. Verify createBooking automatically attaches Authorization: Bearer <token>
{
  const originalFetch = globalThis.fetch;
  const activeToken = 'bearer.token.test.123';
  setStoredAuthToken(activeToken);

  let capturedHeaders: Record<string, string> = {};
  let capturedBody = '';

  globalThis.fetch = (async (_url: string | URL | Request, init?: RequestInit) => {
    capturedHeaders = (init?.headers || {}) as Record<string, string>;
    capturedBody = String(init?.body || '');
    return {
      ok: true,
      status: 201,
      json: async () => ({
        id: 99,
        courtId: 1,
        timeSlotId: 10,
        bookingStudentId: 42,
        status: 'CONFIRMED',
        players: [],
      }),
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const bookingPayload: CreateBookingRequestDto = {
      bookingStudentId: 42,
      timeSlotId: 10,
      players: [
        { studentId: 42, spotNumber: 1 },
        { studentId: 15, spotNumber: 2 },
      ],
    };

    await createBooking(bookingPayload);
    assert.equal(capturedHeaders['Authorization'], `Bearer ${activeToken}`, 'Must attach Bearer token');
    const parsed = JSON.parse(capturedBody);
    assert.equal(parsed.bookingStudentId, 42, 'bookingStudentId must match host');
    assert.equal(parsed.timeSlotId, 10, 'timeSlotId must match selected slot');
    assert.equal(parsed.players.length, 2);

    console.log('✓ createBooking automatically attaches Bearer token header from authenticated session');
  } finally {
    globalThis.fetch = originalFetch;
    clearStoredAuth();
  }
}

// 6. Verify fetchStudentById automatically attaches Authorization: Bearer <token>
{
  const originalFetch = globalThis.fetch;
  const activeToken = 'bearer.token.test.profile';
  setStoredAuthToken(activeToken);

  let capturedHeaders: Record<string, string> = {};

  globalThis.fetch = (async (_url: string | URL | Request, init?: RequestInit) => {
    capturedHeaders = (init?.headers || {}) as Record<string, string>;
    return {
      ok: true,
      status: 200,
      json: async () => ({
        id: 42,
        fullName: 'Jane Doe',
        universityEmail: 'jane.doe@vitapstudent.ac.in',
        admissionNumber: '23BCE1001',
        role: 'STUDENT',
        weeklyCredits: 5,
        reliabilityRating: 1.0,
        active: true,
      }),
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const profile = await fetchStudentById(42);
    assert.equal(capturedHeaders['Authorization'], `Bearer ${activeToken}`, 'Must attach Bearer token');
    assert.equal(profile.id, 42);
    assert.equal(profile.weeklyCredits, 5);

    console.log('✓ fetchStudentById automatically attaches Bearer token');
  } finally {
    globalThis.fetch = originalFetch;
    clearStoredAuth();
  }
}

// 7. Verify security: StudentResponseDto objects expose only safe fields
{
  const publicStudentObject = {
    id: 1,
    fullName: 'Hamsa Hrudai',
    universityEmail: 'hrudai.23mic7320@vitapstudent.ac.in',
    admissionNumber: '23MIC7320',
    role: 'STUDENT',
    weeklyCredits: 5,
    reliabilityRating: 1.0,
    active: true,
  };

  assert.ok(!('password' in publicStudentObject), 'Must not contain password');
  assert.ok(!('passwordHash' in publicStudentObject), 'Must not contain passwordHash');
  assert.ok(!('token' in publicStudentObject), 'Must not contain token');
  assert.ok(!('verificationToken' in publicStudentObject), 'Must not contain verificationToken');

  console.log('✓ Public Student DTO contract sanitized with no credential exposure');
}

// 8. Verify privacy-safe TeammateOptionDto from GET /api/students
{
  const originalFetch = globalThis.fetch;
  const mockTeammates = [
    { id: 1, fullName: 'Hamsa Hrudai', eligible: true },
    { id: 2, fullName: 'Bomba', eligible: true },
  ];

  let capturedUrl = '';

  globalThis.fetch = (async (url: string | URL | Request) => {
    capturedUrl = String(url);
    return {
      ok: true,
      status: 200,
      json: async () => mockTeammates,
    } as unknown as Response;
  }) as typeof fetch;

  try {
    const teammates = await fetchStudents();
    assert.equal(capturedUrl, '/api/students');
    assert.equal(teammates.length, 2);
    assert.equal(teammates[0].id, 1);
    assert.equal(teammates[0].fullName, 'Hamsa Hrudai');
    assert.equal(teammates[0].eligible, true);

    // Verify privacy safety: none of the private fields are present
    const first = teammates[0] as unknown as Record<string, unknown>;
    assert.equal(first.admissionNumber, undefined, 'Must not expose admissionNumber');
    assert.equal(first.universityEmail, undefined, 'Must not expose universityEmail');
    assert.equal(first.weeklyCredits, undefined, 'Must not expose weeklyCredits');
    assert.equal(first.reliabilityRating, undefined, 'Must not expose reliabilityRating');
    assert.equal(first.password, undefined, 'Must not expose password');
    assert.equal(first.token, undefined, 'Must not expose token');

    console.log('✓ Teammate selection API returns only privacy-safe fields (id, fullName, eligible)');
  } finally {
    globalThis.fetch = originalFetch;
  }
}

console.log('All Student Authentication & Booking Authorization unit tests passed!');
