package com.fulcrumdigital.policyrenewal.controller;

import com.fulcrumdigital.policyrenewal.config.ClockConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;

// Demo-only controller: lets the app's simulated "today" be moved forward
// via an API call, instead of waiting real days for reminders/lapses to
// naturally trigger. Every date calculation in the app (Job 1, Job 2, the
// poller's backoff, renewal timestamps) already reads from the injected
// Clock bean, so advancing it here changes behavior everywhere at once.
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final Clock clock;

    @GetMapping("/today")
    public String today() {
        return LocalDate.now(clock).toString();
    }

    @PostMapping("/advance-clock")
    public String advanceClock(@RequestParam String date) {
        ClockConfig.advanceTo(LocalDate.parse(date));
        return "Simulated date set to " + date;
    }

    @PostMapping("/reset-clock")
    public String resetClock() {
        ClockConfig.resetToSystemClock();
        return "Clock reset to system time";
    }
}
