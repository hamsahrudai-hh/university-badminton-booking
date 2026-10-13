package edu.university.badminton.repository;

import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class SessionConfigRepositoryTest {

    @Autowired
    private SessionConfigRepository sessionConfigRepository;

    @Test
    @DisplayName("Should save morning session configuration with agreed schedule and queue rules")
    void shouldSaveMorningSessionConfig() {
        SessionConfig morningConfig = new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        );

        SessionConfig saved = sessionConfigRepository.saveAndFlush(morningConfig);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSessionType()).isEqualTo(SessionType.MORNING);
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(saved.getSlotDurationMinutes()).isEqualTo(30);
        assertThat(saved.getQueueOpenTime()).isEqualTo(LocalTime.of(21, 0));
        assertThat(saved.getBookingStartTime()).isEqualTo(LocalTime.of(21, 5));
        assertThat(saved.getQueueDaysInAdvance()).isEqualTo(1);
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should save evening session configuration with separately configurable queue rules")
    void shouldSaveEveningSessionConfig() {
        SessionConfig eveningConfig = new SessionConfig(
                SessionType.EVENING,
                LocalTime.of(15, 30),
                LocalTime.of(19, 0),
                30,
                LocalTime.of(12, 0),
                LocalTime.of(12, 5),
                0
        );

        SessionConfig saved = sessionConfigRepository.saveAndFlush(eveningConfig);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSessionType()).isEqualTo(SessionType.EVENING);
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(15, 30));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(saved.getSlotDurationMinutes()).isEqualTo(30);
        assertThat(saved.getQueueOpenTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(saved.getBookingStartTime()).isEqualTo(LocalTime.of(12, 5));
        assertThat(saved.getQueueDaysInAdvance()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should find session configuration by session type")
    void shouldFindBySessionType() {
        SessionConfig config = new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        );
        sessionConfigRepository.saveAndFlush(config);

        Optional<SessionConfig> found = sessionConfigRepository.findBySessionType(SessionType.MORNING);

        assertThat(found).isPresent();
        assertThat(found.get().getSessionType()).isEqualTo(SessionType.MORNING);
    }

    @Test
    @DisplayName("Should list active session configurations")
    void shouldFindActiveSessionConfigs() {
        SessionConfig morning = new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        );
        SessionConfig evening = new SessionConfig(
                SessionType.EVENING,
                LocalTime.of(15, 30),
                LocalTime.of(19, 0),
                30,
                LocalTime.of(12, 0),
                LocalTime.of(12, 5),
                0
        );
        sessionConfigRepository.saveAndFlush(morning);
        sessionConfigRepository.saveAndFlush(evening);

        List<SessionConfig> activeList = sessionConfigRepository.findByActiveTrue();

        assertThat(activeList).hasSize(2);
    }

    @Test
    @DisplayName("Should reject duplicate session configuration for the same session type")
    void shouldRejectDuplicateSessionType() {
        SessionConfig config1 = new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        );
        sessionConfigRepository.saveAndFlush(config1);

        SessionConfig config2 = new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        );

        assertThatThrownBy(() -> sessionConfigRepository.saveAndFlush(config2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
