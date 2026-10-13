package edu.university.badminton.repository;

import edu.university.badminton.model.Court;
import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.model.SlotStatus;
import edu.university.badminton.model.TimeSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class TimeSlotRepositoryTest {

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private SessionConfigRepository sessionConfigRepository;

    private Court court1;
    private SessionConfig morningSession;

    @BeforeEach
    void setUp() {
        court1 = courtRepository.saveAndFlush(new Court(1));
        morningSession = sessionConfigRepository.saveAndFlush(new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        ));
    }

    @Test
    @DisplayName("Should save a dated time slot with default AVAILABLE status and creation timestamp")
    void shouldSaveTimeSlotWithDefaults() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        LocalDateTime start = LocalDateTime.of(date, LocalTime.of(6, 0));
        LocalDateTime end = LocalDateTime.of(date, LocalTime.of(6, 30));

        TimeSlot slot = new TimeSlot(court1, morningSession, date, start, end);
        TimeSlot saved = timeSlotRepository.saveAndFlush(slot);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCourt().getId()).isEqualTo(court1.getId());
        assertThat(saved.getSessionConfig().getId()).isEqualTo(morningSession.getId());
        assertThat(saved.getSlotDate()).isEqualTo(date);
        assertThat(saved.getStartTime()).isEqualTo(start);
        assertThat(saved.getEndTime()).isEqualTo(end);
        assertThat(saved.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should find slots by date")
    void shouldFindSlotsByDate() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        TimeSlot slot1 = new TimeSlot(court1, morningSession, date,
                LocalDateTime.of(date, LocalTime.of(6, 0)),
                LocalDateTime.of(date, LocalTime.of(6, 30)));
        TimeSlot slot2 = new TimeSlot(court1, morningSession, date,
                LocalDateTime.of(date, LocalTime.of(6, 30)),
                LocalDateTime.of(date, LocalTime.of(7, 0)));

        timeSlotRepository.saveAndFlush(slot1);
        timeSlotRepository.saveAndFlush(slot2);

        List<TimeSlot> slots = timeSlotRepository.findBySlotDate(date);
        assertThat(slots).hasSize(2);
    }

    @Test
    @DisplayName("Should find slot by court ID and start time")
    void shouldFindByCourtIdAndStartTime() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        LocalDateTime start = LocalDateTime.of(date, LocalTime.of(7, 0));
        LocalDateTime end = LocalDateTime.of(date, LocalTime.of(7, 30));

        TimeSlot slot = new TimeSlot(court1, morningSession, date, start, end);
        timeSlotRepository.saveAndFlush(slot);

        Optional<TimeSlot> found = timeSlotRepository.findByCourtIdAndStartTime(court1.getId(), start);
        assertThat(found).isPresent();
        assertThat(found.get().getEndTime()).isEqualTo(end);
    }

    @Test
    @DisplayName("Should support BLOCKED_TOURNAMENT status for tournament court blocking")
    void shouldSupportBlockedTournamentStatus() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        LocalDateTime start = LocalDateTime.of(date, LocalTime.of(7, 30));
        LocalDateTime end = LocalDateTime.of(date, LocalTime.of(8, 0));

        TimeSlot blockedSlot = new TimeSlot(court1, morningSession, date, start, end, SlotStatus.BLOCKED_TOURNAMENT);
        TimeSlot saved = timeSlotRepository.saveAndFlush(blockedSlot);

        assertThat(saved.getStatus()).isEqualTo(SlotStatus.BLOCKED_TOURNAMENT);

        List<TimeSlot> blockedList = timeSlotRepository.findBySlotDateAndStatus(date, SlotStatus.BLOCKED_TOURNAMENT);
        assertThat(blockedList).hasSize(1);
    }

    @Test
    @DisplayName("Should enforce uniqueness constraint preventing duplicate slot for same court and start time")
    void shouldRejectDuplicateSlotForSameCourtAndStartTime() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        LocalDateTime start = LocalDateTime.of(date, LocalTime.of(8, 0));
        LocalDateTime end = LocalDateTime.of(date, LocalTime.of(8, 30));

        TimeSlot slot1 = new TimeSlot(court1, morningSession, date, start, end);
        timeSlotRepository.saveAndFlush(slot1);

        TimeSlot duplicateSlot = new TimeSlot(court1, morningSession, date, start, end);

        assertThatThrownBy(() -> timeSlotRepository.saveAndFlush(duplicateSlot))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should fetch slots with details avoiding N+1 queries")
    void shouldFindSlotsWithDetails() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        TimeSlot slot = new TimeSlot(court1, morningSession, date,
                LocalDateTime.of(date, LocalTime.of(6, 0)),
                LocalDateTime.of(date, LocalTime.of(6, 30)));
        timeSlotRepository.saveAndFlush(slot);

        List<TimeSlot> slots = timeSlotRepository.findBySlotDateWithDetails(date);

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).getCourt().getCourtNumber()).isEqualTo(1);
        assertThat(slots.get(0).getSessionConfig().getSessionType()).isEqualTo(SessionType.MORNING);
    }

    @Test
    @DisplayName("Should reject creating a zero-length time slot")
    void shouldRejectZeroLengthSlot() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        LocalDateTime sameTime = LocalDateTime.of(date, LocalTime.of(8, 0));

        assertThatThrownBy(() -> new TimeSlot(court1, morningSession, date, sameTime, sameTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endTime must be strictly after startTime");
    }

    @Test
    @DisplayName("Should reject creating an inverted time slot where end is before start")
    void shouldRejectInvertedTimeSlot() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        LocalDateTime start = LocalDateTime.of(date, LocalTime.of(8, 30));
        LocalDateTime end = LocalDateTime.of(date, LocalTime.of(8, 0));

        assertThatThrownBy(() -> new TimeSlot(court1, morningSession, date, start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endTime must be strictly after startTime");
    }

    @Test
    @DisplayName("Should reject session config with non-positive duration")
    void shouldRejectSessionConfigWithNonPositiveDuration() {
        assertThatThrownBy(() -> new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                0,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slotDurationMinutes must be positive");
    }

    @Test
    @DisplayName("Should reject session config where start time is not before end time")
    void shouldRejectSessionConfigWithInvalidTimeWindow() {
        assertThatThrownBy(() -> new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(8, 30),
                LocalTime.of(6, 0),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startTime must be strictly before endTime");
    }
}
