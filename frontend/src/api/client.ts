/**
 * API client for interacting with the Spring Boot backend REST endpoints.
 */

export interface CourtResponseDto {
  id: number;
  courtNumber: number;
  active: boolean;
}

export type SessionTypeDto = 'MORNING' | 'EVENING';

export type SlotStatusDto = 'AVAILABLE' | 'BOOKED' | 'BLOCKED_TOURNAMENT' | 'MAINTENANCE';

export interface TimeSlotResponseDto {
  id: number;
  courtId: number | null;
  courtNumber: number | null;
  sessionType: SessionTypeDto | null;
  slotDate: string;
  startTime: string;
  endTime: string;
  status: SlotStatusDto;
}

export interface SessionConfigResponseDto {
  id: number;
  sessionType: SessionTypeDto;
  startTime: string;
  endTime: string;
  slotDurationMinutes: number;
  queueOpenTime: string;
  bookingStartTime: string;
  queueDaysInAdvance: number;
  active: boolean;
}

export interface UpdateSessionConfigRequestDto {
  startTime?: string;
  endTime?: string;
  slotDurationMinutes?: number;
  queueOpenTime?: string;
  bookingStartTime?: string;
  queueDaysInAdvance?: number;
  active?: boolean;
}

export class ApiError extends Error {
  readonly status: number;
  readonly statusText: string;

  constructor(status: number, statusText: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.statusText = statusText;
  }
}

export interface UpdateCourtStatusRequestDto {
  active: boolean;
}

/**
 * Fetches registered courts from GET /api/courts.
 * Optionally filters for active-only courts via query param.
 */
export async function fetchCourts(activeOnly = false): Promise<CourtResponseDto[]> {
  const url = activeOnly ? '/api/courts?activeOnly=true' : '/api/courts';
  const response = await fetch(url, {
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    let errorMessage = `Failed to fetch courts: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

/**
 * Fetches a single court by database ID from GET /api/courts/{id}.
 */
export async function fetchCourtById(id: number): Promise<CourtResponseDto> {
  const response = await fetch(`/api/courts/${id}`, {
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    let errorMessage = `Failed to fetch court: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

/**
 * Updates a court's active status via PATCH /api/courts/{id}/status.
 * Strictly requires STAFF or ADMIN role.
 */
export async function updateCourtStatus(
  courtId: number,
  active: boolean
): Promise<CourtResponseDto> {
  const token = getStoredAuthToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`/api/courts/${courtId}/status`, {
    method: 'PATCH',
    headers,
    body: JSON.stringify({ active }),
  });

  if (!response.ok) {
    let errorMessage = `Failed to update court status: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

/**
 * Fetches recurring session configurations from GET /api/sessions.
 */
export async function fetchSessions(): Promise<SessionConfigResponseDto[]> {
  const response = await fetch('/api/sessions', {
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    throw new ApiError(
      response.status,
      response.statusText,
      `Failed to fetch session configurations: ${response.status} ${response.statusText}`
    );
  }

  return response.json();
}

/**
 * Fetches a specific recurring session configuration by ID from GET /api/sessions/{id}.
 */
export async function fetchSessionById(id: number): Promise<SessionConfigResponseDto> {
  const response = await fetch(`/api/sessions/${id}`, {
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    let errorMessage = `Failed to fetch session: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

/**
 * Updates a recurring session configuration via PUT /api/sessions/{id}.
 * Strictly requires STAFF or ADMIN role.
 */
export async function updateSessionConfig(
  id: number,
  payload: UpdateSessionConfigRequestDto
): Promise<SessionConfigResponseDto> {
  const token = getStoredAuthToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`/api/sessions/${id}`, {
    method: 'PUT',
    headers,
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    let errorMessage = `Failed to update session rules: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

/**
 * Fetches dated time slots for a specific date (YYYY-MM-DD) from GET /api/slots?date=YYYY-MM-DD.
 */
export async function fetchSlots(date: string): Promise<TimeSlotResponseDto[]> {
  const response = await fetch(`/api/slots?date=${encodeURIComponent(date)}`, {
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    let errorMessage = `Failed to fetch slots: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON error body fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

export interface SlotGenerationResponseDto {
  date: string;
  totalSlots: number;
  createdSlots: number;
  existingSlots: number;
  message: string;
}

/**
 * Generates time slots for a given calendar date via POST /api/slots/generate.
 * Strictly requires STAFF or ADMIN role.
 */
export async function generateSlots(date: string): Promise<SlotGenerationResponseDto> {
  const token = getStoredAuthToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch('/api/slots/generate', {
    method: 'POST',
    headers,
    body: JSON.stringify({ date: date.trim() }),
  });

  if (!response.ok) {
    let errorMessage = `Failed to generate slots: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON error body fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

export type UserRole = 'STUDENT' | 'STAFF' | 'ADMIN';

export interface StudentResponseDto {
  id: number;
  fullName: string;
  universityEmail: string;
  admissionNumber: string;
  role: UserRole;
  weeklyCredits: number;
  reliabilityRating: number | null;
  active: boolean;
}

export interface BookingPlayerAssignmentDto {
  studentId: number;
  spotNumber: number;
}

export interface CreateBookingRequestDto {
  bookingStudentId: number;
  timeSlotId: number;
  players: BookingPlayerAssignmentDto[];
  idempotencyKey?: string | null;
}

export interface BookingPlayerResponseDto {
  studentId: number;
  studentName: string;
  admissionNumber: string;
  spotNumber: number;
  qrToken: string;
  checkedIn: boolean;
  creditsDeducted: number;
  remainingWeeklyCredits: number;
}

export type BookingStatusDto = 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';

export interface BookingResponseDto {
  bookingId: number;
  timeSlotId: number;
  courtNumber: number | null;
  slotDate: string;
  startTime: string;
  endTime: string;
  bookingStudentId: number;
  bookingStudentName: string;
  status: BookingStatusDto;
  creditsDeductedPerPlayer: number;
  players: BookingPlayerResponseDto[];
  createdAt: string;
}

/**
 * Privacy-safe representation of an eligible teammate candidate from GET /api/students.
 * Only contains id, display name, and eligibility status to safeguard student privacy.
 */
export interface TeammateOptionDto {
  id: number;
  fullName: string;
  eligible: boolean;
}

/**
 * Fetches registered active students for teammate group selection in a privacy-safe format from GET /api/students.
 * Returns only database ID, display name, and eligibility status.
 */
export async function fetchStudents(): Promise<TeammateOptionDto[]> {
  const response = await fetch('/api/students', {
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    let errorMessage = `Failed to fetch teammates: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

/**
 * Fetches student profile and current credit balance from GET /api/students/{id}.
 */
export async function fetchStudentById(id: number): Promise<StudentResponseDto> {
  const token = getStoredAuthToken();
  const headers: Record<string, string> = {
    Accept: 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`/api/students/${id}`, {
    headers,
  });

  if (!response.ok) {
    let errorMessage = `Failed to fetch student profile: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

/**
 * Atomically confirms a court booking for a group from POST /api/bookings.
 */
export async function createBooking(request: CreateBookingRequestDto): Promise<BookingResponseDto> {
  const token = getStoredAuthToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch('/api/bookings', {
    method: 'POST',
    headers,
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    let errorMessage = `Failed to confirm booking: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

/**
 * Fetches booking details from GET /api/bookings/{id}.
 */
export async function fetchBookingById(id: number): Promise<BookingResponseDto> {
  const token = getStoredAuthToken();
  const headers: Record<string, string> = {
    Accept: 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`/api/bookings/${id}`, {
    headers,
  });

  if (!response.ok) {
    let errorMessage = `Failed to fetch booking: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

export interface RegisterStudentRequestDto {
  fullName: string;
  universityEmail: string;
  admissionNumber: string;
  password: string;
}

export interface RegistrationResponseDto {
  studentId: number;
  fullName: string;
  universityEmail: string;
  admissionNumber: string;
  emailVerified: boolean;
  message: string;
}

export interface VerifyEmailRequestDto {
  token: string;
}

export interface VerifyEmailResponseDto {
  studentId: number;
  fullName: string;
  universityEmail: string;
  emailVerified: boolean;
  weeklyCredits: number;
  message: string;
}

/**
 * Registers a new student account using POST /api/auth/register.
 */
export async function registerStudent(
  request: RegisterStudentRequestDto
): Promise<RegistrationResponseDto> {
  const response = await fetch('/api/auth/register', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    let errorMessage = `Registration failed: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

/**
 * Verifies a student's university email address using POST /api/auth/verify-email.
 */
export async function verifyEmail(token: string): Promise<VerifyEmailResponseDto> {
  const response = await fetch('/api/auth/verify-email', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify({ token: token.trim() }),
  });

  if (!response.ok) {
    let errorMessage = `Email verification failed: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

/**
 * Verifies a student's university email address via GET /api/auth/verify-email?token=...
 */
export async function verifyEmailByQueryParam(token: string): Promise<VerifyEmailResponseDto> {
  const response = await fetch(`/api/auth/verify-email?token=${encodeURIComponent(token.trim())}`, {
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    let errorMessage = `Email verification failed: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

export interface CheckInRequestDto {
  qrToken: string;
  staffIdentifier?: string;
}

export interface CheckInResponseDto {
  success: boolean;
  message: string;
  alreadyCheckedIn: boolean;
  bookingId: number;
  bookingPlayerId: number;
  studentId: number;
  studentName: string;
  admissionNumber: string;
  spotNumber: number;
  courtId: number;
  courtNumber: number;
  slotStartTime: string;
  slotEndTime: string;
  checkedInAt: string;
  checkedInBy: string;
}

export interface PenalizedPlayerDto {
  bookingPlayerId: number;
  bookingId: number;
  studentId: number;
  studentName: string;
  admissionNumber: string;
  spotNumber: number;
  courtNumber: number;
  slotStartTime: string;
  forfeitedCredits: number;
  updatedReliabilityRating: number;
}

export interface NoShowProcessingResponseDto {
  processedCount: number;
  penalizedPlayers: PenalizedPlayerDto[];
  message: string;
}

export interface ProcessNoShowsRequestDto {
  timeSlotId?: number;
}

export interface LoginRequestDto {
  identifier?: string;
  usernameOrEmail?: string;
  password: string;
}

export interface LoginResponseDto {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  id: number;
  studentId: number;
  fullName: string;
  email: string;
  universityEmail: string;
  admissionNumber: string;
  role: 'STUDENT' | 'STAFF' | 'ADMIN';
  weeklyCredits?: number;
  reliabilityRating?: number | null;
  emailVerified?: boolean;
}

const AUTH_TOKEN_KEY = 'badminton_auth_token';
const AUTH_USER_KEY = 'badminton_auth_user';

let inMemoryToken: string | null = null;
let inMemoryUser: LoginResponseDto | null = null;

export function getStoredAuthToken(): string | null {
  try {
    if (typeof localStorage !== 'undefined') {
      const val = localStorage.getItem(AUTH_TOKEN_KEY);
      if (val !== null) return val;
    }
  } catch {
    // fallback to memory
  }
  return inMemoryToken;
}

export function setStoredAuthToken(token: string | null): void {
  try {
    if (typeof localStorage !== 'undefined') {
      if (token) {
        localStorage.setItem(AUTH_TOKEN_KEY, token);
      } else {
        localStorage.removeItem(AUTH_TOKEN_KEY);
      }
    }
  } catch {
    // fallback to memory
  }
  inMemoryToken = token;
}

export function getStoredAuthUser(): LoginResponseDto | null {
  try {
    if (typeof localStorage !== 'undefined') {
      const raw = localStorage.getItem(AUTH_USER_KEY);
      if (raw) return JSON.parse(raw);
    }
  } catch {
    // fallback to memory
  }
  return inMemoryUser;
}

export function setStoredAuthUser(user: LoginResponseDto | null): void {
  try {
    if (typeof localStorage !== 'undefined') {
      if (user) {
        localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
      } else {
        localStorage.removeItem(AUTH_USER_KEY);
      }
    }
  } catch {
    // fallback to memory
  }
  inMemoryUser = user;
}

export function clearStoredAuth(): void {
  setStoredAuthToken(null);
  setStoredAuthUser(null);
}

/**
 * Authenticates a user with email/admission number and password via POST /api/auth/login.
 */
export async function login(request: LoginRequestDto): Promise<LoginResponseDto> {
  const identifier = (request.identifier || request.usernameOrEmail || '').trim();
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify({
      identifier,
      usernameOrEmail: identifier,
      password: request.password,
    }),
  });

  if (!response.ok) {
    let errorMessage = `Login failed: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  const raw = await response.json();
  const data: LoginResponseDto = {
    token: raw.token,
    tokenType: raw.tokenType || 'Bearer',
    expiresInSeconds: raw.expiresInSeconds ?? 86400,
    id: raw.id ?? raw.studentId,
    studentId: raw.id ?? raw.studentId,
    fullName: raw.fullName,
    email: raw.email ?? raw.universityEmail,
    universityEmail: raw.email ?? raw.universityEmail,
    admissionNumber: raw.admissionNumber,
    role: raw.role,
    weeklyCredits: raw.weeklyCredits ?? 6,
    reliabilityRating: raw.reliabilityRating ?? null,
    emailVerified: raw.emailVerified ?? true,
  };
  setStoredAuthToken(data.token);
  setStoredAuthUser(data);
  return data;
}

/**
 * Staff verification: checks in a player using their scanned QR token via POST /api/check-in.
 * Authenticated via Bearer JWT token (requires STAFF or ADMIN role).
 */
export async function checkInPlayer(request: CheckInRequestDto): Promise<CheckInResponseDto> {
  const token = getStoredAuthToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch('/api/check-in', {
    method: 'POST',
    headers,
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    let errorMessage = `Check-in failed: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

/**
 * Triggers batch processing of no-shows for slots whose 10-minute grace period has passed via POST /api/check-in/process-no-shows.
 * Authenticated via Bearer JWT token (requires STAFF or ADMIN role).
 */
export async function processNoShows(
  request?: ProcessNoShowsRequestDto
): Promise<NoShowProcessingResponseDto> {
  const token = getStoredAuthToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch('/api/check-in/process-no-shows', {
    method: 'POST',
    headers,
    body: JSON.stringify(request || {}),
  });

  if (!response.ok) {
    let errorMessage = `No-show processing failed: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

export interface JoinQueueRequestDto {
  studentId?: number;
  date: string;
  sessionType: SessionTypeDto;
}

export interface QueueEntryResponseDto {
  queueEntryId: number;
  queueId: number;
  studentId: number;
  status: 'WAITING' | 'ADMITTED' | 'EXPIRED' | 'COMPLETED' | 'CANCELLED';
  joinedAt: string;
  admittedAt: string | null;
  expiresAt: string | null;
  positionInLine: number;
  remainingSeconds: number;
  priorityTier?: 'STANDARD' | 'REDUCED';
  message: string;
}

export interface QueueStatusResponseDto {
  queueId: number | null;
  sessionDate: string;
  sessionType: SessionTypeDto;
  totalEntries: number;
  waitingCount: number;
  hasActiveOpportunity: boolean;
  remainingSeconds: number;
  queueOpenDateTime?: string;
  bookingStartDateTime?: string;
  sessionEndDateTime?: string;
  serverTime?: string;
  isQueueOpen?: boolean;
  isBookingOpen?: boolean;
  message: string;
}

/**
 * Enters the authenticated student into the booking queue via POST /api/queue/join.
 */
export async function joinQueue(request: JoinQueueRequestDto): Promise<QueueEntryResponseDto> {
  const token = getStoredAuthToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch('/api/queue/join', {
    method: 'POST',
    headers,
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    let errorMessage = `Failed to join queue: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

/**
 * Fetches the current public status and timing windows for a session queue via GET /api/queue/status.
 */
export async function fetchQueueStatus(
  date: string,
  sessionType: SessionTypeDto
): Promise<QueueStatusResponseDto> {
  const params = new URLSearchParams({
    date,
    sessionType,
  });

  const response = await fetch(`/api/queue/status?${params.toString()}`, {
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    let errorMessage = `Failed to fetch queue status: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

/**
 * Checks an individual student's queue entry, live FIFO line position, priority tier, or admission window via GET /api/queue/position.
 */
export async function fetchStudentQueuePosition(
  date: string,
  sessionType: SessionTypeDto,
  studentId?: number
): Promise<QueueEntryResponseDto> {
  const token = getStoredAuthToken();
  const headers: Record<string, string> = {
    Accept: 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const params = new URLSearchParams({
    date,
    sessionType,
  });
  if (studentId != null) {
    params.set('studentId', String(studentId));
  }

  const response = await fetch(`/api/queue/position?${params.toString()}`, {
    headers,
  });

  if (!response.ok) {
    let errorMessage = `Failed to fetch queue position: ${response.status} ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson && typeof errorJson.error === 'string') {
        errorMessage = errorJson.error;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(response.status, response.statusText, errorMessage);
  }

  return response.json();
}

