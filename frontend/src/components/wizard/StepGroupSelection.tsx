import type React from 'react';
import type { RegisteredStudent, GroupPlayerAssignment, TimeSlot } from '../../types/booking';

interface StepGroupSelectionProps {
  selectedSlot: TimeSlot;
  courtName: string;
  allRegisteredStudents: RegisteredStudent[];
  selectedPlayers: GroupPlayerAssignment[];
  onUpdatePlayers: (players: GroupPlayerAssignment[]) => void;
  onBack: () => void;
  onContinue: () => void;
}

export const StepGroupSelection: React.FC<StepGroupSelectionProps> = ({
  selectedSlot,
  courtName,
  allRegisteredStudents,
  selectedPlayers,
  onUpdatePlayers,
  onBack,
  onContinue,
}) => {
  // Primary student is always student s-1 (Alex Chen)
  const isStudentSelected = (studentId: string) => {
    return selectedPlayers.some((p) => p.student.id === studentId);
  };

  const handleToggleStudent = (student: RegisteredStudent) => {
    // Cannot deselect self (primary booking student)
    if (student.id === 's-1') return;

    if (isStudentSelected(student.id)) {
      // Remove student and re-index spot numbers
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
      // Add student to next spot
      const nextSpotNumber = selectedPlayers.length + 1;
      onUpdatePlayers([...selectedPlayers, { student, spotNumber: nextSpotNumber }]);
    }
  };

  const playerCount = selectedPlayers.length;
  const isGroupValid = playerCount >= 2 && playerCount <= 4;

  return (
    <div className="space-y-4">
      <div>
        <div className="flex items-center gap-2">
          <h2 className="text-base sm:text-lg font-bold text-slate-900">Step 3: Select Group Members & Spots</h2>
          <span className="text-xs text-slate-500 font-medium">· {courtName} · {selectedSlot.label}</span>
        </div>
        <p className="text-xs text-slate-500 mt-0.5">
          Per university rules, reservations require 2 to 4 registered, eligible students (including yourself).
        </p>
      </div>

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
                  {assignment ? assignment.student.admissionNumber : 'Empty spot'}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Registered Student Directory */}
      <div className="space-y-1.5">
        <h3 className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
          Select From Registered Campus Teammates
        </h3>
        <div className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white">
          {allRegisteredStudents.map((student) => {
            const isSelected = isStudentSelected(student.id);
            const isSelf = student.id === 's-1';
            const assignment = selectedPlayers.find((p) => p.student.id === student.id);

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
                    disabled={isSelf}
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
                      <span className="rounded-full bg-emerald-100 px-1.5 py-0.2 text-[9px] font-semibold text-emerald-800">
                        Eligible
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-500">
                      {student.admissionNumber} · {student.email}
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
          className="inline-flex h-9 items-center justify-center gap-1.5 rounded-lg bg-slate-900 px-5 text-xs font-semibold text-white shadow-2xs transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-900"
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
