package edu.university.badminton.repository;

import edu.university.badminton.model.CreditResetExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CreditResetExecution} records.
 */
@Repository
public interface CreditResetExecutionRepository extends JpaRepository<CreditResetExecution, Long> {

    /**
     * Checks if a credit reset has already been recorded for the given period key (e.g., "2026-W42").
     */
    boolean existsByPeriodKey(String periodKey);

    /**
     * Finds the credit reset execution for a specific period key.
     */
    Optional<CreditResetExecution> findByPeriodKey(String periodKey);

    /**
     * Finds the most recent successful credit reset execution.
     */
    Optional<CreditResetExecution> findTopByOrderByExecutedAtDesc();
}
