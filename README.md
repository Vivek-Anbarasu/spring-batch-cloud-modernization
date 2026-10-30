# Spring Batch Cloud Modernization

[![CI](https://github.com/Vivek-Anbarasu/spring-batch-cloud-modernization/actions/workflows/ci.yml/badge.svg)](https://github.com/Vivek-Anbarasu/spring-batch-cloud-modernization/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-27-orange.svg)](https://jdk.java.net/27/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A reference implementation of the migration path most large enterprises still have ahead of them:
a legacy scheduled extract (Struts / Sybase / Autosys) turned into a **transient, self-terminating
Kubernetes job** that processes a bounded dataset, reports what it did, and exits.

The domain is deliberately boring — 100,000 corporate credit accounts scored into risk tiers — so
the interesting parts are visible: chunking, fault tolerance, restart semantics, and what the pod
looks like once it is a `CronJob` instead of a daemon.

## What it does

```
  100,000 synthetic accounts
            │
            ▼
  ItemStreamReader ── generates on demand, never loads the dataset
            │
            ▼  chunk of 5,000
  CreditRiskProcessor ── ratio → HIGH / MEDIUM / LOW
            │              throws IllegalArgumentException on a negative balance
            ▼
  fault-tolerant step ── skip(IllegalArgumentException), limit 10
            │
            ▼
  ItemWriter ── one log line per committed chunk
            │
            ▼
  reconciliation line ── read=100000 written=99999 skipped=1
            │
            ▼
  SpringApplication.exit(context) ── pod exits with the job's status
```

## Design decisions

**Chunk size of 5,000, not 500 or 50,000.** The chunk is the unit of transaction and of restart.
Smaller chunks mean more commits and more round trips to the job repository; larger chunks mean a
longer rollback window and a bigger in-memory working set. 5,000 gives 20 commits for this dataset
— a number the integration test asserts, so a change to the chunk size cannot pass unnoticed.

**The skip policy names one exception type.** `.skip(IllegalArgumentException.class)` covers bad
data (a negative balance). It deliberately does not cover `Exception`: a skip policy that catches
everything turns a database outage into a `COMPLETED` job that quietly lost 40,000 rows. The skip
limit of 10 is the second half of that guard — beyond ten bad rows the job fails, because at that
point the input is broken rather than dirty.

**The reader is an `ItemStreamReader`, not a lambda.** A lambda's counter survives between
executions, so a restart inside the same JVM would resume from an exhausted counter and report a
successful run that read zero records. Resetting in `open()` is what makes the reader restartable,
and `BatchJobIntegrationTest.resetsTheReaderBetweenExecutions` fails if that reset is removed.

**The job reports its own reconciliation.** Skips are silent by default, and a silently skipped row
is a lost row. The job listener logs `read / written / skipped` at the end of every run, which is
the line an operator actually looks for:

```
Step migrationStep finished COMPLETED: read=100000, written=99999, skipped=1
```

**Java 27 is the target, not a requirement.** The build pins `<maven.compiler.release>27</maven.compiler.release>`
to match the runtime image. The code itself uses nothing newer than pattern matching for switch with
guards, which was finalised in Java 21 — so the release level can be lowered without touching the
source. Saying so is more useful than implying the pipeline depends on a language feature it doesn't.

## Measured

One run on a laptop, with the synthetic in-memory reader and H2:

```
Step migrationStep finished COMPLETED: read=100000, written=99999, skipped=1
Job: [SimpleJob: [name=cloudModernizationJob]] completed with the following status: [COMPLETED] in 187ms
```

This is a correctness measurement, not a throughput benchmark: the reader generates records instead
of reading them and the writer logs instead of writing, so the number says nothing about a real
extract. What it does show is 20 chunk commits, one skip, and a clean exit.

## Build and run

Requires JDK 27 and Maven 3.6.3+.

```bash
mvn clean package
java -jar target/spring-batch-cloud-modernization-1.0.0.jar
```

`spring.batch.job.enabled=true` in `application.properties` means the job runs on startup and the
application exits when it finishes. To select a job explicitly, or to pass parameters from a
scheduler:

```bash
java -jar target/spring-batch-cloud-modernization-1.0.0.jar \
  --spring.batch.job.name=cloudModernizationJob \
  --runDate=2026-10-08
```

Note the `--` on the parameter: `runDate=...` without it is an application argument, not a job
parameter, and will not identify the job instance.

## Tests

```bash
mvn test     # 17 tests
```

| Test | What it pins down |
| --- | --- |
| `CreditRecordTest` (4) | ratio arithmetic, and the zero/negative-asset guard that would otherwise be a division by zero |
| `CreditRiskProcessorTest` (10) | every tier boundary from both sides (0.70 is HIGH, 0.6999 is MEDIUM), status derivation, and that a negative balance throws the exact type the step skips |
| `BatchJobIntegrationTest` (3) | the real job against a real H2 job repository: 100,000 read, 99,999 written, 1 skipped in the processor, 0 rollbacks, 20 commits; a second execution still reads the full dataset; a completed job instance cannot be run twice |

The integration test is not mocked. The behaviour worth testing — chunking, skip handling and the
job repository — only exists when the launcher, the step and the repository are all real.

## Deploying to Kubernetes

The `spring-batch-cloud-modernization-helm/` chart deploys the job as a `CronJob`.

```bash
helm lint spring-batch-cloud-modernization-helm
helm template my-release spring-batch-cloud-modernization-helm -f spring-batch-cloud-modernization-helm/values-prod.yaml
helm upgrade --install my-release spring-batch-cloud-modernization-helm \
  -f spring-batch-cloud-modernization-helm/values-prod.yaml \
  --namespace batch --create-namespace
```

Three things in the chart are worth calling out, because each is a common way these deployments
break:

- **JVM flags go through `JAVA_TOOL_OPTIONS`, not `args`.** The image entrypoint is
  `java org.springframework.boot.loader.launch.JarLauncher`. Anything passed in `args` lands after
  the main class and is read as a program argument, so `-Xmx` is silently ignored.
- **`initialize-schema` is `never` in production.** Creating the batch metadata tables from every
  pod means concurrent pods racing on DDL against a shared database — and usually a pod that has no
  permission to run it. The tables are owned by the release migration.
- **The database password is not in the chart.** Production and SIT mount a Key Vault secret through
  the Secrets Store CSI driver and reference the materialised Secret by name
  (`datasource.existingSecret`). The local default is a throwaway H2 instance.

CI lints and renders the chart for the default, SIT and production values, because a chart that
lints is not the same as a chart that renders.

## Known limitations

Stated plainly, because a reference implementation that pretends to be finished is worse than one
that is honest about its edges:

- The reader is synthetic and the writer logs. Replacing them with a real extract and a
  `JdbcBatchItemWriter` is the work this repository is a template for, not work it has done.
- The step is single-threaded. Partitioning or a multi-threaded step would change the throughput
  story and the job repository load, and neither is demonstrated here.
- No container image is built in CI. The chart expects `image.repository`/`image.tag` to point at
  an image you have built.
- No actuator dependency, so there is no `/actuator/prometheus`; the monitoring annotations are
  therefore disabled in every environment.

## Licence

MIT — see [LICENSE](LICENSE).
