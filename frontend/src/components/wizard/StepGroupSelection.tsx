import React, { useEffect, useState, useMemo } from 'react';
import { fetchStudents, type TeammateOptionDto } from '../../api/client';
import type { RegisteredStudent, GroupPlayerAssignment, TimeSlot } from '../../types/booking';

interface StepGroupSelectionProps {
  selectedSlot: TimeSlot;
  courtName: string;
  allRegisteredStudents?: RegisteredStudent[];
  selectedPlayers: GroupPlayerAssignment[];
  onUpdatePlayers: (players: GroupPlayerAssignment[]) => void;
  onBack: () => void;
  onContinue: () => void;
  currentHostId?: number;
  onNavigateRegister?: () => void;
}

export const StepGroupSelection: React.FC<StepGroupSelectionProps> = ({
  selectedSlot,
  courtName,
  selectedPlayers,
  onUpdatePlayers,
  onBack,
  onContinue,
  currentHostId,
  onNavigateRegister,
}) => {
  const [realStudents, setRealStudents] = useState<TeammateOptionDto[] | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const loadStudents = () => {
    setIsLoading(true);
    setError(null);
    fetchStudents()
      .then((data) => {
        setRealStudents(data);
        setIsLoading(false);
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : 'Failed to connect to student directory');
        setIsLoading(false);
      });
  };

  useEffect(() => {
    let isMounted = true;
    fetchStudents()
      .then((data) => {
        if (isMounted) {
          setRealStudents(data);
          setIsLoading(false);
        }
      })
      .catch((err) => {
        if (isMounted) {
          setError(err instanceof Error ? err.message : 'Failed to connect to student directory');
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  // Map backend TeammateOptionDto to RegisteredStudent representation
  const availableStudents: RegisteredStudent[] = useMemo(() => {
    if (!realStudents || realStudents.length === 0) return [];

    return realStudents.map((s) => ({
      id: String(s.id),
      numericId: s.id,
      name: s.fullName,
      isEligible: Boolean(s.eligible),
    }));
  }, [realStudents]);

  // Identify the booking host from available real students
  const hostStudent = useMemo(() => {
    if (availableStudents.length === 0) return null;
    if (currentHostId) {
      const match = availableStudents.find((s) => s.numericId === currentHostId);
      if (match) return match;
    }
    const spot1 = selectedPlayers.find((p) => p.spotNumber === 1);
    if (spot1 && spot1.student.numericId) {
      const match = availableStudents.find((s) => s.numericId === spot1.student.numericId);
      if (match) return match;
    }
    return availableStudents[0];
  }, [availableStudents, currentHostId, selectedPlayers]);

  const bookingHostId = hostStudent ? hostStudent.id : null;

  // Synchronize selectedPlayers so Spot 1 is the real host and teammates only contain real backend students
  useEffect(() => {
    if (!hostStudent) return;

    const spot1 = selectedPlayers.find((p) => p.spotNumber === 1);
    const hostNeedsUpdate = !spot1 || spot1.student.numericId !== hostStudent.numericId;

    // Filter valid teammates: must have real numericId and exist in availableStudents
    const validTeammateAssignments = selectedPlayers
      .filter((p) => p.spotNumber > 1 && p.student.numericId && p.student.numericId !== hostStudent.numericId)
      .map((p) => {
        const fresh = availableStudents.find((s) => s.numericId === p.student.numericId);
        return fresh ? { ...p, student: fresh } : null;
      })
      .filter(Boolean) as GroupPlayerAssignment[];

    const hasMockOrInvalidPlayers = selectedPlayers.some(
      (p) => !p.student.numericId || !availableStudents.some((s) => s.numericId === p.student.numericId)
    );

    if (hostNeedsUpdate || hasMockOrInvalidPlayers) {
      const updated: GroupPlayerAssignment[] = [
        { spotNumber: 1, student: hostStudent },
        ...validTeammateAssignments.map((p, idx) => ({ ...p, spotNumber: idx + 2 })),
      ];
      onUpdatePlayers(updated);
    }
  }, [hostStudent, availableStudents, selectedPlayers, onUpdatePlayers]);

  const isStudentSelected = (studentId: string) => {
    return selectedPlayers.some((p) => p.student.id === studentId);
  };

  const handleToggleStudent = (student: RegisteredStudent) => {
    if (hostStudent && student.numericId === hostStudent.numericId) return;

    if (isStudentSelected(student.id)) {
      const remaining = selectedPlayers.filter((p) => p.student.id !== student.id);
      const reindexed = remaining.map((p, index) => ({
        ...p,
        spotNumber: index + 1,
      }));
      onUpdatePlayers(reindexed);
    } else {
      if (selectedPlayers.length >= 4) {
        alert('Maximum group capacity: 4 spots per court.');
        return;
      }
      const nextSpotNumber = selectedPlayers.length + 1;
      onUpdatePlayers([...selectedPlayers, { student, spotNumber: nextSpotNumber }]);
    }
  };

  const playerCount = selectedPlayers.length;
  const isGroupValid = playerCount >= 2 && playerCount <= 4;

  return (
    <div className="space-y-4">
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="text-base sm:text-lg font-bold text-slate-900">Step 3: Select Group Members & Spots</h2>
          <span className="text-xs text-slate-500 font-medium">· {courtName} · {selectedSlot.label}</span>

          {isLoading && (
            <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-500">
              <svg className="h-2.5 w-2.5 animate-spin text-slate-400" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
              </svg>
              Connecting...
            </span>
          )}

          {!isLoading && !error && realStudents && (
            <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-semibold text-emerald-800">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-600"></span>
              Live API ({realStudents.length} Teammates)
            </span>
          )}

          {!isLoading && error && (
            <span className="inline-flex items-center gap-1 rounded-full bg-rose-100 px-2 py-0.5 text-[10px] font-semibold text-rose-800">
              API Error
            </span>
          )}
        </div>
        <p className="text-xs text-slate-500 mt-0.5">
          Per university rules, reservations require 2 to 4 registered, eligible students (including yourself).
        </p>
      </div>

      {/* Privacy Notice */}
      <div className="rounded-lg border border-slate-200 bg-slate-50/70 p-2.5 text-[11px] text-slate-600 flex items-start gap-2">
        <span className="font-semibold text-slate-700 shrink-0">Privacy Note:</span>
        <span>
          Teammates are queried from <code>GET /api/students</code> in a privacy-safe format (ID and display name only). Private admission numbers, emails, and individual credit balances are protected.
        </span>
      </div>

      {/* Error Notice */}
      {error && (
        <div className="rounded-xl border border-rose-200 bg-rose-50/80 p-3 text-xs text-rose-900 flex items-center justify-between">
          <div>
            <p className="font-bold">Failed to load registered students</p>
            <p className="text-[11px] text-rose-700">{error}</p>
          </div>
          <button
            type="button"
            onClick={loadStudents}
            className="rounded-md border border-rose-300 bg-white px-2.5 py-1 text-xs font-semibold text-rose-800 shadow-2xs hover:bg-rose-50"
          >
            Retry
          </button>
        </div>
      )}

      {/* Validation status pill */}
      <div
        className={`rounded-xl border p-2.5 sm:p-3 text-xs transition ${
          isGroupValid
            ? 'border-emerald-200 bg-emerald-50/80 text-emerald-900'
            : 'border-amber-200 bg-amber-50/80 text-amber-900'
        }`}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span
              className={`h-2 w-2 rounded-full ${
                isGroupValid ? 'bg-emerald-600' : 'bg-amber-600'
              }`}
            />
            <span className="font-semibold text-xs">
              {isGroupValid
                ? `Group Ready: ${playerCount} of 4 spots allocated`
                : `Minimum 2 players required (Currently ${playerCount} selected)`}
            </span>
          </div>
          <span className="font-mono text-xs font-bold">{playerCount} / 4 Spots</span>
        </div>
        <p className="mt-0.5 text-[11px] opacity-80">
          Every player must be verified as registered and free from conflicting time slots.
        </p>
      </div>

      {/* Spot Assignment Visual Roster */}
      <div className="space-y-1.5">
        <h3 className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
          Current Court Spot Assignments
        </h3>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          {[1, 2, 3, 4].map((spotNum) => {
            const assignment = selectedPlayers.find((p) => p.spotNumber === spotNum);

            return (
              <div
                key={spotNum}
                className={`flex flex-col rounded-lg border p-2 text-center transition ${
                  assignment
                    ? 'border-emerald-600 bg-emerald-50/50 text-emerald-950'
                    : 'border-dashed border-slate-200 bg-slate-50/70 text-slate-400'
                }`}
              >
                <span className="text-[10px] font-bold uppercase tracking-wider">
                  Spot {spotNum}
                </span>
                <span className="mt-0.5 text-xs font-semibold truncate">
                  {assignment ? assignment.student.name : 'Unassigned'}
                </span>
                <span className="text-[10px] font-mono text-slate-400 truncate">
                  {assignment
                    ? assignment.spotNumber === 1
                      ? 'Spot 1 · Host'
                      : `Spot ${assignment.spotNumber} · Teammate`
                    : 'Empty spot'}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Registered Student Directory or Empty State */}
      <div className="space-y-1.5">
        <h3 className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
          Select From Registered Campus Teammates
        </h3>

        {!isLoading && !error && realStudents && realStudents.length === 0 && (
          <div className="rounded-xl border border-amber-200 bg-amber-50/60 p-5 text-center">
            <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-full bg-amber-100 text-amber-700">
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
            </div>
            <h4 className="mt-2 text-xs font-bold text-amber-900">
              No Registered Students in Database
            </h4>
            <p className="mt-1 text-[11px] text-amber-800 max-w-md mx-auto">
              <code>GET /api/students</code> returned 0 student accounts. The development database does not contain registered students yet.
            </p>
            <p className="mt-1 text-[11px] text-amber-700 max-w-md mx-auto">
              Per university rules, bookings require 2 to 4 registered, eligible students. Without registered accounts in the database, group selection cannot proceed to booking confirmation.
            </p>
            <div className="mt-3 flex items-center justify-center gap-2">
              {onNavigateRegister && (
                <button
                  type="button"
                  onClick={onNavigateRegister}
                  className="rounded-md bg-emerald-700 px-3 py-1.5 text-xs font-bold text-white shadow-2xs hover:bg-emerald-800"
                >
                  Register Student Account
                </button>
              )}
              <button
                type="button"
                onClick={loadStudents}
                className="rounded-md border border-amber-300 bg-white px-3 py-1.5 text-xs font-semibold text-amber-900 shadow-2xs hover:bg-amber-50"
              >
                Refresh Student Directory
              </button>
            </div>
          </div>
        )}

        {!isLoading && !error && realStudents && realStudents.length === 1 && (
          <div className="rounded-xl border border-amber-200 bg-amber-50/70 p-3.5 text-xs text-amber-900 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div className="space-y-0.5">
              <div className="flex items-center gap-1.5 font-bold text-amber-950">
                <span className="h-2 w-2 rounded-full bg-amber-600"></span>
                <span>Additional Teammate Required (Min 2 Players)</span>
              </div>
              <p className="text-[11px] text-amber-800">
                Currently, only 1 student account ({hostStudent?.name}) is registered in the database. A booking group requires 2 to 4 registered students.
              </p>
            </div>
            {onNavigateRegister && (
              <button
                type="button"
                onClick={onNavigateRegister}
                className="inline-flex items-center justify-center rounded-lg bg-emerald-700 px-3 py-1.5 text-[11px] font-bold text-white shadow-2xs hover:bg-emerald-800 shrink-0 cursor-pointer"
              >
                + Register Teammate Account
              </button>
            )}
          </div>
        )}

        {availableStudents.length > 0 && (
          <div className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white">
            {availableStudents.map((student) => {
              const isSelected = isStudentSelected(student.id);
              const isSelf = bookingHostId ? student.id === bookingHostId : false;
              const assignment = selectedPlayers.find((p) => p.student.id === student.id);
              const isEligible = student.isEligible;

              return (
                <div
                  key={student.id}
                  className="flex items-center justify-between p-2.5 sm:p-3 hover:bg-slate-50/50 transition"
                >
                  <div className="flex items-center gap-2.5">
                    <input
                      type="checkbox"
                      id={`student-${student.id}`}
                      checked={isSelected}
                      disabled={isSelf || !isEligible}
                      onChange={() => handleToggleStudent(student)}
                      className="h-3.5 w-3.5 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500 cursor-pointer disabled:cursor-not-allowed"
                    />
                    <label htmlFor={`student-${student.id}`} className="cursor-pointer select-none">
                      <div className="flex items-center gap-1.5">
                        <span className="text-xs font-bold text-slate-900">{student.name}</span>
                        {isSelf && (
                          <span className="rounded bg-slate-100 px-1.5 py-0.2 text-[9px] font-semibold text-slate-600">
                            Booking Host
                          </span>
                        )}
                        {isEligible ? (
                          <span className="rounded-full bg-emerald-100 px-1.5 py-0.2 text-[9px] font-semibold text-emerald-800">
                            Eligible
                          </span>
                        ) : (
                          <span className="rounded-full bg-rose-100 px-1.5 py-0.2 text-[9px] font-semibold text-rose-800">
                            Ineligible
                          </span>
                        )}
                      </div>
                      <p className="text-[11px] text-slate-500">
                        {isSelf
                          ? 'Spot 1 Host (Your Account)'
                          : 'Campus Teammate · Eligible for group play'}
                      </p>
                    </label>
                  </div>

                  <div>
                    {assignment ? (
                      <span className="rounded-md bg-emerald-700 px-2 py-0.5 text-[11px] font-bold text-white shadow-2xs">
                        Spot {assignment.spotNumber}
                      </span>
                    ) : (
                      <span className="text-[11px] text-slate-400">Available</span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
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
          <span>Back to Court</span>
        </button>

        <button
          type="button"
          disabled={!isGroupValid}
          onClick={onContinue}
          className="inline-flex h-9 items-center justify-center gap-1.5 rounded-lg bg-[#047857] px-5 text-xs font-semibold text-white shadow-2xs transition hover:bg-[#065F46] disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer"
        >
          <span>Continue to Review</span>
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>
    </div>
  );
};
