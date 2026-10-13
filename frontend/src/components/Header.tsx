import React, { useState, useEffect, useRef } from 'react';
import { computeHeaderNavState } from '../utils/navigation.ts';

export interface HeaderProps {
  studentName: string;
  admissionNumber: string;
  userRole?: 'STUDENT' | 'STAFF' | 'ADMIN';
  isRealAccount?: boolean;
  isAuthenticated?: boolean;
  onNavigateHome: () => void;
  onStartBooking?: () => void;
  onOpenSecondaryNav: (destination: string) => void;
  isWizardActive: boolean;
  currentAuthScreen?: 'none' | 'login' | 'register' | 'verify-email';
  onNavigateLogin?: () => void;
  onNavigateRegister?: () => void;
  onNavigateVerifyEmail?: () => void;
  onSignOut?: () => void;
  isStaffCheckInActive?: boolean;
  onNavigateStaffCheckIn?: () => void;
  isCourtManagementActive?: boolean;
  onNavigateCourtManagement?: () => void;
  isSlotManagementActive?: boolean;
  onNavigateSlotManagement?: () => void;
  isSessionRulesActive?: boolean;
  onNavigateSessionRules?: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  studentName,
  admissionNumber,
  userRole,
  isRealAccount = false,
  isAuthenticated = false,
  onNavigateHome,
  onStartBooking,
  onOpenSecondaryNav,
  isWizardActive,
  currentAuthScreen = 'none',
  onNavigateLogin,
  onNavigateRegister,
  onNavigateVerifyEmail,
  onSignOut,
  isStaffCheckInActive = false,
  onNavigateStaffCheckIn,
  isCourtManagementActive = false,
  onNavigateCourtManagement,
  isSlotManagementActive = false,
  onNavigateSlotManagement,
  isSessionRulesActive = false,
  onNavigateSessionRules,
}) => {
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const { isStaffOrAdmin, isDashboardActive, isMoreMenuActive } = computeHeaderNavState({
    isWizardActive,
    currentAuthScreen,
    isStaffCheckInActive,
    isCourtManagementActive,
    isSlotManagementActive,
    isSessionRulesActive,
    userRole,
  });

  // Close menus on click outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target as Node)
      ) {
        setIsDropdownOpen(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsDropdownOpen(false);
        setIsMobileMenuOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, []);

  const closeAllMenus = () => {
    setIsDropdownOpen(false);
    setIsMobileMenuOpen(false);
  };

  const handleHomeClick = () => {
    closeAllMenus();
    onNavigateHome();
  };

  const handleStartBookingClick = () => {
    closeAllMenus();
    if (onStartBooking) {
      onStartBooking();
    } else {
      onNavigateHome();
    }
  };

  const handleSecondaryClick = (dest: string) => {
    closeAllMenus();
    onOpenSecondaryNav(dest);
  };

  const handleCourtManagementClick = () => {
    closeAllMenus();
    onNavigateCourtManagement?.();
  };

  const handleSlotManagementClick = () => {
    closeAllMenus();
    onNavigateSlotManagement?.();
  };

  const handleSessionRulesClick = () => {
    closeAllMenus();
    onNavigateSessionRules?.();
  };

  const handleStaffCheckInClick = () => {
    closeAllMenus();
    onNavigateStaffCheckIn?.();
  };

  const handleLoginClick = () => {
    closeAllMenus();
    onNavigateLogin?.();
  };

  const handleRegisterClick = () => {
    closeAllMenus();
    onNavigateRegister?.();
  };

  const handleVerifyEmailClick = () => {
    closeAllMenus();
    onNavigateVerifyEmail?.();
  };

  const handleSignOutClick = () => {
    closeAllMenus();
    onSignOut?.();
  };

  return (
    <header className="border-b border-slate-200/80 bg-white/95 backdrop-blur-xs sticky top-0 z-30">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-2 sm:px-6">
        {/* Left: Brand / Home */}
        <button
          type="button"
          onClick={handleHomeClick}
          className="flex items-center gap-2 text-left transition hover:opacity-90 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 rounded-lg p-0.5 cursor-pointer"
          aria-label="UniBadminton Home"
        >
          <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-[#064E3B] text-white font-extrabold text-[11px] shadow-2xs">
            UB
          </div>
          <div className="flex items-baseline gap-1.5">
            <span className="text-sm font-bold tracking-tight text-[#172033]">
              UniBadminton
            </span>
            <span className="hidden sm:inline text-[11px] text-[#64748B] font-medium">
              Court Reservation
            </span>
          </div>
        </button>

        {/* Center / Desktop Navigation */}
        <nav
          className="hidden md:flex items-center gap-1 text-xs font-medium text-slate-600"
          aria-label="Main Navigation"
        >
          {/* Dashboard */}
          <button
            type="button"
            onClick={handleHomeClick}
            className={`rounded-lg px-2.5 py-1.5 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer ${
              isDashboardActive
                ? 'bg-[#ECFDF5] font-semibold text-[#047857]'
                : 'hover:text-[#172033] hover:bg-slate-50'
            }`}
          >
            Dashboard
          </button>

          {/* Book a Court (Primary Action) */}
          <button
            type="button"
            onClick={handleStartBookingClick}
            className={`rounded-lg px-2.5 py-1.5 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer font-semibold ${
              isWizardActive
                ? 'bg-[#047857] text-white shadow-2xs'
                : 'text-[#047857] hover:bg-[#ECFDF5] hover:text-[#065F46]'
            }`}
          >
            Book a Court
          </button>

          {/* My QR Passes (for Authenticated Users) */}
          {isAuthenticated && (
            <button
              type="button"
              onClick={() => handleSecondaryClick('My QR Passes')}
              className="rounded-lg px-2.5 py-1.5 transition hover:text-slate-900 hover:bg-slate-50 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer"
            >
              My QR Passes
            </button>
          )}

          {/* "More" Dropdown Menu */}
          <div className="relative" ref={dropdownRef}>
            <button
              type="button"
              onClick={() => setIsDropdownOpen((prev) => !prev)}
              aria-haspopup="true"
              aria-expanded={isDropdownOpen}
              className={`flex items-center gap-1 rounded-lg px-2.5 py-1.5 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer ${
                isMoreMenuActive || isDropdownOpen
                  ? 'bg-slate-100 font-semibold text-slate-900'
                  : 'hover:text-slate-900 hover:bg-slate-50'
              }`}
            >
              <span>More</span>
              <svg
                className={`h-3.5 w-3.5 text-slate-400 transition-transform ${
                  isDropdownOpen ? 'rotate-180 text-slate-600' : ''
                }`}
                viewBox="0 0 20 20"
                fill="currentColor"
                aria-hidden="true"
              >
                <path
                  fillRule="evenodd"
                  d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z"
                  clipRule="evenodd"
                />
              </svg>
            </button>

            {/* Dropdown Floating Panel */}
            {isDropdownOpen && (
              <div
                className="absolute right-0 mt-1.5 w-52 rounded-xl border border-slate-200 bg-white p-1.5 shadow-lg ring-1 ring-black/5 z-40 animate-in fade-in slide-in-from-top-1 duration-150"
                role="menu"
                aria-orientation="vertical"
              >
                {/* Secondary destinations */}
                <div className="px-2 py-1 text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Explore
                </div>
                <button
                  type="button"
                  onClick={() => handleSecondaryClick('Equipment')}
                  className="w-full text-left rounded-lg px-2.5 py-1.5 text-xs text-slate-700 hover:bg-slate-50 hover:text-slate-900 transition flex items-center justify-between cursor-pointer"
                  role="menuitem"
                >
                  <span>Equipment</span>
                </button>
                <button
                  type="button"
                  onClick={() => handleSecondaryClick('Tournaments')}
                  className="w-full text-left rounded-lg px-2.5 py-1.5 text-xs text-slate-700 hover:bg-slate-50 hover:text-slate-900 transition flex items-center justify-between cursor-pointer"
                  role="menuitem"
                >
                  <span>Tournaments</span>
                </button>
                <button
                  type="button"
                  onClick={() => handleSecondaryClick('Rules & Queue')}
                  className="w-full text-left rounded-lg px-2.5 py-1.5 text-xs text-slate-700 hover:bg-slate-50 hover:text-slate-900 transition flex items-center justify-between cursor-pointer"
                  role="menuitem"
                >
                  <span>Rules & Queue</span>
                </button>

                {/* Staff & Admin Section */}
                {isStaffOrAdmin && (
                  <>
                    <div className="my-1 border-t border-slate-100" />
                    <div className="px-2 py-1 text-[10px] font-bold uppercase tracking-wider text-emerald-700">
                      Staff & Admin
                    </div>
                    {onNavigateCourtManagement && (
                      <button
                        type="button"
                        onClick={handleCourtManagementClick}
                        className={`w-full text-left rounded-lg px-2.5 py-1.5 text-xs transition flex items-center justify-between cursor-pointer ${
                          isCourtManagementActive
                            ? 'bg-slate-900 text-white font-semibold'
                            : 'text-slate-700 hover:bg-slate-50 hover:text-slate-900'
                        }`}
                        role="menuitem"
                      >
                        <span>Court Management</span>
                        <span className="text-[10px] font-medium text-slate-400">4 Courts</span>
                      </button>
                    )}
                    {onNavigateSlotManagement && (
                      <button
                        type="button"
                        onClick={handleSlotManagementClick}
                        className={`w-full text-left rounded-lg px-2.5 py-1.5 text-xs transition flex items-center justify-between cursor-pointer ${
                          isSlotManagementActive
                            ? 'bg-slate-900 text-white font-semibold'
                            : 'text-slate-700 hover:bg-slate-50 hover:text-slate-900'
                        }`}
                        role="menuitem"
                      >
                        <span>Time Slot Management</span>
                        <span className="text-[10px] font-medium text-slate-400">Schedule</span>
                      </button>
                    )}
                    {onNavigateSessionRules && (
                      <button
                        type="button"
                        onClick={handleSessionRulesClick}
                        className={`w-full text-left rounded-lg px-2.5 py-1.5 text-xs transition flex items-center justify-between cursor-pointer ${
                          isSessionRulesActive
                            ? 'bg-slate-900 text-white font-semibold'
                            : 'text-slate-700 hover:bg-slate-50 hover:text-slate-900'
                        }`}
                        role="menuitem"
                      >
                        <span>Session Rules</span>
                        <span className="text-[10px] font-medium text-slate-400">Rules & Timing</span>
                      </button>
                    )}
                    {onNavigateStaffCheckIn && (
                      <button
                        type="button"
                        onClick={handleStaffCheckInClick}
                        className={`w-full text-left rounded-lg px-2.5 py-1.5 text-xs transition flex items-center justify-between cursor-pointer ${
                          isStaffCheckInActive
                            ? 'bg-slate-900 text-white font-semibold'
                            : 'text-slate-700 hover:bg-slate-50 hover:text-slate-900'
                        }`}
                        role="menuitem"
                      >
                        <span>Staff Check-in</span>
                        <span className="text-[10px] font-medium text-slate-400">QR Scan</span>
                      </button>
                    )}
                  </>
                )}
              </div>
            )}
          </div>
        </nav>

        {/* Right: Auth Profile / Account Actions (Desktop) */}
        <div className="hidden md:flex items-center gap-2">
          {isAuthenticated ? (
            /* Authenticated Student Identity & Sign Out */
            <div className="flex items-center gap-2">
              <div className="text-right">
                <span className="text-xs font-semibold text-slate-900 block leading-tight">
                  {studentName}
                </span>
                <div className="flex items-center justify-end gap-1">
                  <span className="text-[10px] font-mono text-slate-500 leading-tight">
                    {admissionNumber}
                  </span>
                  {userRole && userRole !== 'STUDENT' && (
                    <span className="rounded bg-slate-900 px-1 py-0.2 text-[9px] font-bold text-white leading-tight">
                      {userRole}
                    </span>
                  )}
                  {isRealAccount && (
                    <span className="rounded bg-emerald-100 px-1 py-0.2 text-[9px] font-bold text-emerald-800 leading-tight">
                      Verified
                    </span>
                  )}
                </div>
              </div>
              <div
                className="flex h-7 w-7 items-center justify-center rounded-full bg-emerald-100 border border-emerald-300 text-[11px] font-bold text-emerald-800"
                aria-hidden="true"
              >
                {studentName ? studentName.charAt(0).toUpperCase() : 'U'}
              </div>
              {onSignOut && (
                <button
                  type="button"
                  onClick={handleSignOutClick}
                  title="Sign out of your account"
                  className="rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-600 hover:text-rose-700 hover:bg-rose-50 hover:border-rose-200 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-rose-500 cursor-pointer"
                >
                  Sign Out
                </button>
              )}
            </div>
          ) : (
            /* Unauthenticated Visitor Options: Single clean Sign In and Register */
            <div className="flex items-center gap-1.5">
              {currentAuthScreen === 'verify-email' && onNavigateVerifyEmail && (
                <button
                  type="button"
                  onClick={handleVerifyEmailClick}
                  className="rounded-lg border border-emerald-300 bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-800 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer"
                >
                  Verify Email
                </button>
              )}
              {onNavigateLogin && (
                <button
                  type="button"
                  onClick={handleLoginClick}
                  className={`inline-flex h-8 items-center justify-center rounded-lg px-3 text-xs font-semibold transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer ${
                    currentAuthScreen === 'login'
                      ? 'bg-emerald-800 text-white'
                      : 'bg-emerald-700 text-white hover:bg-emerald-800 shadow-2xs'
                  }`}
                >
                  Sign In
                </button>
              )}
              {onNavigateRegister && (
                <button
                  type="button"
                  onClick={handleRegisterClick}
                  className={`inline-flex h-8 items-center justify-center rounded-lg border px-3 text-xs font-semibold transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer ${
                    currentAuthScreen === 'register'
                      ? 'border-emerald-600 bg-emerald-50 text-emerald-900'
                      : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  Register
                </button>
              )}
            </div>
          )}
        </div>

        {/* Mobile: Hamburger Menu Toggle Button */}
        <div className="flex md:hidden items-center gap-1.5">
          {!isAuthenticated && onNavigateLogin && (
            <button
              type="button"
              onClick={handleLoginClick}
              className="inline-flex h-7 items-center justify-center rounded-lg bg-emerald-700 px-2.5 text-[11px] font-semibold text-white shadow-2xs hover:bg-emerald-800 transition cursor-pointer"
            >
              Sign In
            </button>
          )}

          {isAuthenticated && (
            <div
              className="flex h-6.5 w-6.5 items-center justify-center rounded-full bg-emerald-100 border border-emerald-300 text-[11px] font-bold text-emerald-800"
              title={`${studentName} (${admissionNumber})`}
            >
              {studentName ? studentName.charAt(0).toUpperCase() : 'U'}
            </div>
          )}

          <button
            type="button"
            onClick={() => setIsMobileMenuOpen((prev) => !prev)}
            aria-label={isMobileMenuOpen ? 'Close navigation menu' : 'Open navigation menu'}
            aria-expanded={isMobileMenuOpen}
            className="rounded-lg p-1.5 text-slate-700 hover:bg-slate-100 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-emerald-600 cursor-pointer"
          >
            {isMobileMenuOpen ? (
              <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            ) : (
              <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16M4 18h16" />
              </svg>
            )}
          </button>
        </div>
      </div>

      {/* Mobile Navigation Drawer / Panel */}
      {isMobileMenuOpen && (
        <div className="border-t border-slate-200 bg-white px-4 py-3 shadow-md md:hidden animate-in fade-in slide-in-from-top-1 duration-150">
          <div className="space-y-1">
            {/* Primary actions */}
            <div className="px-1 py-1 text-[10px] font-bold uppercase tracking-wider text-slate-400">
              Menu
            </div>
            <button
              type="button"
              onClick={handleHomeClick}
              className={`w-full text-left rounded-lg px-3 py-2 text-xs font-medium transition cursor-pointer ${
                isDashboardActive
                  ? 'bg-slate-100 text-slate-900 font-bold'
                  : 'text-slate-700 hover:bg-slate-50'
              }`}
            >
              Dashboard
            </button>
            <button
              type="button"
              onClick={handleStartBookingClick}
              className={`w-full text-left rounded-lg px-3 py-2 text-xs font-semibold transition cursor-pointer ${
                isWizardActive
                  ? 'bg-emerald-700 text-white font-bold'
                  : 'text-emerald-700 hover:bg-emerald-50'
              }`}
            >
              Book a Court
            </button>
            {isAuthenticated && (
              <button
                type="button"
                onClick={() => handleSecondaryClick('My QR Passes')}
                className="w-full text-left rounded-lg px-3 py-2 text-xs text-slate-700 hover:bg-slate-50 transition cursor-pointer"
              >
                My QR Passes
              </button>
            )}

            {/* Secondary destinations */}
            <div className="pt-2 px-1 py-1 text-[10px] font-bold uppercase tracking-wider text-slate-400 border-t border-slate-100">
              Explore
            </div>
            <button
              type="button"
              onClick={() => handleSecondaryClick('Equipment')}
              className="w-full text-left rounded-lg px-3 py-1.5 text-xs text-slate-700 hover:bg-slate-50 transition cursor-pointer"
            >
              Equipment
            </button>
            <button
              type="button"
              onClick={() => handleSecondaryClick('Tournaments')}
              className="w-full text-left rounded-lg px-3 py-1.5 text-xs text-slate-700 hover:bg-slate-50 transition cursor-pointer"
            >
              Tournaments
            </button>
            <button
              type="button"
              onClick={() => handleSecondaryClick('Rules & Queue')}
              className="w-full text-left rounded-lg px-3 py-1.5 text-xs text-slate-700 hover:bg-slate-50 transition cursor-pointer"
            >
              Rules & Queue
            </button>

            {/* Staff & Admin Section on Mobile */}
            {isStaffOrAdmin && (
              <>
                <div className="pt-2 px-1 py-1 text-[10px] font-bold uppercase tracking-wider text-emerald-700 border-t border-slate-100">
                  Staff & Admin
                </div>
                {onNavigateCourtManagement && (
                  <button
                    type="button"
                    onClick={handleCourtManagementClick}
                    className={`w-full text-left rounded-lg px-3 py-1.5 text-xs transition cursor-pointer ${
                      isCourtManagementActive
                        ? 'bg-slate-900 text-white font-semibold'
                        : 'text-slate-700 hover:bg-slate-50'
                    }`}
                  >
                    Court Management
                  </button>
                )}
                {onNavigateSlotManagement && (
                  <button
                    type="button"
                    onClick={handleSlotManagementClick}
                    className={`w-full text-left rounded-lg px-3 py-1.5 text-xs transition cursor-pointer ${
                      isSlotManagementActive
                        ? 'bg-slate-900 text-white font-semibold'
                        : 'text-slate-700 hover:bg-slate-50'
                    }`}
                  >
                    Time Slot Management
                  </button>
                )}
                {onNavigateSessionRules && (
                  <button
                    type="button"
                    onClick={handleSessionRulesClick}
                    className={`w-full text-left rounded-lg px-3 py-1.5 text-xs transition cursor-pointer ${
                      isSessionRulesActive
                        ? 'bg-slate-900 text-white font-semibold'
                        : 'text-slate-700 hover:bg-slate-50'
                    }`}
                  >
                    Session Rules
                  </button>
                )}
                {onNavigateStaffCheckIn && (
                  <button
                    type="button"
                    onClick={handleStaffCheckInClick}
                    className={`w-full text-left rounded-lg px-3 py-1.5 text-xs transition cursor-pointer ${
                      isStaffCheckInActive
                        ? 'bg-slate-900 text-white font-semibold'
                        : 'text-slate-700 hover:bg-slate-50'
                    }`}
                  >
                    Staff Check-in
                  </button>
                )}
              </>
            )}

            {/* Auth / Account on Mobile */}
            <div className="pt-2 border-t border-slate-100">
              {isAuthenticated ? (
                <div className="rounded-lg bg-slate-50 p-2.5">
                  <div className="flex items-center justify-between mb-2">
                    <div>
                      <span className="text-xs font-semibold text-slate-900 block">
                        {studentName}
                      </span>
                      <span className="text-[10px] font-mono text-slate-500">
                        {admissionNumber}
                      </span>
                    </div>
                    {userRole && userRole !== 'STUDENT' && (
                      <span className="rounded bg-slate-900 px-1.5 py-0.5 text-[9px] font-bold text-white">
                        {userRole}
                      </span>
                    )}
                  </div>
                  {onSignOut && (
                    <button
                      type="button"
                      onClick={handleSignOutClick}
                      className="w-full rounded-lg border border-slate-300 bg-white py-1.5 text-xs font-semibold text-rose-700 hover:bg-rose-50 transition cursor-pointer text-center"
                    >
                      Sign Out
                    </button>
                  )}
                </div>
              ) : (
                <div className="flex flex-col gap-1.5 pt-1">
                  {currentAuthScreen === 'verify-email' && onNavigateVerifyEmail && (
                    <button
                      type="button"
                      onClick={handleVerifyEmailClick}
                      className="w-full rounded-lg border border-emerald-300 bg-emerald-50 py-1.5 text-xs font-semibold text-emerald-800 transition cursor-pointer text-center"
                    >
                      Verify Email
                    </button>
                  )}
                  {onNavigateLogin && (
                    <button
                      type="button"
                      onClick={handleLoginClick}
                      className="w-full rounded-lg bg-emerald-700 py-1.5 text-xs font-semibold text-white shadow-2xs hover:bg-emerald-800 transition cursor-pointer text-center"
                    >
                      Sign In
                    </button>
                  )}
                  {onNavigateRegister && (
                    <button
                      type="button"
                      onClick={handleRegisterClick}
                      className="w-full rounded-lg border border-slate-300 bg-white py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition cursor-pointer text-center"
                    >
                      Register
                    </button>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </header>
  );
};
