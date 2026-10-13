import { useState, useMemo, useEffect } from 'react';
import { Header } from './components/Header';
import { HomeDashboard } from './components/HomeDashboard';
import { PlaceholderModal } from './components/PlaceholderModal';
import { QRPassModal } from './components/QRPassModal';
import { StudentLoginScreen } from './components/auth/StudentLoginScreen';
import { StudentRegistrationScreen } from './components/auth/StudentRegistrationScreen';
import { EmailVerificationScreen } from './components/auth/EmailVerificationScreen';
import { StaffCheckInScreen } from './components/staff/StaffCheckInScreen';
import { AdminCourtManagementScreen } from './components/admin/AdminCourtManagementScreen';
import { AdminSlotManagementScreen } from './components/admin/AdminSlotManagementScreen';
import { AdminSessionRulesScreen } from './components/admin/AdminSessionRulesScreen';
import { WizardProgress } from './components/wizard/WizardProgress';
import { StepSlotSelection } from './components/wizard/StepSlotSelection';
import { StepCourtSelection } from './components/wizard/StepCourtSelection';
import { StepGroupSelection } from './components/wizard/StepGroupSelection';
import { StepReviewConfirm } from './components/wizard/StepReviewConfirm';
import { BookingQueueModal } from './components/queue/BookingQueueModal';
import { QueueCountdownBanner } from './components/queue/QueueCountdownBanner';
import {
  TIME_SLOTS,
  INITIAL_UPCOMING_BOOKING,
  getMockCourtDataForSlot,
} from './data/mockData';
import {
  createBooking,
  fetchStudentById,
  fetchStudentQueuePosition,
  getStoredAuthToken,
  getStoredAuthUser,
  clearStoredAuth,
  ApiError,
  type CreateBookingRequestDto,
  type BookingPlayerAssignmentDto,
  type LoginResponseDto,
  type QueueEntryResponseDto,
} from './api/client';
import type {
  WizardStep,
  BookingWizardData,
  StudentProfile,
  UpcomingBooking,
  TimeSlot,
  GroupPlayerAssignment,
} from './types/booking';

const CURRENT_YEAR = 2026;

const GUEST_STUDENT_PROFILE: StudentProfile = {
  name: 'Guest Student',
  admissionNumber: 'NOT SIGNED IN',
  email: 'Sign in to access court bookings',
  weeklyCreditsRemaining: 0,
  weeklyCreditsTotal: 6,
  priorityStatus: 'Reduced Priority (0 Credits)',
  activeBookingsCount: 0,
  nextCreditResetDay: 'Monday, 12:00 AM',
  numericId: undefined,
  id: 'guest',
  isRealAccount: false,
};

function getInitialAuthFromUrl(): { token: string | null; screen: 'none' | 'login' | 'register' | 'verify-email' } {
  try {
    const searchParams = new URLSearchParams(window.location.search);
    const urlToken = searchParams.get('token') || searchParams.get('verifyToken');
    if (urlToken && urlToken.trim().length > 0) {
      return { token: urlToken.trim(), screen: 'verify-email' };
    }
  } catch {
    // Graceful fallback for non-browser/SSR environments
  }
  return { token: null, screen: 'none' };
}

export function App() {
  // Initial state derived from URL query parameters (e.g. ?token=...)
  const [initialAuth] = useState(getInitialAuthFromUrl);
  const [authScreen, setAuthScreen] = useState<'none' | 'login' | 'register' | 'verify-email'>(initialAuth.screen);
  const [verificationToken, setVerificationToken] = useState<string | null>(initialAuth.token);
  const [loginNoticeMessage, setLoginNoticeMessage] = useState<string | null>(null);

  // Authenticated User State
  const [currentUser, setCurrentUser] = useState<LoginResponseDto | null>(() => getStoredAuthUser());

  // Navigation / View State
  const [isWizardActive, setIsWizardActive] = useState<boolean>(false);
  const [isStaffCheckInActive, setIsStaffCheckInActive] = useState<boolean>(false);
  const [isCourtManagementActive, setIsCourtManagementActive] = useState<boolean>(false);
  const [isSlotManagementActive, setIsSlotManagementActive] = useState<boolean>(false);
  const [isSessionRulesActive, setIsSessionRulesActive] = useState<boolean>(false);
  const [staffCheckInPresetToken, setStaffCheckInPresetToken] = useState<string>('');
  const [secondaryNavDestination, setSecondaryNavDestination] = useState<string | null>(null);
  const [showQRModal, setShowQRModal] = useState<boolean>(false);
  const [successToast, setSuccessToast] = useState<string | null>(null);

  // Booking submission state
  const [isSubmittingBooking, setIsSubmittingBooking] = useState<boolean>(false);
  const [bookingSubmitError, setBookingSubmitError] = useState<string | null>(null);

  // Booking Queue Admission & Window State
  const [showQueueModal, setShowQueueModal] = useState<boolean>(false);
  const [activeQueueEntry, setActiveQueueEntry] = useState<QueueEntryResponseDto | null>(null);
  const [queueRemainingSeconds, setQueueRemainingSeconds] = useState<number | null>(null);

  // Home Dashboard Data
  const [studentProfile, setStudentProfile] = useState<StudentProfile>(() => {
    const stored = getStoredAuthUser();
    if (stored?.id) {
      return {
        name: stored.fullName,
        admissionNumber: stored.admissionNumber,
        email: stored.email || stored.universityEmail,
        weeklyCreditsRemaining: stored.weeklyCredits ?? 6,
        weeklyCreditsTotal: 6,
        priorityStatus: (stored.weeklyCredits ?? 6) > 0 ? 'High Priority' : 'Reduced Priority (0 Credits)',
        activeBookingsCount: 0,
        nextCreditResetDay: 'Monday, 12:00 AM',
        numericId: stored.id,
        id: String(stored.id),
        isRealAccount: true,
      };
    }
    return GUEST_STUDENT_PROFILE;
  });

  const [upcomingBooking, setUpcomingBooking] = useState<UpcomingBooking | null>(INITIAL_UPCOMING_BOOKING);
  const [isBookingWindowOpen, setIsBookingWindowOpen] = useState<boolean>(true);

  // Wizard Step State
  const [wizardStep, setWizardStep] = useState<WizardStep>(1);

  // Wizard Booking Selection State
  const [wizardData, setWizardData] = useState<BookingWizardData>(() => {
    const stored = getStoredAuthUser();
    const initialPlayers: GroupPlayerAssignment[] = stored?.id
      ? [
          {
            student: {
              id: String(stored.id),
              numericId: stored.id,
              name: stored.fullName,
              admissionNumber: stored.admissionNumber,
              email: stored.email || stored.universityEmail,
              isEligible: (stored.weeklyCredits ?? 6) >= 1,
              weeklyCredits: stored.weeklyCredits ?? 6,
            },
            spotNumber: 1,
          },
        ]
      : [];
    return {
      slot: TIME_SLOTS[0],
      courtId: 1,
      courtName: 'Court 1',
      players: initialPlayers,
    };
  });

  // 1. Session verification on startup: validate stored token against backend
  useEffect(() => {
    const token = getStoredAuthToken();
    const stored = getStoredAuthUser();
    if (token && stored?.id) {
      fetchStudentById(stored.id)
        .then((fresh) => {
          const updatedProfile: StudentProfile = {
            name: fresh.fullName,
            admissionNumber: fresh.admissionNumber,
            email: fresh.universityEmail,
            weeklyCreditsRemaining: fresh.weeklyCredits ?? 6,
            weeklyCreditsTotal: 6,
            priorityStatus: (fresh.weeklyCredits ?? 0) > 0 ? 'High Priority' : 'Reduced Priority (0 Credits)',
            activeBookingsCount: 0,
            nextCreditResetDay: 'Monday, 12:00 AM',
            numericId: fresh.id,
            id: String(fresh.id),
            isRealAccount: true,
          };
          setStudentProfile(updatedProfile);
          setCurrentUser((prev) => (prev ? { ...prev, ...fresh, id: fresh.id, studentId: fresh.id } : prev));
        })
        .catch((err) => {
          if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
            console.warn('Session expired or invalidated by server, clearing storage.');
            clearStoredAuth();
            setCurrentUser(null);
            setStudentProfile(GUEST_STUDENT_PROFILE);
            setLoginNoticeMessage('Your session has expired. Please sign in again.');
          }
        });
    }
  }, []);

  // Calculate court availability for the currently selected slot
  const currentSlotCourts = useMemo(() => {
    if (!wizardData.slot) return [];
    return getMockCourtDataForSlot(wizardData.slot.id);
  }, [wizardData.slot]);

  // Handlers for Auth Lifecycle
  const handleLoginSuccess = async (user: LoginResponseDto) => {
    setCurrentUser(user);
    setLoginNoticeMessage(null);
    setAuthScreen('none');

    try {
      const freshStudent = await fetchStudentById(user.id);
      const realProfile: StudentProfile = {
        name: freshStudent.fullName,
        admissionNumber: freshStudent.admissionNumber,
        email: freshStudent.universityEmail,
        weeklyCreditsRemaining: freshStudent.weeklyCredits ?? 6,
        weeklyCreditsTotal: 6,
        priorityStatus: (freshStudent.weeklyCredits ?? 0) > 0 ? 'High Priority' : 'Reduced Priority (0 Credits)',
        activeBookingsCount: 0,
        nextCreditResetDay: 'Monday, 12:00 AM',
        numericId: freshStudent.id,
        id: String(freshStudent.id),
        isRealAccount: true,
      };
      setStudentProfile(realProfile);
    } catch {
      const fallbackProfile: StudentProfile = {
        name: user.fullName,
        admissionNumber: user.admissionNumber,
        email: user.email || user.universityEmail,
        weeklyCreditsRemaining: user.weeklyCredits ?? 6,
        weeklyCreditsTotal: 6,
        priorityStatus: (user.weeklyCredits ?? 6) > 0 ? 'High Priority' : 'Reduced Priority (0 Credits)',
        activeBookingsCount: 0,
        nextCreditResetDay: 'Monday, 12:00 AM',
        numericId: user.id,
        id: String(user.id),
        isRealAccount: true,
      };
      setStudentProfile(fallbackProfile);
    }

    setSuccessToast(`Welcome, ${user.fullName}! Signed in successfully.`);
    setTimeout(() => setSuccessToast(null), 5000);
  };

  const handleSignOut = () => {
    clearStoredAuth();
    setCurrentUser(null);
    setActiveQueueEntry(null);
    setQueueRemainingSeconds(null);
    setStudentProfile(GUEST_STUDENT_PROFILE);
    setIsWizardActive(false);
    setIsCourtManagementActive(false);
    setIsSlotManagementActive(false);
    setIsSessionRulesActive(false);
    setWizardStep(1);
    setAuthScreen('none');
    setSuccessToast('Signed out of student account.');
    setTimeout(() => setSuccessToast(null), 4000);
  };

  const handleWizardQueueExpired = () => {
    setIsWizardActive(false);
    setWizardStep(1);
    setActiveQueueEntry((prev) => (prev ? { ...prev, status: 'EXPIRED' } : null));
    setLoginNoticeMessage('Your 60-second booking opportunity has expired. Please rejoin the queue to obtain a new window.');
    setShowQueueModal(true);
  };

  const handleProceedFromQueueToBooking = () => {
    setShowQueueModal(false);
    setWizardStep(1);
    setIsWizardActive(true);
  };

  // Sync queue entry on sign-in or student ID change
  useEffect(() => {
    if (!currentUser?.id) {
      const timer = setTimeout(() => {
        setActiveQueueEntry(null);
        setQueueRemainingSeconds(null);
      }, 0);
      return () => clearTimeout(timer);
    }
    const today = new Date().toISOString().slice(0, 10);
    fetchStudentQueuePosition(today, 'MORNING', currentUser.id)
      .then((entry) => {
        setActiveQueueEntry(entry);
        if (entry.status === 'ADMITTED' && entry.remainingSeconds != null) {
          setQueueRemainingSeconds(entry.remainingSeconds);
        }
      })
      .catch(() => {
        fetchStudentQueuePosition(today, 'EVENING', currentUser.id)
          .then((entry) => {
            setActiveQueueEntry(entry);
            if (entry.status === 'ADMITTED' && entry.remainingSeconds != null) {
              setQueueRemainingSeconds(entry.remainingSeconds);
            }
          })
          .catch(() => {
            // Not in queue
          });
      });
  }, [currentUser?.id]);

  // Handle countdown when queue status is ADMITTED
  useEffect(() => {
    if (activeQueueEntry?.status !== 'ADMITTED') {
      const timer = setTimeout(() => {
        setQueueRemainingSeconds(null);
      }, 0);
      return () => clearTimeout(timer);
    }
    if (activeQueueEntry.expiresAt) {
      const targetTime = new Date(activeQueueEntry.expiresAt).getTime();
      const interval = window.setInterval(() => {
        const diff = Math.max(0, Math.round((targetTime - Date.now()) / 1000));
        setQueueRemainingSeconds(diff);
        if (diff <= 0) {
          window.clearInterval(interval);
          setActiveQueueEntry((prev) => (prev ? { ...prev, status: 'EXPIRED' } : null));
          if (isWizardActive) {
            handleWizardQueueExpired();
          }
        }
      }, 1000);
      return () => window.clearInterval(interval);
    }
  }, [activeQueueEntry?.status, activeQueueEntry?.expiresAt, isWizardActive]);

  // Handler to start booking from home dashboard
  const handleStartBooking = () => {
    if (!currentUser?.id) {
      setLoginNoticeMessage('Please sign in with your university credentials to make a court reservation.');
      setAuthScreen('login');
      return;
    }

    // Strict Queue Admission Check:
    if (activeQueueEntry?.status !== 'ADMITTED') {
      setShowQueueModal(true);
      return;
    }

    setWizardStep(1);
    setIsWizardActive(true);
  };

  // Step 1: Select Slot with Revalidation
  const handleSelectSlot = (slot: TimeSlot) => {
    setWizardData((prev) => {
      const newSlotCourts = getMockCourtDataForSlot(slot.id);
      const courtStillValid = prev.courtId
        ? newSlotCourts.some(
            (c) => c.courtId === prev.courtId && c.spots.some((s) => s.status === 'available')
          )
        : false;

      const targetCourtId = courtStillValid ? prev.courtId : (prev.courtId ?? 1);
      const resolvedRealTimeSlotId =
        (targetCourtId && slot.courtSlotMap?.[targetCourtId]) ??
        slot.realTimeSlotId ??
        null;

      return {
        ...prev,
        slot,
        courtId: courtStillValid ? prev.courtId : null,
        courtName: courtStillValid ? prev.courtName : null,
        realTimeSlotId: resolvedRealTimeSlotId,
      };
    });
  };

  // Step 2: Select Court with Revalidation
  const handleSelectCourt = (courtId: number, courtName: string, realTimeSlotId?: number) => {
    setWizardData((prev) => {
      const resolvedSlotId =
        realTimeSlotId ??
        prev.slot?.courtSlotMap?.[courtId] ??
        prev.slot?.realTimeSlotId ??
        prev.realTimeSlotId ??
        null;

      return {
        ...prev,
        courtId,
        courtName,
        realTimeSlotId: resolvedSlotId,
        players: prev.players,
      };
    });
  };

  // Step 3: Update Players / Group Roster
  const handleUpdatePlayers = (players: GroupPlayerAssignment[]) => {
    setWizardData((prev) => ({
      ...prev,
      players,
    }));
  };

  // Step 4: Finalize & Confirm Real Booking via POST /api/bookings
  const handleConfirmBooking = async (idempotencyKey: string) => {
    if (!wizardData.slot || !wizardData.courtId || !wizardData.courtName) return;

    if (!currentUser?.id) {
      setLoginNoticeMessage('Your session has expired. Please sign in again to confirm this reservation.');
      setAuthScreen('login');
      setIsWizardActive(false);
      return;
    }

    setIsSubmittingBooking(true);
    setBookingSubmitError(null);

    try {
      // Find target numeric timeSlotId
      let targetTimeSlotId: number | null = wizardData.realTimeSlotId ?? null;
      if (!targetTimeSlotId && wizardData.courtId && wizardData.slot?.courtSlotMap?.[wizardData.courtId]) {
        targetTimeSlotId = wizardData.slot.courtSlotMap[wizardData.courtId];
      }
      if (!targetTimeSlotId && wizardData.slot?.realTimeSlotId) {
        targetTimeSlotId = wizardData.slot.realTimeSlotId;
      }
      if (!targetTimeSlotId && Number.isInteger(Number(wizardData.slot.id))) {
        targetTimeSlotId = Number(wizardData.slot.id);
      }

      if (!targetTimeSlotId) {
        throw new Error(
          'Missing database TimeSlot ID. Real backend bookings require a valid numeric TimeSlot ID from GET /api/slots.'
        );
      }

      // Check group size
      if (wizardData.players.length < 2 || wizardData.players.length > 4) {
        throw new Error('A reservation requires between 2 and 4 registered students.');
      }

      // Authoritative host identity derived directly from authenticated currentUser
      const bookingStudentId = currentUser.id;

      // Prepare player assignments
      const playerAssignments: BookingPlayerAssignmentDto[] = wizardData.players.map((p) => {
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

      // Ensure the authenticated booking host is in the player roster
      if (!playerAssignments.some((p) => p.studentId === bookingStudentId)) {
        playerAssignments.unshift({ studentId: bookingStudentId, spotNumber: 1 });
      }

      const requestPayload: CreateBookingRequestDto = {
        bookingStudentId,
        timeSlotId: targetTimeSlotId,
        players: playerAssignments,
        idempotencyKey,
      };

      const bookingResponse = await createBooking(requestPayload);

      // Fetch the real persisted balance for the booking student from GET /api/students/{id}
      try {
        const updatedStudent = await fetchStudentById(bookingStudentId);
        setStudentProfile((prev) => ({
          ...prev,
          weeklyCreditsRemaining: updatedStudent.weeklyCredits,
          priorityStatus: updatedStudent.weeklyCredits > 0 ? 'High Priority' : 'Reduced Priority (0 Credits)',
          activeBookingsCount: prev.activeBookingsCount + 1,
        }));
      } catch (profileErr) {
        console.warn('Could not refresh student profile from backend:', profileErr);
      }

      // Create new upcoming booking from the confirmed backend response
      const newBooking: UpcomingBooking = {
        id: `UB-${bookingResponse.bookingId}`,
        slot: wizardData.slot,
        courtId: bookingResponse.courtNumber ?? wizardData.courtId,
        courtName: `Court ${bookingResponse.courtNumber ?? wizardData.courtId}`,
        bookingUser: bookingResponse.bookingStudentName,
        creditsDeducted: bookingResponse.creditsDeductedPerPlayer,
        status: 'Confirmed',
        players: bookingResponse.players.map((p) => ({
          studentId: String(p.studentId),
          name: p.studentName,
          admissionNumber: p.admissionNumber,
          spotNumber: p.spotNumber,
          qrToken: p.qrToken,
          checkedIn: p.checkedIn,
        })),
      };

      setUpcomingBooking(newBooking);
      setActiveQueueEntry((prev) => (prev ? { ...prev, status: 'COMPLETED' } : null));
      setQueueRemainingSeconds(null);
      setIsWizardActive(false);
      setSuccessToast(
        `Reservation #${bookingResponse.bookingId} confirmed for ${newBooking.courtName} (${newBooking.slot.label})! 1 Credit deducted per player.`
      );
      setTimeout(() => setSuccessToast(null), 5000);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        clearStoredAuth();
        setCurrentUser(null);
        setStudentProfile(GUEST_STUDENT_PROFILE);
        setIsWizardActive(false);
        setLoginNoticeMessage('Your authentication session has expired. Please sign in again to complete your booking.');
        setAuthScreen('login');
        return;
      }
      const errorMessage = err instanceof Error ? err.message : 'Failed to confirm booking due to an unknown error.';
      if (
        errorMessage.toLowerCase().includes('expired') ||
        errorMessage.toLowerCase().includes('booking opportunity') ||
        errorMessage.toLowerCase().includes('booking queue')
      ) {
        setActiveQueueEntry((prev) => (prev ? { ...prev, status: 'EXPIRED' } : null));
      }
      setBookingSubmitError(errorMessage);
    } finally {
      setIsSubmittingBooking(false);
    }
  };

  const handleCancelWizard = () => {
    setIsWizardActive(false);
    setWizardStep(1);
  };

  const handleNavigateHome = () => {
    setAuthScreen('none');
    setIsWizardActive(false);
    setIsStaffCheckInActive(false);
    setIsCourtManagementActive(false);
    setIsSlotManagementActive(false);
    setIsSessionRulesActive(false);
    setStaffCheckInPresetToken('');
    setSecondaryNavDestination(null);
  };

  const handleNavigateLogin = () => {
    setAuthScreen('login');
    setIsWizardActive(false);
    setIsStaffCheckInActive(false);
    setIsCourtManagementActive(false);
    setIsSlotManagementActive(false);
    setIsSessionRulesActive(false);
  };

  const handleNavigateRegister = () => {
    setAuthScreen('register');
    setIsWizardActive(false);
    setIsStaffCheckInActive(false);
    setIsCourtManagementActive(false);
    setIsSlotManagementActive(false);
    setIsSessionRulesActive(false);
  };

  const handleNavigateVerifyEmail = (token?: string) => {
    if (token) {
      setVerificationToken(token);
    }
    setAuthScreen('verify-email');
    setIsWizardActive(false);
    setIsStaffCheckInActive(false);
    setIsCourtManagementActive(false);
    setIsSlotManagementActive(false);
    setIsSessionRulesActive(false);
  };

  const handleNavigateStaffCheckIn = (token?: string) => {
    setIsStaffCheckInActive(true);
    setIsCourtManagementActive(false);
    setIsSlotManagementActive(false);
    setIsSessionRulesActive(false);
    setStaffCheckInPresetToken(token || '');
    setIsWizardActive(false);
    setAuthScreen('none');
    setSecondaryNavDestination(null);
  };

  const handleNavigateCourtManagement = () => {
    setIsCourtManagementActive(true);
    setIsSlotManagementActive(false);
    setIsSessionRulesActive(false);
    setIsStaffCheckInActive(false);
    setIsWizardActive(false);
    setAuthScreen('none');
    setSecondaryNavDestination(null);
  };

  const handleNavigateSlotManagement = () => {
    setIsSlotManagementActive(true);
    setIsCourtManagementActive(false);
    setIsSessionRulesActive(false);
    setIsStaffCheckInActive(false);
    setIsWizardActive(false);
    setAuthScreen('none');
    setSecondaryNavDestination(null);
  };

  const handleNavigateSessionRules = () => {
    setIsSessionRulesActive(true);
    setIsSlotManagementActive(false);
    setIsCourtManagementActive(false);
    setIsStaffCheckInActive(false);
    setIsWizardActive(false);
    setAuthScreen('none');
    setSecondaryNavDestination(null);
  };

  return (
    <div className="min-h-screen bg-slate-50 text-slate-800 flex flex-col font-sans selection:bg-emerald-100 selection:text-emerald-900">
      {/* Toast Notification */}
      {successToast && (
        <div className="fixed top-3 right-3 z-50 rounded-xl bg-slate-900 px-3.5 py-2.5 text-xs font-medium text-white shadow-lg border border-slate-700 flex items-center gap-2">
          <span className="flex h-4 w-4 items-center justify-center rounded-full bg-emerald-500 text-white font-bold text-[10px]">
            ✓
          </span>
          <span>{successToast}</span>
        </div>
      )}

      {/* Header */}
      <Header
        studentName={studentProfile.name}
        admissionNumber={studentProfile.admissionNumber}
        userRole={currentUser?.role}
        isRealAccount={studentProfile.isRealAccount}
        isAuthenticated={Boolean(currentUser)}
        onNavigateHome={handleNavigateHome}
        onStartBooking={handleStartBooking}
        onOpenSecondaryNav={(dest) => {
          if (dest === 'My QR Passes') {
            setShowQRModal(true);
          } else {
            setSecondaryNavDestination(dest);
          }
        }}
        isWizardActive={isWizardActive}
        currentAuthScreen={authScreen}
        onNavigateLogin={handleNavigateLogin}
        onNavigateRegister={handleNavigateRegister}
        onNavigateVerifyEmail={() => handleNavigateVerifyEmail()}
        onSignOut={handleSignOut}
        isStaffCheckInActive={isStaffCheckInActive}
        onNavigateStaffCheckIn={() => handleNavigateStaffCheckIn()}
        isCourtManagementActive={isCourtManagementActive}
        onNavigateCourtManagement={handleNavigateCourtManagement}
        isSlotManagementActive={isSlotManagementActive}
        onNavigateSlotManagement={handleNavigateSlotManagement}
        isSessionRulesActive={isSessionRulesActive}
        onNavigateSessionRules={handleNavigateSessionRules}
      />

      {/* Content Area */}
      <main className="flex-1 pb-8">
        {authScreen === 'login' ? (
          <div className="px-4 sm:px-6">
            <StudentLoginScreen
              onLoginSuccess={handleLoginSuccess}
              onNavigateHome={handleNavigateHome}
              onNavigateToRegister={handleNavigateRegister}
              onNavigateToVerification={(prefill) => handleNavigateVerifyEmail(prefill)}
              noticeMessage={loginNoticeMessage}
            />
          </div>
        ) : authScreen === 'register' ? (
          <div className="px-4 sm:px-6">
            <StudentRegistrationScreen
              onNavigateHome={handleNavigateHome}
              onNavigateToVerification={(prefillToken) => handleNavigateVerifyEmail(prefillToken)}
              onNavigateToLogin={handleNavigateLogin}
            />
          </div>
        ) : authScreen === 'verify-email' ? (
          <div className="px-4 sm:px-6">
            <EmailVerificationScreen
              initialToken={verificationToken}
              onNavigateHome={handleNavigateHome}
              onStartBooking={() => {
                if (currentUser) {
                  setAuthScreen('none');
                  handleStartBooking();
                } else {
                  setLoginNoticeMessage('Email verified successfully! Please sign in with your credentials to start booking.');
                  setAuthScreen('login');
                }
              }}
              onNavigateToRegister={handleNavigateRegister}
              onNavigateToLogin={handleNavigateLogin}
            />
          </div>
        ) : isStaffCheckInActive ? (
          <StaffCheckInScreen
            onBackToDashboard={handleNavigateHome}
            suggestedToken={
              staffCheckInPresetToken ||
              upcomingBooking?.players?.[0]?.qrToken ||
              ''
            }
          />
        ) : isCourtManagementActive ? (
          <AdminCourtManagementScreen
            onBackToDashboard={handleNavigateHome}
            currentUser={currentUser}
          />
        ) : isSlotManagementActive ? (
          <AdminSlotManagementScreen
            onBackToDashboard={handleNavigateHome}
            currentUser={currentUser}
          />
        ) : isSessionRulesActive ? (
          <AdminSessionRulesScreen
            onBackToDashboard={handleNavigateHome}
            currentUser={currentUser}
          />
        ) : isWizardActive ? (
          <div>
            <QueueCountdownBanner
              queueEntry={activeQueueEntry}
              onExpired={handleWizardQueueExpired}
            />

            {/* Step Progress Bar */}
            <WizardProgress currentStep={wizardStep} onCancel={handleCancelWizard} />

            <div className="mx-auto max-w-4xl px-4 py-4 sm:py-5 sm:px-6">
              {wizardStep === 1 && (
                <StepSlotSelection
                  slots={TIME_SLOTS}
                  selectedSlot={wizardData.slot}
                  onSelectSlot={handleSelectSlot}
                  onContinue={() => setWizardStep(2)}
                />
              )}

              {wizardStep === 2 && wizardData.slot && (
                <StepCourtSelection
                  selectedSlot={wizardData.slot}
                  courts={currentSlotCourts}
                  selectedCourtId={wizardData.courtId}
                  onSelectCourt={handleSelectCourt}
                  onBack={() => setWizardStep(1)}
                  onContinue={() => setWizardStep(3)}
                />
              )}

              {wizardStep === 3 && wizardData.slot && wizardData.courtName && (
                <StepGroupSelection
                  selectedSlot={wizardData.slot}
                  courtName={wizardData.courtName}
                  selectedPlayers={wizardData.players}
                  onUpdatePlayers={handleUpdatePlayers}
                  onBack={() => setWizardStep(2)}
                  onContinue={() => setWizardStep(4)}
                  currentHostId={currentUser?.id}
                  onNavigateRegister={handleNavigateRegister}
                />
              )}

              {wizardStep === 4 && (
                <StepReviewConfirm
                  data={wizardData}
                  profile={studentProfile}
                  onBack={() => setWizardStep(3)}
                  onConfirmBooking={handleConfirmBooking}
                  isSubmitting={isSubmittingBooking}
                  submitError={bookingSubmitError}
                />
              )}
            </div>
          </div>
        ) : (
          <div className="px-4 sm:px-6">
            <HomeDashboard
              profile={studentProfile}
              upcomingBooking={upcomingBooking}
              isBookingWindowOpen={isBookingWindowOpen}
              isAuthenticated={Boolean(currentUser)}
              onToggleWindowMock={() => setIsBookingWindowOpen((prev) => !prev)}
              onStartBooking={handleStartBooking}
              onViewQRPass={() => setShowQRModal(true)}
              onNavigateLogin={handleNavigateLogin}
              onNavigateRegister={handleNavigateRegister}
              onNavigateVerifyEmail={() => handleNavigateVerifyEmail()}
              onOpenQueueModal={() => setShowQueueModal(true)}
              activeQueueStatus={activeQueueEntry?.status ?? 'NONE'}
              queuePosition={activeQueueEntry?.positionInLine ?? null}
              queueRemainingSeconds={queueRemainingSeconds}
            />
          </div>
        )}
      </main>

      {/* Secondary Destinations Modal */}
      <PlaceholderModal
        destination={secondaryNavDestination}
        onClose={() => setSecondaryNavDestination(null)}
      />

      {/* QR Pass Inspection Modal */}
      {showQRModal && (
        <QRPassModal
          booking={upcomingBooking}
          onClose={() => setShowQRModal(false)}
          onOpenStaffScanner={(token) => {
            setShowQRModal(false);
            handleNavigateStaffCheckIn(token);
          }}
        />
      )}

      {/* Booking Queue Admission Modal */}
      <BookingQueueModal
        isOpen={showQueueModal}
        onClose={() => setShowQueueModal(false)}
        isAuthenticated={Boolean(currentUser)}
        currentStudentId={currentUser?.id}
        currentStudentName={currentUser?.fullName}
        weeklyCredits={studentProfile.weeklyCreditsRemaining}
        activeQueueEntry={activeQueueEntry}
        setActiveQueueEntry={setActiveQueueEntry}
        onProceedToBooking={handleProceedFromQueueToBooking}
        onNavigateLogin={handleNavigateLogin}
      />

      {/* Minimalist Portal Footer */}
      <footer className="border-t border-slate-200/80 bg-white py-4 text-center text-xs text-slate-500">
        <div className="mx-auto max-w-5xl px-4 flex flex-col sm:flex-row items-center justify-between gap-2.5">
          <p>© {CURRENT_YEAR} University Sports Complex · Badminton Court Reservation System</p>
          <div className="flex items-center gap-2.5 text-[11px] text-slate-400">
            <span>4 Courts</span>
            <span>·</span>
            <span>30-min Slots</span>
            <span>·</span>
            <span>6 Weekly Credits</span>
            <span>·</span>
            <span>10-min QR Check-in</span>
          </div>
        </div>
      </footer>
    </div>
  );
}

export default App;
