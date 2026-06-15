package com.ecommerce.ecommerce.batch.scheduler;

import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
//@RequiredArgsConstructor
public class DailySalesScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailySalesScheduler.class);

    private final JobLauncher jobLauncher;
    private final Job dailySalesJob;

    public DailySalesScheduler(JobLauncher jobLauncher,Job dailySalesJob) {
        this.jobLauncher = jobLauncher;
        this.dailySalesJob = dailySalesJob;
    }

    @Scheduled(cron = "${app.batch.schedule}")
    public void runDailySalesJob() {
        launchJob();
    }

    // Called by both the scheduler and the controller
    public void launchJob() {
        try {
            // Unique parameters allow the same job to run multiple times
            JobParameters params = new JobParametersBuilder()
                    .addLocalDateTime("runAt", LocalDateTime.now())
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(dailySalesJob, params);
            log.info("category=BATCH event=daily_sales_job_started status={}", execution.getStatus());

        } catch (Exception e) {
        	throw new RuntimeException("failed to launch daily sales job: " + e.getMessage());
        }
    }
}
