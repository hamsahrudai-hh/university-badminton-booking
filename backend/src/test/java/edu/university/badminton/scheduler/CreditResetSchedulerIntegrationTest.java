package edu.university.badminton.scheduler;

import edu.university.badminton.service.CreditResetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CreditResetSchedulerIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CreditResetSchedulingConfig.class, WeeklyCreditResetScheduler.class)
            .withBean(CreditResetService.class, () -> mock(CreditResetService.class));

    @Test
    @DisplayName("Credit reset scheduler bean is NOT present by default when property is absent (Safe Default)")
    void shouldNotLoadSchedulerByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(WeeklyCreditResetScheduler.class);
            assertThat(context).doesNotHaveBean(CreditResetSchedulingConfig.class);
        });
    }

    @Test
    @DisplayName("Credit reset scheduler bean is NOT present when app.credit-reset.scheduler.enabled=false")
    void shouldNotLoadSchedulerWhenDisabledExplicitly() {
        contextRunner
                .withPropertyValues("app.credit-reset.scheduler.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(WeeklyCreditResetScheduler.class);
                    assertThat(context).doesNotHaveBean(CreditResetSchedulingConfig.class);
                });
    }

    @Test
    @DisplayName("Credit reset scheduler bean IS loaded and registered when app.credit-reset.scheduler.enabled=true")
    void shouldLoadSchedulerWhenEnabledExplicitly() {
        contextRunner
                .withPropertyValues("app.credit-reset.scheduler.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(WeeklyCreditResetScheduler.class);
                    assertThat(context).hasSingleBean(CreditResetSchedulingConfig.class);
                });
    }
}
