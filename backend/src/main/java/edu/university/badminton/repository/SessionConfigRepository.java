package edu.university.badminton.repository;

import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for SessionConfig entities.
 */
@Repository
public interface SessionConfigRepository extends JpaRepository<SessionConfig, Long> {

    Optional<SessionConfig> findBySessionType(SessionType sessionType);

    List<SessionConfig> findByActiveTrue();

    boolean existsBySessionType(SessionType sessionType);

    List<SessionConfig> findAllByOrderByStartTimeAsc();

    List<SessionConfig> findByActiveTrueOrderByStartTimeAsc();
}
