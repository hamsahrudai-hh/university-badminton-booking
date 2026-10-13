import type { SessionType, TimeSlot } from '../types/booking';
import type { TimeSlotResponseDto } from '../api/client';

export function formatTimeFromIso(isoStr: string): string {
  const timePart = isoStr.includes('T') ? isoStr.split('T')[1] : isoStr;
  const parts = timePart.split(':');
  const hours = parseInt(parts[0], 10);
  const minutes = parseInt(parts[1], 10);
  const period = hours >= 12 ? 'PM' : 'AM';
  const displayHours = hours % 12 === 0 ? 12 : hours % 12;
  const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
  return `${pad(displayHours)}:${pad(minutes)} ${period}`;
}

export function groupDtosToTimeSlots(dtos: TimeSlotResponseDto[], fallbackSlots: TimeSlot[]): TimeSlot[] {
  const map = new Map<string, TimeSlot>();
  const sortedDtos = [...dtos].sort((a, b) => a.startTime.localeCompare(b.startTime));

  for (const dto of sortedDtos) {
    const session: SessionType = dto.sessionType === 'MORNING' ? 'morning' : 'evening';
    const startTimeFormatted = formatTimeFromIso(dto.startTime);
    const endTimeFormatted = formatTimeFromIso(dto.endTime);
    const key = `${session}_${startTimeFormatted}_${endTimeFormatted}`;

    let slot = map.get(key);
    if (!slot) {
      const matchingMock = fallbackSlots.find(
        (m) => m.session === session && m.startTime === startTimeFormatted
      );

      const id = matchingMock
        ? matchingMock.id
        : `${session === 'morning' ? 'm' : 'e'}-${dto.startTime.slice(11, 16).replace(':', '')}`;

      slot = {
        id,
        session,
        startTime: startTimeFormatted,
        endTime: endTimeFormatted,
        label: `${startTimeFormatted} – ${endTimeFormatted}`,
        realTimeSlotId: dto.id,
        courtSlotMap: {},
        isoStartTime: dto.startTime,
        isoEndTime: dto.endTime,
        slotDate: dto.slotDate,
      };
      map.set(key, slot);
    }

    if (slot.courtSlotMap && dto.id != null) {
      if (dto.courtNumber != null) {
        slot.courtSlotMap[dto.courtNumber] = dto.id;
      }
      if (dto.courtId != null) {
        slot.courtSlotMap[dto.courtId] = dto.id;
      }
      // Prioritize Court 1 as default slot ID if present
      if (dto.courtNumber === 1 || dto.courtId === 1) {
        slot.realTimeSlotId = dto.id;
      }
    }
  }

  return Array.from(map.values());
}
