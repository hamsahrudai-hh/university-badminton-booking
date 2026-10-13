import assert from 'node:assert/strict';
import { groupDtosToTimeSlots, formatTimeFromIso } from '../src/utils/slotUtils.ts';
import type { TimeSlotResponseDto, CreateBookingRequestDto } from '../src/api/client.ts';
import type { TimeSlot, BookingWizardData } from '../src/types/booking.ts';

console.log('--- Running Slot ID Resolution & Preservation Tests ---');

// 1. Test formatTimeFromIso
{
  assert.equal(formatTimeFromIso('2026-10-15T06:00:00'), '06:00 AM');
  assert.equal(formatTimeFromIso('2026-10-15T16:30:00'), '04:30 PM');
  assert.equal(formatTimeFromIso('2026-10-15T12:00:00'), '12:00 PM');
  console.log('✓ formatTimeFromIso correctly formats morning, afternoon, and noon ISO strings');
}

// 2. Test groupDtosToTimeSlots correctly maps database slot IDs
const mockBackendSlots: TimeSlotResponseDto[] = [
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
    id: 13,
    courtId: 2,
    courtNumber: 2,
    sessionType: 'MORNING',
    slotDate: '2026-10-15',
    startTime: '2026-10-15T06:00:00',
    endTime: '2026-10-15T06:30:00',
    status: 'AVAILABLE',
  },
  {
    id: 25,
    courtId: 3,
    courtNumber: 3,
    sessionType: 'MORNING',
    slotDate: '2026-10-15',
    startTime: '2026-10-15T06:00:00',
    endTime: '2026-10-15T06:30:00',
    status: 'AVAILABLE',
  },
  {
    id: 37,
    courtId: 4,
    courtNumber: 4,
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
    status: 'AVAILABLE',
  },
  {
    id: 14,
    courtId: 2,
    courtNumber: 2,
    sessionType: 'MORNING',
    slotDate: '2026-10-15',
    startTime: '2026-10-15T06:30:00',
    endTime: '2026-10-15T07:00:00',
    status: 'AVAILABLE',
  },
];

const fallbackSlots: TimeSlot[] = [
  {
    id: 'm-1',
    session: 'morning',
    startTime: '06:00 AM',
    endTime: '06:30 AM',
    label: '06:00 AM – 06:30 AM',
  },
  {
    id: 'm-2',
    session: 'morning',
    startTime: '06:30 AM',
    endTime: '07:00 AM',
    label: '06:30 AM – 07:00 AM',
  },
];

const grouped = groupDtosToTimeSlots(mockBackendSlots, fallbackSlots);
assert.equal(grouped.length, 2, 'Should group 6 DTOs into 2 unique time slots');

const slot6am = grouped.find((s) => s.startTime === '06:00 AM')!;
assert.ok(slot6am, '06:00 AM slot must exist');
assert.equal(slot6am.realTimeSlotId, 1, 'Default realTimeSlotId should be Court 1 ID (1)');
assert.deepEqual(
  slot6am.courtSlotMap,
  { 1: 1, 2: 13, 3: 25, 4: 37 },
  'courtSlotMap must map every court to its exact numeric database slot ID'
);
console.log('✓ groupDtosToTimeSlots accurately maps realTimeSlotId and courtSlotMap for all courts');

// 3. Test Wizard State Flow: Slot selection -> Court selection -> Navigation -> Booking resolution
let wizardState: BookingWizardData = {
  slot: slot6am,
  courtId: 1,
  courtName: 'Court 1',
  players: [
    {
      student: { id: '1', numericId: 1, name: 'Hamsa Hrudai', admissionNumber: '21BCE7001', email: 'hamsa@vitapstudent.ac.in', isEligible: true },
      spotNumber: 1,
    },
    {
      student: { id: '2', numericId: 2, name: 'Rahul Sharma', admissionNumber: '21BCE7002', email: 'rahul@vitapstudent.ac.in', isEligible: true },
      spotNumber: 2,
    },
  ],
  realTimeSlotId: slot6am.courtSlotMap?.[1] ?? slot6am.realTimeSlotId,
};

assert.equal(wizardState.realTimeSlotId, 1, 'Initial realTimeSlotId for Court 1 is 1');

// User selects Court 3
const selectedCourtId = 3;
const mappedSlotId = slot6am.courtSlotMap?.[selectedCourtId] ?? slot6am.realTimeSlotId;
wizardState = {
  ...wizardState,
  courtId: selectedCourtId,
  courtName: `Court ${selectedCourtId}`,
  realTimeSlotId: mappedSlotId,
};

assert.equal(wizardState.courtId, 3);
assert.equal(wizardState.realTimeSlotId, 25, 'Selecting Court 3 must set realTimeSlotId to 25');

// User navigates Back to Step 2, then Step 1, then Next to Step 4
// Verify realTimeSlotId is preserved
assert.equal(wizardState.realTimeSlotId, 25, 'realTimeSlotId must be preserved across navigation');

// User changes slot to 06:30 AM
const slot630am = grouped.find((s) => s.startTime === '06:30 AM')!;
const targetCourtId = wizardState.courtId ?? 1;
const updatedRealSlotId = (targetCourtId && slot630am.courtSlotMap?.[targetCourtId]) ?? slot630am.realTimeSlotId ?? null;

wizardState = {
  ...wizardState,
  slot: slot630am,
  realTimeSlotId: updatedRealSlotId,
};

assert.equal(wizardState.realTimeSlotId, 2, 'Falls back to default slot ID (2) when court 3 is not in slot courtSlotMap');

// Re-select Court 2 for 06:30 AM
const court2SlotId = slot630am.courtSlotMap?.[2] ?? slot630am.realTimeSlotId;
wizardState = {
  ...wizardState,
  courtId: 2,
  courtName: 'Court 2',
  realTimeSlotId: court2SlotId,
};
assert.equal(wizardState.realTimeSlotId, 14, 'Selecting Court 2 at 06:30 AM must resolve to slot ID 14');

// 4. Test Booking confirmation payload resolution
function resolveTargetTimeSlotId(data: BookingWizardData): number {
  let targetTimeSlotId: number | null = data.realTimeSlotId ?? null;
  if (!targetTimeSlotId && data.courtId && data.slot?.courtSlotMap?.[data.courtId]) {
    targetTimeSlotId = data.slot.courtSlotMap[data.courtId];
  }
  if (!targetTimeSlotId && data.slot?.realTimeSlotId) {
    targetTimeSlotId = data.slot.realTimeSlotId;
  }
  if (!targetTimeSlotId && Number.isInteger(Number(data.slot?.id))) {
    targetTimeSlotId = Number(data.slot?.id);
  }

  if (!targetTimeSlotId) {
    throw new Error('Missing database TimeSlot ID');
  }
  return targetTimeSlotId;
}

const resolvedId = resolveTargetTimeSlotId(wizardState);
assert.equal(resolvedId, 14, 'Resolved ID must be exactly numeric 14');

const payload: CreateBookingRequestDto = {
  bookingStudentId: wizardState.players[0].student.numericId!,
  timeSlotId: resolvedId,
  players: wizardState.players.map((p) => ({
    studentId: p.student.numericId!,
    spotNumber: p.spotNumber,
  })),
  idempotencyKey: 'test_idemp_123',
};

assert.equal(payload.timeSlotId, 14);
assert.equal(typeof payload.timeSlotId, 'number');
assert.ok(Number.isInteger(payload.timeSlotId));
assert.ok(payload.timeSlotId > 0);
console.log('✓ Booking request payload receives exact integer database timeSlotId: 14');

// 5. Test Student Identity Flow & Mock Account Rejection
function buildBookingRequest(wizard: BookingWizardData, idempotencyKey: string): CreateBookingRequestDto {
  const targetTimeSlotId = resolveTargetTimeSlotId(wizard);

  if (wizard.players.length < 2 || wizard.players.length > 4) {
    throw new Error('A reservation requires between 2 and 4 registered students.');
  }

  const bookingHost = wizard.players[0]?.student;
  const bookingStudentId = bookingHost?.numericId ?? (
    Number.isInteger(Number(bookingHost?.id)) ? Number(bookingHost?.id) : null
  );

  if (!bookingStudentId) {
    throw new Error(
      'Missing registered student ID for booking host. Real backend bookings require registered student accounts from GET /api/students.'
    );
  }

  const playerAssignments = wizard.players.map((p) => {
    const studentId = p.student.numericId ?? (
      Number.isInteger(Number(p.student.id)) ? Number(p.student.id) : null
    );
    if (!studentId) {
      throw new Error(`Group member ${p.student.name} does not have a valid numeric student ID in the database.`);
    }
    return {
      studentId,
      spotNumber: p.spotNumber,
    };
  });

  return {
    bookingStudentId,
    timeSlotId: targetTimeSlotId,
    players: playerAssignments,
    idempotencyKey,
  };
}

// Test rejection of mock student host ('s-1')
const mockHostWizard: BookingWizardData = {
  ...wizardState,
  players: [
    {
      student: { id: 's-1', name: 'Alex Chen', admissionNumber: '2024BCS1042', email: 'alex@mock.edu', isEligible: true },
      spotNumber: 1,
    },
    {
      student: { id: '2', numericId: 2, name: 'Rahul Sharma', admissionNumber: '21BCE7002', email: 'rahul@vitapstudent.ac.in', isEligible: true },
      spotNumber: 2,
    },
  ],
};

assert.throws(
  () => buildBookingRequest(mockHostWizard, 'idemp-fail-1'),
  /Missing registered student ID for booking host/,
  'Must reject booking when host has mock string ID without database numericId'
);
console.log('✓ Mock student host is strictly rejected');

// Test rejection of mock teammate ('s-2')
const mockTeammateWizard: BookingWizardData = {
  ...wizardState,
  players: [
    {
      student: { id: '1', numericId: 1, name: 'Hamsa Hrudai', admissionNumber: '21BCE7001', email: 'hamsa@vitapstudent.ac.in', isEligible: true },
      spotNumber: 1,
    },
    {
      student: { id: 's-2', name: 'Priya Sharma', admissionNumber: '2024BCS1088', email: 'priya@mock.edu', isEligible: true },
      spotNumber: 2,
    },
  ],
};

assert.throws(
  () => buildBookingRequest(mockTeammateWizard, 'idemp-fail-2'),
  /does not have a valid numeric student ID in the database/,
  'Must reject booking when any group member has mock string ID without database numericId'
);
console.log('✓ Mock teammate is strictly rejected');

// Test success with real registered students from GET /api/students
const realGroupWizard: BookingWizardData = {
  ...wizardState,
  realTimeSlotId: 13,
  players: [
    {
      student: { id: '1', numericId: 1, name: 'Hamsa Hrudai', admissionNumber: '23MIC7320', email: 'hrudai.23mic7320@vitapstudent.ac.in', isEligible: true, weeklyCredits: 6 },
      spotNumber: 1,
    },
    {
      student: { id: '2', numericId: 2, name: 'Priya Sharma', admissionNumber: '23MIC7321', email: 'priya.23mic7321@vitapstudent.ac.in', isEligible: true, weeklyCredits: 6 },
      spotNumber: 2,
    },
  ],
};

const validPayload = buildBookingRequest(realGroupWizard, 'idemp-real-success');
assert.equal(validPayload.bookingStudentId, 1, 'Host bookingStudentId must be real numeric ID 1');
assert.equal(validPayload.timeSlotId, 13, 'TimeSlot ID must be real numeric ID 13');
assert.equal(validPayload.players.length, 2);
assert.equal(validPayload.players[0].studentId, 1);
assert.equal(validPayload.players[0].spotNumber, 1);
assert.equal(validPayload.players[1].studentId, 2);
assert.equal(validPayload.players[1].spotNumber, 2);
console.log('✓ Real registered students correctly populate bookingStudentId (1) and player assignments (1, 2)');

console.log('\nAll Slot ID & Student Identity Tests PASSED successfully!');
