import type React from 'react';

interface HeaderProps {
  studentName: string;
  admissionNumber: string;
  onNavigateHome: () => void;
  onOpenSecondaryNav: (destination: string) => void;
  isWizardActive: boolean;
}

export const Header: React.FC<HeaderProps> = ({
  studentName,
  admissionNumber,
  onNavigateHome,
  onOpenSecondaryNav,
  isWizardActive,
}) => {
  return (
    <header className="border-b border-slate-200/80 bg-white/95 backdrop-blur-xs sticky top-0 z-20">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-2 sm:px-6">
        {/* Brand & Home */}
        <button
          type="button"
          onClick={onNavigateHome}
          className="flex items-center gap-2 text-left transition hover:opacity-90 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 rounded-lg p-0.5"
          aria-label="Return to Dashboard"
        >
          <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-emerald-700 text-white font-extrabold text-[11px] shadow-2xs">
            UB
          </div>
          <div className="flex items-baseline gap-1.5">
            <span className="text-sm font-bold tracking-tight text-slate-900">
              UniBadminton
            </span>
            <span className="hidden sm:inline text-[11px] text-slate-400 font-medium">
              Court Reservation
            </span>
          </div>
        </button>

        {/* Secondary Navigation */}
        <nav className="hidden md:flex items-center gap-0.5 text-xs font-medium text-slate-600">
          <button
            type="button"
            onClick={onNavigateHome}
            className={`rounded-md px-2.5 py-1 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 ${
              !isWizardActive
                ? 'bg-slate-100 font-semibold text-slate-900'
                : 'hover:text-slate-900 hover:bg-slate-50'
            }`}
          >
            Dashboard
          </button>
          <button
            type="button"
            onClick={() => onOpenSecondaryNav('My QR Passes')}
            className="rounded-md px-2.5 py-1 transition hover:text-slate-900 hover:bg-slate-50 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600"
          >
            My QR Passes
          </button>
          <button
            type="button"
            onClick={() => onOpenSecondaryNav('Equipment')}
            className="rounded-md px-2.5 py-1 transition hover:text-slate-900 hover:bg-slate-50 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600"
          >
            Equipment
          </button>
          <button
            type="button"
            onClick={() => onOpenSecondaryNav('Tournaments')}
            className="rounded-md px-2.5 py-1 transition hover:text-slate-900 hover:bg-slate-50 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600"
          >
            Tournaments
          </button>
          <button
            type="button"
            onClick={() => onOpenSecondaryNav('Rules & Queue')}
            className="rounded-md px-2.5 py-1 transition hover:text-slate-900 hover:bg-slate-50 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600"
          >
            Rules & Queue
          </button>
        </nav>

        {/* Compact Student Identity */}
        <div className="flex items-center gap-2">
          <div className="text-right">
            <span className="text-xs font-semibold text-slate-900 block leading-tight">
              {studentName}
            </span>
            <span className="text-[10px] font-mono text-slate-500 block leading-tight">
              {admissionNumber}
            </span>
          </div>
          <div className="flex h-6.5 w-6.5 items-center justify-center rounded-full bg-slate-100 border border-slate-300 text-[11px] font-bold text-slate-700">
            {studentName.charAt(0)}
          </div>
        </div>
      </div>
    </header>
  );
};
