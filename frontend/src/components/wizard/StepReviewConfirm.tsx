import React, { useState, useEffect } from 'react';
import type { BookingWizardData, StudentProfile } from '../../types/booking';

interface StepReviewConfirmProps {
  data: BookingWizardData;
  profile: StudentProfile;
  onBack: () => void;
  onConfirmBooking: () => void;
}

export const StepReviewConfirm: React.FC<StepReviewConfirmProps> = ({
  data,
  profile,
  onBack,
  onConfirmBooking,
}) => {
  const [secondsLeft, setSecondsLeft] = useState(60);

  useEffect(() => {
    if (secondsLeft <= 0) return;
    const interval = setInterval(() => {
      setSecondsLeft((s) => (s > 0 ? s - 1 : 0));
    }, 1000);
    return () => clearInterval(interval);
  }, [secondsLeft]);

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
            <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">
              Reserved Slot
            </span>
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
                  </div>
                  <p className="text-[10px] text-slate-500 font-mono">{item.student.admissionNumber}</p>
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
            <span className="text-[10px] text-slate-400 font-medium block uppercase tracking-wider">Credit Deduction</span>
            <p className="font-bold text-slate-800 mt-0.5">
              1 Credit deducted from {profile.name}
            </p>
            <p className="text-[11px] text-slate-500">
              Remaining balance will be {profile.weeklyCreditsRemaining - 1} credits.
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

      {/* Demo Notice */}
      <div className="rounded-lg border border-amber-200/80 bg-amber-50/70 p-3 text-xs text-amber-900 flex items-start gap-2">
        <svg className="h-4 w-4 shrink-0 text-amber-600 mt-0.5" viewBox="0 0 20 20" fill="currentColor">
          <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
        </svg>
        <span>
          <strong>Mock Demonstration Mode:</strong> Confirming simulates atomic spot lock and deducts 1 mock credit without real backend network requests.
        </span>
      </div>

      {/* Wizard Step Footer */}
      <div className="flex items-center justify-between border-t border-slate-200 pt-3.5">
        <button
          type="button"
          onClick={onBack}
          className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-400"
        >
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
          <span>Back to Group Selection</span>
        </button>

        <button
          type="button"
          onClick={onConfirmBooking}
          className="inline-flex h-9 items-center justify-center gap-1.5 rounded-lg bg-emerald-700 px-5 text-xs font-bold text-white shadow-2xs transition hover:bg-emerald-800 active:scale-[0.99] cursor-pointer focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600"
        >
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
            <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
          </svg>
          <span>Confirm demo booking</span>
        </button>
      </div>
    </div>
  );
};
