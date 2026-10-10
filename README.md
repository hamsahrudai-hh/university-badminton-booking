# University Badminton Court Booking Website

## 1. Project Overview & Goals
The **University Badminton Court Booking Website** is a browser-based platform designed for students, staff, and administrators to facilitate fair, transparent, and conflict-free booking of university badminton courts. 

This system is built specifically as a responsive web application (not a mobile app) to streamline court access, manage peak-hour booking demand via fair queueing, enforce credit policies, and track on-court attendance using secure QR check-ins.

---

## 2. User Roles & Permissions
The platform defines three distinct roles with role-based access control (RBAC):

- **Student**:
  - Authenticate using university email and unique admission number.
  - Join booking queues during scheduled queue opening windows.
  - Book 30-minute court slots for registered groups of 2 to 4 students within a 60-second window.
  - Manage and track personal weekly booking credits.
  - View individual, secure check-in QR codes for confirmed bookings.
  - Submit equipment requests and view sports profile/scorecard data.

- **Staff**:
  - Scan and validate individual player QR codes at the sports facility.
  - Verify player identity, booking details, court, and time slot.
  - Track individual attendance per player and prevent duplicate or invalid check-ins.
  - Manage equipment handovers and check-ins.

- **Administrator**:
  - Manage courts and define operating schedules.
  - Configure queue opening and booking start times.
  - Manage booking rules and user accounts.
  - Schedule tournaments and block courts for official events.
  - Oversee equipment inventory, track attendance reports, and audit logs.

---

## 3. Core Features & Business Rules

### Courts and Schedule Rules
- **Four Badminton Courts**: Courts 1 through 4.
- **30-Minute Time Slots**:
  - **Morning Sessions**: 6:00 AM – 8:30 AM (5 slots).
  - **Evening Sessions**: 3:30 PM – 7:00 PM (7 slots).
- **Court Capacity**: 4 player spots per court per 30-minute slot.
- **Conflict Prevention**: Overlapping bookings and double-booking of players or spots are strictly prevented.

### Queue & Atomic Booking Engine
- **Queue Timing**:
  - Morning queue opens at 9:00 PM on the previous day; booking starts at 9:05 PM.
  - Evening queue uses a separately configurable queue opening and start time.
- **Fairness & Ordering**: Queue order is timestamp-first, incorporating credit-based priority rules.
- **60-Second Booking Window**: Each user admitted from the queue receives a 60-second opportunity to complete their booking.
- **Atomic Booking**: Spot and court allocation are handled atomically to prevent race conditions and double-claims.
- **Group Bookings**:
  - Groups consist of 2 to 4 registered students, including the booking user.
  - Every player must be registered and eligible before the booking can be confirmed.
  - A student cannot be booked into overlapping or conflicting slots.

### Credits & Attendance Lifecycle
- **Weekly Credits**: Each student starts every week with 6 credits.
- **Credit Deductions**: 1 credit is deducted per confirmed booking according to agreed booking-credit rules.
- **Zero Credits Policy**: When credits reach zero, the student's queue priority is reduced rather than their account being disabled.
- **Weekly Credit Reset**: Credits are automatically reset on a weekly schedule.
- **10-Minute No-Show Rule**:
  - A no-show occurs if a player's QR code is not validly scanned within 10 minutes after the slot start time.
  - If a player is marked as a no-show, their remaining credits for the rest of the week are set to zero.
  - Attendance is tracked separately for each individual player in a group.

### Security & QR Check-in
- **Authentication**: University email validation, unique admission number, secure password handling, and JWT-based session security.
- **Data Protection**: Passwords, auth tokens, and sensitive student information are never exposed in URLs or QR codes.
- **Secure QR Codes**: Unpredictable, cryptographically secure booking tokens generated individually for each booked player.
- **Staff Verification**: Attendance check-ins record scan timestamp and staff identifier while preventing duplicate check-ins.

### Additional Features
- **Tournaments**: Admin-scheduled tournaments that automatically block courts from general bookings.
- **Equipment Requests**: Request and tracking system for racquets and shuttlecocks.
- **Event-Driven Notifications**: Asynchronous alerts for queue turns, booking confirmations, and attendance status.
- **Sports Profile & Scorecards**: Pluggable adapter interface for external university sports API integration.
- **No Monetary Transactions**: Zero payment processing or monetary fees.

---

## 4. Technology Stack
- **Frontend**: React, Vite, TypeScript, Tailwind CSS
- **Backend**: Java and Spring Boot (Spring Security, Spring Data JPA, Bean Validation)
- **Database**: MySQL
- **Security**: Spring Security & JWT (JSON Web Tokens)
- **Messaging**: RabbitMQ (asynchronous events & notifications)
- **Deployment**: Docker & Docker Compose
- **Version Control**: Git & GitHub
- **API Documentation**: OpenAPI / Swagger

---

## 5. Development Roadmap
- **Phase 1**: Project Initialization & Environment Setup (Repository, backend & frontend skeletons, Docker configuration)
- **Phase 2**: Domain Modeling & Database Schema (Entities for courts, slots, bookings, players, credits, queue, equipment, tournaments)
- **Phase 3**: Authentication, Authorization & Security (JWT, university email & admission number validation, RBAC)
- **Phase 4**: Queue Management & Atomic Booking Engine (Timestamp queue strategy, 60s timer, atomic spot locking, credit management)
- **Phase 5**: QR Check-in & Attendance Lifecycle (Token generation, staff scanner endpoints, 10-min no-show enforcement)
- **Phase 6**: Supporting Modules & Event Architecture (RabbitMQ event handlers, tournament court blocking, equipment requests, sports profile adapter)
- **Phase 7**: Frontend User Interface & End-to-End Testing (Interactive court grid, queue flow, player/staff/admin dashboards)

---

## 6. Development Setup Instructions
> **Note:** Detailed local development setup instructions (including prerequisites, environment variables, Docker Compose execution, and database migration steps) will be added here as the application modules are implemented.
