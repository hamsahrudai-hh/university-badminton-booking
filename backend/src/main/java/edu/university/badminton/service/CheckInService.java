package edu.university.badminton.service;

import edu.university.badminton.dto.CheckInRequestDto;
import edu.university.badminton.dto.CheckInResponseDto;
import edu.university.badminton.dto.NoShowProcessingResponseDto;
import edu.university.badminton.dto.PenalizedPlayerDto;
import edu.university.badminton.model.BookingPlayer;
import edu.university.badminton.model.BookingStatus;
import edu.university.badminton.model.Student;
import edu.university.badminton.model.TimeSlot;
import edu.university.badminton.repository.BookingPlayerRepository;
import edu.university.badminton.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Service managing staff QR check-in verification, 10-minute grace period enforcement,
 * and automated/manual no-show forfeiture processing.
 */
@Service
public class CheckInService {

    private static final Logger log = LoggerFactory.getLogger(CheckInService.class);

    /**
     * Number of minutes prior to slot start when staff check-in opens.
     */
    public static final int CHECK_IN_WINDOW_OPENS_MINUTES_BEFORE = 15;

    /**
     * Number of minutes after slot start permitted for check-in before grace period expires.
     */
    public static final int GRACE_PERIOD_MINUTES_AFTER = 10;

    /**
     * Reliability rating deduction penalty applied to each no-show player.
     */
    public static final double NO_SHOW_RELIABILITY_PENALTY = 10.0;

    private final BookingPlayerRepository bookingPlayerRepository;
    private final StudentRepository studentRepository;
    private final Clock clock;

    public CheckInService(BookingPlayerRepository bookingPlayerRepository,
                           StudentRepository studentRepository,
                           Clock clock) {
        this.bookingPlayerRepository = bookingPlayerRepository;
        this.studentRepository = studentRepository;
        this.clock = clock;
    }

    /**
     * Staff validates a player's QR token and records check-in.
     * <p>
     * Verification rules:
     * 1. Token must be valid and non-blank.
     * 2. Booking must be CONFIRMED (not CANCELLED).
     * 3. Cannot check in if already marked as a no-show.
     * 4. Idempotent: If already checked in, returns success without modifying timestamps or deducting credits.
     * 5. Check-in must occur within the permitted window: [startTime - 15m, startTime + 10m].
     *
     * @param request the check-in request with QR token and staff identifier
     * @return response containing player, booking, court, and check-in confirmation
     */
    /**
     * Staff validates a player's QR token and records check-in.
     */
    @Transactional
    public CheckInResponseDto checkInPlayer(CheckInRequestDto request) {
        return checkInPlayer(request, null);
    }

    /**
     * Authenticated staff validates a player's QR token and records check-in with verified staff identity.
     *
     * @param request the check-in request with QR token
     * @param authenticatedStaffId the verified staff identity extracted from the authenticated JWT
     * @return response containing player, booking, court, and check-in confirmation
     */
    @Transactional
    public CheckInResponseDto checkInPlayer(CheckInRequestDto request, String authenticatedStaffId) {
        if (request == null || request.qrToken() == null || request.qrToken().isBlank()) {
            throw new IllegalArgumentException("QR check-in token cannot be null or blank.");
        }

        String rawToken = request.qrToken().trim();
        String staffId = authenticatedStaffId != null && !authenticatedStaffId.isBlank()
                ? authenticatedStaffId.trim()
                : (request.staffIdentifier() != null && !request.staffIdentifier().isBlank()
                        ? request.staffIdentifier().trim()
                        : "FACILITY_STAFF");

        // Acquire pessimistic lock on the player row to prevent concurrent race conditions
        BookingPlayer player = bookingPlayerRepository.findByQrTokenWithDetailsAndLock(rawToken)
                .or(() -> bookingPlayerRepository.findByQrTokenWithDetails(rawToken))
                .orElseThrow(() -> new NoSuchElementException("Invalid or unknown QR check-in token: " + rawToken));

        // 1. Verify booking status
        if (player.getBooking().getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Cannot check in player for a cancelled booking.");
        }

        // 2. Reject if already marked as a no-show
        if (Boolean.TRUE.equals(player.getNoShow())) {
            throw new IllegalStateException("Player is marked as a no-show. The 10-minute check-in deadline has expired.");
        }

        // 3. Idempotent return if already checked in
        if (Boolean.TRUE.equals(player.getCheckedIn())) {
            return CheckInResponseDto.fromEntity(player, true, "Player has already been checked in.");
        }

        // 4. Validate check-in time window against slot start time
        TimeSlot slot = player.getBooking().getTimeSlot();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        LocalDateTime slotStart = slot.getStartTime();
        LocalDateTime windowOpens = slotStart.minusMinutes(CHECK_IN_WINDOW_OPENS_MINUTES_BEFORE);
        LocalDateTime graceDeadline = slotStart.plusMinutes(GRACE_PERIOD_MINUTES_AFTER);

        if (now.isBefore(windowOpens)) {
            throw new IllegalStateException(String.format(
                    "Check-in window is not yet open for slot at %s. Opens at %s.",
                    slotStart, windowOpens
            ));
        }

        if (now.isAfter(graceDeadline)) {
            throw new IllegalStateException(String.format(
                    "Check-in grace period expired at %s for slot at %s. Current time is %s.",
                    graceDeadline, slotStart, now
            ));
        }

        // 5. Record verified check-in
        player.setCheckedIn(true);
        player.setCheckedInAt(clock.instant());
        player.setCheckedInBy(staffId);
        BookingPlayer saved = bookingPlayerRepository.save(player);

        return CheckInResponseDto.fromEntity(saved, false, "Player checked in successfully.");
    }

    /**
     * Processes no-shows for confirmed bookings whose 10-minute grace period has passed.
     */
    @Transactional
    public NoShowProcessingResponseDto processNoShows(Long timeSlotId) {
        return processNoShows(timeSlotId, null);
    }

    /**
     * Processes no-shows for confirmed bookings with authenticated staff/scheduler accountability.
     * Prevents race conditions and duplicate penalties via pessimistic locking and idempotent status checks.
     */
    @Transactional
    public NoShowProcessingResponseDto processNoShows(Long timeSlotId, String authenticatedStaffId) {
        String actor = authenticatedStaffId != null && !authenticatedStaffId.isBlank()
                ? authenticatedStaffId.trim()
                : "SYSTEM_NO_SHOW_PROCESSOR";

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        LocalDateTime cutoff = now.minusMinutes(GRACE_PERIOD_MINUTES_AFTER);

        log.debug("Initiating no-show processing by actor '{}' with cutoff timestamp {}", actor, cutoff);

        List<Long> pendingPlayerIds = timeSlotId != null
                ? bookingPlayerRepository.findPendingNoShowIdsForSlot(BookingStatus.CONFIRMED, timeSlotId, cutoff)
                : bookingPlayerRepository.findPendingNoShowIds(BookingStatus.CONFIRMED, cutoff);

        List<PenalizedPlayerDto> penalizedList = new ArrayList<>();
        int skippedCount = 0;

        for (Long playerId : pendingPlayerIds) {
            // Lock the player row with pessimistic write lock to prevent race conditions with concurrent check-ins or no-show runs
            Optional<BookingPlayer> optPlayer = bookingPlayerRepository.findByIdWithDetailsAndLock(playerId)
                    .or(() -> bookingPlayerRepository.findByIdWithDetails(playerId));

            if (optPlayer.isEmpty()) {
                log.debug("Skipping player id={} because player record was not found", playerId);
                skippedCount++;
                continue;
            }

            BookingPlayer player = optPlayer.get();

            // Double check: if already checked in or already marked as no-show, skip safely
            if (Boolean.TRUE.equals(player.getCheckedIn())) {
                log.debug("Skipping player id={} because player has already checked in", player.getId());
                skippedCount++;
                continue;
            }
            if (Boolean.TRUE.equals(player.getNoShow())) {
                log.debug("Skipping player id={} because no-show penalty was already applied", player.getId());
                skippedCount++;
                continue;
            }
            if (player.getBooking().getStatus() != BookingStatus.CONFIRMED) {
                log.debug("Skipping player id={} because booking is {}", player.getId(), player.getBooking().getStatus());
                skippedCount++;
                continue;
            }

            // Lock student to atomically alter credits and reliability
            Student student = studentRepository.findByIdWithLock(player.getStudent().getId())
                    .orElse(player.getStudent());

            player.setNoShow(true);
            player.setNoShowProcessedAt(clock.instant());

            int remainingCredits = student.getWeeklyCredits() != null ? student.getWeeklyCredits() : 0;
            student.setWeeklyCredits(0);

            double currentRating = student.getReliabilityRating() != null
                    ? student.getReliabilityRating()
                    : Student.DEFAULT_RELIABILITY_RATING;
            double newRating = Math.max(0.0, Math.round((currentRating - NO_SHOW_RELIABILITY_PENALTY) * 10.0) / 10.0);
            student.setReliabilityRating(newRating);

            studentRepository.save(student);
            bookingPlayerRepository.save(player);

            log.info("Penalized no-show player: bookingPlayerId={}, bookingId={}, studentId={}, studentName='{}', slotStartTime={}, previousCredits={}, newCredits=0, previousRating={}, newRating={}",
                    player.getId(),
                    player.getBooking().getId(),
                    student.getId(),
                    student.getFullName(),
                    player.getBooking().getTimeSlot().getStartTime(),
                    remainingCredits,
                    currentRating,
                    newRating);

            penalizedList.add(new PenalizedPlayerDto(
                    player.getId(),
                    player.getBooking().getId(),
                    student.getId(),
                    student.getFullName(),
                    student.getAdmissionNumber(),
                    player.getSpotNumber(),
                    player.getBooking().getTimeSlot().getCourt().getCourtNumber(),
                    player.getBooking().getTimeSlot().getStartTime(),
                    remainingCredits,
                    newRating
            ));
        }

        if (penalizedList.size() > 0 || skippedCount > 0) {
            log.info("No-show processing completed by actor '{}': penalizedCount={}, skippedCount={}, candidateCount={}",
                    actor, penalizedList.size(), skippedCount, pendingPlayerIds.size());
        }

        String message = penalizedList.isEmpty()
                ? "No pending un-checked-in players found beyond the grace period deadline."
                : String.format("Processed %d no-show player(s). Remaining credits forfeited and reliability ratings adjusted.", penalizedList.size());

        return new NoShowProcessingResponseDto(penalizedList.size(), penalizedList, message);
    }
}
