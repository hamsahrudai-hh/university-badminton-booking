import { useState, type FormEvent, type ChangeEvent } from 'react';
import {
  registerStudent,
  ApiError,
  type RegistrationResponseDto,
} from '../../api/client';

interface StudentRegistrationScreenProps {
  onNavigateHome: () => void;
  onNavigateToVerification: (prefillToken?: string) => void;
  onNavigateToLogin?: () => void;
}

interface FormState {
  fullName: string;
  universityEmail: string;
  admissionNumber: string;
  password: string;
  confirmPassword: string;
}

interface FormErrors {
  fullName?: string;
  universityEmail?: string;
  admissionNumber?: string;
  password?: string;
  confirmPassword?: string;
}

const REQUIRED_EMAIL_DOMAIN = 'vitapstudent.ac.in';

export function StudentRegistrationScreen({
  onNavigateHome,
  onNavigateToVerification,
  onNavigateToLogin,
}: StudentRegistrationScreenProps) {
  const [formData, setFormData] = useState<FormState>({
    fullName: '',
    universityEmail: '',
    admissionNumber: '',
    password: '',
    confirmPassword: '',
  });

  const [touched, setTouched] = useState<Record<keyof FormState, boolean>>({
    fullName: false,
    universityEmail: false,
    admissionNumber: false,
    password: false,
    confirmPassword: false,
  });

  const [clientErrors, setClientErrors] = useState<FormErrors>({});
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [registrationSuccess, setRegistrationSuccess] = useState<RegistrationResponseDto | null>(null);

  const validateField = (name: keyof FormState, value: string, currentForm: FormState): string | undefined => {
    switch (name) {
      case 'fullName':
        if (!value.trim()) return 'Full name is required.';
        if (value.trim().length < 2) return 'Full name must be at least 2 characters.';
        return undefined;

      case 'universityEmail': {
        const trimmed = value.trim().toLowerCase();
        if (!trimmed) return 'University email is required.';
        const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailPattern.test(trimmed)) return 'Please enter a valid email address.';
        if (!trimmed.endsWith(`@${REQUIRED_EMAIL_DOMAIN}`)) {
          return `Email must end with @${REQUIRED_EMAIL_DOMAIN}.`;
        }
        return undefined;
      }

      case 'admissionNumber':
        if (!value.trim()) return 'Admission number is required.';
        if (value.trim().length < 3) return 'Admission number must be at least 3 characters.';
        return undefined;

      case 'password':
        if (!value) return 'Password is required.';
        if (value.length < 8) return 'Password must be at least 8 characters long.';
        return undefined;

      case 'confirmPassword':
        if (!value) return 'Please confirm your password.';
        if (value !== currentForm.password) return 'Passwords do not match.';
        return undefined;

      default:
        return undefined;
    }
  };

  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    const fieldName = name as keyof FormState;
    const nextFormData = { ...formData, [fieldName]: value };
    setFormData(nextFormData);

    if (touched[fieldName]) {
      const error = validateField(fieldName, value, nextFormData);
      setClientErrors((prev) => ({ ...prev, [fieldName]: error }));
    }

    // Clear server error banner upon editing
    if (submitError) {
      setSubmitError(null);
    }
  };

  const handleBlur = (e: React.FocusEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    const fieldName = name as keyof FormState;
    setTouched((prev) => ({ ...prev, [fieldName]: true }));
    const error = validateField(fieldName, value, formData);
    setClientErrors((prev) => ({ ...prev, [fieldName]: error }));
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitError(null);

    // Validate all fields
    const errors: FormErrors = {};
    (Object.keys(formData) as Array<keyof FormState>).forEach((field) => {
      const err = validateField(field, formData[field], formData);
      if (err) errors[field] = err;
    });

    setTouched({
      fullName: true,
      universityEmail: true,
      admissionNumber: true,
      password: true,
      confirmPassword: true,
    });

    setClientErrors(errors);

    if (Object.keys(errors).length > 0) {
      return;
    }

    setIsSubmitting(true);

    try {
      const response = await registerStudent({
        fullName: formData.fullName.trim(),
        universityEmail: formData.universityEmail.trim().toLowerCase(),
        admissionNumber: formData.admissionNumber.trim().toUpperCase(),
        password: formData.password,
      });

      setRegistrationSuccess(response);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          setSubmitError(
            err.message ||
              'A student with this university email or admission number is already registered.'
          );
        } else if (err.status === 400) {
          setSubmitError(err.message || 'Please verify your registration details.');
        } else {
          setSubmitError(err.message || `Server error (${err.status}). Please try again later.`);
        }
      } else {
        setSubmitError(
          err instanceof Error
            ? err.message
            : 'Unable to connect to the registration service. Please verify backend connection.'
        );
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-xl py-4 sm:py-6">
      {/* Top Breadcrumb / Navigation */}
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
          Live Student Registration
        </span>
      </div>

      {registrationSuccess ? (
        /* Success State Card */
        <div className="rounded-xl border border-emerald-200 bg-white p-5 sm:p-6 shadow-2xs space-y-4">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-100 text-emerald-800">
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
                <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-900">
                Registration Submitted Successfully
              </h2>
              <p className="text-xs text-slate-600 mt-0.5">
                Account created for{' '}
                <strong className="text-slate-900">{registrationSuccess.fullName}</strong> (
                {registrationSuccess.admissionNumber}).
              </p>
            </div>
          </div>

          {/* Critical Requirement Notice: Incomplete until email verification */}
          <div className="rounded-lg border border-amber-200 bg-amber-50/80 p-3.5 text-xs text-amber-900 space-y-1.5">
            <div className="flex items-center gap-1.5 font-bold text-amber-950">
              <svg className="h-4 w-4 shrink-0 text-amber-700" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
              <span>Email Verification Required Before Booking</span>
            </div>
            <p className="text-[11px] leading-relaxed text-amber-800">
              Your account is currently <strong className="font-semibold text-amber-950">inactive with 0 weekly credits</strong>.
              You cannot book courts, join morning/evening queues, or appear in active group selections until your
              university email address is verified.
            </p>
            <div className="mt-2 rounded bg-amber-100/70 px-2.5 py-1.5 text-[11px] text-amber-900 font-mono">
              Delivery target: {registrationSuccess.universityEmail}
            </div>
          </div>

          {/* Development Environment Guidance */}
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600 space-y-1">
            <p className="font-semibold text-slate-800 flex items-center gap-1">
              <span>🛠️ Local Development Testing Note</span>
            </p>
            <p className="text-[11px] text-slate-500 leading-relaxed">
              In development mode, verification emails are logged by the backend service. Check your running backend
              terminal for the <code className="rounded bg-slate-200 px-1 py-0.5 text-slate-800 font-mono text-[10px]">[DEV EMAIL DISPATCH]</code> line
              to copy your 64-character verification token or click the verification link.
            </p>
          </div>

          {/* Action Buttons */}
          <div className="pt-2 flex flex-col sm:flex-row gap-2.5">
            <button
              type="button"
              onClick={() => onNavigateToVerification()}
              className="inline-flex h-9.5 flex-1 items-center justify-center gap-1.5 rounded-lg bg-emerald-700 px-4 text-xs font-semibold text-white shadow-2xs hover:bg-emerald-800 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600"
            >
              <span>Enter Verification Token</span>
              <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
                <path strokeLinecap="round" strokeLinejoin="round" d="M14 5l7 7m0 0l-7 7m7-7H3" />
              </svg>
            </button>

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
        /* Registration Form Card */
        <div className="rounded-xl border border-slate-200/90 bg-white p-5 sm:p-6 shadow-2xs space-y-4">
          <div className="border-b border-slate-100 pb-3">
            <h1 className="text-lg font-bold tracking-tight text-slate-900">
              Student Account Registration
            </h1>
            <p className="text-xs text-slate-500 mt-0.5">
              Create your account using your verified institutional <strong className="text-slate-700">@{REQUIRED_EMAIL_DOMAIN}</strong> email.
            </p>
          </div>

          {/* Server Error Alert */}
          {submitError && (
            <div
              role="alert"
              className="rounded-lg border border-red-200 bg-red-50 p-3 text-xs text-red-800 flex items-start gap-2"
            >
              <svg className="h-4 w-4 shrink-0 text-red-600 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <div>
                <p className="font-semibold text-red-900">Registration Error</p>
                <p className="text-[11px] text-red-700 mt-0.5">{submitError}</p>
              </div>
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate className="space-y-3.5">
            {/* Full Name */}
            <div>
              <label htmlFor="reg-fullName" className="block text-xs font-semibold text-slate-700 mb-1">
                Full Name <span className="text-red-500">*</span>
              </label>
              <input
                id="reg-fullName"
                name="fullName"
                type="text"
                autoComplete="name"
                disabled={isSubmitting}
                value={formData.fullName}
                onChange={handleChange}
                onBlur={handleBlur}
                aria-invalid={!!clientErrors.fullName}
                aria-describedby={clientErrors.fullName ? 'fullName-error' : undefined}
                placeholder="e.g. Alice Smith"
                className={`w-full rounded-lg border px-3 py-2 text-xs text-slate-900 shadow-2xs placeholder:text-slate-400 focus:outline-hidden focus:ring-2 ${
                  clientErrors.fullName && touched.fullName
                    ? 'border-red-300 focus:ring-red-500'
                    : 'border-slate-200 focus:border-emerald-600 focus:ring-emerald-600'
                }`}
              />
              {clientErrors.fullName && touched.fullName && (
                <p id="fullName-error" className="mt-1 text-[11px] text-red-600">
                  {clientErrors.fullName}
                </p>
              )}
            </div>

            {/* University Email */}
            <div>
              <label htmlFor="reg-email" className="block text-xs font-semibold text-slate-700 mb-1">
                University Email Address <span className="text-red-500">*</span>
              </label>
              <input
                id="reg-email"
                name="universityEmail"
                type="email"
                autoComplete="email"
                disabled={isSubmitting}
                value={formData.universityEmail}
                onChange={handleChange}
                onBlur={handleBlur}
                aria-invalid={!!clientErrors.universityEmail}
                aria-describedby={clientErrors.universityEmail ? 'email-error' : 'email-help'}
                placeholder="student.name@vitapstudent.ac.in"
                className={`w-full rounded-lg border px-3 py-2 text-xs text-slate-900 shadow-2xs placeholder:text-slate-400 focus:outline-hidden focus:ring-2 font-mono ${
                  clientErrors.universityEmail && touched.universityEmail
                    ? 'border-red-300 focus:ring-red-500'
                    : 'border-slate-200 focus:border-emerald-600 focus:ring-emerald-600'
                }`}
              />
              <p id="email-help" className="mt-1 text-[11px] text-slate-400">
                Must end with <code className="font-semibold text-slate-600 font-mono">@{REQUIRED_EMAIL_DOMAIN}</code>.
              </p>
              {clientErrors.universityEmail && touched.universityEmail && (
                <p id="email-error" className="mt-0.5 text-[11px] text-red-600">
                  {clientErrors.universityEmail}
                </p>
              )}
            </div>

            {/* Admission Number */}
            <div>
              <label htmlFor="reg-admissionNumber" className="block text-xs font-semibold text-slate-700 mb-1">
                University Admission Number <span className="text-red-500">*</span>
              </label>
              <input
                id="reg-admissionNumber"
                name="admissionNumber"
                type="text"
                autoComplete="off"
                disabled={isSubmitting}
                value={formData.admissionNumber}
                onChange={handleChange}
                onBlur={handleBlur}
                aria-invalid={!!clientErrors.admissionNumber}
                aria-describedby={clientErrors.admissionNumber ? 'admission-error' : undefined}
                placeholder="e.g. 24BCS001"
                className={`w-full rounded-lg border px-3 py-2 text-xs text-slate-900 shadow-2xs placeholder:text-slate-400 focus:outline-hidden focus:ring-2 font-mono uppercase ${
                  clientErrors.admissionNumber && touched.admissionNumber
                    ? 'border-red-300 focus:ring-red-500'
                    : 'border-slate-200 focus:border-emerald-600 focus:ring-emerald-600'
                }`}
              />
              {clientErrors.admissionNumber && touched.admissionNumber && (
                <p id="admission-error" className="mt-1 text-[11px] text-red-600">
                  {clientErrors.admissionNumber}
                </p>
              )}
            </div>

            {/* Password & Confirmation Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {/* Password */}
              <div>
                <label htmlFor="reg-password" className="block text-xs font-semibold text-slate-700 mb-1">
                  Password <span className="text-red-500">*</span>
                </label>
                <input
                  id="reg-password"
                  name="password"
                  type="password"
                  autoComplete="new-password"
                  disabled={isSubmitting}
                  value={formData.password}
                  onChange={handleChange}
                  onBlur={handleBlur}
                  aria-invalid={!!clientErrors.password}
                  aria-describedby={clientErrors.password ? 'password-error' : undefined}
                  placeholder="Min. 8 characters"
                  className={`w-full rounded-lg border px-3 py-2 text-xs text-slate-900 shadow-2xs placeholder:text-slate-400 focus:outline-hidden focus:ring-2 ${
                    clientErrors.password && touched.password
                      ? 'border-red-300 focus:ring-red-500'
                      : 'border-slate-200 focus:border-emerald-600 focus:ring-emerald-600'
                  }`}
                />
                {clientErrors.password && touched.password && (
                  <p id="password-error" className="mt-1 text-[11px] text-red-600">
                    {clientErrors.password}
                  </p>
                )}
              </div>

              {/* Confirm Password */}
              <div>
                <label htmlFor="reg-confirmPassword" className="block text-xs font-semibold text-slate-700 mb-1">
                  Confirm Password <span className="text-red-500">*</span>
                </label>
                <input
                  id="reg-confirmPassword"
                  name="confirmPassword"
                  type="password"
                  autoComplete="new-password"
                  disabled={isSubmitting}
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  onBlur={handleBlur}
                  aria-invalid={!!clientErrors.confirmPassword}
                  aria-describedby={clientErrors.confirmPassword ? 'confirm-error' : undefined}
                  placeholder="Re-enter password"
                  className={`w-full rounded-lg border px-3 py-2 text-xs text-slate-900 shadow-2xs placeholder:text-slate-400 focus:outline-hidden focus:ring-2 ${
                    clientErrors.confirmPassword && touched.confirmPassword
                      ? 'border-red-300 focus:ring-red-500'
                      : 'border-slate-200 focus:border-emerald-600 focus:ring-emerald-600'
                  }`}
                />
                {clientErrors.confirmPassword && touched.confirmPassword && (
                  <p id="confirm-error" className="mt-1 text-[11px] text-red-600">
                    {clientErrors.confirmPassword}
                  </p>
                )}
              </div>
            </div>

            {/* Submit Action */}
            <div className="pt-3 border-t border-slate-100 flex flex-col sm:flex-row items-center justify-between gap-3">
              <div className="flex flex-wrap items-center gap-2.5">
                {onNavigateToLogin && (
                  <button
                    type="button"
                    onClick={onNavigateToLogin}
                    className="text-xs text-emerald-700 hover:text-emerald-900 font-semibold underline underline-offset-2"
                  >
                    Already registered? Sign in
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => onNavigateToVerification()}
                  className="text-xs text-slate-500 hover:text-slate-800 font-medium underline underline-offset-2"
                >
                  Verify your token
                </button>
              </div>

              <div className="flex items-center gap-2 w-full sm:w-auto">
                <button
                  type="button"
                  disabled={isSubmitting}
                  onClick={onNavigateHome}
                  className="w-1/2 sm:w-auto inline-flex h-9 items-center justify-center rounded-lg border border-slate-200 bg-white px-3.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600"
                >
                  Cancel
                </button>

                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="w-1/2 sm:w-auto inline-flex h-9 items-center justify-center gap-1.5 rounded-lg bg-emerald-700 px-5 text-xs font-semibold text-white shadow-2xs hover:bg-emerald-800 disabled:opacity-60 disabled:cursor-not-allowed transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer"
                >
                  {isSubmitting ? (
                    <>
                      <svg className="h-3.5 w-3.5 animate-spin" viewBox="0 0 24 24" fill="none">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
                      </svg>
                      <span>Creating Account...</span>
                    </>
                  ) : (
                    <span>Register Student</span>
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
