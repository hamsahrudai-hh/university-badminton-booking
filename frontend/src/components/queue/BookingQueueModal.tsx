import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  fetchQueueStatus,
  fetchStudentQueuePosition,
  joinQueue,
  type QueueStatusResponseDto,
  type QueueEntryResponseDto,
  type SessionTypeDto,
  ApiError,
} from '../../api/client';

interface BookingQueueModalProps {
  isOpen: boolean;
  onClose: () => void;
  isAuthenticated: boolean;
  currentStudentId?: number;
  currentStudentName?: string;
  weeklyCredits: number;
  activeQueueEntry: QueueEntryResponseDto | null;
  setActiveQueueEntry: (entry: QueueEntryResponseDto | null) => void;
  onProceedToBooking: () => void;
  onNavigateLogin?: () => void;
}

export const BookingQueueModal: React.FC<BookingQueueModalProps> = ({
  isOpen,
  onClose,
  isAuthenticated,
  currentStudentId,
  currentStudentName,
  weeklyCredits,
  activeQueueEntry,
  setActiveQueueEntry,
  onProceedToBooking,
  onNavigateLogin,
}) => {
  const getTodayIsoDate = () => {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };

  const [selectedDate, setSelectedDate] = useState<string>(getTodayIsoDate);
  const [selectedSessionType, setSelectedSessionType] = useState<SessionTypeDto>('MORNING');

  const [queueStatus, setQueueStatus] = useState<QueueStatusResponseDto | null>(null);
  const [isLoadingStatus, setIsLoadingStatus] = useState<boolean>(false);
  const [statusError, setStatusError] = useState<string | null>(null);

  const [isJoining, setIsJoining] = useState<boolean>(false);
  const [joinError, setJoinError] = useState<string | null>(null);

  // Remaining seconds calculated live
  const [remainingAdmittedSeconds, setRemainingAdmittedSeconds] = useState<number>(0);

  // Poll timer reference
  const pollTimerRef = useRef<number | null>(null);

  // 1. Fetch public queue status
  const loadQueueStatus = useCallback(async () => {
    setIsLoadingStatus(true);
    setStatusError(null);
    try {
      const status = await fetchQueueStatus(selectedDate, selectedSessionType);
      setQueueStatus(status);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch queue status';
      setStatusError(msg);
    } finally {
      setIsLoadingStatus(false);
    }
  }, [selectedDate, selectedSessionType]);

  // 2. Fetch student's individual position if signed in
  const loadStudentPosition = useCallback(async () => {
    if (!isAuthenticated || !currentStudentId) return;
    try {
      const entry = await fetchStudentQueuePosition(selectedDate, selectedSessionType, currentStudentId);
      setActiveQueueEntry(entry);
      if (entry.status === 'ADMITTED' && entry.remainingSeconds != null) {
        setRemainingAdmittedSeconds(Math.max(0, entry.remainingSeconds));
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        // Not in queue for this session
        if (activeQueueEntry?.status !== 'COMPLETED') {
          setActiveQueueEntry(null);
        }
      } else {
        console.warn('Could not inspect student queue position:', err);
      }
    }
  }, [isAuthenticated, currentStudentId, selectedDate, selectedSessionType, activeQueueEntry, setActiveQueueEntry]);

  // Reload when modal opens or session selection changes
  useEffect(() => {
    if (!isOpen) return;
    const timer = setTimeout(() => {
      void loadQueueStatus();
      void loadStudentPosition();
    }, 0);
    return () => clearTimeout(timer);
  }, [isOpen, selectedDate, selectedSessionType, loadQueueStatus, loadStudentPosition]);

  // Polling for live queue advancement when student is WAITING
  useEffect(() => {
    if (!isOpen || !isAuthenticated || !currentStudentId) return;

    if (activeQueueEntry?.status === 'WAITING') {
      const interval = window.setInterval(async () => {
        await loadStudentPosition();
        await loadQueueStatus();
      }, 3000);
      pollTimerRef.current = interval;
      return () => {
        window.clearInterval(interval);
      };
    }
  }, [isOpen, isAuthenticated, currentStudentId, activeQueueEntry?.status, loadStudentPosition, loadQueueStatus]);

  // Countdown timer when student is ADMITTED
  useEffect(() => {
    if (activeQueueEntry?.status !== 'ADMITTED') return;

    if (activeQueueEntry.expiresAt) {
      const targetTime = new Date(activeQueueEntry.expiresAt).getTime();
      const interval = window.setInterval(() => {
        const diff = Math.max(0, Math.round((targetTime - Date.now()) / 1000));
        setRemainingAdmittedSeconds(diff);
        if (diff <= 0) {
          window.clearInterval(interval);
          loadStudentPosition();
        }
      }, 1000);
      return () => window.clearInterval(interval);
    } else {
      const interval = window.setInterval(() => {
        setRemainingAdmittedSeconds((prev) => {
          if (prev <= 1) {
            window.clearInterval(interval);
            loadStudentPosition();
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
      return () => window.clearInterval(interval);
    }
  }, [activeQueueEntry?.status, activeQueueEntry?.expiresAt, loadStudentPosition]);

  // Handle joining the queue
  const handleJoinQueue = async () => {
    if (!isAuthenticated || !currentStudentId) {
      if (onNavigateLogin) {
        onClose();
        onNavigateLogin();
      }
      return;
    }

    setIsJoining(true);
    setJoinError(null);

    try {
      const entry = await joinQueue({
        studentId: currentStudentId,
        date: selectedDate,
        sessionType: selectedSessionType,
      });
      setActiveQueueEntry(entry);
      await loadQueueStatus();
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to join booking queue';
      setJoinError(msg);
    } finally {
      setIsJoining(false);
    }
  };

  if (!isOpen) return null;

  const priorityTierText = weeklyCredits > 0 ? 'Standard Priority (Tier 0)' : 'Reduced Priority (Tier 1)';
  const priorityBadgeColor =
    weeklyCredits > 0 ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-xs">
      <div className="relative w-full max-w-lg rounded-2xl border border-slate-200 bg-white shadow-2xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        {/* Modal Header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4 bg-slate-50/70">
          <div className="flex items-center gap-2">
            <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-emerald-700 text-white font-bold text-xs">
              ⚡
            </span>
            <div>
              <h2 className="text-sm font-bold text-slate-900">Queue Admission & Fairness</h2>
              <p className="text-[11px] text-slate-500">60-second atomic reservation window</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1 text-slate-400 hover:bg-slate-200 hover:text-slate-700 transition"
            aria-label="Close modal"
          >
            ✕
          </button>
        </div>

        <div className="p-6 space-y-4 max-h-[80vh] overflow-y-auto">
          {/* Session & Date Controls */}
          <div className="grid grid-cols-2 gap-3 bg-slate-50 p-3 rounded-xl border border-slate-100 text-xs">
            <div>
              <label htmlFor="queue-date-picker" className="block text-[11px] font-semibold text-slate-600 mb-1">
                Session Date
              </label>
              <input
                id="queue-date-picker"
                type="date"
                value={selectedDate}
                onChange={(e) => setSelectedDate(e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs text-slate-800 font-medium focus:border-emerald-600 focus:outline-hidden"
              />
            </div>
            <div>
              <label htmlFor="queue-session-picker" className="block text-[11px] font-semibold text-slate-600 mb-1">
                Session Type
              </label>
              <select
                id="queue-session-picker"
                value={selectedSessionType}
                onChange={(e) => setSelectedSessionType(e.target.value as SessionTypeDto)}
                className="w-full rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs text-slate-800 font-medium focus:border-emerald-600 focus:outline-hidden"
              >
                <option value="MORNING">Morning (06:00 – 08:30)</option>
                <option value="EVENING">Evening (17:00 – 21:30)</option>
              </select>
            </div>
          </div>

          {/* Authoritative Schedule Display */}
          <div className="rounded-xl border border-slate-200 bg-white p-3.5 space-y-2 text-xs">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-slate-700">Authoritative Schedule</span>
              {queueStatus && (
                <span
                  className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
                    queueStatus.isQueueOpen ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-600'
                  }`}
                >
                  {queueStatus.isQueueOpen ? 'Queue Open' : 'Queue Closed'}
                </span>
              )}
            </div>

            <div className="text-[11px] text-slate-600 space-y-1">
              <p>
                <strong>Morning:</strong> Queue opens at <strong>9:00 PM</strong> (night prior), admissions begin at{' '}
                <strong>9:05 PM</strong>.
              </p>
              <p>
                <strong>Evening:</strong> Queue opens at <strong>12:00 PM</strong> (noon), admissions begin at{' '}
                <strong>12:05 PM</strong>.
              </p>
              {queueStatus?.serverTime && (
                <p className="text-[10px] text-slate-400">
                  Server Time: {new Date(queueStatus.serverTime).toLocaleTimeString()} ({selectedDate})
                </p>
              )}
            </div>

            {queueStatus && (
              <div className="mt-2 pt-2 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-500">
                <span>Waiting in line: <strong>{queueStatus.waitingCount}</strong></span>
                {queueStatus.hasActiveOpportunity && (
                  <span className="text-amber-700 font-medium">
                    Opportunity active ({queueStatus.remainingSeconds}s remaining)
                  </span>
                )}
              </div>
            )}
          </div>

          {/* Status Error if any */}
          {statusError && (
            <div className="rounded-lg bg-red-50 p-3 text-xs text-red-700 border border-red-200">
              {statusError}
            </div>
          )}

          {/* Student Status & Priority Section */}
          <div className="rounded-xl border border-slate-200 bg-white p-4 space-y-3">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-xs font-bold text-slate-800">Your Queue Status</span>
                {currentStudentName && (
                  <p className="text-[10px] text-slate-500">{currentStudentName}</p>
                )}
              </div>
              <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${priorityBadgeColor}`}>
                {priorityTierText}
              </span>
            </div>

            {!isAuthenticated ? (
              <div className="text-center py-4 space-y-2">
                <p className="text-xs text-slate-600">
                  Sign in with your university account to join the queue and claim booking admission.
                </p>
                {onNavigateLogin && (
                  <button
                    type="button"
                    onClick={() => {
                      onClose();
                      onNavigateLogin();
                    }}
                    className="inline-flex h-8 items-center justify-center rounded-lg bg-emerald-700 px-4 text-xs font-semibold text-white shadow-2xs hover:bg-emerald-800 transition"
                  >
                    Sign In
                  </button>
                )}
              </div>
            ) : activeQueueEntry?.status === 'ADMITTED' ? (
              <div className="rounded-xl bg-emerald-50 border border-emerald-300 p-4 text-center space-y-3 animate-pulse">
                <div className="inline-flex h-10 w-10 items-center justify-center rounded-full bg-emerald-600 text-white text-lg font-bold">
                  ✓
                </div>
                <div>
                  <h3 className="text-sm font-extrabold text-emerald-950">You Are Admitted!</h3>
                  <p className="text-xs text-emerald-800 mt-0.5">
                    Your 60-second court reservation window is active.
                  </p>
                </div>

                <div className="text-2xl font-black text-emerald-900 tracking-tight">
                  ⏱️ {remainingAdmittedSeconds}s remaining
                </div>

                <button
                  type="button"
                  onClick={() => {
                    onClose();
                    onProceedToBooking();
                  }}
                  className="w-full inline-flex h-9.5 items-center justify-center rounded-lg bg-emerald-700 px-4 text-xs font-bold text-white shadow-md hover:bg-emerald-800 transition"
                >
                  Proceed to Book Court Now →
                </button>
              </div>
            ) : activeQueueEntry?.status === 'WAITING' ? (
              <div className="rounded-xl bg-sky-50 border border-sky-200 p-4 text-center space-y-2">
                <div className="text-2xl font-extrabold text-sky-900">
                  #{activeQueueEntry.positionInLine} in Line
                </div>
                <p className="text-xs text-sky-800 font-medium">
                  Waiting for your turn. Each admitted student receives 60 seconds.
                </p>
                <div className="flex items-center justify-center gap-1.5 text-[11px] text-sky-600">
                  <span className="inline-block h-2 w-2 rounded-full bg-sky-500 animate-ping" />
                  <span>Auto-checking position...</span>
                </div>
                {activeQueueEntry.priorityTier === 'REDUCED' && (
                  <p className="text-[10px] text-amber-700 mt-1">
                    Note: Reduced Priority (0 weekly credits). Students with positive credits are admitted ahead in FIFO order.
                  </p>
                )}
              </div>
            ) : activeQueueEntry?.status === 'EXPIRED' ? (
              <div className="rounded-xl bg-amber-50 border border-amber-200 p-3.5 space-y-2 text-center">
                <p className="text-xs font-bold text-amber-900">Booking Opportunity Expired</p>
                <p className="text-[11px] text-amber-800">
                  Your 60-second booking window expired before confirmation. You can rejoin the queue anytime.
                </p>
                <button
                  type="button"
                  onClick={handleJoinQueue}
                  disabled={isJoining}
                  className="inline-flex h-8 items-center justify-center rounded-lg bg-amber-700 px-4 text-xs font-semibold text-white hover:bg-amber-800 transition"
                >
                  {isJoining ? 'Joining...' : 'Rejoin Queue'}
                </button>
              </div>
            ) : activeQueueEntry?.status === 'COMPLETED' ? (
              <div className="rounded-xl bg-slate-50 border border-slate-200 p-3.5 space-y-1 text-center">
                <p className="text-xs font-bold text-slate-800">Booking Completed</p>
                <p className="text-[11px] text-slate-600">
                  You have successfully confirmed a reservation for this session.
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                <p className="text-xs text-slate-600">
                  Join the queue to receive an exclusive 60-second booking window when admissions open.
                </p>
                {joinError && (
                  <div className="rounded-lg bg-red-50 p-2.5 text-[11px] text-red-700 border border-red-200">
                    {joinError}
                  </div>
                )}
                <button
                  type="button"
                  onClick={handleJoinQueue}
                  disabled={isJoining || isLoadingStatus}
                  className="w-full inline-flex h-9 items-center justify-center rounded-lg bg-emerald-700 px-4 text-xs font-semibold text-white shadow-2xs hover:bg-emerald-800 transition disabled:bg-slate-200 disabled:text-slate-400 disabled:cursor-not-allowed"
                >
                  {isJoining ? 'Joining Queue...' : 'Join Queue Now'}
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Modal Footer */}
        <div className="flex items-center justify-end border-t border-slate-100 px-6 py-3 bg-slate-50">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-slate-300 bg-white px-4 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 transition"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
