import type React from 'react';
import type { StudentProfile, UpcomingBooking } from '../types/booking';

interface HomeDashboardProps {
  profile: StudentProfile;
  upcomingBooking: UpcomingBooking | null;
  isBookingWindowOpen: boolean;
  onToggleWindowMock: () => void;
  onStartBooking: () => void;
  onViewQRPass: () => void;
}

export const HomeDashboard: React.FC<HomeDashboardProps> = ({
  profile,
  upcomingBooking,
  isBookingWindowOpen,
  onToggleWindowMock,
  onStartBooking,
  onViewQRPass,
}) => {
  return (
    <div className="mx-auto max-w-4xl space-y-4 py-4 sm:py-5">
      {/* 1. Primary Action & Booking Window Banner */}
      <div className="rounded-xl border border-slate-200/90 bg-white p-4.5 sm:p-5 shadow-2xs">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="space-y-0.5">
            <div className="flex items-center gap-2">
              <h1 className="text-base sm:text-lg font-bold tracking-tight text-slate-900">
                Badminton Court Reservation
              </h1>
              <span
                className={`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                  isBookingWindowOpen
                    ? 'bg-emerald-100 text-emerald-800'
                    : 'bg-slate-100 text-slate-600'
                }`}
              >
                {isBookingWindowOpen ? 'Queue Open (Demo)' : 'Queue Closed'}
              </span>
            </div>
            <p className="text-xs text-slate-500">
              4 courts · 30-min intervals · 2–4 players per reservation
            </p>
          </div>

          {/* Primary Action Button */}
          <div className="flex flex-col items-start sm:items-end gap-1">
            <button
              type="button"
              disabled={!isBookingWindowOpen}
              onClick={onStartBooking}
              className={`inline-flex h-9.5 items-center justify-center gap-2 rounded-lg px-5 text-xs font-semibold transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 ${
                isBookingWindowOpen
                  ? 'bg-emerald-700 text-white shadow-2xs hover:bg-emerald-800 active:scale-[0.99] cursor-pointer'
                  : 'bg-slate-200 text-slate-400 cursor-not-allowed'
              }`}
            >
              <svg
                className="h-3.5 w-3.5"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth="2.5"
              >
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
              </svg>
              <span>Start booking</span>
            </button>
            {!isBookingWindowOpen && (
              <span className="text-[10px] text-amber-700 font-medium">
                Disabled outside configured booking window
              </span>
            )}
          </div>
        </div>

        {/* Demo Window Simulation Toggle */}
        <div className="mt-3.5 flex items-center justify-between border-t border-slate-100 pt-2.5 text-xs text-slate-500">
          <div className="flex items-center gap-1.5">
            <span className="text-[11px] text-slate-400">Mock control:</span>
            <button
              type="button"
              onClick={onToggleWindowMock}
              className="text-[11px] font-semibold text-emerald-700 hover:text-emerald-900 underline underline-offset-2 focus-visible:outline-hidden focus-visible:ring-1 focus-visible:ring-emerald-600 rounded"
            >
              Toggle window state ({isBookingWindowOpen ? 'Set to Closed' : 'Set to Open'})
            </button>
          </div>
          <span className="text-[11px] text-slate-400">
            Illustrative schedule rule preview
          </span>
        </div>
      </div>

      {/* 2. Key Information Grid: Credits & Queue Status */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {/* Weekly Credits Card */}
        <div className="rounded-xl border border-slate-200/90 bg-white p-4.5 shadow-2xs flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                Weekly Credits
              </span>
              <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-semibold text-emerald-700">
                {profile.priorityStatus}
              </span>
            </div>

            <div className="mt-2.5 flex items-baseline gap-1.5">
              <span className="text-2xl sm:text-3xl font-extrabold text-slate-900">
                {profile.weeklyCreditsRemaining}
              </span>
              <span className="text-xs font-medium text-slate-400">
                / {profile.weeklyCreditsTotal} remaining
              </span>
            </div>

            {/* Visual credit dots */}
            <div className="mt-2 flex gap-1">
              {Array.from({ length: profile.weeklyCreditsTotal }).map((_, i) => (
                <div
                  key={i}
                  className={`h-1.5 flex-1 rounded-full ${
                    i < profile.weeklyCreditsRemaining ? 'bg-emerald-600' : 'bg-slate-200'
                  }`}
                />
              ))}
            </div>
          </div>

          <div className="mt-3.5 border-t border-slate-100 pt-2.5 text-[11px] text-slate-500 space-y-0.5">
            <p>
              <strong>Resets:</strong> {profile.nextCreditResetDay}
            </p>
            <p className="text-slate-400">
              1 credit per booking · 0 credits reduces queue priority
            </p>
          </div>
        </div>

        {/* Queue Status & Opening Times Card */}
        <div className="rounded-xl border border-slate-200/90 bg-white p-4.5 shadow-2xs flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                Booking Queue
              </span>
              <span
                className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                  isBookingWindowOpen
                    ? 'bg-emerald-100 text-emerald-800'
                    : 'bg-amber-100 text-amber-800'
                }`}
              >
                {isBookingWindowOpen ? 'Active' : 'Closed'}
              </span>
            </div>

            <div className="mt-2.5">
              <p className="text-[11px] text-slate-500">Next Scheduled Window</p>
              <p className="text-sm font-bold text-slate-900 mt-0.5">
                9:00 PM Queue (Opens 9:05 PM)
              </p>
            </div>
          </div>

          <div className="mt-3.5 border-t border-slate-100 pt-2.5 text-[11px] text-slate-500 space-y-0.5">
            <p>
              <strong>Morning Sessions:</strong> 9:00 PM previous day
            </p>
            <p className="text-slate-400">
              60-second atomic reservation window per admitted student
            </p>
          </div>
        </div>
      </div>

      {/* 3. Upcoming Booking Information */}
      <div className="rounded-xl border border-slate-200/90 bg-white p-4.5 shadow-2xs">
        <div className="flex items-center justify-between border-b border-slate-100 pb-2.5">
          <div className="flex items-center gap-2">
            <h2 className="text-xs font-bold uppercase tracking-wider text-slate-700">
              Upcoming Booking
            </h2>
            {upcomingBooking && (
              <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-semibold text-emerald-800">
                {upcomingBooking.status}
              </span>
            )}
          </div>
          <span className="text-[11px] text-slate-400">Attendance tracking</span>
        </div>

        {upcomingBooking ? (
          <div className="mt-3 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="space-y-0.5">
              <div className="flex items-center gap-2">
                <span className="text-sm font-bold text-slate-900">
                  {upcomingBooking.slot.label}
                </span>
                <span className="text-xs text-slate-300">·</span>
                <span className="text-xs font-semibold text-slate-700">
                  {upcomingBooking.courtName}
                </span>
              </div>
              <p className="text-xs text-slate-600">
                Players: {upcomingBooking.players.map((p) => `${p.name} (Spot ${p.spotNumber})`).join(', ')}
              </p>
              <p className="text-[11px] text-amber-700">
                Scan player QR code within 10 minutes of slot start time.
              </p>
            </div>

            <button
              type="button"
              onClick={onViewQRPass}
              className="inline-flex h-8.5 items-center justify-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600"
            >
              <svg className="h-3.5 w-3.5 text-slate-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z" />
              </svg>
              <span>View QR Passes</span>
            </button>
          </div>
        ) : (
          <div className="py-4 text-center text-xs text-slate-500">
            <p>No active court bookings scheduled for today.</p>
            <p className="text-slate-400 mt-0.5">Click &quot;Start booking&quot; above when queue opens.</p>
          </div>
        )}
      </div>
    </div>
  );
};
