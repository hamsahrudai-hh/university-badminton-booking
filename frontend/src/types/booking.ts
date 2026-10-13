export type SessionType = 'morning' | 'evening';

export interface TimeSlot {
  id: string;
  session: SessionType;
  startTime: string;
  endTime: string;
  label: string;
  realTimeSlotId?: number;
  courtSlotMap?: Record<number, number>; // courtNumber or courtId -> database slot ID
  isoStartTime?: string;
  isoEndTime?: string;
  slotDate?: string;
}

export type SpotStatus = 'available' | 'booked' | 'selected';

export interface PlayerSpot {
  spotNumber: number; // 1 to 4
  status: SpotStatus;
  playerName?: string;
}

export interface CourtSlotData {
  courtId: number;
  courtName: string;
  type: string;
  spots: PlayerSpot[];
}

export interface StudentProfile {
  name: string;
  admissionNumber: string;
  email: string;
  weeklyCreditsRemaining: number;
  weeklyCreditsTotal: number;
  priorityStatus: 'High Priority' | 'Reduced Priority (0 Credits)';
  activeBookingsCount: number;
  nextCreditResetDay: string;
  numericId?: number;
  id?: string;
  isRealAccount?: boolean;
}

export interface RegisteredStudent {
  id: string;
  name: string;
  admissionNumber?: string;
  email?: string;
  isEligible: boolean;
  hasConflict?: boolean;
  numericId?: number;
  weeklyCredits?: number;
}

export interface GroupPlayerAssignment {
  student: RegisteredStudent;
  spotNumber: number;
}

export interface UpcomingBooking {
  id: string;
  slot: TimeSlot;
  courtId: number;
  courtName: string;
  players: {
    studentId: string;
    name: string;
    admissionNumber: string;
    spotNumber: number;
    qrToken: string;
    checkedIn: boolean;
  }[];
  bookingUser: string;
  creditsDeducted: number;
  status: 'Confirmed' | 'Completed' | 'No-Show';
}

export type WizardStep = 1 | 2 | 3 | 4;

export interface BookingWizardData {
  slot: TimeSlot | null;
  courtId: number | null;
  courtName: string | null;
  players: GroupPlayerAssignment[];
  realTimeSlotId?: number | null;
}
