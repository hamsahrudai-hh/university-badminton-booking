# University Badminton Court Booking Website

## Project Goal
Build a browser-based university badminton court booking website for students, staff, and administrators. This is a website, not a mobile app.

## Technology
- Frontend: React, Vite, TypeScript, Tailwind CSS
- Backend: Java and Spring Boot
- Database: MySQL
- Security: Spring Security and JWT
- Messaging: RabbitMQ for asynchronous events
- Deployment: Docker and Docker Compose
- Version control: Git and GitHub
- API documentation: OpenAPI/Swagger

## Court and Schedule Rules
- The university has four badminton courts.
- Each booking slot lasts 30 minutes.
- Morning sessions run from 6:00 AM to 8:30 AM.
- Evening sessions run from 3:30 PM to 7:00 PM.
- Show available time slots, courts, and four player spots per court.
- Prevent overlapping bookings and double-booking of players or spots.

## Queue and Booking Rules
- Morning booking queue opens at 9:00 PM on the previous day.
- Morning booking actually starts at 9:05 PM.
- Evening booking uses a separately configurable queue opening time.
- Queue order is timestamp-first, with any additional fairness rules documented and tested.
- Each user's booking opportunity lasts 60 seconds.
- A booking must be secured atomically so two users cannot claim the same spot.
- Groups contain two to four registered students, including the booking user.
- Every player must be registered and eligible before a group booking is confirmed.
- A student may book spots for registered group members.
- Do not allow a student to be booked into conflicting time slots.

## Credits and Attendance
- Each student starts each week with six credits.
- Deduct one credit per booking according to the agreed booking-credit rules.
- When credits reach zero, the student's queue priority is reduced, not automatically disabled.
- If a player is a no-show, set that player's remaining credits to zero for the rest of the week.
- A no-show occurs if that player's QR code has not been validly scanned within 10 minutes after the slot starts.
- Reset credits weekly.
- Track attendance separately for each player in a group.

## Authentication and Security
- Students authenticate using their university email and unique admission number.
- Validate university email eligibility and use email verification where feasible.
- Use secure password handling and role-based access control.
- Never expose passwords, authentication tokens, or sensitive student data in URLs or QR codes.
- QR codes must use secure, unpredictable booking tokens.
- Only authorized staff can verify attendance.
- Never commit secrets or real student data to Git.

## QR Check-in
- Generate an individual QR code for each booked player.
- Staff scan the QR code to verify the correct player, booking, court, and time.
- Prevent duplicate or invalid check-ins.
- Record check-in time and staff identity.

## Additional Features
- Sports profile and scorecard integration, using an adapter if an external university API is available.
- Tournaments that administrators can schedule and use to block courts.
- Equipment requests.
- Notifications for relevant booking and attendance events.
- Separate student, staff, and administrator permissions.
- Administrative management of courts, schedules, queue times, booking rules, users, tournaments, equipment, and reports.
- No payments or monetary transactions.

## Architecture and Design
- Keep frontend, backend, and infrastructure responsibilities separate.
- Use clear service boundaries and layered backend code: Controller, Service, Repository.
- Design for future separation into focused services without creating unnecessary microservices too early.
- Use design patterns only when they solve a real problem.
- Consider Strategy for queue policies, State for booking lifecycle, Observer/event-driven processing for notifications, Adapter for scorecard integration, and Repository for data access.
- Keep booking confirmation and spot allocation transaction-safe.
- Document any decision that changes an agreed requirement.

## Development Rules
1. Inspect existing files and documentation before changing code.
2. Explain the plan before making large changes.
3. Make small, reviewable changes.
4. Do not modify unrelated files.
5. Write tests for critical booking and security rules.
6. Run relevant tests and report the actual results.
7. Do not claim a feature works unless it has been tested.
8. Ask for clarification if requirements conflict.
9. Never add a framework, service, or dependency without justification.
10. Keep setup instructions beginner-friendly.
