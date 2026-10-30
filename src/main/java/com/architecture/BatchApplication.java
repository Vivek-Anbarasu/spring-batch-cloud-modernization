package com.architecture;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class BatchApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(BatchApplication.class, args);
        // A batch pod exists to run one job and stop. Exiting with the job's status is what lets
        // the CronJob / Autosys scheduler see success or failure instead of a pod that lingers.
        int exitCode = SpringApplication.exit(context);
        System.exit(exitCode);
    }
}
