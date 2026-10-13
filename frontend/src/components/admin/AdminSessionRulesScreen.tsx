import React, { useState, useEffect, useCallback, useId } from 'react';
import {
  fetchSessions,
  updateSessionConfig,
  getStoredAuthUser,
  type SessionConfigResponseDto,
  type UpdateSessionConfigRequestDto,
  type LoginResponseDto,
  ApiError,
} from '../../api/client';
import { formatTimeFromIso } from '../../utils/slotUtils';

interface AdminSessionRulesScreenProps {
  onBackToDashboard: () => void;
  currentUser?: LoginResponseDto | null;
}

export const AdminSessionRulesScreen: React.FC<AdminSessionRulesScreenProps> = ({
  onBackToDashboard,
  currentUser: propUser,
}) => {
  const [currentUser] = useState<LoginResponseDto | null>(() => propUser ?? getStoredAuthUser());
  const [sessions, setSessions] = useState<SessionConfigResponseDto[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [editingSession, setEditingSession] = useState<SessionConfigResponseDto | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [formValidationError, setFormValidationError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Form states for modal
  const [formStartTime, setFormStartTime] = useState<string>('');
  const [formEndTime, setFormEndTime] = useState<string>('');
  const [formDuration, setFormDuration] = useState<number>(30);
  const [formQueueOpenTime, setFormQueueOpenTime] = useState<string>('');
  const [formBookingStartTime, setFormBookingStartTime] = useState<string>('');
  const [formAdvanceDays, setFormAdvanceDays] = useState<number>(1);
  const [formActive, setFormActive] = useState<boolean>(true);

  // Accessible unique IDs for form controls
  const startTimeId = useId();
  const endTimeId = useId();
  const durationId = useId();
  const queueOpenTimeId = useId();
  const bookingStartTimeId = useId();
  const advanceDaysId = useId();
  const activeToggleId = useId();

  const isStaffOrAdmin =
    currentUser?.role === 'STAFF' || currentUser?.role === 'ADMIN';

  const loadSessions = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const data = await fetchSessions();
      setSessions(data);
    } catch (err) {
      if (err instanceof ApiError) {
        setErrorMessage(err.message);
      } else if (err instanceof Error) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage('Failed to load session rules from server.');
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    let isMounted = true;
    fetchSessions()
      .then((data) => {
        if (isMounted) {
          setSessions(data);
          setIsLoading(false);
        }
      })
      .catch((err) => {
        if (isMounted) {
          if (err instanceof ApiError) {
            setErrorMessage(err.message);
          } else if (err instanceof Error) {
            setErrorMessage(err.message);
          } else {
            setErrorMessage('Failed to load session rules from server.');
          }
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  const openEditModal = (session: SessionConfigResponseDto) => {
    if (!isStaffOrAdmin) return;
    setEditingSession(session);
    setFormValidationError(null);
    setFormStartTime(session.startTime.slice(0, 5));
    setFormEndTime(session.endTime.slice(0, 5));
    setFormDuration(session.slotDurationMinutes);
    setFormQueueOpenTime(session.queueOpenTime.slice(0, 5));
    setFormBookingStartTime(session.bookingStartTime.slice(0, 5));
    setFormAdvanceDays(session.queueDaysInAdvance);
    setFormActive(session.active);
  };

  const closeEditModal = () => {
    setEditingSession(null);
    setFormValidationError(null);
  };

  const handleToggleActiveQuick = async (session: SessionConfigResponseDto) => {
    if (!isStaffOrAdmin) {
      setErrorMessage('Access Denied: Only STAFF and ADMIN roles can modify session settings.');
      return;
    }

    const nextActive = !session.active;
    setIsLoading(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const updated = await updateSessionConfig(session.id, { active: nextActive });
      setSessions((prev) =>
        prev.map((s) => (s.id === updated.id ? updated : s))
      );
      setSuccessMessage(
        `${session.sessionType} session is now ${updated.active ? 'ACTIVE' : 'INACTIVE'}. Future slot generation and queue admissions will reflect this change.`
      );
    } catch (err) {
      if (err instanceof ApiError) {
        setErrorMessage(err.message);
      } else if (err instanceof Error) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage('Failed to toggle session status.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleSaveSession = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingSession || !isStaffOrAdmin) return;

    setFormValidationError(null);

    // Client-side validations
    if (!formStartTime || !formEndTime) {
      setFormValidationError('Start time and end time are required.');
      return;
    }

    if (formStartTime >= formEndTime) {
      setFormValidationError(`Session start time (${formStartTime}) must be strictly before end time (${formEndTime}).`);
      return;
    }

    if (!formDuration || formDuration <= 0) {
      setFormValidationError('Slot duration must be a positive number of minutes.');
      return;
    }

    // Check window length
    const [startH, startM] = formStartTime.split(':').map(Number);
    const [endH, endM] = formEndTime.split(':').map(Number);
    const totalMinutes = (endH * 60 + endM) - (startH * 60 + startM);
    if (formDuration > totalMinutes) {
      setFormValidationError(`Slot duration (${formDuration}m) cannot exceed total session window (${totalMinutes}m).`);
      return;
    }

    if (!formQueueOpenTime || !formBookingStartTime) {
      setFormValidationError('Queue opening time and booking admission start time are required.');
      return;
    }

    if (formQueueOpenTime >= formBookingStartTime) {
      setFormValidationError(`Queue opening time (${formQueueOpenTime}) must be strictly before booking admission start time (${formBookingStartTime}).`);
      return;
    }

    if (formAdvanceDays < 0) {
      setFormValidationError('Queue advance days cannot be negative.');
      return;
    }

    // Format times into HH:mm:ss for backend LocalTime
    const padTime = (t: string) => (t.length === 5 ? `${t}:00` : t);

    const payload: UpdateSessionConfigRequestDto = {
      startTime: padTime(formStartTime),
      endTime: padTime(formEndTime),
      slotDurationMinutes: formDuration,
      queueOpenTime: padTime(formQueueOpenTime),
      bookingStartTime: padTime(formBookingStartTime),
      queueDaysInAdvance: formAdvanceDays,
      active: formActive,
    };

    setIsSubmitting(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const updated = await updateSessionConfig(editingSession.id, payload);
      setSessions((prev) =>
        prev.map((s) => (s.id === updated.id ? updated : s))
      );
      setSuccessMessage(
        `Successfully updated ${updated.sessionType} session rules! Existing bookings and historical slots remain preserved.`
      );
      closeEditModal();
    } catch (err) {
      if (err instanceof ApiError) {
        setFormValidationError(err.message);
      } else if (err instanceof Error) {
        setFormValidationError(err.message);
      } else {
        setFormValidationError('Failed to update session rules on server.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const calculateSlotsCount = (startStr: string, endStr: string, durationMinutes: number) => {
    try {
      const [sh, sm] = startStr.split(':').map(Number);
      const [eh, em] = endStr.split(':').map(Number);
      const totalMins = (eh * 60 + em) - (sh * 60 + sm);
      if (totalMins <= 0 || durationMinutes <= 0) return 0;
      return Math.floor(totalMins / durationMinutes);
    } catch {
      return 0;
    }
  };

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 sm:px-6">
      {/* Header Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-6 border-b border-slate-200">
        <div>
          <div className="flex items-center gap-2">
            <span className="rounded-md bg-emerald-100 px-2.5 py-0.5 text-xs font-bold text-emerald-800">
              Admin
            </span>
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
              Schedule & Timing
            </span>
          </div>
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-900">
            Session Rules Management
          </h1>
          <p className="mt-1 text-xs text-slate-500">
            Authoritative, database-backed operating sessions, slot intervals, and queue opening schedules.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={loadSessions}
            disabled={isLoading}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 shadow-2xs hover:bg-slate-50 hover:text-slate-900 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 disabled:opacity-50 cursor-pointer"
          >
            <svg
              className={`h-3.5 w-3.5 ${isLoading ? 'animate-spin' : ''}`}
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Refresh
          </button>
          <button
            type="button"
            onClick={onBackToDashboard}
            className="inline-flex items-center rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white shadow-2xs hover:bg-slate-800 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-900 cursor-pointer"
          >
            Back to Dashboard
          </button>
        </div>
      </div>

      {/* View-Only Banner for non-staff */}
      {!isStaffOrAdmin && (
        <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-3.5 text-xs text-amber-800 flex items-start gap-2.5 shadow-2xs">
          <svg className="h-4 w-4 shrink-0 text-amber-600 mt-0.5" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
          </svg>
          <div>
            <strong className="font-semibold block">View-Only Mode</strong>
            You are browsing session configurations as a student or guest. Administrative editing controls are restricted to staff and administrators.
          </div>
        </div>
      )}

      {/* Safety Guarantee Banner */}
      <div className="mt-4 rounded-xl border border-blue-200 bg-blue-50/80 p-3.5 text-xs text-blue-900 flex items-start gap-2.5 shadow-2xs">
        <svg className="h-4 w-4 shrink-0 text-blue-600 mt-0.5" viewBox="0 0 20 20" fill="currentColor">
          <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm.75-11.25a.75.75 0 00-1.5 0v4.5c0 .414.336.75.75.75h4.5a.75.75 0 000-1.5h-3.75V6.75z" clipRule="evenodd" />
        </svg>
        <div>
          <strong className="font-semibold">Schedule Integrity Guarantee:</strong> Session rule updates govern future slot generation cycles and queue openings. All existing reservations, player check-in passes, and historic records are strictly immutable and never silently overwritten.
        </div>
      </div>

      {/* Success Notification */}
      {successMessage && (
        <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-3.5 text-xs text-emerald-800 flex items-start justify-between gap-2 shadow-2xs">
          <div className="flex items-center gap-2">
            <svg className="h-4 w-4 shrink-0 text-emerald-600" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.857-9.809a.75.75 0 00-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 10-1.06 1.061l2.5 2.5a.75.75 0 001.137-.089l4-5.5z" clipRule="evenodd" />
            </svg>
            <span>{successMessage}</span>
          </div>
          <button
            type="button"
            onClick={() => setSuccessMessage(null)}
            className="text-emerald-700 hover:text-emerald-900 cursor-pointer text-sm font-bold"
          >
            ×
          </button>
        </div>
      )}

      {/* Error Notification */}
      {errorMessage && (
        <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-3.5 text-xs text-rose-800 flex items-start justify-between gap-2 shadow-2xs">
          <div className="flex items-center gap-2">
            <svg className="h-4 w-4 shrink-0 text-rose-600" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z" clipRule="evenodd" />
            </svg>
            <span>{errorMessage}</span>
          </div>
          <button
            type="button"
            onClick={() => setErrorMessage(null)}
            className="text-rose-700 hover:text-rose-900 cursor-pointer text-sm font-bold"
          >
            ×
          </button>
        </div>
      )}

      {/* Loading Skeleton */}
      {isLoading && sessions.length === 0 ? (
        <div className="mt-6 grid grid-cols-1 md:grid-cols-2 gap-6">
          {[1, 2].map((i) => (
            <div key={i} className="animate-pulse rounded-2xl border border-slate-200 bg-white p-6 shadow-sm space-y-4">
              <div className="h-6 w-36 rounded bg-slate-200" />
              <div className="h-4 w-48 rounded bg-slate-100" />
              <div className="h-24 rounded-lg bg-slate-100" />
              <div className="h-20 rounded-lg bg-slate-100" />
            </div>
          ))}
        </div>
      ) : (
        /* Sessions Grid */
        <div className="mt-6 grid grid-cols-1 md:grid-cols-2 gap-6">
          {sessions.map((session) => {
            const isMorning = session.sessionType === 'MORNING';
            const slotsPerCourt = calculateSlotsCount(
              session.startTime,
              session.endTime,
              session.slotDurationMinutes
            );
            const totalSlotsAcross4Courts = slotsPerCourt * 4;

            return (
              <div
                key={session.id}
                className={`rounded-2xl border bg-white shadow-xs transition hover:shadow-md flex flex-col justify-between overflow-hidden ${
                  session.active
                    ? isMorning
                      ? 'border-amber-200 ring-1 ring-amber-100'
                      : 'border-indigo-200 ring-1 ring-indigo-100'
                    : 'border-slate-300 opacity-75'
                }`}
              >
                {/* Top Card Banner */}
                <div
                  className={`px-5 py-4 flex items-center justify-between border-b ${
                    isMorning
                      ? 'bg-amber-50/60 border-amber-100'
                      : 'bg-indigo-50/60 border-indigo-100'
                  }`}
                >
                  <div className="flex items-center gap-2.5">
                    <div
                      className={`flex h-8 w-8 items-center justify-center rounded-lg text-sm font-black shadow-2xs ${
                        isMorning
                          ? 'bg-amber-500 text-white'
                          : 'bg-indigo-600 text-white'
                      }`}
                    >
                      {isMorning ? '☀️' : '🌙'}
                    </div>
                    <div>
                      <h2 className="text-sm font-bold text-slate-900 tracking-tight">
                        {session.sessionType} SESSION
                      </h2>
                      <span className="text-[10px] font-mono text-slate-500">
                        Config ID #{session.id}
                      </span>
                    </div>
                  </div>

                  {/* Status Badge */}
                  <span
                    className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                      session.active
                        ? 'bg-emerald-100 text-emerald-800'
                        : 'bg-slate-100 text-slate-600'
                    }`}
                  >
                    <span
                      className={`h-1.5 w-1.5 rounded-full ${
                        session.active ? 'bg-emerald-600' : 'bg-slate-400'
                      }`}
                    />
                    {session.active ? 'Active' : 'Disabled'}
                  </span>
                </div>

                {/* Card Body */}
                <div className="p-5 space-y-4 flex-1">
                  {/* Schedule Details */}
                  <div className="rounded-xl border border-slate-100 bg-slate-50/70 p-3.5 space-y-2.5">
                    <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                      Court Operating Window
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-xs text-slate-600 font-medium">Session Hours:</span>
                      <span className="text-xs font-bold text-slate-900 font-mono">
                        {formatTimeFromIso(session.startTime)} – {formatTimeFromIso(session.endTime)}
                        <span className="text-[10px] text-slate-400 ml-1 font-normal">
                          ({session.startTime.slice(0, 5)} - {session.endTime.slice(0, 5)})
                        </span>
                      </span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-xs text-slate-600 font-medium">Slot Duration:</span>
                      <span className="text-xs font-bold text-slate-900">
                        {session.slotDurationMinutes} minutes
                      </span>
                    </div>
                    <div className="flex items-center justify-between pt-1 border-t border-slate-200/60">
                      <span className="text-[11px] text-slate-500">Slot Capacity:</span>
                      <span className="text-[11px] font-semibold text-emerald-700">
                        {slotsPerCourt} slots / court • {totalSlotsAcross4Courts} total
                      </span>
                    </div>
                  </div>

                  {/* Queue & Admission Schedule */}
                  <div className="rounded-xl border border-slate-100 bg-slate-50/70 p-3.5 space-y-2.5">
                    <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                      Queue & Booking Rules
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-xs text-slate-600 font-medium">Queue Opens:</span>
                      <span className="text-xs font-bold text-slate-900 font-mono">
                        {formatTimeFromIso(session.queueOpenTime)}
                        <span className="text-[10px] text-slate-500 ml-1 font-normal">
                          ({session.queueDaysInAdvance === 1 ? 'Previous night' : 'Same day'})
                        </span>
                      </span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-xs text-slate-600 font-medium">Admissions Start:</span>
                      <span className="text-xs font-bold text-slate-900 font-mono">
                        {formatTimeFromIso(session.bookingStartTime)}
                      </span>
                    </div>
                    <div className="flex items-center justify-between pt-1 border-t border-slate-200/60">
                      <span className="text-[11px] text-slate-500">Queue Advance Period:</span>
                      <span className="text-[11px] font-semibold text-slate-700">
                        {session.queueDaysInAdvance} day{session.queueDaysInAdvance === 1 ? '' : 's'} prior
                      </span>
                    </div>
                  </div>
                </div>

                {/* Card Actions (Staff/Admin Only) */}
                {isStaffOrAdmin && (
                  <div className="px-5 py-3.5 bg-slate-50/80 border-t border-slate-100 flex items-center justify-between gap-2">
                    <button
                      type="button"
                      onClick={() => handleToggleActiveQuick(session)}
                      className={`text-xs font-medium px-2.5 py-1 rounded-lg transition cursor-pointer ${
                        session.active
                          ? 'text-slate-600 hover:text-rose-700 hover:bg-rose-50'
                          : 'text-emerald-700 hover:text-emerald-900 hover:bg-emerald-50'
                      }`}
                    >
                      {session.active ? 'Disable Session' : 'Activate Session'}
                    </button>
                    <button
                      type="button"
                      onClick={() => openEditModal(session)}
                      className="inline-flex items-center gap-1 rounded-lg bg-emerald-700 px-3 py-1.5 text-xs font-semibold text-white shadow-2xs hover:bg-emerald-800 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer"
                    >
                      <svg className="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
                        <path d="M5.433 13.917l1.262-3.155A4 4 0 017.58 9.42l6.92-6.918a2.121 2.121 0 013 3l-6.92 6.918c-.383.383-.84.685-1.343.882l-3.154 1.262a.5.5 0 01-.65-.65z" />
                        <path d="M3.5 5.75c0-.69.56-1.25 1.25-1.25H10A.75.75 0 0010 3H4.75A2.75 2.75 0 002 5.75v9.5A2.75 2.75 0 004.75 18h9.5A2.75 2.75 0 0017 15.25V10a.75.75 0 00-1.5 0v5.25c0 .69-.56 1.25-1.25 1.25h-9.5c-.69 0-1.25-.56-1.25-1.25v-9.5z" />
                      </svg>
                      Edit Rules
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Edit Session Modal */}
      {editingSession && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-2xs p-4 overflow-y-auto"
          role="dialog"
          aria-modal="true"
          aria-labelledby="modal-title"
        >
          <div className="w-full max-w-lg rounded-2xl bg-white shadow-2xl ring-1 ring-black/5 animate-in fade-in zoom-in-95 duration-150 overflow-hidden my-8">
            <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50/80 px-6 py-4">
              <div>
                <h3 id="modal-title" className="text-base font-bold text-slate-900">
                  Edit {editingSession.sessionType} Session Rules
                </h3>
                <p className="text-xs text-slate-500">
                  Update database configuration parameters for this recurring session.
                </p>
              </div>
              <button
                type="button"
                onClick={closeEditModal}
                className="rounded-lg p-1 text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition cursor-pointer"
                aria-label="Close dialog"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleSaveSession} className="p-6 space-y-4">
              {formValidationError && (
                <div className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800 flex items-center gap-2">
                  <svg className="h-4 w-4 shrink-0 text-rose-600" viewBox="0 0 20 20" fill="currentColor">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z" clipRule="evenodd" />
                  </svg>
                  <span>{formValidationError}</span>
                </div>
              )}

              {/* Operating Window */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label htmlFor={startTimeId} className="block text-xs font-semibold text-slate-700 mb-1">
                    Start Time (24h)
                  </label>
                  <input
                    id={startTimeId}
                    type="time"
                    value={formStartTime}
                    onChange={(e) => setFormStartTime(e.target.value)}
                    required
                    className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-mono text-slate-900 shadow-2xs focus:border-emerald-600 focus:outline-hidden focus:ring-1 focus:ring-emerald-600"
                  />
                </div>
                <div>
                  <label htmlFor={endTimeId} className="block text-xs font-semibold text-slate-700 mb-1">
                    End Time (24h)
                  </label>
                  <input
                    id={endTimeId}
                    type="time"
                    value={formEndTime}
                    onChange={(e) => setFormEndTime(e.target.value)}
                    required
                    className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-mono text-slate-900 shadow-2xs focus:border-emerald-600 focus:outline-hidden focus:ring-1 focus:ring-emerald-600"
                  />
                </div>
              </div>

              {/* Slot Duration */}
              <div>
                <label htmlFor={durationId} className="block text-xs font-semibold text-slate-700 mb-1">
                  Slot Duration (Minutes)
                </label>
                <input
                  id={durationId}
                  type="number"
                  min="5"
                  max="180"
                  step="5"
                  value={formDuration}
                  onChange={(e) => setFormDuration(parseInt(e.target.value, 10) || 0)}
                  required
                  className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-900 shadow-2xs focus:border-emerald-600 focus:outline-hidden focus:ring-1 focus:ring-emerald-600"
                />
                <span className="mt-1 block text-[10px] text-slate-500">
                  Standard university badminton slot duration is 30 minutes.
                </span>
              </div>

              {/* Queue Opening & Admissions */}
              <div className="grid grid-cols-2 gap-3 pt-2 border-t border-slate-100">
                <div>
                  <label htmlFor={queueOpenTimeId} className="block text-xs font-semibold text-slate-700 mb-1">
                    Queue Opens (24h)
                  </label>
                  <input
                    id={queueOpenTimeId}
                    type="time"
                    value={formQueueOpenTime}
                    onChange={(e) => setFormQueueOpenTime(e.target.value)}
                    required
                    className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-mono text-slate-900 shadow-2xs focus:border-emerald-600 focus:outline-hidden focus:ring-1 focus:ring-emerald-600"
                  />
                </div>
                <div>
                  <label htmlFor={bookingStartTimeId} className="block text-xs font-semibold text-slate-700 mb-1">
                    Admissions Start (24h)
                  </label>
                  <input
                    id={bookingStartTimeId}
                    type="time"
                    value={formBookingStartTime}
                    onChange={(e) => setFormBookingStartTime(e.target.value)}
                    required
                    className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-mono text-slate-900 shadow-2xs focus:border-emerald-600 focus:outline-hidden focus:ring-1 focus:ring-emerald-600"
                  />
                </div>
              </div>

              {/* Queue Advance Days */}
              <div>
                <label htmlFor={advanceDaysId} className="block text-xs font-semibold text-slate-700 mb-1">
                  Queue Opens Days in Advance
                </label>
                <select
                  id={advanceDaysId}
                  value={formAdvanceDays}
                  onChange={(e) => setFormAdvanceDays(parseInt(e.target.value, 10))}
                  className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-900 shadow-2xs focus:border-emerald-600 focus:outline-hidden focus:ring-1 focus:ring-emerald-600"
                >
                  <option value={1}>1 Day in advance (Previous night / evening)</option>
                  <option value={0}>0 Days in advance (Same day)</option>
                </select>
              </div>

              {/* Active Toggle */}
              <div className="flex items-center gap-2 pt-2 border-t border-slate-100">
                <input
                  type="checkbox"
                  id={activeToggleId}
                  checked={formActive}
                  onChange={(e) => setFormActive(e.target.checked)}
                  className="h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-600 cursor-pointer"
                />
                <label htmlFor={activeToggleId} className="text-xs font-semibold text-slate-800 cursor-pointer">
                  Session is Active (Available for slot generation and booking queues)
                </label>
              </div>

              {/* Action Buttons */}
              <div className="pt-4 flex items-center justify-end gap-2 border-t border-slate-100">
                <button
                  type="button"
                  onClick={closeEditModal}
                  disabled={isSubmitting}
                  className="rounded-lg border border-slate-300 bg-white px-3.5 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 transition cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-700 px-4 py-1.5 text-xs font-semibold text-white shadow-2xs hover:bg-emerald-800 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 disabled:opacity-50 cursor-pointer"
                >
                  {isSubmitting ? (
                    <>
                      <svg className="h-3.5 w-3.5 animate-spin" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                      </svg>
                      Saving...
                    </>
                  ) : (
                    'Save Changes'
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
