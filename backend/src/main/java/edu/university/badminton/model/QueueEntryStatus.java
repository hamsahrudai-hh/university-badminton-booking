package edu.university.badminton.model;

/**
 * Lifecycle status of an individual student's entry in a booking queue.
 */
public enum QueueEntryStatus {
    /**
     * In the queue waiting to be admitted in FIFO order.
     */
    WAITING,

    /**
     * Admitted to the booking window; holds the active 60-second booking opportunity.
     */
    ADMITTED,

    /**
     * The 60-second booking opportunity expired without completing a booking.
     */
    EXPIRED,

    /**
     * Booking successfully confirmed.
     */
    COMPLETED,

    /**
     * Voluntarily left or cancelled from the queue.
     */
    CANCELLED
}
