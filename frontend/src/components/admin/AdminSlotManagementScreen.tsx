import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  fetchSlots,
  generateSlots,
  getStoredAuthUser,
  type TimeSlotResponseDto,
  type LoginResponseDto,
  type SessionTypeDto,
  type SlotStatusDto,
  ApiError,
} from '../../api/client';
import { formatTimeFromIso } from '../../utils/slotUtils';

interface AdminSlotManagementScreenProps {
  onBackToDashboard: () => void;
  currentUser?: LoginResponseDto | null;
}

export const AdminSlotManagementScreen: React.FC<AdminSlotManagementScreenProps> = ({
  onBackToDashboard,
  currentUser: propUser,
}) => {
  const [currentUser] = useState<LoginResponseDto | null>(() => propUser ?? getStoredAuthUser());

  // Default to today in YYYY-MM-DD or 2026-10-15 fallback
  const getInitialDate = () => {
    try {
      return new Date().toISOString().slice(0, 10);
    } catch {
      return '2026-10-15';
    }
  };

  const [selectedDate, setSelectedDate] = useState<string>(getInitialDate);
  const [dateInput, setDateInput] = useState<string>(getInitialDate);
  const [slots, setSlots] = useState<TimeSlotResponseDto[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isGenerating, setIsGenerating] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Filters
  const [courtFilter, setCourtFilter] = useState<string>('all');
  const [sessionFilter, setSessionFilter] = useState<string>('all');
  const [statusFilter, setStatusFilter] = useState<string>('all');

  const isStaffOrAdmin =
    currentUser?.role === 'STAFF' || currentUser?.role === 'ADMIN';

  const loadSlots = useCallback(async (date: string) => {
    if (!date || !date.trim()) {
      setErrorMessage('Please select a valid date in YYYY-MM-DD format.');
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);

    try {
      const data = await fetchSlots(date.trim());
      setSlots(data);
    } catch (err) {
      if (err instanceof ApiError) {
        setErrorMessage(err.message);
      } else if (err instanceof Error) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage(`Failed to fetch time slots for ${date}.`);
      }
      setSlots([]);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    let isMounted = true;
    if (selectedDate) {
      fetchSlots(selectedDate)
        .then((data) => {
          if (isMounted) {
            setSlots(data);
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
              setErrorMessage(`Failed to fetch time slots for ${selectedDate}.`);
            }
            setSlots([]);
            setIsLoading(false);
          }
        });
    }

    return () => {
      isMounted = false;
    };
  }, [selectedDate]);

  const handleApplyDate = (newDate: string) => {
    const trimmed = newDate.trim();
    setDateInput(trimmed);
    setSelectedDate(trimmed);
  };

  const handleQuickDate = (offsetDays: number) => {
    const now = new Date();
    now.setDate(now.getDate() + offsetDays);
    const dateStr = now.toISOString().slice(0, 10);
    handleApplyDate(dateStr);
  };

  const handleGenerateSlots = async () => {
    if (!isStaffOrAdmin) {
      setErrorMessage('Access Denied: Only STAFF and ADMIN roles can generate time slots.');
      return;
    }

    if (!selectedDate || !selectedDate.trim()) {
      setErrorMessage('Please specify a valid calendar date before generating slots.');
      return;
    }

    setIsGenerating(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const result = await generateSlots(selectedDate.trim());
      setSuccessMessage(result.message);
      // Refresh slot list from server
      await loadSlots(selectedDate.trim());
    } catch (err) {
      if (err instanceof ApiError) {
        setErrorMessage(err.message);
      } else if (err instanceof Error) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage(`Failed to generate slots for ${selectedDate}.`);
      }
    } finally {
      setIsGenerating(false);
    }
  };

  // Filtered Slots
  const filteredSlots = useMemo(() => {
    return slots.filter((slot) => {
      if (courtFilter !== 'all' && String(slot.courtNumber) !== courtFilter) {
        return false;
      }
      if (sessionFilter !== 'all' && slot.sessionType !== (sessionFilter as SessionTypeDto)) {
        return false;
      }
      if (statusFilter !== 'all' && slot.status !== (statusFilter as SlotStatusDto)) {
        return false;
      }
      return true;
    });
  }, [slots, courtFilter, sessionFilter, statusFilter]);

  // Statistics
  const stats = useMemo(() => {
    const available = slots.filter((s) => s.status === 'AVAILABLE').length;
    const booked = slots.filter((s) => s.status === 'BOOKED').length;
    const blocked = slots.filter((s) => s.status === 'BLOCKED_TOURNAMENT').length;
    const maintenance = slots.filter((s) => s.status === 'MAINTENANCE').length;
    return {
      total: slots.length,
      available,
      booked,
      blocked,
      maintenance,
    };
  }, [slots]);

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 sm:px-6">
      {/* Header Bar */}
      <div className="mb-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-slate-200 pb-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold tracking-tight text-slate-900 sm:text-2xl">
              Time Slot Management
            </h1>
            <span
              className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                isStaffOrAdmin
                  ? 'bg-emerald-100 text-emerald-800 border border-emerald-300'
                  : 'bg-amber-100 text-amber-800 border border-amber-300'
              }`}
            >
              {currentUser?.role ?? 'VIEW-ONLY (GUEST)'}
            </span>
          </div>
          <p className="mt-1 text-xs text-slate-500">
            Inspect schedule slots across all 4 courts and generate missing sessions safely and idempotently.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onBackToDashboard}
            className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 hover:text-slate-900 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer"
          >
            ← Back to Dashboard
          </button>
          <button
            type="button"
            onClick={() => loadSlots(selectedDate)}
            disabled={isLoading || isGenerating}
            className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white shadow-2xs hover:bg-slate-800 transition disabled:opacity-50 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-700 cursor-pointer"
          >
            {isLoading ? 'Loading...' : '↻ Refresh'}
          </button>
        </div>
      </div>

      {/* Date Picker & Slot Generation Bar */}
      <div className="mb-6 rounded-2xl border border-slate-200 bg-white p-4 sm:p-5 shadow-2xs">
        <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
          <div className="flex-1 max-w-md">
            <label htmlFor="slot-date-input" className="block text-xs font-bold text-slate-700 mb-1.5">
              Select Schedule Date
            </label>
            <div className="flex items-center gap-2">
              <input
                id="slot-date-input"
                type="date"
                value={dateInput}
                onChange={(e) => handleApplyDate(e.target.value)}
                className="w-full rounded-xl border border-slate-300 px-3 py-2 text-xs font-medium text-slate-800 focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600 focus:outline-hidden"
              />
              <button
                type="button"
                onClick={() => handleQuickDate(0)}
                className="rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-100 transition whitespace-nowrap cursor-pointer"
              >
                Today
              </button>
              <button
                type="button"
                onClick={() => handleQuickDate(1)}
                className="rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-100 transition whitespace-nowrap cursor-pointer"
              >
                Tomorrow
              </button>
            </div>
          </div>

          {/* Action Button for Staff/Admin */}
          <div>
            {isStaffOrAdmin ? (
              <button
                type="button"
                onClick={handleGenerateSlots}
                disabled={isGenerating || isLoading}
                className="w-full sm:w-auto rounded-xl bg-emerald-700 px-4 py-2 text-xs font-bold text-white shadow-2xs hover:bg-emerald-800 transition disabled:opacity-50 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer flex items-center justify-center gap-1.5"
              >
                {isGenerating ? (
                  <>
                    <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                    <span>Generating Slots...</span>
                  </>
                ) : (
                  <>
                    <span>⚡ Generate Slots for Date</span>
                  </>
                )}
              </button>
            ) : (
              <div className="text-[11px] italic text-slate-400">
                Slot generation requires STAFF or ADMIN authorization.
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Preservation & Idempotency Notice */}
      <div className="mb-6 rounded-xl border border-blue-200 bg-blue-50/80 p-3.5 text-xs text-blue-900">
        <div className="flex items-center gap-1.5 font-semibold text-blue-950">
          <span>🛡</span>
          <span>Preservation & Idempotent Generation Policy</span>
        </div>
        <p className="mt-1 text-blue-800 leading-relaxed">
          Standard schedule covers 30-minute slots: morning <strong>6:00–8:30 AM</strong> (5 slots/court) and evening <strong>3:30–7:00 PM</strong> (7 slots/court) across 4 courts (48 slots total/day).
          Generating slots for an existing date is safe and idempotent — <strong>already booked spots, active reservations, and tournament blocks are never modified or deleted</strong>.
        </p>
      </div>

      {/* Alerts */}
      {successMessage && (
        <div className="mb-4 rounded-xl border border-emerald-200 bg-emerald-50 p-3.5 text-xs font-medium text-emerald-900 flex items-start justify-between gap-2">
          <div className="flex items-center gap-2">
            <span className="text-emerald-700 font-bold">✓</span>
            <span>{successMessage}</span>
          </div>
          <button
            type="button"
            onClick={() => setSuccessMessage(null)}
            className="text-emerald-700 hover:text-emerald-900 font-bold cursor-pointer"
          >
            ✕
          </button>
        </div>
      )}

      {errorMessage && (
        <div className="mb-4 rounded-xl border border-rose-200 bg-rose-50 p-3.5 text-xs font-medium text-rose-900 flex items-start justify-between gap-2">
          <div className="flex items-center gap-2">
            <span className="text-rose-700 font-bold">⚠</span>
            <span>{errorMessage}</span>
          </div>
          <button
            type="button"
            onClick={() => setErrorMessage(null)}
            className="text-rose-700 hover:text-rose-900 font-bold cursor-pointer"
          >
            ✕
          </button>
        </div>
      )}

      {/* Summary Statistics */}
      <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-5">
        <div className="rounded-xl border border-slate-200 bg-white p-3 text-center shadow-2xs">
          <span className="text-xs text-slate-500 font-medium">Total Slots</span>
          <p className="text-lg font-bold text-slate-900">{stats.total}</p>
        </div>
        <div className="rounded-xl border border-emerald-200 bg-emerald-50/50 p-3 text-center shadow-2xs">
          <span className="text-xs text-emerald-700 font-medium">Available</span>
          <p className="text-lg font-bold text-emerald-900">{stats.available}</p>
        </div>
        <div className="rounded-xl border border-blue-200 bg-blue-50/50 p-3 text-center shadow-2xs">
          <span className="text-xs text-blue-700 font-medium">Booked</span>
          <p className="text-lg font-bold text-blue-900">{stats.booked}</p>
        </div>
        <div className="rounded-xl border border-amber-200 bg-amber-50/50 p-3 text-center shadow-2xs">
          <span className="text-xs text-amber-700 font-medium">Tournament</span>
          <p className="text-lg font-bold text-amber-900">{stats.blocked}</p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-center shadow-2xs">
          <span className="text-xs text-slate-500 font-medium">Maintenance</span>
          <p className="text-lg font-bold text-slate-800">{stats.maintenance}</p>
        </div>
      </div>

      {/* Filter Toolbar */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 pb-3">
        <div className="flex flex-wrap items-center gap-2">
          {/* Court Filter */}
          <div className="flex items-center gap-1.5">
            <span className="text-xs font-semibold text-slate-700">Court:</span>
            <select
              value={courtFilter}
              onChange={(e) => setCourtFilter(e.target.value)}
              className="rounded-lg border border-slate-300 bg-white px-2.5 py-1 text-xs font-medium text-slate-800 focus:outline-hidden"
            >
              <option value="all">All Courts</option>
              <option value="1">Court 1</option>
              <option value="2">Court 2</option>
              <option value="3">Court 3</option>
              <option value="4">Court 4</option>
            </select>
          </div>

          {/* Session Filter */}
          <div className="flex items-center gap-1.5">
            <span className="text-xs font-semibold text-slate-700">Session:</span>
            <select
              value={sessionFilter}
              onChange={(e) => setSessionFilter(e.target.value)}
              className="rounded-lg border border-slate-300 bg-white px-2.5 py-1 text-xs font-medium text-slate-800 focus:outline-hidden"
            >
              <option value="all">All Sessions</option>
              <option value="MORNING">Morning (6:00–8:30 AM)</option>
              <option value="EVENING">Evening (3:30–7:00 PM)</option>
            </select>
          </div>

          {/* Status Filter */}
          <div className="flex items-center gap-1.5">
            <span className="text-xs font-semibold text-slate-700">Status:</span>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="rounded-lg border border-slate-300 bg-white px-2.5 py-1 text-xs font-medium text-slate-800 focus:outline-hidden"
            >
              <option value="all">All Statuses</option>
              <option value="AVAILABLE">Available</option>
              <option value="BOOKED">Booked</option>
              <option value="BLOCKED_TOURNAMENT">Blocked (Tournament)</option>
              <option value="MAINTENANCE">Maintenance</option>
            </select>
          </div>
        </div>

        <span className="text-xs text-slate-400">
          Showing {filteredSlots.length} of {slots.length} slots for {selectedDate}
        </span>
      </div>

      {/* Slots Table */}
      {isLoading ? (
        <div className="flex items-center justify-center py-16 text-slate-500 text-sm">
          <div className="h-5 w-5 animate-spin rounded-full border-2 border-emerald-600 border-t-transparent mr-2" />
          Loading time slots for {selectedDate}...
        </div>
      ) : slots.length === 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-12 text-center shadow-2xs">
          <p className="text-slate-600 font-medium mb-2">No time slots found for {selectedDate}.</p>
          <p className="text-xs text-slate-400 max-w-md mx-auto mb-4">
            Time slots have not been generated for this date yet. Authorized staff or administrators can generate the complete 4-court schedule using the button above.
          </p>
          {isStaffOrAdmin && (
            <button
              type="button"
              onClick={handleGenerateSlots}
              disabled={isGenerating}
              className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-700 px-4 py-2 text-xs font-bold text-white shadow-2xs hover:bg-emerald-800 transition disabled:opacity-50 cursor-pointer"
            >
              ⚡ Generate Slots for {selectedDate}
            </button>
          )}
        </div>
      ) : filteredSlots.length === 0 ? (
        <div className="rounded-xl border border-slate-200 bg-white p-8 text-center text-slate-500 text-xs">
          No slots match the selected filters.
        </div>
      ) : (
        <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xs">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-slate-200 bg-slate-50/80 text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                <tr>
                  <th scope="col" className="px-4 py-3">Slot ID</th>
                  <th scope="col" className="px-4 py-3">Court</th>
                  <th scope="col" className="px-4 py-3">Session</th>
                  <th scope="col" className="px-4 py-3">Time Window</th>
                  <th scope="col" className="px-4 py-3">Status</th>
                  <th scope="col" className="px-4 py-3 text-right">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredSlots.map((slot) => {
                  const startTimeFormatted = formatTimeFromIso(slot.startTime);
                  const endTimeFormatted = formatTimeFromIso(slot.endTime);

                  return (
                    <tr key={slot.id} className="hover:bg-slate-50/50 transition">
                      <td className="px-4 py-3 font-mono font-medium text-slate-500">
                        #{slot.id}
                      </td>
                      <td className="px-4 py-3 font-semibold text-slate-900">
                        Court {slot.courtNumber ?? slot.courtId ?? '-'}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${
                            slot.sessionType === 'MORNING'
                              ? 'bg-amber-100 text-amber-800'
                              : 'bg-indigo-100 text-indigo-800'
                          }`}
                        >
                          {slot.sessionType}
                        </span>
                      </td>
                      <td className="px-4 py-3 font-medium text-slate-800">
                        {startTimeFormatted} – {endTimeFormatted}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold ${
                            slot.status === 'AVAILABLE'
                              ? 'bg-emerald-100 text-emerald-800 border border-emerald-200'
                              : slot.status === 'BOOKED'
                              ? 'bg-blue-100 text-blue-800 border border-blue-200'
                              : slot.status === 'BLOCKED_TOURNAMENT'
                              ? 'bg-purple-100 text-purple-800 border border-purple-200'
                              : 'bg-slate-200 text-slate-800 border border-slate-300'
                          }`}
                        >
                          <span
                            className={`h-1.5 w-1.5 rounded-full ${
                              slot.status === 'AVAILABLE'
                                ? 'bg-emerald-600'
                                : slot.status === 'BOOKED'
                                ? 'bg-blue-600'
                                : slot.status === 'BLOCKED_TOURNAMENT'
                                ? 'bg-purple-600'
                                : 'bg-slate-600'
                            }`}
                          />
                          {slot.status === 'AVAILABLE'
                            ? 'Available'
                            : slot.status === 'BOOKED'
                            ? 'Booked'
                            : slot.status === 'BLOCKED_TOURNAMENT'
                            ? 'Tournament Block'
                            : 'Maintenance'}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right text-slate-400 text-[11px]">
                        {slot.status === 'BOOKED' ? (
                          <span className="text-blue-700 font-semibold">Active Booking</span>
                        ) : slot.status === 'AVAILABLE' ? (
                          <span>Ready for play</span>
                        ) : (
                          <span>Unavailable</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};
