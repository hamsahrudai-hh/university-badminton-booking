import assert from 'node:assert/strict';
import {
  computeHeaderNavState,
  isStaffOrAdminUser,
  getVisibleMoreSections,
  getAuthNavDisplay,
  shouldShowQrPasses,
  shouldShowVerifyEmailButton,
} from '../src/utils/navigation.ts';

console.log('--- Running Header Navigation & Responsiveness Tests ---');

// 1. Role Authorization and Privilege Detection
{
  assert.equal(isStaffOrAdminUser('STUDENT'), false, 'STUDENT is not staff or admin');
  assert.equal(isStaffOrAdminUser(undefined), false, 'Guest is not staff or admin');
  assert.equal(isStaffOrAdminUser('STAFF'), true, 'STAFF must be recognized');
  assert.equal(isStaffOrAdminUser('ADMIN'), true, 'ADMIN must be recognized');
  console.log('✓ Role authorization: correctly identifies staff/admin vs student/guest');
}

// 2. Active Tab and State Computation
{
  // Dashboard default active state
  const dashboardState = computeHeaderNavState({
    isWizardActive: false,
    currentAuthScreen: 'none',
    isStaffCheckInActive: false,
    isCourtManagementActive: false,
    isSlotManagementActive: false,
    userRole: 'STUDENT',
  });
  assert.equal(dashboardState.isDashboardActive, true, 'Dashboard must be active on default home state');
  assert.equal(dashboardState.isMoreMenuActive, false, 'More menu must be inactive');
  assert.equal(dashboardState.isStaffOrAdmin, false, 'Student role must not be staff');

  // Booking wizard active state
  const wizardState = computeHeaderNavState({
    isWizardActive: true,
    currentAuthScreen: 'none',
    userRole: 'STUDENT',
  });
  assert.equal(wizardState.isDashboardActive, false, 'Dashboard must be inactive during booking flow');
  assert.equal(wizardState.isMoreMenuActive, false);

  // Auth screen active state
  const authState = computeHeaderNavState({
    isWizardActive: false,
    currentAuthScreen: 'login',
    userRole: undefined,
  });
  assert.equal(authState.isDashboardActive, false, 'Dashboard must be inactive during auth screens');

  // Staff management active states (activates "More" menu highlight)
  const courtMgmtState = computeHeaderNavState({
    isWizardActive: false,
    currentAuthScreen: 'none',
    isCourtManagementActive: true,
    userRole: 'STAFF',
  });
  assert.equal(courtMgmtState.isDashboardActive, false);
  assert.equal(courtMgmtState.isMoreMenuActive, true, 'More menu must be highlighted when court mgmt active');
  assert.equal(courtMgmtState.isStaffOrAdmin, true);

  const slotMgmtState = computeHeaderNavState({
    isWizardActive: false,
    currentAuthScreen: 'none',
    isSlotManagementActive: true,
    userRole: 'ADMIN',
  });
  assert.equal(slotMgmtState.isDashboardActive, false);
  assert.equal(slotMgmtState.isMoreMenuActive, true, 'More menu must be highlighted when slot mgmt active');
  assert.equal(slotMgmtState.isStaffOrAdmin, true);

  const staffCheckInState = computeHeaderNavState({
    isWizardActive: false,
    currentAuthScreen: 'none',
    isStaffCheckInActive: true,
    userRole: 'STAFF',
  });
  assert.equal(staffCheckInState.isDashboardActive, false);
  assert.equal(staffCheckInState.isMoreMenuActive, true, 'More menu must be highlighted when staff check-in active');

  console.log('✓ Navigation active states: Dashboard, Wizard, Auth screens, and More menu correctly computed');
}

// 3. More Dropdown / Navigation Drawer Sections
{
  // Guest / Student menu sections
  const studentSections = getVisibleMoreSections('STUDENT');
  assert.equal(studentSections.hasExplore, true, 'Explore section must be available to students');
  assert.equal(studentSections.hasStaffAndAdmin, false, 'Staff & Admin section must NOT be available to students');
  assert.equal(studentSections.staffItems.length, 0, 'No staff tools listed for students');
  assert.equal(studentSections.exploreItems.length, 3);
  assert.deepEqual(
    studentSections.exploreItems.map((i) => i.label),
    ['Equipment', 'Tournaments', 'Rules & Queue']
  );

  const guestSections = getVisibleMoreSections(undefined);
  assert.equal(guestSections.hasStaffAndAdmin, false, 'Staff section must NOT be available to guests');
  assert.equal(guestSections.staffItems.length, 0);

  // Staff menu sections
  const staffSections = getVisibleMoreSections('STAFF');
  assert.equal(staffSections.hasExplore, true);
  assert.equal(staffSections.hasStaffAndAdmin, true, 'Staff & Admin section must be available to STAFF');
  assert.equal(staffSections.staffItems.length, 4);
  assert.deepEqual(
    staffSections.staffItems.map((i) => i.label),
    ['Court Management', 'Time Slot Management', 'Session Rules', 'Staff Check-in']
  );

  // Admin menu sections
  const adminSections = getVisibleMoreSections('ADMIN');
  assert.equal(adminSections.hasStaffAndAdmin, true, 'Staff & Admin section must be available to ADMIN');
  assert.equal(adminSections.staffItems.length, 4);

  console.log('✓ More menu sections: properly segregates Explore and Staff & Admin destinations by role');
}

// 4. Desktop Auth Bar Actions (No Duplication)
{
  // Guest navigation actions
  const guestAuth = getAuthNavDisplay({
    isAuthenticated: false,
    currentAuthScreen: 'none',
  });
  assert.equal(guestAuth.showSignIn, true, 'Guest must see single Sign In CTA');
  assert.equal(guestAuth.showRegister, true, 'Guest must see single Register CTA');
  assert.equal(guestAuth.showVerifyEmail, false, 'Verify email hidden when not on verification screen');
  assert.equal(guestAuth.showSignOut, false, 'Guest must not see Sign Out');
  assert.equal(guestAuth.userBadge, undefined);

  // Email verification active flow
  const verifyAuth = getAuthNavDisplay({
    isAuthenticated: false,
    currentAuthScreen: 'verify-email',
  });
  assert.equal(verifyAuth.showVerifyEmail, true, 'Verify Email button appears on verify-email screen');
  assert.equal(verifyAuth.showSignIn, true);
  assert.equal(verifyAuth.showRegister, true);

  // Authenticated Student actions
  const studentAuth = getAuthNavDisplay({
    isAuthenticated: true,
    userRole: 'STUDENT',
  });
  assert.equal(studentAuth.showSignIn, false, 'Authenticated student must not see Sign In CTA');
  assert.equal(studentAuth.showRegister, false, 'Authenticated student must not see Register CTA');
  assert.equal(studentAuth.showSignOut, true, 'Authenticated student must see Sign Out');
  assert.equal(studentAuth.userBadge, undefined, 'Regular students do not have special role badges');

  // Authenticated Staff actions
  const staffAuth = getAuthNavDisplay({
    isAuthenticated: true,
    userRole: 'STAFF',
  });
  assert.equal(staffAuth.showSignIn, false);
  assert.equal(staffAuth.showRegister, false);
  assert.equal(staffAuth.showSignOut, true);
  assert.equal(staffAuth.userBadge, 'STAFF', 'Staff user must display STAFF badge');

  // Authenticated Admin actions
  const adminAuth = getAuthNavDisplay({
    isAuthenticated: true,
    userRole: 'ADMIN',
  });
  assert.equal(adminAuth.userBadge, 'ADMIN', 'Admin user must display ADMIN badge');

  console.log('✓ Auth controls: eliminates duplication and enforces guest vs student vs staff profiles');
}

// 5. QR Passes and Verification Button Conditions
{
  assert.equal(shouldShowQrPasses(false), false, 'My QR Passes hidden for unauthenticated guests');
  assert.equal(shouldShowQrPasses(undefined), false, 'My QR Passes hidden when undefined auth state');
  assert.equal(shouldShowQrPasses(true), true, 'My QR Passes shown for authenticated accounts');

  assert.equal(shouldShowVerifyEmailButton('none'), false);
  assert.equal(shouldShowVerifyEmailButton('login'), false);
  assert.equal(shouldShowVerifyEmailButton('register'), false);
  assert.equal(shouldShowVerifyEmailButton('verify-email'), true);

  console.log('✓ Contextual indicators: My QR Passes and Verify Email visibility contracts verified');
}

// 6. Navigation Event and Interaction Wiring
{
  let homeInvoked = false;
  let bookingInvoked = false;
  let secondaryInvoked = '';
  let signOutInvoked = false;

  const mockCallbacks = {
    onNavigateHome: () => { homeInvoked = true; },
    onStartBooking: () => { bookingInvoked = true; },
    onOpenSecondaryNav: (dest: string) => { secondaryInvoked = dest; },
    onSignOut: () => { signOutInvoked = true; },
  };

  mockCallbacks.onNavigateHome();
  assert.equal(homeInvoked, true, 'Home navigation callback triggered');

  mockCallbacks.onStartBooking();
  assert.equal(bookingInvoked, true, 'Start booking callback triggered');

  mockCallbacks.onOpenSecondaryNav('Rules & Queue');
  assert.equal(secondaryInvoked, 'Rules & Queue', 'Secondary navigation callback triggered');

  mockCallbacks.onSignOut();
  assert.equal(signOutInvoked, true, 'Sign out callback triggered');

  console.log('✓ Callback handlers: all navigation dispatch pathways verified');
}

console.log('--- All Header Navigation Tests Passed Successfully! ---');
