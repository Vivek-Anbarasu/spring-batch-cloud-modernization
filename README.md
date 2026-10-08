# Spring Batch Cloud Modernization Pipeline

[![Java Version](https://img.shields.io/badge/Java-27-orange.svg)](https://jdk.java.net/27/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Infrastructure](https://img.shields.io/badge/Target%20Env-Kubernetes%20%2F%20AKS-blue.svg)](https://azure.microsoft.com/en-us/products/kubernetes-service)

A production-grade, memory-optimized financial data processing engine engineered with **Spring Boot 4.1.1** and **Java 27**. This repository serves as an architectural blueprint for migrating legacy monolith scheduled processes (e.g., Struts / Sybase / Autosys) into modern, containerized, cloud-native transient jobs running on **Azure Kubernetes Service (AKS)**.

---

## 🏗️ Architectural Blueprints

The pipeline models a high-throughput financial batch system designed to process **100,000 corporate credit accounts** via memory-optimized transaction chunks, implementing structural fault tolerance.

```
 [ 100,000 Inbound Records ] ──► [ Custom Streaming Reader ]
                                           │
                                           ▼ (Chunk-Size: 5,000)
                                 [ Fault-Tolerant Step ]
                                           │
          ┌────────────────────────────────┴────────────────────────────────┐
          ▼ (Process)                                                       ▼ (Fault Handling)
 [ CreditRiskProcessor ]                                           [ Skip & Retry Gating ]
  - Java 27 Pattern Match                                           - Captures Data Anomalies
  - Dynamic Risk Tiering                                            - Max Skip Limit: 10
          │
          ▼ (Write)
 [ High-Throughput Writer ] ──► [ Ephemeral Storage Commit ] ──► [ Clean Container Exit ]
```

### Key Architectural Design Patterns
1. **Chunk-Oriented Processing:** Configured with an optimized chunk boundary allocation of **5,000 records per transaction block**. This balances JVM memory utilization with thread-lock overhead, matching specific cloud resource optimization patterns.
2. **Fault-Tolerant Resilience:** Implements an explicit `.faultTolerant().skip(IllegalArgumentException.class).skipLimit(10)` boundary. This isolates dirty upstream data anomalies (such as negative balances) seamlessly without blowing up the core container runtimes mid-job.
3. **Java 27 Type Guard Rails:** Replaces archaic nested loops and legacy if-else conditional branches inside the business layer with modern Java 27 **Pattern Matching for Switch Expressions**, making complex data evaluation highly performant and readable.
4. **Transient Cloud Lifecycle:** Built around a self-terminating runtime loop (`System.exit(SpringApplication.exit(context))`). This ensures that the application spins up, processes data chunks, and cleanly shuts down, completely eliminating idle container hosting costs on **AKS / Kubernetes clusters**.

---

## ⚙️ Prerequisites & Environment Baseline

* **Java Development Kit (JDK) 27** or higher.
* **Apache Maven 3.6.3** or higher.

---

## 🚀 Step-by-Step Getting Started

### 1. Clone the Modernized Repository
```bash
git clone https://github.com/Vivek-Anbarasu/spring-batch-cloud-modernization.git
cd spring-batch-cloud-modernization
```

### 2. Package the Application Artifact
Compile the codebase and bundle it into a runnable package. The system utilizes global properties to guarantee platform-independent builds across Linux/macOS environments and Windows architectures:
```bash
mvn clean package
```

### 3. Execute the Cloud Native Batch
Launch the self-terminating processing loop locally:
```bash
mvn spring-boot:run
```

---

## 📋 Technical Implementation Insights

### Modern Java 27 Pattern-Matching Processor
The execution layer harnesses advanced compiler evaluation features to map account ratios safely to risk profiles without side-effects:
```java
    String tier = switch (Double.valueOf(ratio)) {
    case Double r when r >= 0.7 -> "HIGH_RISK";
    case Double r when r >= 0.4 -> "MEDIUM_RISK";
    case Double r when r >= 0.0 -> "LOW_RISK";
    default -> "UNCLASSIFIED_TIER";
};
```

### Configuration Performance Matrix
Under `src/main/resources/application.properties`, the embedded metadata catalog boundaries are localized for light memory orchestration footprints:
* `spring.batch.job.enabled=true` (Triggers job automation instantly upon cluster initialization pods). 
* `spring.batch.job.enabled=false` - Production it should be false and to be scheduled with cron timing and triggered from Autosys or Airflow  or Kubernetes CronJobs.
* `spring.batch.jdbc.initialize-schema=always` (Configures in-memory tracking structures instantly without pre-existing schemas).

---

##  Sample Autosys trigger command

java -jar spring-batch-cloud-modernization.jar --spring.batch.job.name=creditMigrationJob runDate=2026-10-08

## 📊 Sample Pod Output Log

Upon execution, the processing console logs out step chunks transparently before executing self-termination:

```text
  .   ____          _            __ _ _
 /\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  \ \ \ \
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v4.1.1)

[AKS Batch Pod Execution] Committed chunk of 5000 records seamlessly to storage.
[AKS Batch Pod Execution] Committed chunk of 5000 records seamlessly to storage.
...
[AKS Batch Pod Execution] Committed chunk of 5000 records seamlessly to storage.
Job: [SimpleJob: name=cloudModernizationJob] completed with the following status: [COMPLETED] in 1s 435ms
```
