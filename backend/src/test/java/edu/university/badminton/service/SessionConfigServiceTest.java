package edu.university.badminton.service;

import edu.university.badminton.dto.SessionConfigResponseDto;
import edu.university.badminton.dto.UpdateSessionConfigRequestDto;
import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.repository.SessionConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionConfigServiceTest {

    @Mock
    private SessionConfigRepository sessionConfigRepository;

    @InjectMocks
    private SessionConfigService sessionConfigService;

    @Test
    @DisplayName("Should return empty list when no sessions exist")
    void shouldReturnEmptyListWhenNoSessions() {
        when(sessionConfigRepository.findAllByOrderByStartTimeAsc()).thenReturn(Collections.emptyList());

        List<SessionConfigResponseDto> result = sessionConfigService.getAllSessions();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should map session configs to DTOs")
    void shouldMapSessionsToDto() {
        SessionConfig morning = new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        );
        morning.setId(1L);

        when(sessionConfigRepository.findAllByOrderByStartTimeAsc()).thenReturn(List.of(morning));

        List<SessionConfigResponseDto> result = sessionConfigService.getAllSessions();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sessionType()).isEqualTo(SessionType.MORNING);
        assertThat(result.get(0).slotDurationMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("Should find session by ID successfully")
    void shouldFindSessionByIdSuccessfully() {
        SessionConfig morning = createSampleMorningConfig();
        when(sessionConfigRepository.findById(1L)).thenReturn(Optional.of(morning));

        SessionConfigResponseDto dto = sessionConfigService.getSessionById(1L);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.sessionType()).isEqualTo(SessionType.MORNING);
    }

    @Test
    @DisplayName("Should throw NoSuchElementException when finding session with unknown ID")
    void shouldThrowWhenSessionNotFoundById() {
        when(sessionConfigRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionConfigService.getSessionById(999L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Session configuration not found with ID: 999");
    }

    @Test
    @DisplayName("Should successfully update session rules with valid values")
    void shouldUpdateSessionRulesSuccessfully() {
        SessionConfig morning = createSampleMorningConfig();
        when(sessionConfigRepository.findById(1L)).thenReturn(Optional.of(morning));
        when(sessionConfigRepository.findAll()).thenReturn(List.of(morning));
        when(sessionConfigRepository.save(any(SessionConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateSessionConfigRequestDto request = new UpdateSessionConfigRequestDto(
                LocalTime.of(6, 30),
                LocalTime.of(9, 0),
                45,
                LocalTime.of(20, 0),
                LocalTime.of(20, 10),
                1,
                true
        );

        SessionConfigResponseDto updated = sessionConfigService.updateSessionConfig(1L, request);

        assertThat(updated.startTime()).isEqualTo(LocalTime.of(6, 30));
        assertThat(updated.endTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(updated.slotDurationMinutes()).isEqualTo(45);
        assertThat(updated.queueOpenTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(updated.bookingStartTime()).isEqualTo(LocalTime.of(20, 10));
        verify(sessionConfigRepository).save(morning);
    }

    @Test
    @DisplayName("Should merge partial updates with existing configuration")
    void shouldMergePartialUpdates() {
        SessionConfig morning = createSampleMorningConfig();
        when(sessionConfigRepository.findById(1L)).thenReturn(Optional.of(morning));
        when(sessionConfigRepository.save(any(SessionConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        // Only updating slot duration and active status
        UpdateSessionConfigRequestDto request = new UpdateSessionConfigRequestDto(
                null,
                null,
                25,
                null,
                null,
                null,
                false
        );

        SessionConfigResponseDto updated = sessionConfigService.updateSessionConfig(1L, request);

        assertThat(updated.startTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(updated.endTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(updated.slotDurationMinutes()).isEqualTo(25);
        assertThat(updated.active()).isFalse();
    }

    @Test
    @DisplayName("Should reject update when start time is not strictly before end time")
    void shouldRejectWhenStartTimeEqualsOrAfterEndTime() {
        SessionConfig morning = createSampleMorningConfig();
        when(sessionConfigRepository.findById(1L)).thenReturn(Optional.of(morning));

        UpdateSessionConfigRequestDto request = new UpdateSessionConfigRequestDto(
                LocalTime.of(9, 0),
                LocalTime.of(8, 0),
                30,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> sessionConfigService.updateSessionConfig(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be strictly before end time");
    }

    @Test
    @DisplayName("Should reject update when slot duration is non-positive")
    void shouldRejectNonPositiveDuration() {
        SessionConfig morning = createSampleMorningConfig();
        when(sessionConfigRepository.findById(1L)).thenReturn(Optional.of(morning));

        UpdateSessionConfigRequestDto request = new UpdateSessionConfigRequestDto(
                null,
                null,
                0,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> sessionConfigService.updateSessionConfig(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Slot duration must be a positive integer greater than zero");
    }

    @Test
    @DisplayName("Should reject update when slot duration exceeds total session window")
    void shouldRejectDurationExceedingSessionWindow() {
        SessionConfig morning = createSampleMorningConfig();
        when(sessionConfigRepository.findById(1L)).thenReturn(Optional.of(morning));

        // Window is 2.5h (150 mins), duration is 180 mins
        UpdateSessionConfigRequestDto request = new UpdateSessionConfigRequestDto(
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                180,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> sessionConfigService.updateSessionConfig(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed total session window");
    }

    @Test
    @DisplayName("Should reject update when queue opening time is not before booking admission time")
    void shouldRejectInvalidQueueTimingSequence() {
        SessionConfig morning = createSampleMorningConfig();
        when(sessionConfigRepository.findById(1L)).thenReturn(Optional.of(morning));

        UpdateSessionConfigRequestDto request = new UpdateSessionConfigRequestDto(
                null,
                null,
                null,
                LocalTime.of(21, 10),
                LocalTime.of(21, 5),
                null,
                null
        );

        assertThatThrownBy(() -> sessionConfigService.updateSessionConfig(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Queue opening time (21:10) must be strictly before booking admission start time (21:05)");
    }

    @Test
    @DisplayName("Should reject update when active session overlaps with another active session")
    void shouldRejectOverlappingActiveSessions() {
        SessionConfig morning = createSampleMorningConfig();
        SessionConfig evening = new SessionConfig(
                SessionType.EVENING,
                LocalTime.of(15, 30),
                LocalTime.of(19, 0),
                30,
                LocalTime.of(12, 0),
                LocalTime.of(12, 5),
                0
        );
        evening.setId(2L);

        when(sessionConfigRepository.findById(1L)).thenReturn(Optional.of(morning));
        when(sessionConfigRepository.findAll()).thenReturn(List.of(morning, evening));

        // Attempt to expand morning session from 06:00 to 16:30, which overlaps with evening (15:30 to 19:00)
        UpdateSessionConfigRequestDto request = new UpdateSessionConfigRequestDto(
                LocalTime.of(6, 0),
                LocalTime.of(16, 30),
                30,
                null,
                null,
                null,
                true
        );

        assertThatThrownBy(() -> sessionConfigService.updateSessionConfig(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlaps with existing active session EVENING");
    }

    @Test
    @DisplayName("Should allow timing overlap if the other session is deactivated")
    void shouldAllowTimingIfOtherSessionIsInactive() {
        SessionConfig morning = createSampleMorningConfig();
        SessionConfig evening = new SessionConfig(
                SessionType.EVENING,
                LocalTime.of(15, 30),
                LocalTime.of(19, 0),
                30,
                LocalTime.of(12, 0),
                LocalTime.of(12, 5),
                0
        );
        evening.setId(2L);
        evening.setActive(false); // Inactive!

        when(sessionConfigRepository.findById(1L)).thenReturn(Optional.of(morning));
        when(sessionConfigRepository.findAll()).thenReturn(List.of(morning, evening));
        when(sessionConfigRepository.save(any(SessionConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        // Expanding morning to 16:30 is permitted because evening is inactive
        UpdateSessionConfigRequestDto request = new UpdateSessionConfigRequestDto(
                LocalTime.of(6, 0),
                LocalTime.of(16, 30),
                30,
                null,
                null,
                null,
                true
        );

        SessionConfigResponseDto updated = sessionConfigService.updateSessionConfig(1L, request);
        assertThat(updated.endTime()).isEqualTo(LocalTime.of(16, 30));
    }

    private SessionConfig createSampleMorningConfig() {
        SessionConfig morning = new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        );
        morning.setId(1L);
        return morning;
    }
}
