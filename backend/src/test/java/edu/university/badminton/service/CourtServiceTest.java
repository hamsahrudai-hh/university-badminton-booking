package edu.university.badminton.service;

import edu.university.badminton.dto.CourtResponseDto;
import edu.university.badminton.model.Court;
import edu.university.badminton.repository.CourtRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourtServiceTest {

    @Mock
    private CourtRepository courtRepository;

    @InjectMocks
    private CourtService courtService;

    @Test
    @DisplayName("Should return empty list when no courts in database")
    void shouldReturnEmptyListWhenNoCourts() {
        when(courtRepository.findAllByOrderByCourtNumberAsc()).thenReturn(Collections.emptyList());

        List<CourtResponseDto> result = courtService.getAllCourts();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should map courts to DTOs in ascending court number order")
    void shouldMapCourtsToDto() {
        Court court1 = new Court(1);
        court1.setId(10L);
        Court court2 = new Court(2);
        court2.setId(20L);

        when(courtRepository.findAllByOrderByCourtNumberAsc()).thenReturn(List.of(court1, court2));

        List<CourtResponseDto> result = courtService.getAllCourts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).courtNumber()).isEqualTo(1);
        assertThat(result.get(1).courtNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should return active courts only")
    void shouldReturnActiveCourtsOnly() {
        Court court1 = new Court(1);
        court1.setId(10L);
        court1.setActive(true);

        when(courtRepository.findByActiveTrueOrderByCourtNumberAsc()).thenReturn(List.of(court1));

        List<CourtResponseDto> result = courtService.getActiveCourts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).courtNumber()).isEqualTo(1);
        assertThat(result.get(0).active()).isTrue();
    }

    @Test
    @DisplayName("Should get court by ID when exists")
    void shouldGetCourtByIdWhenExists() {
        Court court = new Court(3);
        court.setId(30L);
        court.setActive(true);

        when(courtRepository.findById(30L)).thenReturn(java.util.Optional.of(court));

        CourtResponseDto result = courtService.getCourtById(30L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(30L);
        assertThat(result.courtNumber()).isEqualTo(3);
        assertThat(result.active()).isTrue();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when getting court with null ID")
    void shouldThrowWhenGetCourtWithNullId() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> courtService.getCourtById(null)
        );
    }

    @Test
    @DisplayName("Should throw NoSuchElementException when court ID not found")
    void shouldThrowWhenCourtNotFound() {
        when(courtRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                java.util.NoSuchElementException.class,
                () -> courtService.getCourtById(99L)
        );
    }

    @Test
    @DisplayName("Should successfully deactivate an active court")
    void shouldDeactivateCourtSuccessfully() {
        Court court = new Court(1);
        court.setId(1L);
        court.setActive(true);

        when(courtRepository.findByIdWithLock(1L)).thenReturn(java.util.Optional.of(court));
        when(courtRepository.save(org.mockito.ArgumentMatchers.any(Court.class))).thenAnswer(invocation -> invocation.getArgument(0));

        edu.university.badminton.dto.UpdateCourtStatusRequestDto request =
                new edu.university.badminton.dto.UpdateCourtStatusRequestDto(false);

        CourtResponseDto updated = courtService.updateCourtStatus(1L, request);

        assertThat(updated.active()).isFalse();
        assertThat(court.getActive()).isFalse();
    }

    @Test
    @DisplayName("Should successfully activate an inactive court")
    void shouldActivateCourtSuccessfully() {
        Court court = new Court(2);
        court.setId(2L);
        court.setActive(false);

        when(courtRepository.findByIdWithLock(2L)).thenReturn(java.util.Optional.of(court));
        when(courtRepository.save(org.mockito.ArgumentMatchers.any(Court.class))).thenAnswer(invocation -> invocation.getArgument(0));

        edu.university.badminton.dto.UpdateCourtStatusRequestDto request =
                new edu.university.badminton.dto.UpdateCourtStatusRequestDto(true);

        CourtResponseDto updated = courtService.updateCourtStatus(2L, request);

        assertThat(updated.active()).isTrue();
        assertThat(court.getActive()).isTrue();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when updating court with null ID")
    void shouldThrowWhenUpdateCourtWithNullId() {
        edu.university.badminton.dto.UpdateCourtStatusRequestDto request =
                new edu.university.badminton.dto.UpdateCourtStatusRequestDto(true);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> courtService.updateCourtStatus(null, request)
        );
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when updating court with null request or status")
    void shouldThrowWhenUpdateCourtWithNullPayload() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> courtService.updateCourtStatus(1L, null)
        );

        edu.university.badminton.dto.UpdateCourtStatusRequestDto nullStatusRequest =
                new edu.university.badminton.dto.UpdateCourtStatusRequestDto(null);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> courtService.updateCourtStatus(1L, nullStatusRequest)
        );
    }

    @Test
    @DisplayName("Should throw NoSuchElementException when updating non-existent court")
    void shouldThrowWhenUpdatingNonExistentCourt() {
        when(courtRepository.findByIdWithLock(999L)).thenReturn(java.util.Optional.empty());

        edu.university.badminton.dto.UpdateCourtStatusRequestDto request =
                new edu.university.badminton.dto.UpdateCourtStatusRequestDto(false);

        org.junit.jupiter.api.Assertions.assertThrows(
                java.util.NoSuchElementException.class,
                () -> courtService.updateCourtStatus(999L, request)
        );
    }

    @Test
    @DisplayName("Should throw IllegalStateException on duplicate status transition")
    void shouldThrowWhenUpdatingToSameStatus() {
        Court court = new Court(1);
        court.setId(1L);
        court.setActive(true);

        when(courtRepository.findByIdWithLock(1L)).thenReturn(java.util.Optional.of(court));

        edu.university.badminton.dto.UpdateCourtStatusRequestDto request =
                new edu.university.badminton.dto.UpdateCourtStatusRequestDto(true);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> courtService.updateCourtStatus(1L, request)
        );
    }
}
