package com.fulcrumdigital.policyrenewal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    // IMPORTANT: the bean below is a single DelegatingClock instance,
    // created once at startup and injected everywhere. advanceTo() mutates
    // this instance's internal delegate in place, so every class that
    // already holds a reference to it immediately sees the new simulated
    // date. A plain `Clock.fixed(...)` reassigned to a static field would
    // NOT achieve this — beans injected before the reassignment would keep
    // pointing at the old Clock object forever, since Spring copies the
    // reference once at injection time, not on every read.
    private static final DelegatingClock DELEGATING_CLOCK =
            new DelegatingClock(Clock.systemDefaultZone());

    @Bean
    public Clock clock() {
        return DELEGATING_CLOCK;
    }

    public static void advanceTo(LocalDate date) {
        DELEGATING_CLOCK.setDelegate(
                Clock.fixed(date.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    }

    public static void resetToSystemClock() {
        DELEGATING_CLOCK.setDelegate(Clock.systemDefaultZone());
    }

    // A Clock that forwards every call to whatever inner Clock is
    // currently set. Swapping the inner delegate changes behavior for
    // every class that already holds a reference to this outer wrapper.
    private static class DelegatingClock extends Clock {
        private volatile Clock delegate;

        DelegatingClock(Clock delegate) {
            this.delegate = delegate;
        }

        void setDelegate(Clock delegate) {
            this.delegate = delegate;
        }

        @Override
        public ZoneId getZone() {
            return delegate.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return delegate.withZone(zone);
        }

        @Override
        public Instant instant() {
            return delegate.instant();
        }
    }
}
