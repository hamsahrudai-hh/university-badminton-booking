import React, { useState, useEffect, useCallback } from 'react';
import {
  fetchCourts,
  updateCourtStatus,
  getStoredAuthUser,
  type CourtResponseDto,
  type LoginResponseDto,
  ApiError,
} from '../../api/client';

interface AdminCourtManagementScreenProps {
  onBackToDashboard: () => void;
  currentUser?: LoginResponseDto | null;
}

export const AdminCourtManagementScreen: React.FC<AdminCourtManagementScreenProps> = ({
  onBackToDashboard,
  currentUser: propUser,
}) => {
  const [currentUser] = useState<LoginResponseDto | null>(() => propUser ?? getStoredAuthUser());
  const [courts, setCourts] = useState<CourtResponseDto[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isUpdatingCourtId, setIsUpdatingCourtId] = useState<number | null>(null);
  const [filterActiveOnly, setFilterActiveOnly] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const isStaffOrAdmin =
    currentUser?.role === 'STAFF' || currentUser?.role === 'ADMIN';

  const loadCourts = useCallback(async () => {
    setErrorMessage(null);
    try {
      const data = await fetchCourts(filterActiveOnly);
      setCourts(data);
    } catch (err) {
      if (err instanceof ApiError) {
        setErrorMessage(err.message);
      } else if (err instanceof Error) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage('Failed to load courts from server.');
      }
    } finally {
      setIsLoading(false);
    }
  }, [filterActiveOnly]);

  useEffect(() => {
    let isMounted = true;
    fetchCourts(filterActiveOnly)
      .then((data) => {
        if (isMounted) {
          setCourts(data);
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
            setErrorMessage('Failed to load courts from server.');
          }
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [filterActiveOnly]);

  const handleManualRefresh = () => {
    setIsLoading(true);
    loadCourts();
  };

  const handleToggleStatus = async (court: CourtResponseDto) => {
    if (!isStaffOrAdmin) {
      setErrorMessage('Access Denied: Only STAFF and ADMIN roles can modify court statuses.');
      return;
    }

    const newStatus = !court.active;
    const actionLabel = newStatus ? 'activate' : 'deactivate';

    setIsUpdatingCourtId(court.id);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const updated = await updateCourtStatus(court.id, newStatus);
      setCourts((prev) =>
        prev.map((c) => (c.id === updated.id ? updated : c))
      );
      setSuccessMessage(
        `Court ${updated.courtNumber} successfully ${updated.active ? 'activated' : 'deactivated'}. ${
          !updated.active
            ? 'New bookings on this court are now blocked. Existing reservations remain preserved.'
            : 'Players can now reserve slots on this court.'
        }`
      );
    } catch (err) {
      if (err instanceof ApiError) {
        setErrorMessage(
          err.status === 409
            ? `State conflict: Court ${court.courtNumber} is already in the requested state.`
            : err.message
        );
      } else if (err instanceof Error) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage(`Failed to ${actionLabel} Court ${court.courtNumber}.`);
      }
    } finally {
      setIsUpdatingCourtId(null);
    }
  };

  return (
    <div className="mx-auto max-w-4xl px-4 py-6 sm:px-6">
      {/* Header Bar */}
      <div className="mb-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-slate-200 pb-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold tracking-tight text-slate-900 sm:text-2xl">
              Court Management
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
            Configure court availability. Deactivating a court blocks new reservations while preserving existing confirmed bookings.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onBackToDashboard}
            className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 hover:text-slate-900 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600"
          >
            ← Back to Dashboard
          </button>
          <button
            type="button"
            onClick={handleManualRefresh}
            disabled={isLoading}
            className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white shadow-2xs hover:bg-slate-800 transition disabled:opacity-50 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-700"
          >
            {isLoading ? 'Refreshing...' : '↻ Refresh'}
          </button>
        </div>
      </div>

      {/* Access Notice for Non-Staff */}
      {!isStaffOrAdmin && (
        <div className="mb-6 rounded-xl border border-amber-200 bg-amber-50 p-3.5 text-xs text-amber-900">
          <div className="flex items-center gap-2 font-semibold">
            <span>ℹ</span>
            <span>Read-Only View</span>
          </div>
          <p className="mt-1 text-amber-800 leading-relaxed">
            You are currently browsing courts in view-only mode. Management controls (activating or deactivating courts) require a signed-in account with <strong>STAFF</strong> or <strong>ADMIN</strong> privileges.
          </p>
        </div>
      )}

      {/* Safety Policy Banner */}
      <div className="mb-6 rounded-xl border border-blue-200 bg-blue-50/80 p-3.5 text-xs text-blue-900">
        <div className="flex items-center gap-1.5 font-semibold text-blue-950">
          <span>🛡</span>
          <span>Data Preservation Policy</span>
        </div>
        <p className="mt-1 text-blue-800 leading-relaxed">
          Deactivating a court marks it inactive and immediately prevents students from securing new reservations on it. Existing bookings, assigned player spots, QR passes, and check-in records are never deleted or silently cancelled.
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
            className="text-emerald-700 hover:text-emerald-900 font-bold"
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
            className="text-rose-700 hover:text-rose-900 font-bold"
          >
            ✕
          </button>
        </div>
      )}

      {/* Filter Control */}
      <div className="mb-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold text-slate-700">Filter:</span>
          <button
            type="button"
            onClick={() => setFilterActiveOnly(false)}
            className={`rounded-md px-2.5 py-1 text-xs font-medium transition ${
              !filterActiveOnly
                ? 'bg-slate-900 text-white font-semibold'
                : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
            }`}
          >
            All Courts
          </button>
          <button
            type="button"
            onClick={() => setFilterActiveOnly(true)}
            className={`rounded-md px-2.5 py-1 text-xs font-medium transition ${
              filterActiveOnly
                ? 'bg-slate-900 text-white font-semibold'
                : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
            }`}
          >
            Active Only
          </button>
        </div>
        <span className="text-xs text-slate-400">
          Showing {courts.length} court{courts.length === 1 ? '' : 's'}
        </span>
      </div>

      {/* Courts Grid */}
      {isLoading ? (
        <div className="flex items-center justify-center py-16 text-slate-500 text-sm">
          <div className="h-5 w-5 animate-spin rounded-full border-2 border-emerald-600 border-t-transparent mr-2" />
          Loading court statuses...
        </div>
      ) : courts.length === 0 ? (
        <div className="rounded-xl border border-slate-200 bg-white p-8 text-center text-slate-500 text-sm">
          No courts match the current filter.
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {courts.map((court) => {
            const isUpdating = isUpdatingCourtId === court.id;
            return (
              <div
                key={court.id}
                className={`rounded-2xl border bg-white p-5 shadow-2xs transition-all ${
                  court.active
                    ? 'border-slate-200 hover:border-emerald-300'
                    : 'border-rose-200 bg-rose-50/20'
                }`}
              >
                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-3">
                    <div
                      className={`flex h-11 w-11 items-center justify-center rounded-xl text-base font-bold shadow-2xs ${
                        court.active
                          ? 'bg-emerald-700 text-white'
                          : 'bg-slate-300 text-slate-600'
                      }`}
                    >
                      C{court.courtNumber}
                    </div>
                    <div>
                      <h2 className="text-base font-bold text-slate-900">
                        Court {court.courtNumber}
                      </h2>
                      <span className="text-[11px] font-mono text-slate-400">
                        ID: #{court.id}
                      </span>
                    </div>
                  </div>

                  <span
                    className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                      court.active
                        ? 'bg-emerald-100 text-emerald-800 border border-emerald-200'
                        : 'bg-rose-100 text-rose-800 border border-rose-200'
                    }`}
                  >
                    <span
                      className={`h-1.5 w-1.5 rounded-full ${
                        court.active ? 'bg-emerald-600' : 'bg-rose-600'
                      }`}
                    />
                    {court.active ? 'Active' : 'Inactive'}
                  </span>
                </div>

                <div className="mt-4 border-t border-slate-100 pt-3 text-xs text-slate-600">
                  {court.active ? (
                    <p className="text-slate-600">
                      Court is active and accepting new player reservations for standard 30-minute slots.
                    </p>
                  ) : (
                    <p className="text-rose-700 font-medium">
                      Court is inactive. New bookings are rejected. Existing bookings remain preserved.
                    </p>
                  )}
                </div>

                {/* Management Action for Staff / Admin */}
                {isStaffOrAdmin ? (
                  <div className="mt-4 pt-2">
                    {court.active ? (
                      <button
                        type="button"
                        onClick={() => handleToggleStatus(court)}
                        disabled={isUpdating}
                        className="w-full rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs font-semibold text-rose-700 hover:bg-rose-100 hover:text-rose-900 transition disabled:opacity-50 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-rose-500 cursor-pointer"
                      >
                        {isUpdating ? 'Updating Status...' : 'Deactivate Court (Maintenance)'}
                      </button>
                    ) : (
                      <button
                        type="button"
                        onClick={() => handleToggleStatus(court)}
                        disabled={isUpdating}
                        className="w-full rounded-xl border border-emerald-300 bg-emerald-600 px-3 py-2 text-xs font-semibold text-white shadow-2xs hover:bg-emerald-700 transition disabled:opacity-50 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer"
                      >
                        {isUpdating ? 'Updating Status...' : 'Activate Court (Ready)'}
                      </button>
                    )}
                  </div>
                ) : (
                  <div className="mt-4 pt-2 text-[11px] italic text-slate-400 text-center border-t border-slate-100">
                    Controls restricted to authorized staff
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
