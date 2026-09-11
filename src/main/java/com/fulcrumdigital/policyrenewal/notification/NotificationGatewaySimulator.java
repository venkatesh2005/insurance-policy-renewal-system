package com.fulcrumdigital.policyrenewal.notification;

import com.fulcrumdigital.policyrenewal.entity.ReminderOutbox;
import com.fulcrumdigital.policyrenewal.entity.enums.NotificationOutcome;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class NotificationGatewaySimulator {

    private final Random random = new Random();

    public String send(ReminderOutbox event) {
        int roll = random.nextInt(3);
        return switch (roll) {
            case 0  -> NotificationOutcome.SUCCESS;
            case 1  -> NotificationOutcome.INVALID_CONTACT;
            default -> NotificationOutcome.GATEWAY_TIMEOUT;
        };
    }
}
