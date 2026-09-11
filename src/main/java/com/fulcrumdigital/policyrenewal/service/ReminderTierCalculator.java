package com.fulcrumdigital.policyrenewal.service;

import com.fulcrumdigital.policyrenewal.entity.enums.ReminderTier;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// Pure domain logic: given a policy's end date and today's date,
// returns which reminder tier applies — or null if none yet.
// Lives in service/ because it has no Spring Batch dependency;
// it's a business calculation used by the detection job's processor.
@Component
public class ReminderTierCalculator {

    public String calculateTier(LocalDate endDate, LocalDate today) {
        long daysLeft = ChronoUnit.DAYS.between(today, endDate);

        if (daysLeft < 0) {
            return ReminderTier.OVERDUE;
        } else if (daysLeft <= 7) {
            return ReminderTier.DAY_7;
        } else if (daysLeft <= 15) {
            return ReminderTier.DAY_15;
        } else if (daysLeft <= 30) {
            return ReminderTier.DAY_30;
        }
        return null;
    }
}
