package edu.university.badminton.dto;

import java.time.Instant;

/**
 * Summary DTO returned after executing or checking a weekly student credit reset.
 */
public record CreditResetResultDto(
        String periodKey,
        Instant executedAt,
        int studentsResetCount,
        boolean executed,
        boolean alreadyExecuted,
        String triggeredBy,
        String message
) {

    public static CreditResetResultDto success(String periodKey, Instant executedAt, int count, String triggeredBy) {
        return new CreditResetResultDto(
                periodKey,
                executedAt,
                count,
                true,
                false,
                triggeredBy,
                "Weekly credits successfully reset to 6 for " + count + " eligible student(s)."
        );
    }

    public static CreditResetResultDto skippedAlreadyExecuted(String periodKey, Instant executedAt, int count, String triggeredBy) {
        return new CreditResetResultDto(
                periodKey,
                executedAt,
                count,
                false,
                true,
                triggeredBy,
                "Weekly credit reset for period " + periodKey + " was already executed earlier; skipped duplicate run."
        );
    }
}
