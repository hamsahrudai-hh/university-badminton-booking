import type {
  StudentProfile,
  TimeSlot,
  CourtSlotData,
  RegisteredStudent,
  UpcomingBooking,
} from '../types/booking';

export const MOCK_STUDENT: StudentProfile = {
  name: 'Alex Chen',
  admissionNumber: '2024BCS1042',
  email: 'alex.chen@university.edu',
  weeklyCreditsRemaining: 6,
  weeklyCreditsTotal: 6,
  priorityStatus: 'High Priority',
  activeBookingsCount: 1,
  nextCreditResetDay: 'Monday, 12:00 AM',
};

export const REGISTERED_STUDENTS: RegisteredStudent[] = [
  {
    id: 's-1',
    name: 'Alex Chen (You)',
    admissionNumber: '2024BCS1042',
    email: 'alex.chen@university.edu',
    isEligible: true,
  },
  {
    id: 's-2',
    name: 'Priya Sharma',
    admissionNumber: '2024BCS1088',
    email: 'priya.sharma@university.edu',
    isEligible: true,
  },
  {
    id: 's-3',
    name: 'Rohan Mehta',
    admissionNumber: '2024BCS1102',
    email: 'rohan.mehta@university.edu',
    isEligible: true,
  },
  {
    id: 's-4',
    name: 'Ananya Gupta',
    admissionNumber: '2024BCS1145',
    email: 'ananya.gupta@university.edu',
    isEligible: true,
  },
  {
    id: 's-5',
    name: 'David Kim',
    admissionNumber: '2024BCS1067',
    email: 'david.kim@university.edu',
    isEligible: true,
  },
  {
    id: 's-6',
    name: 'Vikram Joshi',
    admissionNumber: '2024BCS1201',
    email: 'vikram.joshi@university.edu',
    isEligible: true,
  },
];

export const TIME_SLOTS: TimeSlot[] = [
  // Morning Session (6:00 AM - 8:30 AM) - 5 Slots
  { id: 'm-1', session: 'morning', startTime: '06:00 AM', endTime: '06:30 AM', label: '06:00 AM – 06:30 AM' },
  { id: 'm-2', session: 'morning', startTime: '06:30 AM', endTime: '07:00 AM', label: '06:30 AM – 07:00 AM' },
  { id: 'm-3', session: 'morning', startTime: '07:00 AM', endTime: '07:30 AM', label: '07:00 AM – 07:30 AM' },
  { id: 'm-4', session: 'morning', startTime: '07:30 AM', endTime: '08:00 AM', label: '07:30 AM – 08:00 AM' },
  { id: 'm-5', session: 'morning', startTime: '08:00 AM', endTime: '08:30 AM', label: '08:00 AM – 08:30 AM' },

  // Evening Session (3:30 PM - 7:00 PM) - 7 Slots
  { id: 'e-1', session: 'evening', startTime: '03:30 PM', endTime: '04:00 PM', label: '03:30 PM – 04:00 PM' },
  { id: 'e-2', session: 'evening', startTime: '04:00 PM', endTime: '04:30 PM', label: '04:00 PM – 04:30 PM' },
  { id: 'e-3', session: 'evening', startTime: '04:30 PM', endTime: '05:00 PM', label: '04:30 PM – 05:00 PM' },
  { id: 'e-4', session: 'evening', startTime: '05:00 PM', endTime: '05:30 PM', label: '05:00 PM – 05:30 PM' },
  { id: 'e-5', session: 'evening', startTime: '05:30 PM', endTime: '06:00 PM', label: '05:30 PM – 06:00 PM' },
  { id: 'e-6', session: 'evening', startTime: '06:00 PM', endTime: '06:30 PM', label: '06:00 PM – 06:30 PM' },
  { id: 'e-7', session: 'evening', startTime: '06:30 PM', endTime: '07:00 PM', label: '06:30 PM – 07:00 PM' },
];

export const COURTS_META = [
  { id: 1, name: 'Court 1', type: 'Synthetic Wooden' },
  { id: 2, name: 'Court 2', type: 'Synthetic Wooden' },
  { id: 3, name: 'Court 3', type: 'Rubber Mat' },
  { id: 4, name: 'Court 4', type: 'Rubber Mat' },
];

export function getMockCourtDataForSlot(slotId: string): CourtSlotData[] {
  const sampleBookedNames = ['Rohan M.', 'Priya S.', 'Karan V.', 'Ananya G.', 'David K.', 'Meera R.'];
  
  return COURTS_META.map((court, courtIndex) => {
    const spots = [1, 2, 3, 4].map((spotNum) => {
      // Deterministic occupancy
      const isBooked = (courtIndex + spotNum + slotId.charCodeAt(slotId.length - 1)) % 5 === 0;
      return {
        spotNumber: spotNum,
        status: (isBooked ? 'booked' : 'available') as 'available' | 'booked' | 'selected',
        playerName: isBooked ? sampleBookedNames[(courtIndex + spotNum) % sampleBookedNames.length] : undefined,
      };
    });

    return {
      courtId: court.id,
      courtName: court.name,
      type: court.type,
      spots,
    };
  });
}

// Initial sample upcoming booking for demonstration
export const INITIAL_UPCOMING_BOOKING: UpcomingBooking = {
  id: 'UB-8831',
  slot: TIME_SLOTS[1], // 06:30 AM - 07:00 AM
  courtId: 2,
  courtName: 'Court 2 (Synthetic Wooden)',
  bookingUser: 'Alex Chen',
  creditsDeducted: 1,
  status: 'Confirmed',
  players: [
    {
      studentId: 's-1',
      name: 'Alex Chen',
      admissionNumber: '2024BCS1042',
      spotNumber: 1,
      qrToken: 'TOKEN_AC_8831',
      checkedIn: false,
    },
    {
      studentId: 's-2',
      name: 'Priya Sharma',
      admissionNumber: '2024BCS1088',
      spotNumber: 2,
      qrToken: 'TOKEN_PS_8831',
      checkedIn: false,
    },
  ],
};
