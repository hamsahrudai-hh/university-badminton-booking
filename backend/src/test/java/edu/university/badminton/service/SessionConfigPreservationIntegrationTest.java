package edu.university.badminton.service;

import edu.university.badminton.dto.SessionConfigResponseDto;
import edu.university.badminton.dto.UpdateSessionConfigRequestDto;
import edu.university.badminton.model.Booking;
import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.model.TimeSlot;
import edu.university.badminton.repository.BookingRepository;
import edu.university.badminton.repository.SessionConfigRepository;
import edu.university.badminton.repository.TimeSlotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SessionConfigPreservationIntegrationTest {

    @Autowired
    private SessionConfigService sessionConfigService;

    @Autowired
    private SessionConfigRepository sessionConfigRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    @DisplayName("Updating SessionConfig must preserve all existing TimeSlot, Booking, and Pass records unchanged")
    void shouldPreserveExistingSlotsAndBookingsWhenSessionConfigUpdated() {
        // 1. Capture snapshot of all existing slots and bookings before any session modification
        List<TimeSlot> slotsBefore = timeSlotRepository.findAll();
        List<Booking> bookingsBefore = bookingRepository.findAll();

        int initialSlotCount = slotsBefore.size();
        int initialBookingCount = bookingsBefore.size();

        // Find existing morning session config
        SessionConfig morningConfig = sessionConfigRepository.findBySessionType(SessionType.MORNING)
                .orElseThrow(() -> new IllegalStateException("Initial MORNING session config not found"));
        Long morningId = morningConfig.getId();

        // 2. Perform session rule update (e.g. changing queue opening time and duration)
        UpdateSessionConfigRequestDto updateRequest = new UpdateSessionConfigRequestDto(
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(20, 30), // queue opens earlier at 20:30
                LocalTime.of(20, 35),
                1,
                true
        );

        SessionConfigResponseDto updatedDto = sessionConfigService.updateSessionConfig(morningId, updateRequest);
        assertThat(updatedDto.queueOpenTime()).isEqualTo(LocalTime.of(20, 30));
        assertThat(updatedDto.bookingStartTime()).isEqualTo(LocalTime.of(20, 35));

        // 3. Verify that all existing time slots remain strictly untouched
        List<TimeSlot> slotsAfter = timeSlotRepository.findAll();
        assertThat(slotsAfter).hasSize(initialSlotCount);

        for (int i = 0; i < slotsBefore.size(); i++) {
            TimeSlot before = slotsBefore.get(i);
            TimeSlot after = slotsAfter.get(i);

            assertThat(after.getId()).isEqualTo(before.getId());
            assertThat(after.getStartTime()).isEqualTo(before.getStartTime());
            assertThat(after.getEndTime()).isEqualTo(before.getEndTime());
            assertThat(after.getStatus()).isEqualTo(before.getStatus());
            assertThat(after.getCourt().getId()).isEqualTo(before.getCourt().getId());
        }

        // 4. Verify that all existing bookings remain strictly untouched
        List<Booking> bookingsAfter = bookingRepository.findAll();
        assertThat(bookingsAfter).hasSize(initialBookingCount);

        for (int i = 0; i < bookingsBefore.size(); i++) {
            Booking before = bookingsBefore.get(i);
            Booking after = bookingsAfter.get(i);

            assertThat(after.getId()).isEqualTo(before.getId());
            assertThat(after.getStatus()).isEqualTo(before.getStatus());
            assertThat(after.getIdempotencyKey()).isEqualTo(before.getIdempotencyKey());
            assertThat(after.getTimeSlot().getId()).isEqualTo(before.getTimeSlot().getId());
            assertThat(after.getBookingUser().getId()).isEqualTo(before.getBookingUser().getId());
        }
    }
}
