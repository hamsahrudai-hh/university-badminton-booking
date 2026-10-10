import { useState, useMemo } from 'react';
import { Header } from './components/Header';
import { HomeDashboard } from './components/HomeDashboard';
import { PlaceholderModal } from './components/PlaceholderModal';
import { QRPassModal } from './components/QRPassModal';
import { WizardProgress } from './components/wizard/WizardProgress';
import { StepSlotSelection } from './components/wizard/StepSlotSelection';
import { StepCourtSelection } from './components/wizard/StepCourtSelection';
import { StepGroupSelection } from './components/wizard/StepGroupSelection';
import { StepReviewConfirm } from './components/wizard/StepReviewConfirm';
import {
  MOCK_STUDENT,
  REGISTERED_STUDENTS,
  TIME_SLOTS,
  INITIAL_UPCOMING_BOOKING,
  getMockCourtDataForSlot,
} from './data/mockData';
import type {
  WizardStep,
  BookingWizardData,
  StudentProfile,
  UpcomingBooking,
  TimeSlot,
  GroupPlayerAssignment,
} from './types/booking';

export function App() {
  // Navigation / View State
  const [isWizardActive, setIsWizardActive] = useState<boolean>(false);
  const [secondaryNavDestination, setSecondaryNavDestination] = useState<string | null>(null);
  const [showQRModal, setShowQRModal] = useState<boolean>(false);
  const [successToast, setSuccessToast] = useState<string | null>(null);

  // Home Dashboard Data
  const [studentProfile, setStudentProfile] = useState<StudentProfile>(MOCK_STUDENT);
  const [upcomingBooking, setUpcomingBooking] = useState<UpcomingBooking | null>(INITIAL_UPCOMING_BOOKING);
  const [isBookingWindowOpen, setIsBookingWindowOpen] = useState<boolean>(true);

  // Wizard Step State
  const [wizardStep, setWizardStep] = useState<WizardStep>(1);

  // Wizard Booking Selection State
  const [wizardData, setWizardData] = useState<BookingWizardData>({
    slot: TIME_SLOTS[0], // default to first morning slot
    courtId: 1,
    courtName: 'Court 1',
    players: [
      { student: REGISTERED_STUDENTS[0], spotNumber: 1 },
      { student: REGISTERED_STUDENTS[1], spotNumber: 2 },
    ],
  });

  // Calculate court availability for the currently selected slot
  const currentSlotCourts = useMemo(() => {
    if (!wizardData.slot) return [];
    return getMockCourtDataForSlot(wizardData.slot.id);
  }, [wizardData.slot]);

  // Handler to start booking from home dashboard
  const handleStartBooking = () => {
    if (!isBookingWindowOpen) return;
    setWizardStep(1);
    setIsWizardActive(true);
  };

  // Step 1: Select Slot with Revalidation
  const handleSelectSlot = (slot: TimeSlot) => {
    setWizardData((prev) => {
      // Revalidation: check if selected court still has available spots in new slot
      const newSlotCourts = getMockCourtDataForSlot(slot.id);
      const courtStillValid = prev.courtId
        ? newSlotCourts.some(
            (c) => c.courtId === prev.courtId && c.spots.some((s) => s.status === 'available')
          )
        : false;

      return {
        ...prev,
        slot,
        courtId: courtStillValid ? prev.courtId : null,
        courtName: courtStillValid ? prev.courtName : null,
      };
    });
  };

  // Step 2: Select Court with Revalidation
  const handleSelectCourt = (courtId: number, courtName: string) => {
    setWizardData((prev) => ({
      ...prev,
      courtId,
      courtName,
      // Preserve existing players if valid, ensure primary user is assigned
      players:
        prev.players.length >= 2
          ? prev.players
          : [
              { student: REGISTERED_STUDENTS[0], spotNumber: 1 },
              { student: REGISTERED_STUDENTS[1], spotNumber: 2 },
            ],
    }));
  };

  // Step 3: Update Players / Group Roster
  const handleUpdatePlayers = (players: GroupPlayerAssignment[]) => {
    setWizardData((prev) => ({
      ...prev,
      players,
    }));
  };

  // Step 4: Finalize & Confirm Demo Booking
  const handleConfirmBooking = () => {
    if (!wizardData.slot || !wizardData.courtId || !wizardData.courtName) return;

    // Deduct 1 weekly credit
    setStudentProfile((prev) => ({
      ...prev,
      weeklyCreditsRemaining: Math.max(0, prev.weeklyCreditsRemaining - 1),
      activeBookingsCount: prev.activeBookingsCount + 1,
    }));

    // Create new upcoming booking with unique QR tokens per player
    const newBooking: UpcomingBooking = {
      id: `UB-${Math.floor(1000 + Math.random() * 9000)}`,
      slot: wizardData.slot,
      courtId: wizardData.courtId,
      courtName: wizardData.courtName,
      bookingUser: studentProfile.name,
      creditsDeducted: 1,
      status: 'Confirmed',
      players: wizardData.players.map((p) => ({
        studentId: p.student.id,
        name: p.student.name.replace(' (You)', ''),
        admissionNumber: p.student.admissionNumber,
        spotNumber: p.spotNumber,
        qrToken: `TOKEN_${p.student.admissionNumber}_${Math.floor(100 + Math.random() * 900)}`,
        checkedIn: false,
      })),
    };

    setUpcomingBooking(newBooking);
    setIsWizardActive(false);
    setSuccessToast(`Reservation confirmed for ${newBooking.courtName} (${newBooking.slot.label})! 1 Credit deducted.`);
    setTimeout(() => setSuccessToast(null), 5000);
  };

  const handleCancelWizard = () => {
    setIsWizardActive(false);
    setWizardStep(1);
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
        onNavigateHome={() => setIsWizardActive(false)}
        onOpenSecondaryNav={(dest) => setSecondaryNavDestination(dest)}
        isWizardActive={isWizardActive}
      />

      {/* Content Area */}
      <main className="flex-1 pb-8">
        {isWizardActive ? (
          <div>
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
                  allRegisteredStudents={REGISTERED_STUDENTS}
                  selectedPlayers={wizardData.players}
                  onUpdatePlayers={handleUpdatePlayers}
                  onBack={() => setWizardStep(2)}
                  onContinue={() => setWizardStep(4)}
                />
              )}

              {wizardStep === 4 && (
                <StepReviewConfirm
                  data={wizardData}
                  profile={studentProfile}
                  onBack={() => setWizardStep(3)}
                  onConfirmBooking={handleConfirmBooking}
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
              onToggleWindowMock={() => setIsBookingWindowOpen((prev) => !prev)}
              onStartBooking={handleStartBooking}
              onViewQRPass={() => setShowQRModal(true)}
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
        />
      )}

      {/* Minimalist Portal Footer */}
      <footer className="border-t border-slate-200/80 bg-white py-4 text-center text-xs text-slate-500">
        <div className="mx-auto max-w-5xl px-4 flex flex-col sm:flex-row items-center justify-between gap-2.5">
          <p>© {new Date().getFullYear()} University Sports Complex · Badminton Court Reservation System</p>
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
