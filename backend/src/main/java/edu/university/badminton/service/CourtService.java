package edu.university.badminton.service;

import edu.university.badminton.dto.CourtResponseDto;
import edu.university.badminton.dto.UpdateCourtStatusRequestDto;
import edu.university.badminton.model.Court;
import edu.university.badminton.repository.CourtRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service providing court queries and status management.
 */
@Service
@Transactional(readOnly = true)
public class CourtService {

    private static final Logger log = LoggerFactory.getLogger(CourtService.class);

    private final CourtRepository courtRepository;

    public CourtService(CourtRepository courtRepository) {
        this.courtRepository = courtRepository;
    }

    /**
     * Returns all registered courts ordered by court number ascending.
     */
    public List<CourtResponseDto> getAllCourts() {
        return courtRepository.findAllByOrderByCourtNumberAsc()
                .stream()
                .map(CourtResponseDto::fromEntity)
                .toList();
    }

    /**
     * Returns all active registered courts ordered by court number ascending.
     */
    public List<CourtResponseDto> getActiveCourts() {
        return courtRepository.findByActiveTrueOrderByCourtNumberAsc()
                .stream()
                .map(CourtResponseDto::fromEntity)
                .toList();
    }

    /**
     * Returns a specific court by database ID.
     */
    public CourtResponseDto getCourtById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Court ID cannot be null.");
        }
        Court court = courtRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Court not found with ID: " + id));
        return CourtResponseDto.fromEntity(court);
    }

    /**
     * Updates a court's active status with pessimistic write locking.
     * Rejects invalid court IDs and invalid state transitions (e.g. activating an already-active court).
     *
     * @param id      database ID of the court
     * @param request payload containing the new active status
     * @return updated court representation
     */
    @Transactional
    public CourtResponseDto updateCourtStatus(Long id, UpdateCourtStatusRequestDto request) {
        if (id == null) {
            throw new IllegalArgumentException("Court ID cannot be null.");
        }
        if (request == null || request.active() == null) {
            throw new IllegalArgumentException("The 'active' status field is required.");
        }

        Court court = courtRepository.findByIdWithLock(id)
                .orElseThrow(() -> new NoSuchElementException("Court not found with ID: " + id));

        if (court.getActive().equals(request.active())) {
            throw new IllegalStateException("Court " + court.getCourtNumber() + " is already " + (request.active() ? "active" : "inactive") + ".");
        }

        court.setActive(request.active());
        Court saved = courtRepository.save(court);

        log.info("Court {} (ID {}) status updated to active={}", saved.getCourtNumber(), saved.getId(), saved.getActive());

        return CourtResponseDto.fromEntity(saved);
    }
}
