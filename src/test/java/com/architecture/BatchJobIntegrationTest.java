package com.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs the real job against the real H2-backed job repository.
 *
 * <p>Deliberately not mocked: the interesting behaviour is the interaction between the step's
 * chunking, the fault-tolerant skip policy and the job repository, and none of that exists when
 * the launcher is stubbed out.
 *
 * <p>{@code spring.batch.job.enabled=false} stops Spring Boot from launching the job during
 * context startup, so each test controls when it runs and with which parameters.
 */
@SpringBootTest(properties = "spring.batch.job.enabled=false")
class BatchJobIntegrationTest {

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private Job cloudModernizationJob;

    @Test
    @DisplayName("reads 100,000 records, skips the one corrupt row and commits 20 chunks")
    void processesEveryRecordAndSkipsTheCorruptOne() throws Exception {
        JobExecution execution = jobOperator.start(cloudModernizationJob, uniqueParameters());

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        StepExecution step = migrationStepOf(execution);

        assertEquals(100_000, step.getReadCount(), "the reader did not produce the full dataset");
        assertEquals(99_999, step.getWriteCount(), "the writer did not receive every valid record");
        assertEquals(1, step.getSkipCount(), "exactly one record should have been skipped");
        assertEquals(1, step.getProcessSkipCount(), "the skip should have happened in the processor");
        assertEquals(0, step.getRollbackCount(), "a skipped row must not roll back the chunk");
        assertEquals(20, step.getCommitCount(),
                "99,999 items at a chunk size of 5,000 is 20 commits");
        assertEquals(BatchStatus.COMPLETED, step.getStatus());
    }

    /**
     * Two executions of the same job with different parameters must each process the whole
     * dataset. This is what catches a reader whose counter is not reset in {@code open()}: the
     * second run would report COMPLETED while reading zero records.
     */
    @Test
    @DisplayName("a second execution with new parameters processes the dataset again")
    void resetsTheReaderBetweenExecutions() throws Exception {
        StepExecution first = migrationStepOf(
                jobOperator.start(cloudModernizationJob, uniqueParameters()));
        StepExecution second = migrationStepOf(
                jobOperator.start(cloudModernizationJob, uniqueParameters()));

        assertEquals(100_000, first.getReadCount());
        assertEquals(100_000, second.getReadCount(), "the reader was not reset for the second run");
    }

    /**
     * Spring Batch refuses to run a completed job instance again. That guard is what makes the
     * job safe to retry from a scheduler: a duplicate trigger cannot double-post the output.
     */
    @Test
    @DisplayName("refuses to re-run a completed job instance")
    void refusesToRerunACompletedInstance() throws Exception {
        JobParameters parameters = uniqueParameters();

        jobOperator.start(cloudModernizationJob, parameters);

        assertThrows(JobInstanceAlreadyCompleteException.class,
                () -> jobOperator.start(cloudModernizationJob, parameters));
    }

    private static JobParameters uniqueParameters() {
        return new JobParametersBuilder()
                .addLong("run.id", System.nanoTime())
                .toJobParameters();
    }

    private static StepExecution migrationStepOf(JobExecution execution) {
        return execution.getStepExecutions().stream()
                .filter(step -> "migrationStep".equals(step.getStepName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("migrationStep did not run"));
    }
}
