package edu.university.badminton.scheduler;

import edu.university.badminton.dto.NoShowProcessingResponseDto;
import edu.university.badminton.service.CheckInService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background scheduled job that periodically detects and penalizes no-show players
 * who missed the 10-minute check-in grace period deadline.
 * <p>
 * Safe activation:
 * - Inactive by default ({@code matchIfMissing = false}).
 * - Activated via {@code app.no-show.scheduler.enabled=true}.
 * - Runs approximately once per minute by default ({@code 0 * * * * *}).
 */
@Component
@ConditionalOnProperty(name = "app.no-show.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class NoShowScheduler {

    private static final Logger log = LoggerFactory.getLogger(NoShowScheduler.class);

    private final CheckInService checkInService;

    public NoShowScheduler(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    /**
     * Executes automatic no-show processing approximately once per minute.
     * Uses server-side time via {@link java.time.Clock} configured in {@link CheckInService}.
     */
    @Scheduled(cron = "${app.no-show.scheduler.cron:0 * * * * *}")
    public void runAutomaticNoShowProcessing() {
        log.debug("Executing scheduled automatic no-show processing check...");
        try {
            NoShowProcessingResponseDto result = checkInService.processNoShows(null, "SYSTEM_AUTOMATIC_SCHEDULER");
            if (result.processedCount() > 0) {
                log.info("Scheduled no-show run processed and penalized {} player(s)", result.processedCount());
            } else {
                log.debug("Scheduled no-show run found 0 players beyond grace period deadline");
            }
        } catch (Exception e) {
            log.error("Scheduled automatic no-show processing encountered an unexpected error: {}", e.getMessage(), e);
        }
    }
}
