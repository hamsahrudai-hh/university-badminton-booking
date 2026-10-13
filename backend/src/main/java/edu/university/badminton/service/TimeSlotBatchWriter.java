package edu.university.badminton.service;

import edu.university.badminton.model.TimeSlot;
import edu.university.badminton.repository.TimeSlotRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dedicated transactional component for persisting batches of TimeSlot entities.
 * <p>
 * Uses {@link Propagation#REQUIRES_NEW} so that any database uniqueness constraint violation
 * (e.g. concurrent generation race conditions) rolls back only this isolated write transaction,
 * leaving the caller's persistence context unpolluted and avoiding {@code UnexpectedRollbackException}.
 */
@Component
public class TimeSlotBatchWriter {

    private final TimeSlotRepository timeSlotRepository;

    public TimeSlotBatchWriter(TimeSlotRepository timeSlotRepository) {
        this.timeSlotRepository = timeSlotRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int writeSlots(List<TimeSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return 0;
        }
        timeSlotRepository.saveAll(slots);
        timeSlotRepository.flush();
        return slots.size();
    }
}
