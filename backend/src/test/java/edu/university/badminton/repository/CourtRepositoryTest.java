package edu.university.badminton.repository;

import edu.university.badminton.model.Court;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CourtRepositoryTest {

    @Autowired
    private CourtRepository courtRepository;

    @Test
    @DisplayName("Should save court with default active status and creation timestamp")
    void shouldSaveCourtWithDefaults() {
        Court court = new Court(1);

        Court saved = courtRepository.saveAndFlush(court);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCourtNumber()).isEqualTo(1);
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should find court by court number")
    void shouldFindByCourtNumber() {
        Court court = new Court(2);
        courtRepository.saveAndFlush(court);

        Optional<Court> found = courtRepository.findByCourtNumber(2);

        assertThat(found).isPresent();
        assertThat(found.get().getCourtNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should check existence by court number")
    void shouldCheckExistenceByCourtNumber() {
        Court court = new Court(3);
        courtRepository.saveAndFlush(court);

        assertThat(courtRepository.existsByCourtNumber(3)).isTrue();
        assertThat(courtRepository.existsByCourtNumber(99)).isFalse();
    }

    @Test
    @DisplayName("Should reject duplicate court number")
    void shouldRejectDuplicateCourtNumber() {
        Court court1 = new Court(4);
        courtRepository.saveAndFlush(court1);

        Court court2 = new Court(4);

        assertThatThrownBy(() -> courtRepository.saveAndFlush(court2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should find only active courts ordered by court number ascending")
    void shouldFindActiveCourtsOrdered() {
        Court court1 = new Court(10);
        court1.setActive(true);
        Court court2 = new Court(5);
        court2.setActive(false);
        Court court3 = new Court(2);
        court3.setActive(true);

        courtRepository.saveAndFlush(court1);
        courtRepository.saveAndFlush(court2);
        courtRepository.saveAndFlush(court3);

        java.util.List<Court> activeCourts = courtRepository.findByActiveTrueOrderByCourtNumberAsc();

        assertThat(activeCourts).hasSize(2);
        assertThat(activeCourts.get(0).getCourtNumber()).isEqualTo(2);
        assertThat(activeCourts.get(1).getCourtNumber()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should find court by ID with pessimistic lock query")
    void shouldFindByIdWithLock() {
        Court court = new Court(7);
        Court saved = courtRepository.saveAndFlush(court);

        Optional<Court> lockedCourt = courtRepository.findByIdWithLock(saved.getId());

        assertThat(lockedCourt).isPresent();
        assertThat(lockedCourt.get().getId()).isEqualTo(saved.getId());
        assertThat(lockedCourt.get().getCourtNumber()).isEqualTo(7);
    }
}
