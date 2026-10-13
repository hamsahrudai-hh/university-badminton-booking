import assert from 'node:assert/strict';
import {
  joinQueue,
  fetchQueueStatus,
  fetchStudentQueuePosition,
  setStoredAuthToken,
  clearStoredAuth,
  type QueueEntryResponseDto,
  type QueueStatusResponseDto,
  ApiError,
} from '../src/api/client.ts';

console.log('--- Running Queue Fairness & Admission Flow Tests ---');

const originalFetch = globalThis.fetch;

// 1. Verify joinQueue client attaches body and Bearer token and deserializes response
{
  clearStoredAuth();
  setStoredAuthToken('mock-jwt-token-queue');

  let interceptedUrl = '';
  let interceptedMethod = '';
  let interceptedHeaders: Record<string, string> = {};
  let interceptedBody = '';

  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    interceptedUrl = String(url);
    interceptedMethod = init?.method ?? 'GET';
    interceptedHeaders = (init?.headers as Record<string, string>) || {};
    interceptedBody = String(init?.body || '');

    const mockResponse: QueueEntryResponseDto = {
      queueEntryId: 501,
      queueId: 100,
      studentId: 1,
      status: 'WAITING',
      joinedAt: '2026-10-14T21:02:00Z',
      admittedAt: null,
      expiresAt: null,
      positionInLine: 2,
      remainingSeconds: 0,
      priorityTier: 'STANDARD',
      message: 'Successfully joined the MORNING queue at position 2.',
    };

    return {
      ok: true,
      status: 201,
      statusText: 'Created',
      json: async () => mockResponse,
    } as unknown as Response;
  }) as typeof fetch;

  const result = await joinQueue({
    studentId: 1,
    date: '2026-10-15',
    sessionType: 'MORNING',
  });

  assert.equal(interceptedUrl, '/api/queue/join');
  assert.equal(interceptedMethod, 'POST');
  assert.equal(interceptedHeaders['Authorization'], 'Bearer mock-jwt-token-queue');
  assert.equal(interceptedHeaders['Content-Type'], 'application/json');

  const parsedBody = JSON.parse(interceptedBody);
  assert.equal(parsedBody.studentId, 1);
  assert.equal(parsedBody.date, '2026-10-15');
  assert.equal(parsedBody.sessionType, 'MORNING');

  assert.equal(result.queueEntryId, 501);
  assert.equal(result.status, 'WAITING');
  assert.equal(result.positionInLine, 2);
  assert.equal(result.priorityTier, 'STANDARD');

  clearStoredAuth();
  console.log('✓ joinQueue properly formats payload, attaches Bearer token, and handles response');
}

// 2. Verify joinQueue handles credit-based reduced priority and closed queue errors
{
  globalThis.fetch = (async () => {
    return {
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      json: async () => ({
        error: 'Queue for MORNING session on 2026-10-15 has not opened yet. Queue opens on 2026-10-14T21:00.',
      }),
    } as unknown as Response;
  }) as typeof fetch;

  await assert.rejects(
    async () => {
      await joinQueue({
        studentId: 3,
        date: '2026-10-15',
        sessionType: 'MORNING',
      });
    },
    (err: unknown) => {
      assert(err instanceof ApiError);
      assert.equal(err.status, 400);
      assert(err.message.includes('has not opened yet'));
      return true;
    }
  );

  console.log('✓ joinQueue correctly propagates server-side schedule boundary errors');
}

// 3. Verify fetchQueueStatus retrieves authoritative timing windows
{
  let interceptedUrl = '';

  globalThis.fetch = (async (url: string | URL | Request) => {
    interceptedUrl = String(url);

    const mockStatus: QueueStatusResponseDto = {
      queueId: 100,
      sessionDate: '2026-10-15',
      sessionType: 'MORNING',
      totalEntries: 5,
      waitingCount: 4,
      hasActiveOpportunity: true,
      remainingSeconds: 42,
      queueOpenDateTime: '2026-10-14T21:00:00',
      bookingStartDateTime: '2026-10-14T21:05:00',
      sessionEndDateTime: '2026-10-15T08:30:00',
      serverTime: '2026-10-14T21:05:18',
      isQueueOpen: true,
      isBookingOpen: true,
      message: 'Booking opportunity is active (42 seconds remaining).',
    };

    return {
      ok: true,
      status: 200,
      statusText: 'OK',
      json: async () => mockStatus,
    } as unknown as Response;
  }) as typeof fetch;

  const statusResult = await fetchQueueStatus('2026-10-15', 'MORNING');

  assert(interceptedUrl.includes('/api/queue/status'));
  assert(interceptedUrl.includes('date=2026-10-15'));
  assert(interceptedUrl.includes('sessionType=MORNING'));
  assert.equal(statusResult.totalEntries, 5);
  assert.equal(statusResult.waitingCount, 4);
  assert.equal(statusResult.hasActiveOpportunity, true);
  assert.equal(statusResult.remainingSeconds, 42);
  assert.equal(statusResult.isBookingOpen, true);

  console.log('✓ fetchQueueStatus accurately parses server timing and admission windows');
}

// 4. Verify fetchStudentQueuePosition parses active 60-second window
{
  setStoredAuthToken('mock-jwt-token-queue');

  globalThis.fetch = (async () => {
    const mockAdmittedEntry: QueueEntryResponseDto = {
      queueEntryId: 501,
      queueId: 100,
      studentId: 1,
      status: 'ADMITTED',
      joinedAt: '2026-10-14T21:02:00Z',
      admittedAt: '2026-10-14T21:05:00Z',
      expiresAt: '2026-10-14T21:06:00Z',
      positionInLine: 0,
      remainingSeconds: 60,
      priorityTier: 'STANDARD',
      message: 'Student Alice Student admitted to 60-second booking opportunity.',
    };

    return {
      ok: true,
      status: 200,
      statusText: 'OK',
      json: async () => mockAdmittedEntry,
    } as unknown as Response;
  }) as typeof fetch;

  const positionResult = await fetchStudentQueuePosition('2026-10-15', 'MORNING', 1);

  assert.equal(positionResult.status, 'ADMITTED');
  assert.equal(positionResult.positionInLine, 0);
  assert.equal(positionResult.remainingSeconds, 60);
  assert.equal(positionResult.expiresAt, '2026-10-14T21:06:00Z');

  clearStoredAuth();
  console.log('✓ fetchStudentQueuePosition verifies admitted 60-second window state');
}

// Restore original fetch
globalThis.fetch = originalFetch;

console.log('--- All Queue Fairness & Admission Flow Tests Passed! ---');
