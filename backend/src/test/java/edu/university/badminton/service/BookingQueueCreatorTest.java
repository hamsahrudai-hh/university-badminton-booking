package edu.university.badminton.service;

import edu.university.badminton.model.BookingQueue;
import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.repository.BookingQueueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingQueueCreatorTest {

    @Mock
    private BookingQueueRepository bookingQueueRepository;

    @InjectMocks
    private BookingQueueCreator bookingQueueCreator;

    @Test
    @DisplayName("Should create and save new BookingQueue")
    void shouldCreateAndSaveQueue() {
        SessionConfig config = new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        );
        LocalDate date = LocalDate.of(2026, 10, 15);

        when(bookingQueueRepository.saveAndFlush(any(BookingQueue.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingQueue created = bookingQueueCreator.createQueue(config, SessionType.MORNING, date);

        assertThat(created).isNotNull();
        assertThat(created.getSessionDate()).isEqualTo(date);
        assertThat(created.getSessionType()).isEqualTo(SessionType.MORNING);
        verify(bookingQueueRepository).saveAndFlush(any(BookingQueue.class));
    }
}
