package edu.university.badminton.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NoShowSchedulerIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class, NoShowScheduler.class)
            .withBean(edu.university.badminton.service.CheckInService.class, () -> org.mockito.Mockito.mock(edu.university.badminton.service.CheckInService.class));

    @Test
    @DisplayName("Scheduler bean is NOT present by default when app.no-show.scheduler.enabled is absent (Safe Default)")
    void shouldNotLoadSchedulerByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(NoShowScheduler.class);
            assertThat(context).doesNotHaveBean(SchedulingConfig.class);
        });
    }

    @Test
    @DisplayName("Scheduler bean is NOT present when app.no-show.scheduler.enabled=false")
    void shouldNotLoadSchedulerWhenDisabledExplicitly() {
        contextRunner
                .withPropertyValues("app.no-show.scheduler.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(NoShowScheduler.class);
                    assertThat(context).doesNotHaveBean(SchedulingConfig.class);
                });
    }

    @Test
    @DisplayName("Scheduler bean IS loaded and registered when app.no-show.scheduler.enabled=true")
    void shouldLoadSchedulerWhenEnabledExplicitly() {
        contextRunner
                .withPropertyValues("app.no-show.scheduler.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(NoShowScheduler.class);
                    assertThat(context).hasSingleBean(SchedulingConfig.class);
                });
    }
}
