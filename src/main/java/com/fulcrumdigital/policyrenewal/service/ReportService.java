package com.fulcrumdigital.policyrenewal.service;

import com.fulcrumdigital.policyrenewal.entity.enums.NotificationOutcome;
import com.fulcrumdigital.policyrenewal.entity.enums.PolicyStatus;
import com.fulcrumdigital.policyrenewal.entity.enums.ReminderTier;
import com.fulcrumdigital.policyrenewal.repository.NotificationLogRepository;
import com.fulcrumdigital.policyrenewal.repository.PolicyRepository;
import com.fulcrumdigital.policyrenewal.repository.ReminderDlqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final PolicyRepository policyRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final ReminderDlqRepository dlqRepository;
    private final Clock clock;

    @Value("${report.output-dir:reports}")
    private String outputDir;

    private static final List<String> TIERS =
            List.of(ReminderTier.DAY_30, ReminderTier.DAY_15, ReminderTier.DAY_7, ReminderTier.OVERDUE);

    public void generateAndWriteReport() {
        LocalDate today = LocalDate.now(clock);

        long activeCount       = policyRepository.countPoliciesByStatus(PolicyStatus.ACTIVE);
        long renewalDueCount   = policyRepository.countPoliciesByStatus(PolicyStatus.RENEWAL_DUE);
        long lapsedCount       = policyRepository.countPoliciesByStatus(PolicyStatus.LAPSED);
        long renewedTodayCount = policyRepository.countPoliciesRenewedOn(today);

        StringBuilder csv = new StringBuilder();
        csv.append("Policy Funnel Report - ").append(today).append("\n\n");
        csv.append("Status,Count\n");
        csv.append("ACTIVE,").append(activeCount).append("\n");
        csv.append("RENEWAL_DUE,").append(renewalDueCount).append("\n");
        csv.append("RENEWED_TODAY,").append(renewedTodayCount).append("\n");
        csv.append("LAPSED,").append(lapsedCount).append("\n\n");
        csv.append("Tier,Sent,FailedInvalidContact,DeadLettered\n");

        log.info("Renewal Funnel Report ({}): ACTIVE={} RENEWAL_DUE={} RENEWED_TODAY={} LAPSED={}",
                today, activeCount, renewalDueCount, renewedTodayCount, lapsedCount);

        for (String tier : TIERS) {
            long sent         = notificationLogRepository.countByTierAndOutcome(tier, NotificationOutcome.SUCCESS);
            long failed       = notificationLogRepository.countByTierAndOutcome(tier, NotificationOutcome.INVALID_CONTACT);
            long deadLettered = dlqRepository.countByTier(tier);

            csv.append(tier).append(",").append(sent).append(",")
               .append(failed).append(",").append(deadLettered).append("\n");

            log.info("  Tier {}: sent={} failedInvalidContact={} deadLettered={}",
                    tier, sent, failed, deadLettered);
        }

        writeToFile(today, csv.toString());
    }

    private void writeToFile(LocalDate date, String content) {
        try {
            Path dir  = Paths.get(outputDir);
            Files.createDirectories(dir);
            Path file = dir.resolve("funnel-report-" + date + ".csv");
            Files.writeString(file, content);
            log.info("Funnel report written to {}", file.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write funnel report file", e);
        }
    }
}
