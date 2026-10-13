package edu.university.badminton.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's background task scheduling when explicitly activated.
 * Controlled by the property {@code app.no-show.scheduler.enabled}.
 * By default, this is inactive (disabled) to ensure safe startup.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.no-show.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class SchedulingConfig {
}
