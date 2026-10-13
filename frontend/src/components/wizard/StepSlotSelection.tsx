import React, { useState, useEffect, useMemo } from 'react';
import { fetchSlots, type TimeSlotResponseDto } from '../../api/client';
import type { SessionType, TimeSlot } from '../../types/booking';
import { groupDtosToTimeSlots } from '../../utils/slotUtils';

interface StepSlotSelectionProps {
  slots: TimeSlot[];
  selectedSlot: TimeSlot | null;
  onSelectSlot: (slot: TimeSlot) => void;
  onContinue: () => void;
  date?: string;
}

export const StepSlotSelection: React.FC<StepSlotSelectionProps> = ({
  slots,
  selectedSlot,
  onSelectSlot,
  onContinue,
  date,
}) => {
  const [session, setSession] = useState<SessionType>(selectedSlot ? selectedSlot.session : 'morning');
  const [realSlots, setRealSlots] = useState<TimeSlotResponseDto[] | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const bookingDate = useMemo(() => {
    if (date) return date;
    if (typeof window !== 'undefined') {
      const queryDate = new URLSearchParams(window.location.search).get('date');
      if (queryDate && /^\d{4}-\d{2}-\d{2}$/.test(queryDate)) {
        return queryDate;
      }
    }
    return '2026-10-15';
  }, [date]);

  const selectedSlotRef = React.useRef(selectedSlot);
  const onSelectSlotRef = React.useRef(onSelectSlot);
  const slotsRef = React.useRef(slots);

  useEffect(() => {
    selectedSlotRef.current = selectedSlot;
    onSelectSlotRef.current = onSelectSlot;
    slotsRef.current = slots;
  });

  const handleRetry = () => {
    setIsLoading(true);
    setError(null);
    fetchSlots(bookingDate)
      .then((data) => {
        setRealSlots(data);
        setIsLoading(false);
        if (data && data.length > 0 && selectedSlotRef.current) {
          const grouped = groupDtosToTimeSlots(data, slotsRef.current);
          const current = selectedSlotRef.current;
          const matched = grouped.find(
            (g) =>
              g.id === current.id ||
              (g.session === current.session && g.startTime === current.startTime)
          );
          if (matched && (!current.realTimeSlotId || !current.courtSlotMap)) {
            onSelectSlotRef.current(matched);
          }
        }
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : 'Network error');
        setIsLoading(false);
      });
  };

  useEffect(() => {
    let isMounted = true;
    fetchSlots(bookingDate)
      .then((data) => {
        if (isMounted) {
          setRealSlots(data);
          setIsLoading(false);

          if (data && data.length > 0 && selectedSlotRef.current) {
            const grouped = groupDtosToTimeSlots(data, slotsRef.current);
            const current = selectedSlotRef.current;
            const matched = grouped.find(
              (g) =>
                g.id === current.id ||
                (g.session === current.session && g.startTime === current.startTime)
            );
            if (matched && (!current.realTimeSlotId || !current.courtSlotMap)) {
              onSelectSlotRef.current(matched);
            }
          }
        }
      })
      .catch((err) => {
        if (isMounted) {
          console.warn('Failed to fetch slots from API, falling back to mock data:', err);
          setError(err instanceof Error ? err.message : 'Network error');
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [bookingDate]);

  // Derive distinct time intervals from real API slots (or fallback to mock slots on request error)
  const displaySlots: TimeSlot[] = useMemo(() => {
    if (error !== null || realSlots === null) {
      return slots;
    }
    return groupDtosToTimeSlots(realSlots, slots);
  }, [realSlots, error, slots]);

  const morningSlots = useMemo(() => displaySlots.filter((s) => s.session === 'morning'), [displaySlots]);
  const eveningSlots = useMemo(() => displaySlots.filter((s) => s.session === 'evening'), [displaySlots]);

  const filteredSlots = session === 'morning' ? morningSlots : eveningSlots;

  return (
    <div className="space-y-4">
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="text-base sm:text-lg font-bold text-slate-900">Step 1: Choose a 30-Minute Time Slot</h2>
          <span className="text-xs text-slate-500 font-medium">· for {bookingDate}</span>

          {isLoading && (
            <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-500">
              <svg className="h-2.5 w-2.5 animate-spin text-slate-400" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
              </svg>
              Connecting...
            </span>
          )}

          {!isLoading && !error && realSlots && (
            <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-semibold text-emerald-800">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-600"></span>
              Live API ({realSlots.length} slots)
            </span>
          )}

          {error && (
            <span
              className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-medium text-amber-800"
              title={error}
            >
              Offline fallback
            </span>
          )}
        </div>
        <p className="text-xs text-slate-500 mt-0.5">
          Select either a morning session (6:00–8:30 AM) or an evening session (3:30–7:00 PM).
        </p>
      </div>

      {/* Non-intrusive offline fallback notice with retry */}
      {error && (
        <div className="rounded-lg border border-amber-200 bg-amber-50/70 p-2 text-[11px] text-amber-800 flex items-center justify-between">
          <span>Notice: Live time slots unavailable ({error}). Displaying local slot data.</span>
          <button
            type="button"
            onClick={handleRetry}
            className="font-semibold text-amber-900 underline hover:no-underline ml-2 cursor-pointer"
          >
            Retry
          </button>
        </div>
      )}

      {/* Session Switcher */}
      <div className="grid grid-cols-2 gap-2.5">
        <button
          type="button"
          onClick={() => setSession('morning')}
          className={`rounded-xl border p-2.5 sm:p-3 text-left transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 ${
            session === 'morning'
              ? 'border-emerald-700 bg-emerald-50/50 ring-1 ring-emerald-700 shadow-2xs'
              : 'border-slate-200 bg-white hover:border-slate-300'
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-900">Morning Session</span>
            <span className="text-[10px] font-semibold text-emerald-800 bg-emerald-100/80 px-2 py-0.2 rounded-full">
              {morningSlots.length} Slots
            </span>
          </div>
          <p className="mt-0.5 text-[11px] text-slate-500">6:00 AM – 8:30 AM</p>
        </button>

        <button
          type="button"
          onClick={() => setSession('evening')}
          className={`rounded-xl border p-2.5 sm:p-3 text-left transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 ${
            session === 'evening'
              ? 'border-emerald-700 bg-emerald-50/50 ring-1 ring-emerald-700 shadow-2xs'
              : 'border-slate-200 bg-white hover:border-slate-300'
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-900">Evening Session</span>
            <span className="text-[10px] font-semibold text-emerald-800 bg-emerald-100/80 px-2 py-0.2 rounded-full">
              {eveningSlots.length} Slots
            </span>
          </div>
          <p className="mt-0.5 text-[11px] text-slate-500">3:30 PM – 7:00 PM</p>
        </button>
      </div>

      {/* 30-Minute Slots Grid */}
      <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 md:grid-cols-4">
        {filteredSlots.map((slot) => {
          const isSelected = selectedSlot?.id === slot.id;

          return (
            <button
              key={slot.id}
              type="button"
              onClick={() => onSelectSlot(slot)}
              className={`flex flex-col items-center justify-center rounded-xl p-3 text-center transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 ${
                isSelected
                  ? 'border-2 border-emerald-700 bg-emerald-50 text-emerald-950 shadow-2xs'
                  : 'border border-slate-200 bg-white text-slate-700 hover:border-emerald-400 hover:bg-slate-50/60'
              }`}
            >
              <span className="text-xs font-bold sm:text-sm text-slate-900">
                {slot.startTime}
              </span>
              <span className="text-[10px] text-slate-500">
                to {slot.endTime}
              </span>
              <span className="mt-1 text-[10px] text-slate-400">
                30 Mins
              </span>
              {isSelected && (
                <span className="mt-1 inline-flex items-center gap-0.5 text-[10px] font-bold text-emerald-700">
                  <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="3">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                  </svg>
                  Selected
                </span>
              )}
            </button>
          );
        })}

        {/* Empty state when API returns 0 slots for this session */}
        {filteredSlots.length === 0 && !isLoading && (
          <div className="col-span-full rounded-xl border border-dashed border-slate-300 bg-slate-50/60 p-6 text-center">
            <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-full bg-slate-100 text-slate-400 mb-2">
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <h3 className="text-xs sm:text-sm font-bold text-slate-700">No time slots found</h3>
            <p className="mt-1 text-xs text-slate-500 max-w-sm mx-auto">
              No {session} slots have been generated for {bookingDate}.
            </p>
          </div>
        )}
      </div>

      {/* Wizard Step Footer */}
      <div className="flex items-center justify-between border-t border-slate-200 pt-3.5">
        <span className="text-xs text-slate-500">
          {selectedSlot ? `Selected: ${selectedSlot.label}` : 'Select a slot to proceed'}
        </span>

        <button
          type="button"
          disabled={!selectedSlot}
          onClick={onContinue}
          className="inline-flex h-9 items-center justify-center gap-1.5 rounded-lg bg-[#047857] px-5 text-xs font-semibold text-white shadow-2xs transition hover:bg-[#065F46] disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer"
        >
          <span>Continue to Court</span>
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>
    </div>
  );
};
