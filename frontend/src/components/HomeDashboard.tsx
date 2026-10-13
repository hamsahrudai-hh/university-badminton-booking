import React, { useState, useEffect } from 'react';
import type { StudentProfile, UpcomingBooking } from '../types/booking';
import {
  fetchSessions,
  fetchCourts,
  type SessionConfigResponseDto,
  type CourtResponseDto,
} from '../api/client';
import heroImage from '../assets/hero.png';

export interface HomeDashboardProps {
  profile: StudentProfile;
  upcomingBooking: UpcomingBooking | null;
  isBookingWindowOpen: boolean;
  isAuthenticated?: boolean;
  onToggleWindowMock: () => void;
  onStartBooking: () => void;
  onViewQRPass: () => void;
  onNavigateLogin?: () => void;
  onNavigateRegister?: () => void;
  onNavigateVerifyEmail?: () => void;
  onOpenQueueModal?: () => void;
  activeQueueStatus?: 'NONE' | 'WAITING' | 'ADMITTED' | 'EXPIRED' | 'COMPLETED' | 'CANCELLED';
  queuePosition?: number | null;
  queueRemainingSeconds?: number | null;
}

export const HomeDashboard: React.FC<HomeDashboardProps> = ({
  profile,
  upcomingBooking,
  isBookingWindowOpen,
  isAuthenticated = false,
  onToggleWindowMock,
  onStartBooking,
  onViewQRPass,
  onNavigateLogin,
  onNavigateRegister,
  onNavigateVerifyEmail,
  onOpenQueueModal,
  activeQueueStatus = 'NONE',
  queuePosition = null,
  queueRemainingSeconds = null,
}) => {
  // Real backend sessions & courts with resilient defaults
  const [sessions, setSessions] = useState<SessionConfigResponseDto[]>([]);
  const [courts, setCourts] = useState<CourtResponseDto[]>([
    { id: 1, courtNumber: 1, active: true },
    { id: 2, courtNumber: 2, active: true },
    { id: 3, courtNumber: 3, active: true },
    { id: 4, courtNumber: 4, active: true },
  ]);

  // Fetch real sessions and courts
  useEffect(() => {
    fetchSessions()
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) {
          setSessions(data);
        }
      })
      .catch(() => {
        // Silently use defaults on network failure
      });

    fetchCourts()
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) {
          setCourts(data);
        }
      })
      .catch(() => {
        // Silently use defaults on network failure
      });
  }, []);

  const morningSession = sessions.find((s) => s.sessionType === 'MORNING');
  const eveningSession = sessions.find((s) => s.sessionType === 'EVENING');

  const morningHours = morningSession
    ? `${morningSession.startTime.substring(0, 5)} – ${morningSession.endTime.substring(0, 5)}`
    : '06:00 – 08:30 AM';
  const eveningHours = eveningSession
    ? `${eveningSession.startTime.substring(0, 5)} – ${eveningSession.endTime.substring(0, 5)}`
    : '03:30 – 07:00 PM';

  const isAdmitted = activeQueueStatus === 'ADMITTED';
  const isWaiting = activeQueueStatus === 'WAITING';
  const canBook = isAdmitted || (isBookingWindowOpen && isAuthenticated);

  return (
    <div className="mx-auto max-w-6xl space-y-6 py-4 sm:py-6">
      {/* 1. Hero Section with Badminton Photo & Dark Green Overlay */}
      <section
        className="relative overflow-hidden rounded-2xl bg-[#064E3B] text-white shadow-sm"
        aria-label="Welcome and Reservation Banner"
      >
        {/* Background Image with Overlay */}
        <div className="absolute inset-0 z-0">
          <img
            src={heroImage}
            alt="Badminton court"
            className="h-full w-full object-cover object-center opacity-30 mix-blend-luminosity"
          />
          <div className="absolute inset-0 bg-gradient-to-r from-[#064E3B] via-[#064E3B]/95 to-[#064E3B]/70" />
        </div>

        {/* Hero Content */}
        <div className="relative z-10 px-6 py-8 sm:px-8 sm:py-10">
          <div className="max-w-2xl">
            <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-800/80 px-3 py-1 text-xs font-semibold tracking-wide text-emerald-200 backdrop-blur-xs">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
              University Sports Complex
            </span>
            <h1 className="mt-3 text-2xl font-bold tracking-tight text-white sm:text-3xl">
              {isAuthenticated ? `Welcome back, ${profile.name.split(' ')[0]}!` : 'Badminton Court Reservations'}
            </h1>
            <p className="mt-2 text-sm leading-relaxed text-emerald-100/90 sm:text-base">
              Reserve your court, join active queues with priority fairness, and show your verified pass at check-in.
            </p>

            {/* Action Buttons */}
            <div className="mt-6 flex flex-wrap items-center gap-3">
              <button
                type="button"
                onClick={onStartBooking}
                disabled={!isBookingWindowOpen && !isAdmitted}
                className={`inline-flex items-center gap-2 rounded-xl px-5 py-2.5 text-xs sm:text-sm font-semibold transition-all shadow-sm focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-400 cursor-pointer ${
                  isAdmitted
                    ? 'bg-emerald-400 text-slate-950 hover:bg-emerald-300 ring-2 ring-emerald-300'
                    : isBookingWindowOpen
                    ? 'bg-emerald-600 text-white hover:bg-emerald-500'
                    : 'bg-emerald-900/60 text-emerald-300/60 cursor-not-allowed border border-emerald-800/50'
                }`}
              >
                <svg
                  className="h-4 w-4"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"
                  />
                </svg>
                <span>
                  {isAdmitted
                    ? `Reserve Court Now (${queueRemainingSeconds ?? 60}s)`
                    : isWaiting
                    ? `In Queue (#${queuePosition})`
                    : 'Book a Court'}
                </span>
              </button>

              {onOpenQueueModal && (
                <button
                  type="button"
                  onClick={onOpenQueueModal}
                  className="inline-flex items-center gap-2 rounded-xl border border-white/20 bg-white/10 px-4 py-2.5 text-xs sm:text-sm font-semibold text-white backdrop-blur-xs transition hover:bg-white/20 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-white cursor-pointer"
                >
                  <svg
                    className="h-4 w-4 text-emerald-300"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    strokeWidth="2"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"
                    />
                  </svg>
                  <span>Queue Admission</span>
                </button>
              )}

              {upcomingBooking && (
                <button
                  type="button"
                  onClick={onViewQRPass}
                  className="inline-flex items-center gap-2 rounded-xl border border-emerald-400/30 bg-emerald-950/40 px-4 py-2.5 text-xs sm:text-sm font-semibold text-emerald-200 transition hover:bg-emerald-950/60 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-400 cursor-pointer"
                >
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z" />
                  </svg>
                  <span>My QR Passes</span>
                </button>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* 2. Main Two-Column Grid */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        {/* LEFT COLUMN: Main Booking Flow & Operational Status (8 Cols) */}
        <div className="space-y-6 lg:col-span-8">
          {/* A. Queue Status Card */}
          <div className="rounded-2xl border border-[#E2E8F0] bg-white p-5 shadow-xs">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3.5">
              <div className="flex items-center gap-2.5">
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-50 text-emerald-700">
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                </div>
                <div>
                  <h2 className="text-sm font-bold text-[#172033]">Queue Status & Admission</h2>
                  <p className="text-[11px] text-[#64748B]">FIFO ordered fairness · 60s booking window</p>
                </div>
              </div>

              {/* Status Badge */}
              <span
                className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${
                  isAdmitted
                    ? 'bg-emerald-100 text-emerald-800 animate-pulse'
                    : isWaiting
                    ? 'bg-sky-100 text-sky-800'
                    : isBookingWindowOpen
                    ? 'bg-emerald-100 text-emerald-800'
                    : 'bg-slate-100 text-slate-600'
                }`}
              >
                <span
                  className={`h-1.5 w-1.5 rounded-full ${
                    isAdmitted
                      ? 'bg-emerald-600'
                      : isWaiting
                      ? 'bg-sky-600'
                      : isBookingWindowOpen
                      ? 'bg-emerald-600'
                      : 'bg-slate-400'
                  }`}
                />
                {isAdmitted
                  ? `Admitted (${queueRemainingSeconds ?? 60}s)`
                  : isWaiting
                  ? `In Line (#${queuePosition})`
                  : isBookingWindowOpen
                  ? 'Queue Open'
                  : 'Queue Closed'}
              </span>
            </div>

            {/* Queue Body Content */}
            <div className="mt-4">
              {isAdmitted ? (
                <div className="rounded-xl border border-emerald-200 bg-emerald-50/70 p-4">
                  <div className="flex items-start justify-between gap-4">
                    <div className="space-y-1">
                      <div className="flex items-center gap-1.5 text-xs font-bold text-emerald-900">
                        <span className="flex h-5 w-5 items-center justify-center rounded-full bg-emerald-600 text-white text-[10px]">✓</span>
                        Booking Admission Window Active!
                      </div>
                      <p className="text-xs text-emerald-800">
                        You have reached the front of the queue. Please select your slot and confirm within{' '}
                        <strong>{queueRemainingSeconds ?? 60} seconds</strong>.
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={onStartBooking}
                      className="shrink-0 inline-flex items-center gap-1.5 rounded-lg bg-[#047857] px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-[#065F46] transition cursor-pointer"
                    >
                      Reserve Now →
                    </button>
                  </div>
                </div>
              ) : isWaiting ? (
                <div className="rounded-xl border border-sky-200 bg-sky-50/70 p-4">
                  <div className="flex items-start justify-between gap-4">
                    <div className="space-y-1">
                      <div className="flex items-center gap-1.5 text-xs font-bold text-sky-900">
                        <span className="flex h-5 w-5 items-center justify-center rounded-full bg-sky-600 text-white text-[10px]">#</span>
                        Position in Queue: #{queuePosition}
                      </div>
                      <p className="text-xs text-sky-800">
                        Waiting in line. You will receive a 60-second admission window when your turn arrives.
                      </p>
                    </div>
                    {onOpenQueueModal && (
                      <button
                        type="button"
                        onClick={onOpenQueueModal}
                        className="shrink-0 inline-flex items-center gap-1 rounded-lg border border-sky-300 bg-white px-3 py-1.5 text-xs font-semibold text-sky-800 hover:bg-sky-50 transition cursor-pointer"
                      >
                        View Status
                      </button>
                    )}
                  </div>
                </div>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div className="rounded-xl border border-slate-100 bg-[#F6F8F7] p-3">
                    <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Morning Queue</span>
                    <p className="text-sm font-bold text-[#172033] mt-0.5">Opens 9:00 PM (Prev Night)</p>
                    <p className="text-[11px] text-[#64748B] mt-0.5">Admissions start at 9:05 PM IST</p>
                  </div>
                  <div className="rounded-xl border border-slate-100 bg-[#F6F8F7] p-3">
                    <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Evening Queue</span>
                    <p className="text-sm font-bold text-[#172033] mt-0.5">Opens 12:00 PM (Noon)</p>
                    <p className="text-[11px] text-[#64748B] mt-0.5">Admissions start at 12:05 PM IST</p>
                  </div>
                </div>
              )}
            </div>

            {/* Footer with Join Queue action and subtle Mock window toggle */}
            <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 pt-3 text-xs text-slate-500">
              <div className="flex items-center gap-2">
                {onOpenQueueModal && (
                  <button
                    type="button"
                    onClick={onOpenQueueModal}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-800 hover:bg-emerald-100 transition cursor-pointer"
                  >
                    <span>⚡ Join or Check Queue</span>
                  </button>
                )}
              </div>

              <div className="flex items-center gap-1.5 text-[11px] text-[#64748B]">
                <span>Preview mode:</span>
                <button
                  type="button"
                  onClick={onToggleWindowMock}
                  className="font-semibold text-emerald-700 hover:text-emerald-800 underline underline-offset-2 cursor-pointer"
                >
                  Toggle window state ({isBookingWindowOpen ? 'Open' : 'Closed'})
                </button>
              </div>
            </div>
          </div>

          {/* B. Today's Sessions Card */}
          <div className="rounded-2xl border border-[#E2E8F0] bg-white p-5 shadow-xs">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-amber-50 text-amber-600">
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
                  </svg>
                </div>
                <h2 className="text-sm font-bold text-[#172033]">Today&apos;s Sessions</h2>
              </div>
              <span className="text-[11px] text-[#64748B]">30-minute intervals</span>
            </div>

            <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-3.5">
              {/* Morning Session Tile */}
              <div className="rounded-xl border border-slate-200/80 bg-slate-50/50 p-4 transition hover:border-emerald-300">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="flex h-6 w-6 items-center justify-center rounded-md bg-amber-100 text-amber-700 text-xs font-bold">
                      ☀️
                    </span>
                    <span className="text-xs font-bold text-[#172033]">Morning Session</span>
                  </div>
                  <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-[10px] font-semibold text-emerald-700">
                    4 Courts Active
                  </span>
                </div>
                <div className="mt-2.5">
                  <span className="text-base font-extrabold text-[#172033]">{morningHours}</span>
                  <p className="text-[11px] text-[#64748B] mt-0.5">5 slots per court · 2–4 players per slot</p>
                </div>
              </div>

              {/* Evening Session Tile */}
              <div className="rounded-xl border border-slate-200/80 bg-slate-50/50 p-4 transition hover:border-emerald-300">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="flex h-6 w-6 items-center justify-center rounded-md bg-indigo-100 text-indigo-700 text-xs font-bold">
                      🌙
                    </span>
                    <span className="text-xs font-bold text-[#172033]">Evening Session</span>
                  </div>
                  <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-[10px] font-semibold text-emerald-700">
                    4 Courts Active
                  </span>
                </div>
                <div className="mt-2.5">
                  <span className="text-base font-extrabold text-[#172033]">{eveningHours}</span>
                  <p className="text-[11px] text-[#64748B] mt-0.5">7 slots per court · 2–4 players per slot</p>
                </div>
              </div>
            </div>
          </div>

          {/* C. Upcoming Bookings Card */}
          <div className="rounded-2xl border border-[#E2E8F0] bg-white p-5 shadow-xs">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-emerald-50 text-emerald-700">
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                  </svg>
                </div>
                <h2 className="text-sm font-bold text-[#172033]">Upcoming Bookings</h2>
              </div>
              {upcomingBooking && (
                <span className="inline-flex items-center rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-800">
                  {upcomingBooking.status}
                </span>
              )}
            </div>

            {upcomingBooking ? (
              <div className="mt-4 rounded-xl border border-slate-200/80 bg-white p-4">
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                  <div className="flex items-start gap-3">
                    {/* Date Badge */}
                    <div className="flex flex-col items-center justify-center rounded-xl bg-emerald-700 px-3 py-2 text-white shadow-xs shrink-0">
                      <span className="text-[10px] font-bold uppercase tracking-wider">
                        {upcomingBooking.slot.label.split(' ')[0] || 'SLOT'}
                      </span>
                      <span className="text-sm font-black">
                        {upcomingBooking.courtName.replace('Court ', 'C')}
                      </span>
                    </div>

                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-bold text-[#172033]">
                          {upcomingBooking.slot.label}
                        </span>
                        <span className="text-xs text-slate-300">·</span>
                        <span className="text-xs font-semibold text-emerald-800">
                          {upcomingBooking.courtName}
                        </span>
                      </div>
                      <p className="text-xs text-[#64748B]">
                        Players: {upcomingBooking.players.map((p) => `${p.name} (Spot ${p.spotNumber})`).join(', ')}
                      </p>
                      <p className="text-[11px] text-amber-800 font-medium">
                        ⏱️ Check-in window: 10 minutes before slot start time at venue desk.
                      </p>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={onViewQRPass}
                    className="inline-flex items-center justify-center gap-2 rounded-xl bg-[#047857] px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-[#065F46] transition cursor-pointer shrink-0"
                  >
                    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z" />
                    </svg>
                    <span>View QR Passes</span>
                  </button>
                </div>
              </div>
            ) : (
              <div className="mt-4 rounded-xl border border-dashed border-slate-200 p-6 text-center">
                <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-full bg-slate-50 text-slate-400">
                  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                  </svg>
                </div>
                <p className="mt-2 text-xs font-medium text-[#172033]">No active bookings scheduled for today</p>
                <p className="mt-0.5 text-[11px] text-[#64748B]">
                  Join the queue during open admission windows or reserve an available slot.
                </p>
                {canBook && (
                  <button
                    type="button"
                    onClick={onStartBooking}
                    className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-800 hover:bg-emerald-100 transition cursor-pointer"
                  >
                    + Book a Court
                  </button>
                )}
              </div>
            )}
          </div>

          {/* D. Court Availability Card */}
          <div className="rounded-2xl border border-[#E2E8F0] bg-white p-5 shadow-xs">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-emerald-50 text-emerald-700">
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                  </svg>
                </div>
                <h2 className="text-sm font-bold text-[#172033]">Court Availability</h2>
              </div>
              <span className="text-[11px] text-[#64748B]">Indoor Wooden Courts · 4 Total</span>
            </div>

            <div className="mt-4 grid grid-cols-2 sm:grid-cols-4 gap-3">
              {courts.slice(0, 4).map((court) => (
                <div
                  key={court.id}
                  className="flex flex-col items-center justify-center rounded-xl border border-slate-200/80 bg-slate-50/40 p-3.5 text-center transition hover:border-emerald-300"
                >
                  <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-white border border-slate-200 shadow-2xs text-xs font-extrabold text-[#064E3B]">
                    C{court.courtNumber}
                  </div>
                  <span className="mt-2 text-xs font-bold text-[#172033]">
                    Court {court.courtNumber}
                  </span>
                  <span
                    className={`mt-1.5 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                      court.active
                        ? 'bg-emerald-50 text-emerald-800'
                        : 'bg-amber-50 text-amber-800'
                    }`}
                  >
                    <span
                      className={`h-1 w-1 rounded-full ${
                        court.active ? 'bg-emerald-600' : 'bg-amber-600'
                      }`}
                    />
                    {court.active ? 'Active' : 'Maintenance'}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* RIGHT COLUMN: Sidebar (4 Cols) */}
        <div className="space-y-6 lg:col-span-4">
          {/* 1. Student Profile & Weekly Credits Card */}
          <div className="rounded-2xl border border-[#E2E8F0] bg-white p-5 shadow-xs">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <span className="text-xs font-bold uppercase tracking-wider text-[#64748B]">
                Student Profile
              </span>
              {profile.isRealAccount && (
                <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-800">
                  Verified
                </span>
              )}
            </div>

            {isAuthenticated ? (
              <div className="mt-4 space-y-4">
                {/* Profile Identity */}
                <div className="flex items-center gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-full bg-[#ECFDF5] border border-emerald-200 text-sm font-bold text-[#047857]">
                    {profile.name ? profile.name.charAt(0).toUpperCase() : 'U'}
                  </div>
                  <div>
                    <h3 className="text-sm font-bold text-[#172033] leading-tight">
                      {profile.name}
                    </h3>
                    <p className="text-xs font-mono text-[#64748B] mt-0.5">
                      {profile.admissionNumber}
                    </p>
                  </div>
                </div>

                {/* Metrics: Credits & Reliability */}
                <div className="grid grid-cols-2 gap-2.5">
                  <div className="rounded-xl border border-slate-100 bg-[#F6F8F7] p-3">
                    <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">Credits</span>
                    <div className="mt-1 flex items-baseline gap-1">
                      <span className="text-xl font-black text-[#172033]">
                        {profile.weeklyCreditsRemaining}
                      </span>
                      <span className="text-xs text-[#64748B]">/ {profile.weeklyCreditsTotal}</span>
                    </div>
                    {/* Visual credit dots */}
                    <div className="mt-2 flex gap-1">
                      {Array.from({ length: profile.weeklyCreditsTotal }).map((_, i) => (
                        <div
                          key={i}
                          className={`h-1.5 flex-1 rounded-full ${
                            i < profile.weeklyCreditsRemaining ? 'bg-[#047857]' : 'bg-slate-200'
                          }`}
                        />
                      ))}
                    </div>
                  </div>

                  <div className="rounded-xl border border-slate-100 bg-[#F6F8F7] p-3">
                    <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">Priority</span>
                    <div className="mt-1">
                      <span className="text-xs font-bold text-emerald-800">
                        {profile.weeklyCreditsRemaining > 0 ? 'Normal Priority' : 'Reduced (0 Cr)'}
                      </span>
                      <p className="text-[10px] text-[#64748B] mt-1">Resets Mon 12:00 AM</p>
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              <div className="mt-4 text-center">
                <p className="text-xs font-medium text-[#172033]">Institutional Account Required</p>
                <p className="mt-1 text-[11px] text-[#64748B]">
                  Sign in with your university admission credentials to view credits and reserve courts.
                </p>
                <div className="mt-3 flex flex-wrap items-center justify-center gap-2">
                  {onNavigateLogin && (
                    <button
                      type="button"
                      onClick={onNavigateLogin}
                      className="rounded-lg bg-[#047857] px-3.5 py-1.5 text-xs font-semibold text-white shadow-2xs hover:bg-[#065F46] transition cursor-pointer"
                    >
                      Sign In
                    </button>
                  )}
                  {onNavigateRegister && (
                    <button
                      type="button"
                      onClick={onNavigateRegister}
                      className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition cursor-pointer"
                    >
                      Register
                    </button>
                  )}
                  {onNavigateVerifyEmail && (
                    <button
                      type="button"
                      onClick={onNavigateVerifyEmail}
                      className="rounded-lg border border-emerald-300 bg-emerald-50 px-2.5 py-1.5 text-xs font-semibold text-emerald-800 hover:bg-emerald-100 transition cursor-pointer"
                    >
                      Verify Token
                    </button>
                  )}
                </div>
              </div>
            )}
          </div>

          {/* 2. Quick Actions Card */}
          <div className="rounded-2xl border border-[#E2E8F0] bg-white p-5 shadow-xs">
            <span className="text-xs font-bold uppercase tracking-wider text-[#64748B]">
              Quick Actions
            </span>

            <div className="mt-3 space-y-2">
              <button
                type="button"
                onClick={onStartBooking}
                className="w-full flex items-center justify-between rounded-xl border border-slate-100 bg-slate-50/60 px-3.5 py-2.5 text-xs font-semibold text-[#172033] hover:bg-emerald-50 hover:border-emerald-200 transition cursor-pointer text-left"
              >
                <div className="flex items-center gap-2.5">
                  <span className="text-emerald-700 font-bold">📅</span>
                  <span>Book a Court</span>
                </div>
                <span className="text-slate-400">→</span>
              </button>

              <button
                type="button"
                onClick={onViewQRPass}
                className="w-full flex items-center justify-between rounded-xl border border-slate-100 bg-slate-50/60 px-3.5 py-2.5 text-xs font-semibold text-[#172033] hover:bg-emerald-50 hover:border-emerald-200 transition cursor-pointer text-left"
              >
                <div className="flex items-center gap-2.5">
                  <span className="text-emerald-700 font-bold">🎟️</span>
                  <span>View My QR Passes</span>
                </div>
                <span className="text-slate-400">→</span>
              </button>

              {onOpenQueueModal && (
                <button
                  type="button"
                  onClick={onOpenQueueModal}
                  className="w-full flex items-center justify-between rounded-xl border border-slate-100 bg-slate-50/60 px-3.5 py-2.5 text-xs font-semibold text-[#172033] hover:bg-emerald-50 hover:border-emerald-200 transition cursor-pointer text-left"
                >
                  <div className="flex items-center gap-2.5">
                    <span className="text-emerald-700 font-bold">⚡</span>
                    <span>Join Queue Line</span>
                  </div>
                  <span className="text-slate-400">→</span>
                </button>
              )}
            </div>
          </div>

          {/* 3. Campus Announcements Card */}
          <div className="rounded-2xl border border-[#E2E8F0] bg-white p-5 shadow-xs">
            <div className="flex items-center gap-2 text-xs font-bold text-[#64748B] uppercase tracking-wider">
              <span className="text-amber-600">📢</span>
              <span>Court Rules & Notices</span>
            </div>

            <ul className="mt-3 space-y-2.5 text-xs text-[#64748B]">
              <li className="flex items-start gap-2">
                <span className="text-emerald-700 font-bold mt-0.5">•</span>
                <span>Non-marking badminton shoes strictly required on synthetic and wooden courts.</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-emerald-700 font-bold mt-0.5">•</span>
                <span>All players must present individual QR passes at the desk within 10 min of slot start.</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-emerald-700 font-bold mt-0.5">•</span>
                <span>Weekly credits reset automatically every Monday at 12:00 AM IST.</span>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
};
