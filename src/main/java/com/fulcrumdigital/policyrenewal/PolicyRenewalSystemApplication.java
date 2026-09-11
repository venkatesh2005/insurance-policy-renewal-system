package com.fulcrumdigital.policyrenewal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// NOTE: @EnableBatchProcessing is deliberately NOT used here.
// spring-boot-starter-batch already autoconfigures JobRepository,
// JobLauncher, JobExplorer, and the transaction manager for you.
// Adding @EnableBatchProcessing manually switches on Spring Batch's
// own raw configuration instead of Boot's, which is the most common
// real-world cause of subtle duplicate/conflicting bean definitions
// in Batch + Boot projects, and it also disconnects application.yml's
// spring.batch.* properties (like job.enabled=false) from the beans
// that are meant to read them.
@SpringBootApplication
@EnableScheduling
public class PolicyRenewalSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(PolicyRenewalSystemApplication.class, args);
    }
}