import { useState, type FormEvent } from 'react';
import { login, ApiError, type LoginResponseDto } from '../../api/client';

interface StudentLoginScreenProps {
  onLoginSuccess: (user: LoginResponseDto) => void;
  onNavigateHome: () => void;
  onNavigateToRegister: () => void;
  onNavigateToVerification: (prefillEmail?: string) => void;
  noticeMessage?: string | null;
}

export function StudentLoginScreen({
  onLoginSuccess,
  onNavigateHome,
  onNavigateToRegister,
  onNavigateToVerification,
  noticeMessage,
}: StudentLoginScreenProps) {
  const [identifier, setIdentifier] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [showPassword, setShowPassword] = useState<boolean>(false);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isUnverifiedError, setIsUnverifiedError] = useState<boolean>(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const cleanIdentifier = identifier.trim();
    if (!cleanIdentifier) {
      setErrorMessage('Please enter your university email or admission number.');
      return;
    }
    if (!password) {
      setErrorMessage('Please enter your password.');
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);
    setIsUnverifiedError(false);

    try {
      const response = await login({
        identifier: cleanIdentifier,
        password,
      });
      onLoginSuccess(response);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          setErrorMessage('Invalid credentials. Please verify your university email/admission number and password.');
        } else if (err.status === 403) {
          const msg = err.message || '';
          if (msg.toLowerCase().includes('verif')) {
            setIsUnverifiedError(true);
            setErrorMessage(
              'Your university email is not verified yet. In accordance with university policy, student accounts must be verified before signing in.'
            );
          } else {
            setErrorMessage(err.message || 'Account access is restricted. Please contact administration.');
          }
        } else {
          setErrorMessage(err.message || `Sign in failed (${err.status}).`);
        }
      } else {
        setErrorMessage(
          err instanceof Error
            ? err.message
            : 'Unable to connect to the authentication server. Please check your connection.'
        );
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-md py-4 sm:py-6">
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
          Student Sign In
        </span>
      </div>

      {/* Main Card */}
      <div className="rounded-xl border border-slate-200/90 bg-white p-5 sm:p-6 shadow-2xs space-y-4">
        <div className="border-b border-slate-100 pb-3">
          <h1 className="text-lg font-bold tracking-tight text-slate-900">
            Sign In to UniBadminton
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Authenticate with your university email or admission number to access court reservations and live queues.
          </p>
        </div>

        {/* Notice Message (e.g. redirected from expired session or registration) */}
        {noticeMessage && (
          <div className="rounded-lg border border-indigo-200 bg-indigo-50/70 p-3 text-xs text-indigo-900 flex items-start gap-2">
            <svg className="h-4 w-4 shrink-0 text-indigo-700 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
              <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <p className="text-[11px] leading-relaxed">{noticeMessage}</p>
          </div>
        )}

        {/* Error Alert */}
        {errorMessage && (
          <div
            role="alert"
            className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800 space-y-2"
          >
            <div className="flex items-start gap-2">
              <svg className="h-4 w-4 shrink-0 text-rose-600 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <div>
                <p className="font-semibold text-rose-900">Authentication Error</p>
                <p className="text-[11px] text-rose-700 mt-0.5 leading-relaxed">{errorMessage}</p>
              </div>
            </div>

            {/* Special Callout if Email is Unverified */}
            {isUnverifiedError && (
              <div className="pt-2 border-t border-rose-200/60 flex items-center justify-between">
                <span className="text-[11px] text-rose-800 font-medium">
                  Have a 64-character verification token?
                </span>
                <button
                  type="button"
                  onClick={() => onNavigateToVerification(identifier.trim())}
                  className="inline-flex items-center gap-1 rounded bg-rose-700 px-2.5 py-1 text-[11px] font-semibold text-white hover:bg-rose-800 transition cursor-pointer"
                >
                  <span>Verify Email Now</span>
                  <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M14 5l7 7m0 0l-7 7m7-7H3" />
                  </svg>
                </button>
              </div>
            )}
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate className="space-y-3.5">
          {/* Identifier Input */}
          <div>
            <label htmlFor="student-identifier" className="block text-xs font-semibold text-slate-700 mb-1">
              University Email or Admission Number <span className="text-rose-500">*</span>
            </label>
            <input
              id="student-identifier"
              type="text"
              autoComplete="username"
              disabled={isSubmitting}
              value={identifier}
              onChange={(e) => {
                setIdentifier(e.target.value);
                if (errorMessage) setErrorMessage(null);
              }}
              placeholder="e.g. suresh.k@vitapstudent.ac.in or 23MIC1001"
              className="w-full rounded-lg border border-slate-200 px-3 py-2 text-xs text-slate-900 shadow-2xs placeholder:text-slate-400 focus:border-emerald-600 focus:outline-hidden focus:ring-2 focus:ring-emerald-600"
            />
          </div>

          {/* Password Input */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label htmlFor="student-password" className="block text-xs font-semibold text-slate-700">
                Password <span className="text-rose-500">*</span>
              </label>
              <button
                type="button"
                onClick={() => setShowPassword((prev) => !prev)}
                className="text-[11px] text-slate-500 hover:text-slate-800 font-medium"
              >
                {showPassword ? 'Hide password' : 'Show password'}
              </button>
            </div>
            <input
              id="student-password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              disabled={isSubmitting}
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                if (errorMessage) setErrorMessage(null);
              }}
              placeholder="Enter your account password"
              className="w-full rounded-lg border border-slate-200 px-3 py-2 text-xs text-slate-900 shadow-2xs placeholder:text-slate-400 focus:border-emerald-600 focus:outline-hidden focus:ring-2 focus:ring-emerald-600"
            />
          </div>

          {/* Submit Button */}
          <div className="pt-2">
            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full inline-flex h-9.5 items-center justify-center gap-1.5 rounded-lg bg-emerald-700 px-4 text-xs font-semibold text-white shadow-2xs hover:bg-emerald-800 disabled:opacity-60 disabled:cursor-not-allowed transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer"
            >
              {isSubmitting ? (
                <>
                  <svg className="h-3.5 w-3.5 animate-spin" viewBox="0 0 24 24" fill="none">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
                  </svg>
                  <span>Signing in...</span>
                </>
              ) : (
                <span>Sign In</span>
              )}
            </button>
          </div>
        </form>

        {/* Footer Navigation Links */}
        <div className="pt-3 border-t border-slate-100 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs">
          <button
            type="button"
            onClick={onNavigateToRegister}
            className="text-emerald-700 hover:text-emerald-900 font-medium underline underline-offset-2"
          >
            Don&apos;t have an account? Register here
          </button>
          <button
            type="button"
            onClick={() => onNavigateToVerification()}
            className="text-slate-500 hover:text-slate-800 font-medium underline underline-offset-2"
          >
            Verify university email
          </button>
        </div>
      </div>
    </div>
  );
}
