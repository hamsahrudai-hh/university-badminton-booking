package edu.university.badminton.scheduler;

import edu.university.badminton.dto.CreditResetResultDto;
import edu.university.badminton.service.CreditResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background scheduled job that performs the weekly student credit reset.
 * <p>
 * Configuration:
 * - Disabled by default ({@code matchIfMissing = false}).
 * - Activated via {@code app.credit-reset.scheduler.enabled=true}.
 * - Default cron: {@code 0 0 0 * * MON} (Monday at midnight 00:00:00).
 * - Timezone: evaluated with the university timezone (${app.timezone:Asia/Kolkata}).
 */
@Component
@ConditionalOnProperty(name = "app.credit-reset.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class WeeklyCreditResetScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyCreditResetScheduler.class);

    private final CreditResetService creditResetService;

    public WeeklyCreditResetScheduler(CreditResetService creditResetService) {
        this.creditResetService = creditResetService;
    }

    /**
     * Executes the weekly credit reset according to configured cron and timezone.
     */
    @Scheduled(cron = "${app.credit-reset.scheduler.cron:0 0 0 * * MON}", zone = "${app.timezone:Asia/Kolkata}")
    public void runWeeklyCreditReset() {
        log.info("Starting scheduled weekly student credit reset job...");
        try {
            CreditResetResultDto result = creditResetService.resetWeeklyCredits("SYSTEM_AUTOMATIC_SCHEDULER");
            if (result.executed()) {
                log.info("Scheduled weekly credit reset completed successfully for period [{}]. Reset {} student(s).",
                        result.periodKey(), result.studentsResetCount());
            } else {
                log.info("Scheduled weekly credit reset skipped: {}", result.message());
            }
        } catch (Exception e) {
            log.error("Scheduled weekly credit reset encountered an unexpected error: {}", e.getMessage(), e);
        }
    }
}
