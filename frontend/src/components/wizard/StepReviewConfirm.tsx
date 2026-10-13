import React, { useState, useEffect } from 'react';
import type { BookingWizardData, StudentProfile } from '../../types/booking';

interface StepReviewConfirmProps {
  data: BookingWizardData;
  profile: StudentProfile;
  onBack: () => void;
  onConfirmBooking: (idempotencyKey: string) => Promise<void> | void;
  isSubmitting?: boolean;
  submitError?: string | null;
}

export const StepReviewConfirm: React.FC<StepReviewConfirmProps> = ({
  data,
  profile,
  onBack,
  onConfirmBooking,
  isSubmitting = false,
  submitError = null,
}) => {
  const [secondsLeft, setSecondsLeft] = useState(60);

  useEffect(() => {
    if (secondsLeft <= 0) return;
    const interval = setInterval(() => {
      setSecondsLeft((s) => (s > 0 ? s - 1 : 0));
    }, 1000);
    return () => clearInterval(interval);
  }, [secondsLeft]);

  const handleConfirm = () => {
    const idempotencyKey = `idemp_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
    onConfirmBooking(idempotencyKey);
  };

  const isGroupValid = data.players.length >= 2 && data.players.length <= 4;
  const targetSlotId =
    data.realTimeSlotId ??
    (data.courtId && data.slot?.courtSlotMap?.[data.courtId]) ??
    data.slot?.realTimeSlotId ??
    (Number.isInteger(Number(data.slot?.id)) ? Number(data.slot?.id) : null);

  return (
    <div className="space-y-4">
      <div>
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-base sm:text-lg font-bold text-slate-900">Step 4: Review & Confirm Reservation</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Verify your court, slot, and teammate details before securing the reservation.
            </p>
          </div>

          {/* 60s Simulated Turn Window */}
          <div className="rounded-lg border border-indigo-200/80 bg-indigo-50/70 px-2.5 py-1 text-right">
            <span className="text-[9px] font-semibold uppercase tracking-wider text-indigo-700 block">
              Turn Window
            </span>
            <span
              className={`font-mono text-sm font-extrabold ${
                secondsLeft <= 15 ? 'text-rose-600 animate-pulse' : 'text-indigo-900'
              }`}
            >
              {secondsLeft < 10 ? `0${secondsLeft}` : secondsLeft}s left
            </span>
          </div>
        </div>
      </div>

      {/* Review Summary Card */}
      <div className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white">
        {/* Slot & Court */}
        <div className="p-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="flex items-center gap-1.5">
              <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">
                Reserved Slot
              </span>
              {targetSlotId != null && (
                <span className="rounded bg-emerald-100 px-1.5 py-0.2 text-[9px] font-semibold text-emerald-800">
                  Slot #{targetSlotId}
                </span>
              )}
            </div>
            <p className="text-sm font-bold text-slate-900 mt-0.5">
              {data.slot?.label}
            </p>
            <p className="text-[11px] text-slate-500 capitalize">{data.slot?.session} Session · 30 Mins</p>
          </div>

          <div className="sm:text-right">
            <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">
              Assigned Facility
            </span>
            <p className="text-sm font-bold text-slate-900 mt-0.5">
              {data.courtName}
            </p>
            <p className="text-[11px] text-emerald-700 font-medium">Standard Campus Badminton Court</p>
          </div>
        </div>

        {/* Players Roster */}
        <div className="p-4 space-y-2">
          <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 block">
            Confirmed Players & Spots ({data.players.length} Spots)
          </span>

          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {data.players.map((item) => (
              <div
                key={item.student.id}
                className="flex items-center justify-between rounded-lg border border-slate-100 bg-slate-50/70 p-2.5"
              >
                <div>
                  <div className="flex items-center gap-1.5">
                    <span className="text-xs font-bold text-slate-900">{item.student.name}</span>
                    <span className="rounded bg-emerald-100 px-1.5 py-0.2 text-[9px] font-bold text-emerald-800">
                      Eligible
                    </span>
                    {item.student.numericId && (
                      <span className="rounded bg-slate-100 px-1.5 py-0.2 text-[9px] font-mono font-semibold text-slate-600">
                        ID #{item.student.numericId}
                      </span>
                    )}
                  </div>
                  <p className="text-[10px] text-slate-500 font-mono">
                    {item.spotNumber === 1
                      ? `${item.student.admissionNumber || profile.admissionNumber} · Booking Host`
                      : `Registered Teammate · Spot ${item.spotNumber}`}
                  </p>
                </div>
                <span className="rounded bg-white border border-slate-200 px-2 py-0.5 text-[11px] font-bold text-slate-800">
                  Spot {item.spotNumber}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Impact & Rules Summary */}
        <div className="p-4 grid grid-cols-1 gap-3 sm:grid-cols-2 text-xs bg-slate-50/50 rounded-b-xl">
          <div>
            <span className="text-[10px] text-slate-400 font-medium block uppercase tracking-wider">Group Credit Deduction</span>
            <p className="font-bold text-slate-800 mt-0.5">
              1 Credit deducted per registered member
            </p>
            <p className="text-[11px] text-slate-500">
              Host {profile.name} (balance: {profile.weeklyCreditsRemaining} credits) and all {data.players.length} group members will have 1 credit deducted atomically in MySQL.
            </p>
          </div>

          <div>
            <span className="text-[10px] text-slate-400 font-medium block uppercase tracking-wider">Attendance Verification</span>
            <p className="font-bold text-slate-800 mt-0.5">
              10-Minute Check-in Grace Period
            </p>
            <p className="text-[11px] text-slate-500">
              Unscanned QR codes 10 minutes post-slot forfeits all remaining credits for the week.
            </p>
          </div>
        </div>
      </div>

      {/* Backend & Dev Identity Notice */}
      <div className="rounded-lg border border-slate-200 bg-slate-50/70 p-3 text-xs text-slate-700 space-y-1">
        <div className="flex items-center gap-1.5 font-semibold text-slate-800">
          <span className="h-2 w-2 rounded-full bg-emerald-600"></span>
          <span>Backend Atomic Transaction (POST /api/bookings)</span>
        </div>
        <p className="text-[11px] text-slate-600">
          Confirming executes the atomic database transaction: verifies 2–4 registered students, reserves the slot, persists individual player QR tokens, and deducts 1 credit per player.
        </p>
        <p className="text-[11px] text-slate-500">
          <em>Authenticated Host: Reservations are secured with server-side JWT verification. The booking host identity is verified from the server-validated session.</em>
        </p>
      </div>

      {/* Error Notice */}
      {submitError && (
        <div className="rounded-xl border border-rose-200 bg-rose-50/90 p-3.5 text-xs text-rose-900 space-y-1">
          <div className="flex items-center gap-1.5 font-bold">
            <svg className="h-4 w-4 shrink-0 text-rose-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>Reservation Request Rejected by Backend</span>
          </div>
          <p className="text-[11px] text-rose-700 pl-5.5">
            {submitError}
          </p>
        </div>
      )}

      {/* Wizard Step Footer */}
      <div className="flex items-center justify-between border-t border-slate-200 pt-3.5">
        <button
          type="button"
          onClick={onBack}
          disabled={isSubmitting}
          className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-400 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
          <span>Back to Group Selection</span>
        </button>

        <button
          type="button"
          disabled={isSubmitting || !isGroupValid}
          onClick={handleConfirm}
          className="inline-flex h-9 items-center justify-center gap-1.5 rounded-lg bg-emerald-700 px-5 text-xs font-bold text-white shadow-2xs transition hover:bg-emerald-800 active:scale-[0.99] cursor-pointer disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-500 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600"
        >
          {isSubmitting ? (
            <>
              <svg className="h-3.5 w-3.5 animate-spin text-white" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
              </svg>
              <span>Submitting Reservation...</span>
            </>
          ) : (
            <>
              <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
                <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
              </svg>
              <span>Confirm Reservation</span>
            </>
          )}
        </button>
      </div>
    </div>
  );
};
