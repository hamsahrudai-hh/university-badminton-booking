import { useState, useEffect, type FormEvent } from 'react';
import {
  verifyEmail,
  ApiError,
  type VerifyEmailResponseDto,
} from '../../api/client';

interface EmailVerificationScreenProps {
  initialToken?: string | null;
  onNavigateHome: () => void;
  onStartBooking?: () => void;
  onNavigateToRegister: () => void;
  onNavigateToLogin?: () => void;
}

export function EmailVerificationScreen({
  initialToken,
  onNavigateHome,
  onStartBooking,
  onNavigateToRegister,
  onNavigateToLogin,
}: EmailVerificationScreenProps) {
  const [tokenInput, setTokenInput] = useState<string>(initialToken?.trim() || '');
  const [isVerifying, setIsVerifying] = useState<boolean>(false);
  const [verificationError, setVerificationError] = useState<string | null>(null);
  const [verificationResult, setVerificationResult] = useState<VerifyEmailResponseDto | null>(null);

  // Automatically trigger verification if an initial token was provided via URL
  useEffect(() => {
    if (initialToken && initialToken.trim().length > 0 && !verificationResult && !isVerifying) {
      handlePerformVerification(initialToken.trim());
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialToken]);

  const handlePerformVerification = async (tokenToVerify: string) => {
    const cleanToken = tokenToVerify.trim();
    if (!cleanToken) {
      setVerificationError('Please enter a verification token.');
      return;
    }

    setIsVerifying(true);
    setVerificationError(null);

    try {
      const response = await verifyEmail(cleanToken);
      setVerificationResult(response);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 404) {
          setVerificationError(
            'Invalid or unrecognized verification token. Please verify that the full 64-character token was copied.'
          );
        } else if (err.status === 409) {
          setVerificationError(
            err.message ||
              'This verification token has either expired (24-hour limit) or has already been used to activate an account.'
          );
        } else if (err.status === 400) {
          setVerificationError(err.message || 'Verification token is required.');
        } else {
          setVerificationError(err.message || `Verification server error (${err.status}).`);
        }
      } else {
        setVerificationError(
          err instanceof Error
            ? err.message
            : 'Unable to connect to the verification server. Please verify your connection.'
        );
      }
    } finally {
      setIsVerifying(false);
    }
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    handlePerformVerification(tokenInput);
  };

  return (
    <div className="mx-auto max-w-xl py-4 sm:py-6">
      {/* Top Breadcrumb Navigation */}
      <div className="mb-4 flex items-center justify-between">
        <button
          type="button"
          onClick={onNavigateHome}
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-600 hover:text-slate-900 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 rounded p-1"
        >
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
            <path strokeLinecap="round" strokeLinejoin="round" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          <span>Back to Dashboard</span>
        </button>

        <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-0.5 text-[11px] font-semibold text-emerald-800 border border-emerald-200/60">
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-600" />
          University Email Verification
        </span>
      </div>

      {verificationResult ? (
        /* Success State Card */
        <div className="rounded-xl border border-emerald-200 bg-white p-5 sm:p-6 shadow-2xs space-y-4">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-100 text-emerald-800">
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-900">
                Email Address Verified Successfully!
              </h2>
              <p className="text-xs text-slate-600 mt-0.5">
                Account activated for{' '}
                <strong className="text-slate-900">{verificationResult.fullName}</strong> (
                {verificationResult.universityEmail}).
              </p>
            </div>
          </div>

          {/* Account Activated Summary */}
          <div className="rounded-lg border border-emerald-100 bg-emerald-50/70 p-4 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-emerald-950">
                Weekly Booking Credits Initialized
              </span>
              <span className="rounded-full bg-emerald-600 px-2.5 py-0.5 text-xs font-bold text-white">
                {verificationResult.weeklyCredits} Credits
              </span>
            </div>
            <p className="text-[11px] text-emerald-900 leading-relaxed">
              Your institutional status has been confirmed. You are now authorized to join morning and evening booking queues,
              participate in group badminton reservations, and check in with QR passes.
            </p>
          </div>

          {/* Action Buttons */}
          <div className="pt-2 flex flex-col sm:flex-row gap-2.5">
            {onNavigateToLogin && (
              <button
                type="button"
                onClick={onNavigateToLogin}
                className="inline-flex h-9.5 flex-1 items-center justify-center gap-1.5 rounded-lg bg-emerald-700 px-4 text-xs font-semibold text-white shadow-2xs hover:bg-emerald-800 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer"
              >
                <span>Sign In with University Account</span>
                <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M14 5l7 7m0 0l-7 7m7-7H3" />
                </svg>
              </button>
            )}

            {!onNavigateToLogin && onStartBooking && (
              <button
                type="button"
                onClick={onStartBooking}
                className="inline-flex h-9.5 flex-1 items-center justify-center gap-1.5 rounded-lg bg-emerald-700 px-4 text-xs font-semibold text-white shadow-2xs hover:bg-emerald-800 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer"
              >
                <span>Start Badminton Booking</span>
                <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M14 5l7 7m0 0l-7 7m7-7H3" />
                </svg>
              </button>
            )}

            <button
              type="button"
              onClick={onNavigateHome}
              className="inline-flex h-9.5 items-center justify-center rounded-lg border border-slate-200 bg-white px-4 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600"
            >
              Return to Dashboard
            </button>
          </div>
        </div>
      ) : (
        /* Verification Input Form Card */
        <div className="rounded-xl border border-slate-200/90 bg-white p-5 sm:p-6 shadow-2xs space-y-4">
          <div className="border-b border-slate-100 pb-3">
            <h1 className="text-lg font-bold tracking-tight text-slate-900">
              Verify University Email
            </h1>
            <p className="text-xs text-slate-500 mt-0.5">
              Confirm ownership of your university email to activate your account and receive weekly court booking credits.
            </p>
          </div>

          {/* Informative Status Banner */}
          <div className="rounded-lg border border-amber-200 bg-amber-50/70 p-3 text-xs text-amber-900 flex items-start gap-2">
            <svg className="h-4 w-4 shrink-0 text-amber-700 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
              <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <p className="text-[11px] leading-relaxed">
              Student accounts remain <strong>inactive with 0 booking credits</strong> until verified.
              Verification tokens expire 24 hours after registration and are single-use.
            </p>
          </div>

          {/* Error Alert */}
          {verificationError && (
            <div
              role="alert"
              className="rounded-lg border border-red-200 bg-red-50 p-3 text-xs text-red-800 flex items-start gap-2"
            >
              <svg className="h-4 w-4 shrink-0 text-red-600 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <div>
                <p className="font-semibold text-red-900">Verification Failed</p>
                <p className="text-[11px] text-red-700 mt-0.5">{verificationError}</p>
              </div>
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate className="space-y-3.5">
            <div>
              <label htmlFor="verify-token" className="block text-xs font-semibold text-slate-700 mb-1">
                Verification Token <span className="text-red-500">*</span>
              </label>
              <textarea
                id="verify-token"
                rows={3}
                disabled={isVerifying}
                value={tokenInput}
                onChange={(e) => {
                  setTokenInput(e.target.value);
                  if (verificationError) setVerificationError(null);
                }}
                placeholder="Paste the 64-character verification token from your university email or console..."
                aria-describedby="token-help"
                className="w-full rounded-lg border border-slate-200 p-2.5 text-xs text-slate-900 font-mono shadow-2xs placeholder:text-slate-400 placeholder:font-sans focus:border-emerald-600 focus:outline-hidden focus:ring-2 focus:ring-emerald-600"
              />
              <p id="token-help" className="mt-1 text-[11px] text-slate-400">
                Tip: If you opened a direct verification link, the token was automatically pre-filled above.
              </p>
            </div>

            {/* Dev Mode Help Box */}
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600 space-y-1">
              <p className="font-semibold text-slate-800 text-[11px]">
                Testing in Development Mode:
              </p>
              <p className="text-[11px] text-slate-500 leading-relaxed">
                Tokens are logged directly in the backend terminal by <code className="text-slate-800 font-mono font-semibold">DevConsoleEmailService</code>.
                Search the backend terminal output for <code className="text-slate-800 font-mono font-semibold">[DEV EMAIL DISPATCH]</code> and copy the 64-character hex token.
              </p>
            </div>

            {/* Actions */}
            <div className="pt-3 border-t border-slate-100 flex flex-col sm:flex-row items-center justify-between gap-3">
              <div className="flex flex-wrap items-center gap-2.5">
                <button
                  type="button"
                  onClick={onNavigateToRegister}
                  className="text-xs text-emerald-700 hover:text-emerald-900 font-medium underline underline-offset-2"
                >
                  Need to register an account first?
                </button>
                {onNavigateToLogin && (
                  <button
                    type="button"
                    onClick={onNavigateToLogin}
                    className="text-xs text-slate-500 hover:text-slate-800 font-medium underline underline-offset-2"
                  >
                    Already verified? Sign in
                  </button>
                )}
              </div>

              <div className="flex items-center gap-2 w-full sm:w-auto">
                <button
                  type="button"
                  disabled={isVerifying}
                  onClick={onNavigateHome}
                  className="w-1/2 sm:w-auto inline-flex h-9 items-center justify-center rounded-lg border border-slate-200 bg-white px-3.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600"
                >
                  Cancel
                </button>

                <button
                  type="submit"
                  disabled={isVerifying || !tokenInput.trim()}
                  className="w-1/2 sm:w-auto inline-flex h-9 items-center justify-center gap-1.5 rounded-lg bg-emerald-700 px-5 text-xs font-semibold text-white shadow-2xs hover:bg-emerald-800 disabled:opacity-60 disabled:cursor-not-allowed transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer"
                >
                  {isVerifying ? (
                    <>
                      <svg className="h-3.5 w-3.5 animate-spin" viewBox="0 0 24 24" fill="none">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
                      </svg>
                      <span>Verifying Token...</span>
                    </>
                  ) : (
                    <span>Verify & Activate</span>
                  )}
                </button>
              </div>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
