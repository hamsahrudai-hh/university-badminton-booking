import React, { useState } from 'react';
import type { SessionType, TimeSlot } from '../../types/booking';

interface StepSlotSelectionProps {
  slots: TimeSlot[];
  selectedSlot: TimeSlot | null;
  onSelectSlot: (slot: TimeSlot) => void;
  onContinue: () => void;
}

export const StepSlotSelection: React.FC<StepSlotSelectionProps> = ({
  slots,
  selectedSlot,
  onSelectSlot,
  onContinue,
}) => {
  const [session, setSession] = useState<SessionType>(selectedSlot ? selectedSlot.session : 'morning');

  const filteredSlots = slots.filter((s) => s.session === session);

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-base sm:text-lg font-bold text-slate-900">Step 1: Choose a 30-Minute Time Slot</h2>
        <p className="text-xs text-slate-500 mt-0.5">
          Select either a morning session (6:00–8:30 AM) or an evening session (3:30–7:00 PM).
        </p>
      </div>

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
              5 Slots
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
              7 Slots
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
          className="inline-flex h-9 items-center justify-center gap-1.5 rounded-lg bg-slate-900 px-5 text-xs font-semibold text-white shadow-2xs transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-900"
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
