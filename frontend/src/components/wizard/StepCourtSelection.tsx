import React, { useEffect, useState, useMemo } from 'react';
import { fetchCourts, type CourtResponseDto } from '../../api/client';
import type { CourtSlotData, TimeSlot } from '../../types/booking';

interface StepCourtSelectionProps {
  selectedSlot: TimeSlot;
  courts: CourtSlotData[];
  selectedCourtId: number | null;
  onSelectCourt: (courtId: number, courtName: string, realTimeSlotId?: number) => void;
  onBack: () => void;
  onContinue: () => void;
}

export const StepCourtSelection: React.FC<StepCourtSelectionProps> = ({
  selectedSlot,
  courts,
  selectedCourtId,
  onSelectCourt,
  onBack,
  onContinue,
}) => {
  const [realCourts, setRealCourts] = useState<CourtResponseDto[] | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const handleRetry = () => {
    setIsLoading(true);
    setError(null);
    fetchCourts()
      .then((data) => {
        setRealCourts(data);
        setIsLoading(false);
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : 'Network error');
        setIsLoading(false);
      });
  };

  useEffect(() => {
    let isMounted = true;
    fetchCourts()
      .then((data) => {
        if (isMounted) {
          setRealCourts(data);
          setIsLoading(false);
        }
      })
      .catch((err) => {
        if (isMounted) {
          console.warn('Failed to load courts from API, falling back to mock data:', err);
          setError(err instanceof Error ? err.message : 'Network error');
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  // Merge real court list with court metadata and mock spot occupancies
  const displayCourts: CourtSlotData[] = useMemo(() => {
    if (!realCourts || realCourts.length === 0) {
      return courts;
    }

    const activeCourts = realCourts.filter((c) => c.active);
    if (activeCourts.length === 0) {
      return courts;
    }

    return activeCourts.map((backendCourt) => {
      const mockCourt = courts.find(
        (m) => m.courtId === backendCourt.courtNumber || m.courtId === backendCourt.id
      );

      if (mockCourt) {
        return {
          ...mockCourt,
          courtId: backendCourt.courtNumber,
          courtName: `Court ${backendCourt.courtNumber}`,
        };
      }

      // Safe fallback if backend returns a court number not present in mock data
      return {
        courtId: backendCourt.courtNumber,
        courtName: `Court ${backendCourt.courtNumber}`,
        type: backendCourt.courtNumber % 2 === 1 ? 'Synthetic Wooden' : 'Rubber Mat',
        spots: [1, 2, 3, 4].map((spotNum) => ({
          spotNumber: spotNum,
          status: 'available' as const,
        })),
      };
    });
  }, [realCourts, courts]);

  return (
    <div className="space-y-4">
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="text-base sm:text-lg font-bold text-slate-900">Step 2: Select a Court</h2>
          <span className="text-xs text-slate-500 font-medium">· for {selectedSlot.label}</span>

          {isLoading && (
            <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-500">
              <svg className="h-2.5 w-2.5 animate-spin text-slate-400" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
              </svg>
              Connecting...
            </span>
          )}

          {!isLoading && !error && realCourts && (
            <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-semibold text-emerald-800">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-600"></span>
              Live API
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
          Choose from the four university badminton courts. Each court accommodates up to 4 players.
        </p>
      </div>

      {/* Non-intrusive error notice banner if offline fallback is active */}
      {error && (
        <div className="rounded-lg border border-amber-200 bg-amber-50/70 p-2 text-[11px] text-amber-800 flex items-center justify-between">
          <span>Notice: Live court data unavailable ({error}). Displaying local court data.</span>
          <button
            type="button"
            onClick={handleRetry}
            className="font-semibold text-amber-900 underline hover:no-underline ml-2 cursor-pointer"
          >
            Retry
          </button>
        </div>
      )}

      {/* 4 Courts Grid */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {displayCourts.map((court) => {
          const isSelected = selectedCourtId === court.courtId;
          const availableSpots = court.spots.filter((s) => s.status === 'available').length;
          const isFull = availableSpots === 0;

          return (
            <button
              key={court.courtId}
              type="button"
              disabled={isFull}
              onClick={() => {
                const mappedSlotId = selectedSlot.courtSlotMap?.[court.courtId] ?? selectedSlot.realTimeSlotId;
                onSelectCourt(court.courtId, court.courtName, mappedSlotId);
              }}
              className={`flex flex-col rounded-xl border p-4 text-left transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 ${
                isFull
                  ? 'border-slate-200 bg-slate-50/70 opacity-60 cursor-not-allowed'
                  : isSelected
                  ? 'border-2 border-emerald-700 bg-emerald-50/40 ring-1 ring-emerald-700 shadow-2xs'
                  : 'border-slate-200 bg-white hover:border-emerald-400 hover:bg-slate-50/60'
              }`}
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div
                    className={`flex h-8 w-8 items-center justify-center rounded-lg font-bold text-xs ${
                      isSelected ? 'bg-emerald-700 text-white' : 'bg-slate-100 text-slate-700'
                    }`}
                  >
                    C{court.courtId}
                  </div>
                  <div>
                    <h3 className="text-xs sm:text-sm font-bold text-slate-900">{court.courtName}</h3>
                    <p className="text-[11px] text-slate-500">{court.type}</p>
                  </div>
                </div>

                <span
                  className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                    isFull
                      ? 'bg-rose-100 text-rose-800'
                      : 'bg-emerald-100 text-emerald-800'
                  }`}
                >
                  {isFull ? 'Fully Booked' : `${availableSpots} spots free`}
                </span>
              </div>

              {/* Slim Badminton Court Graphic */}
              <div className="mt-3 rounded-md bg-emerald-800/80 p-1.5 text-center text-white">
                <div className="relative flex h-7 items-center justify-center rounded border border-emerald-400/40 border-dashed">
                  <div className="absolute inset-y-0 left-1/2 w-0.5 -translate-x-1/2 bg-white/60"></div>
                  <span className="text-[9px] uppercase tracking-widest text-emerald-100/70 font-mono">
                    Court {court.courtId} · Standard Net
                  </span>
                </div>
              </div>

              <div className="mt-2.5 flex items-center justify-between text-[11px] text-slate-500 pt-2 border-t border-slate-100">
                <span>Capacity: 4 player spots</span>
                {isSelected && (
                  <span className="font-bold text-emerald-700 flex items-center gap-1 text-[11px]">
                    <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="3">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                    </svg>
                    Selected
                  </span>
                )}
              </div>
            </button>
          );
        })}
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
          <span>Back to Time Slot</span>
        </button>

        <button
          type="button"
          disabled={!selectedCourtId}
          onClick={onContinue}
          className="inline-flex h-9 items-center justify-center gap-1.5 rounded-lg bg-[#047857] px-5 text-xs font-semibold text-white shadow-2xs transition hover:bg-[#065F46] disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer"
        >
          <span>Continue to Group</span>
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>
    </div>
  );
};
