package com.architecture.config;

import com.architecture.domain.CreditRecord;
import com.architecture.domain.RiskEvaluation;
import com.architecture.processor.CreditRiskProcessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class BatchConfiguration {

    /** How many synthetic accounts the reader produces per execution. */
    static final int TOTAL_RECORDS = 100_000;

    /** One deliberately corrupt row, so the skip path is exercised on every run. */
    static final int CORRUPT_RECORD_ID = 15_000;

    private static final Logger log = LoggerFactory.getLogger(BatchConfiguration.class);

    /**
     * Stands in for the upstream extract. Generates records on demand rather than loading them,
     * which is what keeps the heap flat regardless of dataset size.
     *
     * <p>Implemented as an {@link ItemStreamReader} rather than a bare lambda because the counter
     * has to be reset in {@link ItemStreamReader#open}: a lambda would keep its counter across
     * executions, so a restart inside the same JVM would resume from an exhausted counter and
     * report a successful job that processed nothing.
     */
    @Bean
    public ItemReader<CreditRecord> customReader() {
        return new ItemStreamReader<>() {

            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public void open(ExecutionContext context) {
                counter.set(0);
            }

            @Override
            public CreditRecord read() {
                int id = counter.getAndIncrement();
                if (id >= TOTAL_RECORDS) {
                    return null;
                }
                // The ratio the processor derives is debt / assets, so assets are held constant
                // at 5,000 and the debt spread over 0..400,000 gives ratios from 0.0 to 80.0.
                double debt = (id == CORRUPT_RECORD_ID)
                        ? -500.0
                        : ThreadLocalRandom.current().nextDouble(0, 400_000);
                return new CreditRecord("ACC-" + id, "Corporate Entity " + id, debt, 5_000.0);
            }
        };
    }

    @Bean
    public ItemWriter<RiskEvaluation> customWriter() {
        return chunk -> log.info("Committed chunk of {} risk evaluations", chunk.size());
    }

    @Bean
    public Step migrationStep(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              ItemReader<CreditRecord> reader,
                              CreditRiskProcessor processor,
                              ItemWriter<RiskEvaluation> writer) {
        return new StepBuilder("migrationStep", jobRepository)
                .<CreditRecord, RiskEvaluation>chunk(5_000)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .transactionManager(transactionManager)
                .faultTolerant()
                // Only malformed data is skippable. Catching Exception here would turn an
                // infrastructure outage into a "successful" job that silently lost rows.
                .skip(IllegalArgumentException.class)
                .skipLimit(10)
                .build();
    }

    @Bean
    public Job cloudModernizationJob(JobRepository jobRepository, Step migrationStep) {
        return new JobBuilder("cloudModernizationJob", jobRepository)
                .start(migrationStep)
                .listener(new JobExecutionListener() {
                    @Override
                    public void afterJob(JobExecution execution) {
                        // The one thing an operator needs from a batch log is the reconciliation:
                        // how much was read, how much was written, and how much was dropped.
                        // Skips are silent by default, and a silently skipped row is a lost row.
                        execution.getStepExecutions().forEach(step -> log.info(
                                "Step {} finished {}: read={}, written={}, skipped={}",
                                step.getStepName(), step.getStatus(),
                                step.getReadCount(), step.getWriteCount(), step.getSkipCount()));
                    }
                })
                .build();
    }
}
