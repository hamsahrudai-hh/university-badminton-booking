package edu.university.badminton.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's background task scheduling for the weekly credit reset when explicitly activated.
 * Controlled by the property {@code app.credit-reset.scheduler.enabled}.
 * By default, this is inactive (disabled) to ensure safe startup.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.credit-reset.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class CreditResetSchedulingConfig {
}
