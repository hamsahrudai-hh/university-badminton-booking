package edu.university.badminton.service;

import edu.university.badminton.model.Court;
import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.model.SlotStatus;
import edu.university.badminton.model.TimeSlot;
import edu.university.badminton.repository.TimeSlotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TimeSlotBatchWriterTest {

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @InjectMocks
    private TimeSlotBatchWriter timeSlotBatchWriter;

    @Test
    @DisplayName("Should return 0 when slot list is null or empty")
    void shouldReturnZeroForEmptyList() {
        assertThat(timeSlotBatchWriter.writeSlots(null)).isZero();
        assertThat(timeSlotBatchWriter.writeSlots(Collections.emptyList())).isZero();
        verifyNoInteractions(timeSlotRepository);
    }

    @Test
    @DisplayName("Should save and flush slots")
    void shouldSaveAndFlushSlots() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        Court court = new Court(1);
        SessionConfig session = new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        );
        TimeSlot slot = new TimeSlot(
                court,
                session,
                date,
                LocalDateTime.of(date, LocalTime.of(6, 0)),
                LocalDateTime.of(date, LocalTime.of(6, 30)),
                SlotStatus.AVAILABLE
        );
        List<TimeSlot> slots = List.of(slot);

        int written = timeSlotBatchWriter.writeSlots(slots);

        assertThat(written).isEqualTo(1);
        verify(timeSlotRepository).saveAll(slots);
        verify(timeSlotRepository).flush();
    }
}
