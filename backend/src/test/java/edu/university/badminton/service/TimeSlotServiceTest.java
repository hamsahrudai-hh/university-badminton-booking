package edu.university.badminton.service;

import edu.university.badminton.dto.SlotGenerationResponseDto;
import edu.university.badminton.dto.TimeSlotResponseDto;
import edu.university.badminton.model.Court;
import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.model.SlotStatus;
import edu.university.badminton.model.TimeSlot;
import edu.university.badminton.repository.CourtRepository;
import edu.university.badminton.repository.SessionConfigRepository;
import edu.university.badminton.repository.TimeSlotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeSlotServiceTest {

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private CourtRepository courtRepository;

    @Mock
    private SessionConfigRepository sessionConfigRepository;

    @Mock
    private TimeSlotBatchWriter timeSlotBatchWriter;

    @InjectMocks
    private TimeSlotService timeSlotService;

    @Test
    @DisplayName("Should return empty list when no slots on given date")
    void shouldReturnEmptyListWhenNoSlots() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        when(timeSlotRepository.findBySlotDateWithDetails(date)).thenReturn(Collections.emptyList());

        List<TimeSlotResponseDto> result = timeSlotService.getSlotsByDate(date);

        assertThat(result).isEmpty();
        verify(timeSlotRepository).findBySlotDateWithDetails(date);
    }

    @Test
    @DisplayName("Should map time slots to DTOs using eager-fetched court details")
    void shouldMapTimeSlotsToDto() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        Court court = new Court(1);
        court.setId(10L);

        SessionConfig session = new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        );
        session.setId(20L);

        TimeSlot slot = new TimeSlot(
                court,
                session,
                date,
                LocalDateTime.of(date, LocalTime.of(6, 0)),
                LocalDateTime.of(date, LocalTime.of(6, 30)),
                SlotStatus.AVAILABLE
        );
        slot.setId(100L);

        when(timeSlotRepository.findBySlotDateWithDetails(date)).thenReturn(List.of(slot));

        List<TimeSlotResponseDto> result = timeSlotService.getSlotsByDate(date);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(100L);
        assertThat(result.get(0).courtNumber()).isEqualTo(1);
        assertThat(result.get(0).sessionType()).isEqualTo(SessionType.MORNING);
        assertThat(result.get(0).status()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should generate 48 slots for 4 active courts and standard morning/evening sessions")
    void shouldGenerate48SlotsForFourCourts() {
        LocalDate date = LocalDate.of(2026, 10, 15);

        List<Court> courts = List.of(
                createCourt(1L, 1),
                createCourt(2L, 2),
                createCourt(3L, 3),
                createCourt(4L, 4)
        );
        List<SessionConfig> sessions = List.of(
                createSession(1L, SessionType.MORNING, LocalTime.of(6, 0), LocalTime.of(8, 30), 30),
                createSession(2L, SessionType.EVENING, LocalTime.of(15, 30), LocalTime.of(19, 0), 30)
        );

        when(courtRepository.findByActiveTrueOrderByCourtNumberAsc()).thenReturn(courts);
        when(sessionConfigRepository.findByActiveTrueOrderByStartTimeAsc()).thenReturn(sessions);
        when(timeSlotRepository.findBySlotDateWithDetails(date)).thenReturn(Collections.emptyList());
        when(timeSlotBatchWriter.writeSlots(anyList())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());

        SlotGenerationResponseDto response = timeSlotService.generateSlotsForDate(date);

        assertThat(response.date()).isEqualTo(date);
        assertThat(response.totalSlots()).isEqualTo(48);
        assertThat(response.createdSlots()).isEqualTo(48);
        assertThat(response.existingSlots()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TimeSlot>> captor = ArgumentCaptor.forClass(List.class);
        verify(timeSlotBatchWriter).writeSlots(captor.capture());
        List<TimeSlot> savedSlots = captor.getValue();
        assertThat(savedSlots).hasSize(48);
    }

    @Test
    @DisplayName("Repeated generation should be idempotent and create 0 duplicates")
    void shouldBeIdempotentOnRepeatedGeneration() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        Court court1 = createCourt(1L, 1);
        SessionConfig morning = createSession(1L, SessionType.MORNING, LocalTime.of(6, 0), LocalTime.of(6, 30), 30);

        TimeSlot existingSlot = new TimeSlot(
                court1,
                morning,
                date,
                LocalDateTime.of(date, LocalTime.of(6, 0)),
                LocalDateTime.of(date, LocalTime.of(6, 30)),
                SlotStatus.AVAILABLE
        );

        when(courtRepository.findByActiveTrueOrderByCourtNumberAsc()).thenReturn(List.of(court1));
        when(sessionConfigRepository.findByActiveTrueOrderByStartTimeAsc()).thenReturn(List.of(morning));
        when(timeSlotRepository.findBySlotDateWithDetails(date)).thenReturn(List.of(existingSlot));

        SlotGenerationResponseDto response = timeSlotService.generateSlotsForDate(date);

        assertThat(response.totalSlots()).isEqualTo(1);
        assertThat(response.createdSlots()).isEqualTo(0);
        assertThat(response.existingSlots()).isEqualTo(1);
        verify(timeSlotBatchWriter, never()).writeSlots(anyList());
    }

    @Test
    @DisplayName("Should preserve existing slot with BLOCKED_TOURNAMENT status during generation")
    void shouldPreserveExistingSlotStatuses() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        Court court1 = createCourt(1L, 1);
        SessionConfig morning = createSession(1L, SessionType.MORNING, LocalTime.of(6, 0), LocalTime.of(7, 0), 30);

        // Pre-existing slot 1 (06:00 - 06:30) is blocked for tournament
        TimeSlot blockedSlot = new TimeSlot(
                court1,
                morning,
                date,
                LocalDateTime.of(date, LocalTime.of(6, 0)),
                LocalDateTime.of(date, LocalTime.of(6, 30)),
                SlotStatus.BLOCKED_TOURNAMENT
        );
        blockedSlot.setId(999L);

        when(courtRepository.findByActiveTrueOrderByCourtNumberAsc()).thenReturn(List.of(court1));
        when(sessionConfigRepository.findByActiveTrueOrderByStartTimeAsc()).thenReturn(List.of(morning));
        when(timeSlotRepository.findBySlotDateWithDetails(date)).thenReturn(List.of(blockedSlot));
        when(timeSlotBatchWriter.writeSlots(anyList())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());

        SlotGenerationResponseDto response = timeSlotService.generateSlotsForDate(date);

        // Morning has 2 slots (06:00, 06:30). 1 existed, 1 created.
        assertThat(response.totalSlots()).isEqualTo(2);
        assertThat(response.createdSlots()).isEqualTo(1);
        assertThat(response.existingSlots()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TimeSlot>> captor = ArgumentCaptor.forClass(List.class);
        verify(timeSlotBatchWriter).writeSlots(captor.capture());
        List<TimeSlot> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        // Only the second slot (06:30 - 07:00) is created
        assertThat(saved.get(0).getStartTime()).isEqualTo(LocalDateTime.of(date, LocalTime.of(6, 30)));
    }

    @Test
    @DisplayName("Should preserve BOOKED, BLOCKED_TOURNAMENT, and MAINTENANCE slots on partially populated date")
    void shouldPreserveMultipleStatusesOnPartiallyPopulatedDate() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        Court court1 = createCourt(1L, 1);
        SessionConfig morning = createSession(1L, SessionType.MORNING, LocalTime.of(6, 0), LocalTime.of(8, 30), 30);

        // 3 pre-existing slots with different statuses
        TimeSlot booked = new TimeSlot(court1, morning, date,
                LocalDateTime.of(date, LocalTime.of(6, 0)),
                LocalDateTime.of(date, LocalTime.of(6, 30)),
                SlotStatus.BOOKED);
        booked.setId(101L);

        TimeSlot tournament = new TimeSlot(court1, morning, date,
                LocalDateTime.of(date, LocalTime.of(6, 30)),
                LocalDateTime.of(date, LocalTime.of(7, 0)),
                SlotStatus.BLOCKED_TOURNAMENT);
        tournament.setId(102L);

        TimeSlot maintenance = new TimeSlot(court1, morning, date,
                LocalDateTime.of(date, LocalTime.of(7, 0)),
                LocalDateTime.of(date, LocalTime.of(7, 30)),
                SlotStatus.MAINTENANCE);
        maintenance.setId(103L);

        List<TimeSlot> preExisting = List.of(booked, tournament, maintenance);

        when(courtRepository.findByActiveTrueOrderByCourtNumberAsc()).thenReturn(List.of(court1));
        when(sessionConfigRepository.findByActiveTrueOrderByStartTimeAsc()).thenReturn(List.of(morning));
        when(timeSlotRepository.findBySlotDateWithDetails(date)).thenReturn(preExisting);
        when(timeSlotBatchWriter.writeSlots(anyList())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());

        SlotGenerationResponseDto response = timeSlotService.generateSlotsForDate(date);

        // Morning has 5 slots total (06:00, 06:30, 07:00, 07:30, 08:00). 3 existed, 2 created.
        assertThat(response.totalSlots()).isEqualTo(5);
        assertThat(response.createdSlots()).isEqualTo(2);
        assertThat(response.existingSlots()).isEqualTo(3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TimeSlot>> captor = ArgumentCaptor.forClass(List.class);
        verify(timeSlotBatchWriter).writeSlots(captor.capture());
        List<TimeSlot> created = captor.getValue();
        assertThat(created).hasSize(2);
        assertThat(created.get(0).getStartTime()).isEqualTo(LocalDateTime.of(date, LocalTime.of(7, 30)));
        assertThat(created.get(1).getStartTime()).isEqualTo(LocalDateTime.of(date, LocalTime.of(8, 0)));
        assertThat(created).allMatch(s -> s.getStatus() == SlotStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should recover cleanly when concurrent generation throws DataIntegrityViolationException")
    void shouldRecoverCleanlyOnConcurrentDuplicateKeyRaceCondition() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        Court court1 = createCourt(1L, 1);
        SessionConfig morning = createSession(1L, SessionType.MORNING, LocalTime.of(6, 0), LocalTime.of(6, 30), 30);

        TimeSlot committedByWinner = new TimeSlot(
                court1,
                morning,
                date,
                LocalDateTime.of(date, LocalTime.of(6, 0)),
                LocalDateTime.of(date, LocalTime.of(6, 30)),
                SlotStatus.AVAILABLE
        );

        when(courtRepository.findByActiveTrueOrderByCourtNumberAsc()).thenReturn(List.of(court1));
        when(sessionConfigRepository.findByActiveTrueOrderByStartTimeAsc()).thenReturn(List.of(morning));
        // First query sees 0 slots, prompting generation
        // Second query (after concurrent collision) sees the slot committed by concurrent winner
        when(timeSlotRepository.findBySlotDateWithDetails(date))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(committedByWinner));
        when(timeSlotBatchWriter.writeSlots(anyList()))
                .thenThrow(new DataIntegrityViolationException("Duplicate key uk_court_slot_time"));

        SlotGenerationResponseDto response = timeSlotService.generateSlotsForDate(date);

        assertThat(response.totalSlots()).isEqualTo(1);
        assertThat(response.createdSlots()).isEqualTo(0);
        assertThat(response.existingSlots()).isEqualTo(1);
        assertThat(response.message()).contains("Slots already exist for 2026-10-15");
    }

    @Test
    @DisplayName("Should fail when session configuration has non-positive duration")
    void shouldThrowWhenSessionHasNonPositiveDuration() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        Court court1 = createCourt(1L, 1);
        // Using reflection or a mock to simulate corrupted DB record with duration <= 0
        SessionConfig invalidSession = new SessionConfig();
        invalidSession.setSessionType(SessionType.MORNING);
        invalidSession.setStartTime(LocalTime.of(6, 0));
        invalidSession.setEndTime(LocalTime.of(8, 30));
        invalidSession.setSlotDurationMinutes(0);
        invalidSession.setActive(true);

        when(courtRepository.findByActiveTrueOrderByCourtNumberAsc()).thenReturn(List.of(court1));
        when(sessionConfigRepository.findByActiveTrueOrderByStartTimeAsc()).thenReturn(List.of(invalidSession));

        assertThatThrownBy(() -> timeSlotService.generateSlotsForDate(date))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid slot duration");
    }

    @Test
    @DisplayName("Should fail when session configuration has start time not before end time")
    void shouldThrowWhenSessionHasInvalidTimeWindow() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        Court court1 = createCourt(1L, 1);
        SessionConfig invalidSession = new SessionConfig();
        invalidSession.setSessionType(SessionType.MORNING);
        invalidSession.setStartTime(LocalTime.of(8, 30));
        invalidSession.setEndTime(LocalTime.of(6, 0));
        invalidSession.setSlotDurationMinutes(30);
        invalidSession.setActive(true);

        when(courtRepository.findByActiveTrueOrderByCourtNumberAsc()).thenReturn(List.of(court1));
        when(sessionConfigRepository.findByActiveTrueOrderByStartTimeAsc()).thenReturn(List.of(invalidSession));

        assertThatThrownBy(() -> timeSlotService.generateSlotsForDate(date))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("start time");
    }

    @Test
    @DisplayName("Should fail when no active courts are available")
    void shouldThrowWhenNoActiveCourts() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        when(courtRepository.findByActiveTrueOrderByCourtNumberAsc()).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> timeSlotService.generateSlotsForDate(date))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active courts found");
    }

    @Test
    @DisplayName("Should fail when no active session configurations are available")
    void shouldThrowWhenNoActiveSessions() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        when(courtRepository.findByActiveTrueOrderByCourtNumberAsc()).thenReturn(List.of(createCourt(1L, 1)));
        when(sessionConfigRepository.findByActiveTrueOrderByStartTimeAsc()).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> timeSlotService.generateSlotsForDate(date))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active session configurations found");
    }

    @Test
    @DisplayName("Should fail when date is null")
    void shouldThrowWhenDateIsNull() {
        assertThatThrownBy(() -> timeSlotService.generateSlotsForDate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Court createCourt(Long id, int number) {
        Court court = new Court(number);
        court.setId(id);
        return court;
    }

    private SessionConfig createSession(Long id, SessionType type, LocalTime start, LocalTime end, int duration) {
        SessionConfig config = new SessionConfig(type, start, end, duration, LocalTime.of(21, 0), LocalTime.of(21, 5), 1);
        config.setId(id);
        return config;
    }
}
