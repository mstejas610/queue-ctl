# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

QueueCTL is a CLI-based job queue system in Java that provides background job processing with automatic retries, exponential backoff, dead letter queue (DLQ), file-based persistence, and multi-worker support. It has no external service dependencies — only Java and the file system.

## Build & Test Commands

```bash
mvn clean package          # Build executable fat JAR (target/queuectl-1.0-SNAPSHOT.jar)
mvn test                   # Run all tests
mvn test -Dtest=JobServiceTest              # Run a single test class
mvn test -Dtest=JobServiceTest#testMethod   # Run a single test method
```

Java 17+ and Maven 3.6+ are required.

## Running the CLI

```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar <command>
# or via wrapper scripts:
./queuectl <command>
```

## Architecture

The system uses a layered architecture with constructor-based dependency injection:

```
CLI (CLI.java) — entry point, command parsing via switch statements
  ├→ JobService → JobRepository → JSON files in queuectl-data/jobs/
  ├→ WorkerService → spawns WorkerRunnable as separate JVM processes
  └→ ConfigRepository → queuectl-data/config.json
```

**Key architectural decisions:**

- **Process isolation**: Each worker runs as a separate JVM process (via ProcessBuilder), so worker crashes don't affect the main process.
- **File-based locking**: Concurrency control uses atomic `File.createNewFile()` for lock files (`job-{id}.lock`). No external lock service needed.
- **Polling model**: Workers poll for pending jobs every 1-2 seconds rather than using file system watchers.
- **JSON storage**: All data stored as human-readable JSON files under `queuectl-data/`.

**Job lifecycle**: PENDING → PROCESSING → COMPLETED (on success) or back to PENDING with backoff delay (on failure). After exhausting `maxRetries`, jobs move to DEAD (DLQ). DLQ jobs can be retried via `dlq retry`.

**Backoff formula**: `delay = backoffBase ^ attempts` seconds (configurable via `config set backoff-base`).

**Command execution**: Uses `sh -c` on Unix, `cmd /c` on Windows, with a 5-minute timeout per command.

## Test Structure

Tests use JUnit 5 with temporary directories for isolation. Each test class has `@BeforeEach`/`@AfterEach` for setup and cleanup via recursive file deletion.

```
src/test/java/com/queuectl/
├── model/          # Job and Config model tests
├── repository/     # Persistence and locking tests
├── service/        # Business logic tests
├── worker/         # Backoff calculation, job processing tests
└── integration/    # End-to-end workflow tests
```
