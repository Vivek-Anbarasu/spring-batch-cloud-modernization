package com.architecture.config;

import com.architecture.domain.CreditRecord;
import com.architecture.domain.RiskEvaluation;
import com.architecture.processor.CreditRiskProcessor;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;

import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Configuration
public class BatchConfiguration {

    @Bean
    public ItemReader<CreditRecord> customReader() {
        final int TOTAL_RECORDS = 100000;
        final AtomicInteger counter = new AtomicInteger(0);

        return () -> {
            // Increment the atomic counter safely first
            int id = counter.getAndIncrement();

            // Immediately return null once the strict boundary limit is reached
            if (id >= TOTAL_RECORDS) {
                return null;
            }

            // Each worker thread generates its allocated record independently and concurrently without locks
            double debt = (id == 15000) ? -500.0 : java.util.concurrent.ThreadLocalRandom.current().nextDouble(0, 400000);
            return new CreditRecord("ACC-" + id, "Corporate Entity " + id, debt, 5000.0);
        };
    }

    @Bean
    public ItemWriter<RiskEvaluation> customWriter() {
        return chunk -> {
            IO.println("[AKS Batch Pod Execution] Committed chunk of %d records seamlessly to storage.".formatted(chunk.size()));
        };
    }

    @Bean
    public Step migrationStep(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              ItemReader<CreditRecord> reader,
                              CreditRiskProcessor processor,
                              ItemWriter<RiskEvaluation> writer) {
        return new StepBuilder("migrationStep", jobRepository)
                .<CreditRecord, RiskEvaluation>chunk(5000)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .transactionManager(transactionManager)
                .faultTolerant()
                .skip(IllegalArgumentException.class)
                .skipLimit(10)
                .build();
    }


    @Bean
    public Job cloudModernizationJob(JobRepository jobRepository, Step migrationStep) {
        return new JobBuilder("cloudModernizationJob", jobRepository)
                .start(migrationStep)
                .build();
    }
}
