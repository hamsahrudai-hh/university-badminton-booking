package edu.university.badminton.service;

import edu.university.badminton.model.BookingQueue;
import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.repository.BookingQueueRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Dedicated transactional component for creating BookingQueue records.
 * <p>
 * Uses {@link Propagation#REQUIRES_NEW} so that any duplicate-key collision during simultaneous
 * first-time queue creation rolls back only this isolated transaction, leaving the caller's
 * transaction clean and preventing {@code UnexpectedRollbackException}.
 */
@Component
public class BookingQueueCreator {

    private final BookingQueueRepository bookingQueueRepository;

    public BookingQueueCreator(BookingQueueRepository bookingQueueRepository) {
        this.bookingQueueRepository = bookingQueueRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BookingQueue createQueue(SessionConfig sessionConfig, SessionType sessionType, LocalDate date) {
        BookingQueue newQueue = new BookingQueue(sessionConfig, sessionType, date);
        return bookingQueueRepository.saveAndFlush(newQueue);
    }
}
