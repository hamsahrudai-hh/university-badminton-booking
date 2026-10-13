package edu.university.badminton.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Spring configuration providing an injectable {@link Clock} bean.
 * <p>
 * Using an injectable Clock allows time-dependent queue opening, admission,
 * and opportunity expiry logic to be evaluated with the authoritative university timezone
 * and tested deterministically without thread sleeps.
 */
@Configuration
public class ClockConfig {

    @Value("${app.timezone:Asia/Kolkata}")
    private String timezone;

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of(timezone.trim()));
    }
}

