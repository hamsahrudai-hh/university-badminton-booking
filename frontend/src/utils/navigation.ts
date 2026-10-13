/**
 * Navigation utility functions and role-aware navigation definitions.
 * Provides deterministic state calculations for header, navigation drawer, and menus.
 */

export interface HeaderNavState {
  isStaffOrAdmin: boolean;
  isDashboardActive: boolean;
  isMoreMenuActive: boolean;
}

export interface NavDestinationItem {
  id: string;
  label: string;
  sublabel?: string;
  roleRequired?: 'STAFF' | 'ADMIN';
}

export interface MoreMenuSections {
  hasExplore: boolean;
  hasStaffAndAdmin: boolean;
  exploreItems: NavDestinationItem[];
  staffItems: NavDestinationItem[];
}

export interface AuthNavDisplay {
  showSignIn: boolean;
  showRegister: boolean;
  showVerifyEmail: boolean;
  showSignOut: boolean;
  userBadge?: string;
}

/**
 * Validates whether a user role possesses Staff or Admin privileges.
 */
export function isStaffOrAdminUser(role?: string): boolean {
  return role === 'STAFF' || role === 'ADMIN';
}

/**
 * Computes active highlight states for top-level navigation tabs.
 */
export function computeHeaderNavState(params: {
  isWizardActive: boolean;
  currentAuthScreen: 'none' | 'login' | 'register' | 'verify-email';
  isStaffCheckInActive?: boolean;
  isCourtManagementActive?: boolean;
  isSlotManagementActive?: boolean;
  isSessionRulesActive?: boolean;
  userRole?: 'STUDENT' | 'STAFF' | 'ADMIN';
}): HeaderNavState {
  const isStaffOrAdmin = isStaffOrAdminUser(params.userRole);
  const isDashboardActive =
    !params.isWizardActive &&
    params.currentAuthScreen === 'none' &&
    !params.isStaffCheckInActive &&
    !params.isCourtManagementActive &&
    !params.isSlotManagementActive &&
    !params.isSessionRulesActive;

  const isMoreMenuActive =
    Boolean(params.isStaffCheckInActive) ||
    Boolean(params.isCourtManagementActive) ||
    Boolean(params.isSlotManagementActive) ||
    Boolean(params.isSessionRulesActive);

  return {
    isStaffOrAdmin,
    isDashboardActive,
    isMoreMenuActive,
  };
}

/**
 * Determines whether email verification CTA should be shown.
 * Only visible when user is specifically on the verify-email authentication step.
 */
export function shouldShowVerifyEmailButton(currentAuthScreen?: string): boolean {
  return currentAuthScreen === 'verify-email';
}

/**
 * Determines whether My QR Passes is available.
 * Only visible to authenticated students and staff.
 */
export function shouldShowQrPasses(isAuthenticated?: boolean): boolean {
  return Boolean(isAuthenticated);
}

/**
 * Standard public/student explore destinations.
 */
export function getExploreDestinations(): NavDestinationItem[] {
  return [
    { id: 'equipment', label: 'Equipment' },
    { id: 'tournaments', label: 'Tournaments' },
    { id: 'rules-queue', label: 'Rules & Queue' },
  ];
}

/**
 * Privileged Staff and Admin management destinations.
 */
export function getStaffDestinations(): NavDestinationItem[] {
  return [
    { id: 'courts', label: 'Court Management', sublabel: '4 Courts', roleRequired: 'STAFF' },
    { id: 'slots', label: 'Time Slot Management', sublabel: 'Schedule', roleRequired: 'STAFF' },
    { id: 'sessions', label: 'Session Rules', sublabel: 'Rules & Timing', roleRequired: 'STAFF' },
    { id: 'staff-checkin', label: 'Staff Check-in', sublabel: 'QR Scan', roleRequired: 'STAFF' },
  ];
}

/**
 * Resolves visible sections in the More dropdown or drawer based on role authorization.
 */
export function getVisibleMoreSections(userRole?: string): MoreMenuSections {
  const isStaff = isStaffOrAdminUser(userRole);
  return {
    hasExplore: true,
    hasStaffAndAdmin: isStaff,
    exploreItems: getExploreDestinations(),
    staffItems: isStaff ? getStaffDestinations() : [],
  };
}

/**
 * Resolves desktop authentication action buttons to eliminate UI duplication.
 */
export function getAuthNavDisplay(params: {
  isAuthenticated?: boolean;
  userRole?: 'STUDENT' | 'STAFF' | 'ADMIN';
  currentAuthScreen?: 'none' | 'login' | 'register' | 'verify-email';
}): AuthNavDisplay {
  if (params.isAuthenticated) {
    return {
      showSignIn: false,
      showRegister: false,
      showVerifyEmail: false,
      showSignOut: true,
      userBadge: params.userRole && params.userRole !== 'STUDENT' ? params.userRole : undefined,
    };
  }

  return {
    showSignIn: true,
    showRegister: true,
    showVerifyEmail: params.currentAuthScreen === 'verify-email',
    showSignOut: false,
  };
}
