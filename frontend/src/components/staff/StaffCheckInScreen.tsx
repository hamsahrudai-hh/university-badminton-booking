import React, { useState } from 'react';
import {
  checkInPlayer,
  processNoShows,
  login,
  getStoredAuthUser,
  clearStoredAuth,
  type CheckInResponseDto,
  type NoShowProcessingResponseDto,
  type LoginResponseDto,
  ApiError,
} from '../../api/client';

interface StaffCheckInScreenProps {
  onBackToDashboard: () => void;
  suggestedToken?: string;
}

interface ScanHistoryEntry {
  id: string;
  timestamp: string;
  studentName: string;
  admissionNumber: string;
  courtNumber: number;
  spotNumber: number;
  slotStartTime: string;
  alreadyCheckedIn: boolean;
  checkedInBy: string;
}

export const StaffCheckInScreen: React.FC<StaffCheckInScreenProps> = ({
  onBackToDashboard,
  suggestedToken,
}) => {
  // Authentication State
  const [authUser, setAuthUser] = useState<LoginResponseDto | null>(() => getStoredAuthUser());
  const [loginIdentifier, setLoginIdentifier] = useState<string>('');
  const [loginPassword, setLoginPassword] = useState<string>('');
  const [isLoggingIn, setIsLoggingIn] = useState<boolean>(false);
  const [loginError, setLoginError] = useState<string | null>(null);

  // Scanner Input state
  const [qrTokenInput, setQrTokenInput] = useState<string>(suggestedToken || '');
  const [slotFilterInput, setSlotFilterInput] = useState<string>('');

  // Execution states
  const [isCheckingIn, setIsCheckingIn] = useState<boolean>(false);
  const [checkInResult, setCheckInResult] = useState<CheckInResponseDto | null>(null);
  const [checkInError, setCheckInError] = useState<string | null>(null);

  const [isProcessingNoShows, setIsProcessingNoShows] = useState<boolean>(false);
  const [noShowResult, setNoShowResult] = useState<NoShowProcessingResponseDto | null>(null);
  const [noShowError, setNoShowError] = useState<string | null>(null);

  // Scan History
  const [history, setHistory] = useState<ScanHistoryEntry[]>([]);

  const isStaffOrAdmin = authUser && (authUser.role === 'STAFF' || authUser.role === 'ADMIN');

  const handleStaffLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    const identifier = loginIdentifier.trim();
    if (!identifier || !loginPassword) {
      setLoginError('Please enter your university email / admission number and password.');
      return;
    }

    setIsLoggingIn(true);
    setLoginError(null);

    try {
      const response = await login({
        usernameOrEmail: identifier,
        password: loginPassword,
      });

      if (response.role !== 'STAFF' && response.role !== 'ADMIN') {
        clearStoredAuth();
        setAuthUser(null);
        setLoginError(
          `Access Denied: Account '${response.fullName}' has the ${response.role} role. Only STAFF or ADMIN roles are authorized to perform court check-ins and no-show processing.`
        );
        return;
      }

      setAuthUser(response);
      setLoginPassword('');
      setLoginError(null);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setLoginError(err.message);
      } else if (err instanceof Error) {
        setLoginError(err.message);
      } else {
        setLoginError('Authentication failed. Please check your credentials.');
      }
    } finally {
      setIsLoggingIn(false);
    }
  };

  const handleStaffLogout = () => {
    clearStoredAuth();
    setAuthUser(null);
    setCheckInResult(null);
    setCheckInError(null);
    setNoShowResult(null);
    setNoShowError(null);
  };

  const handleCheckInSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const token = qrTokenInput.trim();
    if (!token) {
      setCheckInError('Please enter or scan a player QR token.');
      return;
    }

    setIsCheckingIn(true);
    setCheckInError(null);
    setCheckInResult(null);

    try {
      const response = await checkInPlayer({
        qrToken: token,
        staffIdentifier: authUser ? `${authUser.fullName} (${authUser.admissionNumber})` : 'FACILITY_STAFF',
      });

      setCheckInResult(response);
      setQrTokenInput('');

      // Add to session scan history
      const entry: ScanHistoryEntry = {
        id: `${response.bookingPlayerId}-${Date.now()}`,
        timestamp: new Date().toLocaleTimeString(),
        studentName: response.studentName,
        admissionNumber: response.admissionNumber,
        courtNumber: response.courtNumber,
        spotNumber: response.spotNumber,
        slotStartTime: response.slotStartTime,
        alreadyCheckedIn: response.alreadyCheckedIn,
        checkedInBy: response.checkedInBy,
      };
      setHistory((prev) => [entry, ...prev.slice(0, 19)]);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          clearStoredAuth();
          setAuthUser(null);
          setCheckInError('Your session has expired. Please log in again to continue.');
        } else if (err.status === 403) {
          setCheckInError('Access Denied: Insufficient staff permissions (403 Forbidden).');
        } else {
          setCheckInError(err.message);
        }
      } else if (err instanceof Error) {
        setCheckInError(err.message);
      } else {
        setCheckInError('An unexpected error occurred while verifying the pass.');
      }
    } finally {
      setIsCheckingIn(false);
    }
  };

  const handleProcessNoShows = async () => {
    setIsProcessingNoShows(true);
    setNoShowError(null);
    setNoShowResult(null);

    const slotId = slotFilterInput.trim() ? parseInt(slotFilterInput.trim(), 10) : undefined;

    try {
      const response = await processNoShows(slotId ? { timeSlotId: slotId } : undefined);
      setNoShowResult(response);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          clearStoredAuth();
          setAuthUser(null);
          setNoShowError('Your session has expired. Please log in again.');
        } else if (err.status === 403) {
          setNoShowError('Access Denied: Insufficient staff permissions (403 Forbidden).');
        } else {
          setNoShowError(err.message);
        }
      } else if (err instanceof Error) {
        setNoShowError(err.message);
      } else {
        setNoShowError('Failed to execute no-show processing.');
      }
    } finally {
      setIsProcessingNoShows(false);
    }
  };

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 sm:px-6 space-y-6">
      {/* Top Header & Breadcrumb */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-slate-200 pb-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="rounded bg-slate-900 px-2 py-0.5 text-[10px] font-bold text-white tracking-wide uppercase">
              Facility Staff Portal
            </span>
            <span className="text-xs text-slate-500 font-medium">Court Marshall Operations</span>
          </div>
          <h1 className="text-xl sm:text-2xl font-bold text-slate-900 mt-1">
            Player QR Check-in & Attendance
          </h1>
        </div>

        <button
          type="button"
          onClick={onBackToDashboard}
          className="inline-flex items-center gap-1.5 self-start sm:self-auto rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 transition cursor-pointer"
        >
          ← Back to Dashboard
        </button>
      </div>

      {/* Staff Authentication Gate */}
      {!isStaffOrAdmin ? (
        <div className="mx-auto max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-md space-y-5">
          <div className="text-center space-y-1.5">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-slate-900 text-white text-lg font-bold">
              🔒
            </div>
            <h2 className="text-lg font-bold text-slate-900">Staff Authentication Required</h2>
            <p className="text-xs text-slate-500 leading-relaxed">
              Court check-in and penalty enforcement are restricted. Please sign in with an authorized
              university account with <strong>STAFF</strong> or <strong>ADMIN</strong> role.
            </p>
          </div>

          {authUser && authUser.role === 'STUDENT' && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 p-3.5 text-xs text-amber-900 space-y-2">
              <div className="flex items-center gap-2 font-bold text-amber-950">
                <span>⚠️</span>
                <span>Active session has STUDENT role</span>
              </div>
              <p className="text-[11px] text-amber-900 leading-normal">
                Signed in as <strong>{authUser.fullName}</strong> ({authUser.admissionNumber}). Students cannot
                execute staff check-ins or no-show penalties.
              </p>
              <button
                type="button"
                onClick={handleStaffLogout}
                className="rounded-md border border-amber-300 bg-white px-2.5 py-1 text-[11px] font-semibold text-amber-900 hover:bg-amber-100/60 cursor-pointer"
              >
                Sign out of student account
              </button>
            </div>
          )}

          <form onSubmit={handleStaffLogin} className="space-y-4">
            <div>
              <label htmlFor="staffEmailInput" className="block text-xs font-semibold text-slate-700 mb-1">
                University Email or Admission Number
              </label>
              <input
                id="staffEmailInput"
                type="text"
                value={loginIdentifier}
                onChange={(e) => setLoginIdentifier(e.target.value)}
                placeholder="e.g. staff.marshal@vitapstudent.ac.in"
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-900 placeholder:text-slate-400 focus:border-slate-900 focus:ring-1 focus:ring-slate-900 outline-hidden"
                autoFocus
              />
            </div>

            <div>
              <label htmlFor="staffPasswordInput" className="block text-xs font-semibold text-slate-700 mb-1">
                Password
              </label>
              <input
                id="staffPasswordInput"
                type="password"
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-900 placeholder:text-slate-400 focus:border-slate-900 focus:ring-1 focus:ring-slate-900 outline-hidden"
              />
            </div>

            {loginError && (
              <div className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs text-rose-900">
                <p className="font-semibold text-rose-950">Login Failed</p>
                <p className="text-[11px] mt-0.5">{loginError}</p>
              </div>
            )}

            <button
              type="submit"
              disabled={isLoggingIn || !loginIdentifier.trim() || !loginPassword}
              className="w-full inline-flex items-center justify-center rounded-lg bg-slate-900 py-2.5 text-xs font-bold text-white shadow-sm hover:bg-slate-800 transition disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
            >
              {isLoggingIn ? 'Authenticating...' : 'Sign In as Staff'}
            </button>
          </form>

          {/* Dev Help Notice */}
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-[11px] text-slate-500 space-y-1">
            <strong className="text-slate-700 block">Development Quick-Start:</strong>
            <p>
              To grant STAFF privileges to an existing verified account in your local database:
            </p>
            <code className="block rounded bg-slate-200/80 px-2 py-1 font-mono text-[10px] text-slate-800 break-all">
              UPDATE students SET role = 'STAFF' WHERE university_email = 'your.email@vitapstudent.ac.in';
            </code>
          </div>
        </div>
      ) : (
        <>
          {/* Authenticated Staff Identity Banner */}
          <div className="rounded-xl border border-emerald-200 bg-emerald-50/60 p-4 shadow-2xs flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <span className="flex h-9 w-9 items-center justify-center rounded-full bg-emerald-600 text-white font-bold text-sm">
                ✓
              </span>
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-bold text-xs text-slate-900">{authUser.fullName}</span>
                  <span className="rounded bg-emerald-700 px-1.5 py-0.5 text-[9px] font-bold text-white uppercase tracking-wider">
                    {authUser.role}
                  </span>
                  <span className="font-mono text-[11px] text-slate-600">
                    ({authUser.admissionNumber})
                  </span>
                </div>
                <p className="text-[11px] text-emerald-900">
                  Authenticated session active · Checked-in records will be authoritatively signed by your account.
                </p>
              </div>
            </div>

            <button
              type="button"
              onClick={handleStaffLogout}
              className="inline-flex items-center gap-1 self-start sm:self-auto rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition cursor-pointer"
            >
              Sign Out
            </button>
          </div>

          {/* Policy Information */}
          <div className="rounded-xl border border-amber-200/80 bg-amber-50/70 p-3.5 text-xs text-amber-900">
            <div className="flex items-start gap-2">
              <span className="font-bold text-amber-800 text-sm">ℹ</span>
              <div className="space-y-1">
                <p className="font-semibold text-amber-950">
                  Staff Authorization & Attendance Grace Period Policy:
                </p>
                <p className="text-[11px] text-amber-900 leading-relaxed">
                  <strong>Check-in window:</strong> Passes can be scanned starting 15 minutes before slot start up to <strong>10 minutes after</strong> slot start time.
                  Unscanned passes forfeit all remaining weekly credits for that week. Staff identity is strictly validated server-side from your JWT bearer token.
                </p>
              </div>
            </div>
          </div>

          {/* Main Grid: Scan Terminal + No-Show Station */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Left Column (2 Cols): QR Check-in Terminal */}
            <div className="lg:col-span-2 space-y-4">
              <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-2xs space-y-4">
                <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                  <span className="flex h-5 w-5 items-center justify-center rounded-full bg-emerald-100 text-emerald-800 text-[11px] font-black">
                    1
                  </span>
                  Scan or Enter Player Pass Token
                </h2>

                <form onSubmit={handleCheckInSubmit} className="space-y-3">
                  <div>
                    <label htmlFor="qrTokenInput" className="block text-xs font-medium text-slate-700 mb-1">
                      Scanned QR Token (from player barcode/phone screen):
                    </label>
                    <div className="flex gap-2">
                      <input
                        id="qrTokenInput"
                        type="text"
                        value={qrTokenInput}
                        onChange={(e) => setQrTokenInput(e.target.value)}
                        placeholder="e.g. QR_7f9b8c1d..."
                        className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-xs font-mono text-slate-900 placeholder:text-slate-400 focus:border-emerald-600 focus:ring-2 focus:ring-emerald-600/20 outline-hidden"
                        autoFocus
                      />
                      <button
                        type="submit"
                        disabled={isCheckingIn || !qrTokenInput.trim()}
                        className="inline-flex items-center justify-center rounded-lg bg-emerald-700 px-4 py-2 text-xs font-semibold text-white shadow-2xs hover:bg-emerald-800 transition disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
                      >
                        {isCheckingIn ? 'Verifying...' : 'Verify & Check In'}
                      </button>
                    </div>
                  </div>

                  {suggestedToken && qrTokenInput !== suggestedToken && (
                    <div className="flex items-center gap-2 pt-1">
                      <span className="text-[11px] text-slate-500">Quick-fill from active booking:</span>
                      <button
                        type="button"
                        onClick={() => setQrTokenInput(suggestedToken)}
                        className="text-[11px] font-mono font-medium text-emerald-700 underline hover:text-emerald-800 cursor-pointer"
                      >
                        {suggestedToken}
                      </button>
                    </div>
                  )}
                </form>

                {/* Check-in Error Alert */}
                {checkInError && (
                  <div className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs text-rose-900 flex items-start gap-2">
                    <span className="font-bold text-rose-700 text-sm">✕</span>
                    <div className="space-y-0.5">
                      <p className="font-bold text-rose-950">Check-In Rejected</p>
                      <p className="text-[11px] text-rose-900">{checkInError}</p>
                    </div>
                  </div>
                )}

                {/* Check-in Success / Idempotent Card */}
                {checkInResult && (
                  <div
                    className={`rounded-xl border p-4 text-xs space-y-3 ${
                      checkInResult.alreadyCheckedIn
                        ? 'border-amber-200 bg-amber-50/80 text-amber-950'
                        : 'border-emerald-200 bg-emerald-50/80 text-emerald-950'
                    }`}
                  >
                    <div className="flex items-center justify-between border-b pb-2 border-slate-200/60">
                      <div className="flex items-center gap-2">
                        <span
                          className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold ${
                            checkInResult.alreadyCheckedIn
                              ? 'bg-amber-200 text-amber-900'
                              : 'bg-emerald-200 text-emerald-900'
                          }`}
                        >
                          {checkInResult.alreadyCheckedIn ? 'ℹ Already Checked In' : '✓ Check-in Verified'}
                        </span>
                        <span className="text-[11px] font-medium text-slate-600">
                          Booking #{checkInResult.bookingId}
                        </span>
                      </div>
                      <span className="font-mono text-[10px] text-slate-500">
                        By {checkInResult.checkedInBy}
                      </span>
                    </div>

                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1 text-[11px]">
                      <div>
                        <span className="text-slate-500 block text-[10px]">Student Name</span>
                        <strong className="text-slate-900">{checkInResult.studentName}</strong>
                      </div>
                      <div>
                        <span className="text-slate-500 block text-[10px]">Admission No</span>
                        <strong className="font-mono text-slate-800">{checkInResult.admissionNumber}</strong>
                      </div>
                      <div>
                        <span className="text-slate-500 block text-[10px]">Court & Spot</span>
                        <strong className="text-slate-900">
                          Court {checkInResult.courtNumber} · Spot {checkInResult.spotNumber}
                        </strong>
                      </div>
                      <div>
                        <span className="text-slate-500 block text-[10px]">Slot Time</span>
                        <strong className="text-slate-900">
                          {checkInResult.slotStartTime ? checkInResult.slotStartTime.replace('T', ' ') : 'N/A'}
                        </strong>
                      </div>
                    </div>

                    <p className="text-[11px] text-slate-600 italic pt-1">{checkInResult.message}</p>
                  </div>
                )}
              </div>

              {/* Session Activity Log */}
              <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-2xs space-y-3">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold text-slate-900 uppercase tracking-wide">
                    Recent Scans This Session ({history.length})
                  </h3>
                  {history.length > 0 && (
                    <button
                      type="button"
                      onClick={() => setHistory([])}
                      className="text-[10px] text-slate-400 hover:text-slate-600 underline cursor-pointer"
                    >
                      Clear History
                    </button>
                  )}
                </div>

                {history.length === 0 ? (
                  <p className="text-xs text-slate-400 py-3 text-center italic">
                    No player passes scanned in this session yet.
                  </p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-[11px]">
                      <thead>
                        <tr className="border-b border-slate-100 text-slate-400">
                          <th className="py-1.5 font-medium">Time</th>
                          <th className="py-1.5 font-medium">Player</th>
                          <th className="py-1.5 font-medium">Admission #</th>
                          <th className="py-1.5 font-medium">Court</th>
                          <th className="py-1.5 font-medium">Spot</th>
                          <th className="py-1.5 font-medium">Status</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {history.map((item) => (
                          <tr key={item.id} className="hover:bg-slate-50/50">
                            <td className="py-1.5 text-slate-500 font-mono">{item.timestamp}</td>
                            <td className="py-1.5 font-semibold text-slate-900">{item.studentName}</td>
                            <td className="py-1.5 font-mono text-slate-600">{item.admissionNumber}</td>
                            <td className="py-1.5 text-slate-700">Court {item.courtNumber}</td>
                            <td className="py-1.5 text-slate-700">Spot {item.spotNumber}</td>
                            <td className="py-1.5">
                              <span
                                className={`rounded px-1.5 py-0.5 text-[9px] font-bold ${
                                  item.alreadyCheckedIn
                                    ? 'bg-amber-100 text-amber-800'
                                    : 'bg-emerald-100 text-emerald-800'
                                }`}
                              >
                                {item.alreadyCheckedIn ? 'Duplicate' : 'Verified'}
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>

            {/* Right Column (1 Col): No-Show Enforcement Station */}
            <div className="space-y-4">
              <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-2xs space-y-4">
                <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                  <span className="flex h-5 w-5 items-center justify-center rounded-full bg-rose-100 text-rose-800 text-[11px] font-black">
                    2
                  </span>
                  No-Show Enforcement
                </h2>

                <p className="text-[11px] text-slate-600 leading-normal">
                  Any player not checked in within 10 minutes of their slot start time is classified as a no-show.
                  Running this tool forfeits remaining weekly credits and adjusts reliability rating.
                </p>

                <div className="space-y-3 pt-1">
                  <div>
                    <label htmlFor="slotFilter" className="block text-xs font-medium text-slate-700 mb-1">
                      Target TimeSlot ID (optional filter):
                    </label>
                    <input
                      id="slotFilter"
                      type="text"
                      value={slotFilterInput}
                      onChange={(e) => setSlotFilterInput(e.target.value)}
                      placeholder="Leave empty for all past-due slots"
                      className="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-900 placeholder:text-slate-400 focus:border-rose-500 focus:ring-1 focus:ring-rose-500 outline-hidden"
                    />
                  </div>

                  <button
                    type="button"
                    onClick={handleProcessNoShows}
                    disabled={isProcessingNoShows}
                    className="w-full inline-flex items-center justify-center rounded-lg bg-rose-700 px-4 py-2 text-xs font-semibold text-white shadow-2xs hover:bg-rose-800 transition disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
                  >
                    {isProcessingNoShows ? 'Processing No-Shows...' : 'Execute No-Show Processing'}
                  </button>
                </div>

                {/* No-show Error */}
                {noShowError && (
                  <div className="rounded-lg border border-rose-200 bg-rose-50 p-2.5 text-xs text-rose-900">
                    <p className="font-semibold text-rose-950">Execution Failed:</p>
                    <p className="text-[11px] mt-0.5">{noShowError}</p>
                  </div>
                )}

                {/* No-show Result Report */}
                {noShowResult && (
                  <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-3.5 space-y-2 text-xs">
                    <div className="flex items-center justify-between border-b border-slate-200/80 pb-1.5">
                      <span className="font-bold text-slate-900">Execution Report</span>
                      <span className="rounded bg-slate-200 px-1.5 py-0.5 text-[10px] font-bold text-slate-800">
                        {noShowResult.processedCount} Penalized
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-600 leading-normal">{noShowResult.message}</p>

                    {noShowResult.penalizedPlayers.length > 0 && (
                      <div className="space-y-1.5 pt-2">
                        <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wide block">
                          Penalized Players:
                        </span>
                        {noShowResult.penalizedPlayers.map((p) => (
                          <div
                            key={p.bookingPlayerId}
                            className="rounded border border-rose-200 bg-white p-2 text-[11px] space-y-0.5"
                          >
                            <div className="flex items-center justify-between">
                              <strong className="text-slate-900">{p.studentName}</strong>
                              <span className="text-rose-700 font-bold text-[10px]">
                                Credits: 0 (forfeited)
                              </span>
                            </div>
                            <p className="text-[10px] text-slate-500">
                              Court {p.courtNumber} · Spot {p.spotNumber} · Rating: {p.updatedReliabilityRating}%
                            </p>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
};
