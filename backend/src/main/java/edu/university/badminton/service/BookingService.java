package edu.university.badminton.service;

import edu.university.badminton.dto.BookingPlayerAssignmentDto;
import edu.university.badminton.dto.BookingResponseDto;
import edu.university.badminton.dto.CreateBookingRequestDto;
import edu.university.badminton.model.Booking;
import edu.university.badminton.model.BookingPlayer;
import edu.university.badminton.model.BookingQueue;
import edu.university.badminton.model.BookingStatus;
import edu.university.badminton.model.Court;
import edu.university.badminton.model.QueueEntry;
import edu.university.badminton.model.QueueEntryStatus;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.model.SlotStatus;
import edu.university.badminton.model.Student;
import edu.university.badminton.model.TimeSlot;
import edu.university.badminton.repository.BookingPlayerRepository;
import edu.university.badminton.repository.BookingQueueRepository;
import edu.university.badminton.repository.BookingRepository;
import edu.university.badminton.repository.CourtRepository;
import edu.university.badminton.repository.QueueEntryRepository;
import edu.university.badminton.repository.StudentRepository;
import edu.university.badminton.repository.TimeSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service managing the atomic court booking transaction, group credit deduction,
 * and queue opportunity completion.
 */
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingPlayerRepository bookingPlayerRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final StudentRepository studentRepository;
    private final BookingQueueRepository bookingQueueRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final CourtRepository courtRepository;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository,
                          BookingPlayerRepository bookingPlayerRepository,
                          TimeSlotRepository timeSlotRepository,
                          StudentRepository studentRepository,
                          BookingQueueRepository bookingQueueRepository,
                          QueueEntryRepository queueEntryRepository,
                          CourtRepository courtRepository,
                          Clock clock) {
        this.bookingRepository = bookingRepository;
        this.bookingPlayerRepository = bookingPlayerRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.studentRepository = studentRepository;
        this.bookingQueueRepository = bookingQueueRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.courtRepository = courtRepository;
        this.clock = clock;
    }

    /**
     * Atomically executes the group booking transaction:
     * 1. Validates group composition (2–4 distinct registered students, including booking student).
     * 2. Checks idempotency token to prevent double-charging on retries.
     * 3. Locks target time slot and verifies availability.
     * 4. Verifies the booking student holds an active ADMITTED queue opportunity.
     * 5. Locks all group members in ID order (preventing lock deadlocks) and validates active status and weekly credits >= 1.
     * 6. Validates absence of conflicting overlapping bookings for any group member.
     * 7. Deducts exactly 1 credit from every group member.
     * 8. Reserves slot, creates booking and individual player passes with secure QR tokens.
     * 9. Transitions the admitted queue entry to COMPLETED.
     *
     * @param request the group booking request
     * @return confirmed booking response representation
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponseDto confirmBooking(CreateBookingRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Booking request cannot be null.");
        }
        if (request.bookingStudentId() == null) {
            throw new IllegalArgumentException("The 'bookingStudentId' field is required.");
        }
        if (request.timeSlotId() == null) {
            throw new IllegalArgumentException("The 'timeSlotId' field is required.");
        }
        if (request.players() == null || request.players().size() < 2 || request.players().size() > 4) {
            throw new IllegalArgumentException("A booking group must contain between 2 and 4 registered students.");
        }

        // Idempotency check: if an idempotency key was supplied and already exists, return existing booking
        String idempotencyKey = request.idempotencyKey() != null && !request.idempotencyKey().isBlank()
                ? request.idempotencyKey().trim()
                : null;
        if (idempotencyKey != null) {
            Optional<Booking> existingBooking = bookingRepository.findByIdempotencyKey(idempotencyKey);
            if (existingBooking.isPresent()) {
                return BookingResponseDto.fromEntity(existingBooking.get());
            }
        }

        // Validate player list: distinct student IDs and distinct spot numbers
        List<BookingPlayerAssignmentDto> playerAssignments = request.players();
        Set<Long> uniqueStudentIds = new HashSet<>();
        Set<Integer> uniqueSpots = new HashSet<>();

        for (BookingPlayerAssignmentDto assignment : playerAssignments) {
            if (assignment.studentId() == null) {
                throw new IllegalArgumentException("Player student ID cannot be null.");
            }
            if (!uniqueStudentIds.add(assignment.studentId())) {
                throw new IllegalArgumentException("Duplicate student in group: student ID " + assignment.studentId() + " appears multiple times.");
            }
            if (assignment.spotNumber() != null) {
                if (assignment.spotNumber() < 1 || assignment.spotNumber() > 4) {
                    throw new IllegalArgumentException("Spot number must be between 1 and 4, got: " + assignment.spotNumber());
                }
                if (!uniqueSpots.add(assignment.spotNumber())) {
                    throw new IllegalArgumentException("Duplicate spot number in group: spot " + assignment.spotNumber() + " is assigned multiple times.");
                }
            }
        }

        // The booking student must be in the group
        if (!uniqueStudentIds.contains(request.bookingStudentId())) {
            throw new IllegalArgumentException("The booking student (ID " + request.bookingStudentId() + ") must be included in the player group.");
        }

        // Auto-assign spot numbers if not provided
        int currentSpot = 1;
        List<BookingPlayerAssignmentDto> normalizedAssignments = new ArrayList<>();
        for (BookingPlayerAssignmentDto assignment : playerAssignments) {
            int spot = assignment.spotNumber() != null ? assignment.spotNumber() : currentSpot++;
            normalizedAssignments.add(new BookingPlayerAssignmentDto(assignment.studentId(), spot));
        }

        // Lock and validate the target TimeSlot
        TimeSlot timeSlot = timeSlotRepository.findByIdWithLock(request.timeSlotId())
                .orElseThrow(() -> new NoSuchElementException("Time slot not found with ID: " + request.timeSlotId()));

        if (timeSlot.getStatus() != SlotStatus.AVAILABLE) {
            throw new IllegalStateException("Time slot " + request.timeSlotId() + " is not available for booking (status: " + timeSlot.getStatus() + ").");
        }

        // Lock and validate that associated Court is active
        Court court = courtRepository.findByIdWithLock(timeSlot.getCourt().getId())
                .orElseThrow(() -> new IllegalStateException("Associated court not found."));

        if (!Boolean.TRUE.equals(court.getActive())) {
            throw new IllegalStateException("Court " + court.getCourtNumber() + " is currently inactive and not available for new bookings.");
        }

        // Validate that booking student has an active ADMITTED opportunity in the session queue
        SessionType sessionType = timeSlot.getSessionConfig() != null
                ? timeSlot.getSessionConfig().getSessionType()
                : null;
        LocalDate slotDate = timeSlot.getSlotDate();

        if (sessionType != null && slotDate != null) {
            BookingQueue queue = bookingQueueRepository.findBySessionDateAndSessionType(slotDate, sessionType)
                    .orElseThrow(() -> new IllegalStateException(
                            "Booking queue has not opened for " + sessionType + " session on " + slotDate + ". Reservations require active queue admission."));

            QueueEntry queueEntry = queueEntryRepository.findByQueueIdAndStudentId(queue.getId(), request.bookingStudentId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Student ID " + request.bookingStudentId() + " is not registered in the booking queue for " + sessionType + " on " + slotDate + ". Direct reservation without queue admission is not permitted."));

            if (queueEntry.getStatus() != QueueEntryStatus.ADMITTED) {
                throw new IllegalStateException("Student ID " + request.bookingStudentId()
                        + " does not hold an active booking opportunity (current queue status: " + queueEntry.getStatus() + ").");
            }

            Instant now = clock.instant();
            if (!queueEntry.isOpportunityActive(now)) {
                queueEntry.expire();
                queueEntryRepository.save(queueEntry);
                throw new IllegalStateException("Booking opportunity has expired. The 60-second window has ended.");
            }
        }

        // Fetch all group members with pessimistic lock in ascending ID order to prevent deadlock
        List<Long> sortedStudentIds = uniqueStudentIds.stream().sorted().toList();
        List<Student> lockedStudents = new ArrayList<>();
        for (Long studentId : sortedStudentIds) {
            Student student = studentRepository.findByIdWithLock(studentId)
                    .orElseThrow(() -> new NoSuchElementException("Registered student not found with ID: " + studentId));
            lockedStudents.add(student);
        }

        Map<Long, Student> studentMap = new HashMap<>();
        for (Student s : lockedStudents) {
            studentMap.put(s.getId(), s);
        }

        // Validate eligibility, credits, and absence of conflicts for EVERY group member
        for (Student student : lockedStudents) {
            if (!Boolean.TRUE.equals(student.getActive())) {
                throw new IllegalStateException("Student " + student.getFullName() + " (ID " + student.getId() + ") is inactive and not eligible for booking.");
            }
            if (!Boolean.TRUE.equals(student.getEmailVerified())) {
                throw new IllegalStateException("Student " + student.getFullName() + " (ID " + student.getId() + ") has not verified their university email.");
            }
            if (student.getWeeklyCredits() == null || student.getWeeklyCredits() < 1) {
                throw new IllegalStateException("Student " + student.getFullName() + " (ID " + student.getId() + ") has insufficient weekly credits (0 credits remaining).");
            }
            if (bookingPlayerRepository.existsByStudentIdAndSlotStartTimeAndBookingStatus(
                    student.getId(), timeSlot.getStartTime(), BookingStatus.CONFIRMED)) {
                throw new IllegalStateException("Student " + student.getFullName() + " (ID " + student.getId() + ") already has a confirmed booking for " + timeSlot.getStartTime() + ".");
            }
        }

        // Deduct exactly 1 credit from EVERY group member
        for (Student student : lockedStudents) {
            student.setWeeklyCredits(student.getWeeklyCredits() - 1);
            studentRepository.save(student);
        }

        // Mark TimeSlot as BOOKED
        timeSlot.setStatus(SlotStatus.BOOKED);
        timeSlotRepository.save(timeSlot);

        // Find queue entry if present
        QueueEntry associatedQueueEntry = null;
        if (sessionType != null && slotDate != null) {
            Optional<BookingQueue> queueOpt = bookingQueueRepository.findBySessionDateAndSessionType(slotDate, sessionType);
            if (queueOpt.isPresent()) {
                associatedQueueEntry = queueEntryRepository.findByQueueIdAndStudentId(queueOpt.get().getId(), request.bookingStudentId()).orElse(null);
            }
        }

        // Create Booking
        Student bookingUser = studentMap.get(request.bookingStudentId());
        Booking booking = new Booking(timeSlot, bookingUser, associatedQueueEntry, idempotencyKey);

        // Add BookingPlayers with secure QR tokens
        for (BookingPlayerAssignmentDto assignment : normalizedAssignments) {
            Student student = studentMap.get(assignment.studentId());
            String qrToken = "QR_" + UUID.randomUUID().toString().replace("-", "");
            BookingPlayer player = new BookingPlayer(booking, student, assignment.spotNumber(), qrToken);
            booking.addPlayer(player);
        }

        Booking savedBooking = bookingRepository.save(booking);

        // Transition admitted queue entry to COMPLETED
        if (associatedQueueEntry != null && associatedQueueEntry.getStatus() == QueueEntryStatus.ADMITTED) {
            associatedQueueEntry.complete();
            queueEntryRepository.save(associatedQueueEntry);
        }

        return BookingResponseDto.fromEntity(savedBooking);
    }

    /**
     * Retrieves a booking by its primary ID.
     */
    @Transactional(readOnly = true)
    public BookingResponseDto getBookingById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Booking not found with ID: " + id));
        return BookingResponseDto.fromEntity(booking);
    }
}
