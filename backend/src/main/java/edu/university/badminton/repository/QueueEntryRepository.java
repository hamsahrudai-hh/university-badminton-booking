package edu.university.badminton.repository;

import edu.university.badminton.model.QueueEntry;
import edu.university.badminton.model.QueueEntryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for QueueEntry entities.
 */
@Repository
public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {

    boolean existsByQueueIdAndStudentId(Long queueId, Long studentId);

    Optional<QueueEntry> findByQueueIdAndStudentId(Long queueId, Long studentId);

    Optional<QueueEntry> findByQueueIdAndStatus(Long queueId, QueueEntryStatus status);

    Optional<QueueEntry> findFirstByQueueIdAndStatusOrderByJoinedAtAscIdAsc(Long queueId, QueueEntryStatus status);

    List<QueueEntry> findByQueueIdAndStatusOrderByJoinedAtAscIdAsc(Long queueId, QueueEntryStatus status);

    @Query("SELECT qe FROM QueueEntry qe " +
           "JOIN FETCH qe.student s " +
           "WHERE qe.queue.id = :queueId AND qe.status = :status " +
           "ORDER BY CASE WHEN s.weeklyCredits > 0 THEN 0 ELSE 1 END ASC, qe.joinedAt ASC, qe.id ASC")
    List<QueueEntry> findWaitingEntriesOrderedByPriority(
            @Param("queueId") Long queueId,
            @Param("status") QueueEntryStatus status
    );

    long countByQueueId(Long queueId);

    long countByQueueIdAndStatus(Long queueId, QueueEntryStatus status);

    @Query("SELECT COUNT(qe) FROM QueueEntry qe " +
           "JOIN qe.student s " +
           "WHERE qe.queue.id = :queueId AND qe.status = 'WAITING' AND (" +
           "  (CASE WHEN s.weeklyCredits > 0 THEN 0 ELSE 1 END < :targetTier) " +
           "  OR (" +
           "    (CASE WHEN s.weeklyCredits > 0 THEN 0 ELSE 1 END = :targetTier) " +
           "    AND (qe.joinedAt < :joinedAt OR (qe.joinedAt = :joinedAt AND qe.id <= :id))" +
           "  )" +
           ")")
    long calculateWaitingPosition(
            @Param("queueId") Long queueId,
            @Param("targetTier") int targetTier,
            @Param("joinedAt") Instant joinedAt,
            @Param("id") Long id
    );

    @Query("SELECT COUNT(qe) FROM QueueEntry qe WHERE qe.queue.id = :queueId AND qe.status = 'WAITING' AND (qe.joinedAt < :joinedAt OR (qe.joinedAt = :joinedAt AND qe.id <= :id))")
    long calculateWaitingPosition(
            @Param("queueId") Long queueId,
            @Param("joinedAt") Instant joinedAt,
            @Param("id") Long id
    );
}
