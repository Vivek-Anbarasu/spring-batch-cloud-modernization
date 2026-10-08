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
        final AtomicInteger counter = new AtomicInteger(0);
        final Iterator<CreditRecord> iterator = Stream.generate(() -> {
            int id = counter.getAndIncrement();
            double debt = (id == 15000) ? -500.0 : (Math.random() * 400000);
            return new CreditRecord("ACC-" + id, "Corporate Entity " + id, debt, 500000.0);
        }).limit(100000).iterator();

        return () -> iterator.hasNext() ? iterator.next() : null;
    }

    @Bean
    public ItemWriter<RiskEvaluation> customWriter() {
        return chunk -> {
            System.out.printf("[AKS Batch Pod Execution] Committed chunk of %d records seamlessly to storage.%n", chunk.size());
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
