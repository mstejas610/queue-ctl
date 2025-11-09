# QueueCTL Architecture & Design

## Overview

QueueCTL is a CLI-based job queue system built with a layered architecture that separates concerns and ensures maintainability. This document provides an in-depth look at the system design, data flow, and key implementation decisions.

---

## System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         CLI Layer                            │
│  (Command Parser, Argument Validation, Output Formatting)   │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                    Service Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ JobService   │  │WorkerService │  │ ConfigService│     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                  Repository Layer                            │
│  ┌──────────────┐  ┌──────────────┐                        │
│  │JobRepository │  │ConfigRepo    │                        │
│  └──────────────┘  └──────────────┘                        │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                  Storage Layer                               │
│         (JSON Files + File-based Locking)                   │
│  ┌──────────────────────────────────────────────────┐      │
│  │  queuectl-data/                                   │      │
│  │    ├── jobs/                                      │      │
│  │    │   ├── job-{id}.json                         │      │
│  │    │   └── job-{id}.lock                         │      │
│  │    └── config.json                                │      │
│  └──────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    Worker Processes                          │
│  (Separate JVM processes polling for pending jobs)          │
└─────────────────────────────────────────────────────────────┘
```

---

## Component Details

### 1. CLI Layer

**Responsibility**: User interface and command routing

**Key Classes**:
- `CLI.java` - Main entry point, command dispatcher

**Functions**:
- Parse command-line arguments
- Validate user input
- Route commands to appropriate services
- Format and display output
- Handle errors and display help

**Example Flow**:
```
User Input → CLI.main() → Command Parser → Service Call → Format Output
```

---

### 2. Service Layer

**Responsibility**: Business logic and orchestration

#### JobService

**Functions**:
- Enqueue new jobs with validation
- List jobs by state
- Calculate statistics
- Manage DLQ operations
- Apply configuration defaults

**Key Methods**:
```java
Job enqueueJob(String id, String command)
List<Job> listJobs(JobState state)
List<Job> getDLQJobs()
Job retryDLQJob(String jobId)
JobStats getStats()
```

#### WorkerService

**Functions**:
- Start worker processes
- Stop workers gracefully
- Track active workers
- Prevent duplicate worker instances

**Key Methods**:
```java
void startWorkers(int count)
void stopWorkers()
int getActiveWorkerCount()
boolean areWorkersRunning()
```

---

### 3. Repository Layer

**Responsibility**: Data persistence abstraction

#### JobRepository

**Functions**:
- CRUD operations for jobs
- File-based locking
- Query jobs by state
- Find jobs ready for execution

**Key Methods**:
```java
Job save(Job job)
Optional<Job> findById(String id)
List<Job> findByState(JobState state)
List<Job> findPendingJobsReadyForExecution()
boolean acquireLock(String jobId)
void releaseLock(String jobId)
```

**Locking Implementation**:
```java
public boolean acquireLock(String jobId) {
    File lockFile = new File(jobsDir, "job-" + jobId + ".lock");
    try {
        // Atomic operation: create file only if it doesn't exist
        if (lockFile.createNewFile()) {
            Files.writeString(lockFile.toPath(), 
                String.valueOf(ProcessHandle.current().pid()));
            return true;
        }
        return false;
    } catch (IOException e) {
        return false;
    }
}
```

#### ConfigRepository

**Functions**:
- Load configuration
- Save configuration
- Provide defaults

---

### 4. Storage Layer

**Responsibility**: Physical data storage

**Structure**:
```
queuectl-data/
├── jobs/
│   ├── job-550e8400-e29b-41d4-a716-446655440000.json
│   ├── job-550e8400-e29b-41d4-a716-446655440000.lock
│   ├── job-660e8400-e29b-41d4-a716-446655440001.json
│   └── ...
└── config.json
```

**Job File Format**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "command": "echo Hello",
  "state": "COMPLETED",
  "attempts": 0,
  "maxRetries": 3,
  "createdAt": "2025-11-08T10:30:00Z",
  "updatedAt": "2025-11-08T10:30:05Z",
  "nextRetryAt": null
}
```

**Config File Format**:
```json
{
  "maxRetries": 3,
  "backoffBase": 2
}
```

---

### 5. Worker Processes

**Responsibility**: Job execution

**Key Class**: `WorkerRunnable.java`

**Workflow**:
```
1. Poll for pending jobs (every 1-2 seconds)
2. Attempt to acquire lock
3. If lock acquired:
   a. Update job state to PROCESSING
   b. Execute command
   c. Handle success/failure
   d. Release lock
4. If lock failed, skip to next job
```

**Retry Logic**:
```java
private void handleJobFailure(Job job) {
    job.setAttempts(job.getAttempts() + 1);
    
    if (job.getAttempts() >= job.getMaxRetries()) {
        // Move to DLQ
        job.setState(JobState.DEAD);
        job.setUpdatedAt(Instant.now());
    } else {
        // Schedule retry with exponential backoff
        Config config = configRepository.load();
        long delaySeconds = (long) Math.pow(config.getBackoffBase(), job.getAttempts());
        job.setNextRetryAt(Instant.now().plusSeconds(delaySeconds));
        job.setState(JobState.PENDING);
        job.setUpdatedAt(Instant.now());
    }
    
    jobRepository.update(job);
}
```

---

## Data Flow

### Job Enqueue Flow

```
User → CLI.enqueue()
  ↓
JobService.enqueueJob()
  ↓
- Generate UUID if needed
- Validate command
- Check for duplicates
- Apply config defaults
- Set timestamps
  ↓
JobRepository.save()
  ↓
Write job-{id}.json
  ↓
Return Job to user
```

### Job Execution Flow

```
Worker polls
  ↓
JobRepository.findPendingJobsReadyForExecution()
  ↓
Filter: state=PENDING AND (nextRetryAt is null OR nextRetryAt < now)
  ↓
For each job:
  ↓
JobRepository.acquireLock(jobId)
  ↓
If lock acquired:
  ↓
Update state to PROCESSING
  ↓
Execute command via ProcessBuilder
  ↓
Check exit code
  ↓
If exit code = 0:
  - Set state to COMPLETED
Else:
  - Increment attempts
  - If attempts < maxRetries:
      - Calculate backoff delay
      - Set nextRetryAt
      - Set state to PENDING
  - Else:
      - Set state to DEAD (DLQ)
  ↓
Release lock
```

### DLQ Retry Flow

```
User → CLI.dlqRetry(jobId)
  ↓
JobService.retryDLQJob(jobId)
  ↓
- Find job by ID
- Verify state is DEAD
- Reset attempts to 0
- Set state to PENDING
- Clear nextRetryAt
- Update timestamp
  ↓
JobRepository.update(job)
  ↓
Job back in queue
```

---

## Concurrency Control

### File-Based Locking

**Why File-Based?**
- Simple to implement
- Works across processes
- No external dependencies
- Atomic file creation guaranteed by OS

**Lock Acquisition**:
```java
File.createNewFile() // Atomic operation
```

**Lock Release**:
```java
File.delete()
```

**Stale Lock Handling**:
- Check lock file age
- Verify PID in lock file is still running
- Remove stale locks automatically (future enhancement)

### Race Condition Prevention

**Scenario**: Two workers try to process the same job

**Solution**:
```
Worker 1: acquireLock("job-123") → Success
Worker 2: acquireLock("job-123") → Fail (file exists)

Worker 1 processes job
Worker 2 skips to next job
```

---

## Retry Mechanism

### Exponential Backoff

**Formula**:
```
delay = base ^ attempts seconds
```

**Example (base=2, max_retries=3)**:
```
Attempt 1: Fails → delay = 2^1 = 2 seconds
Attempt 2: Fails → delay = 2^2 = 4 seconds
Attempt 3: Fails → delay = 2^3 = 8 seconds
Attempt 4: Move to DLQ
```

**Implementation**:
```java
long delaySeconds = (long) Math.pow(config.getBackoffBase(), job.getAttempts());
job.setNextRetryAt(Instant.now().plusSeconds(delaySeconds));
```

**Why Exponential?**
- Reduces load on failing services
- Gives transient issues time to resolve
- Industry standard pattern

---

## Design Patterns

### 1. Repository Pattern

**Purpose**: Abstract data access from business logic

**Benefits**:
- Easy to swap storage implementations
- Testable (can mock repositories)
- Clear separation of concerns

### 2. Service Layer Pattern

**Purpose**: Encapsulate business logic

**Benefits**:
- Reusable business rules
- Transaction boundaries
- Orchestration point

### 3. Dependency Injection

**Purpose**: Loose coupling between components

**Implementation**: Constructor injection
```java
public JobService(JobRepository jobRepository, ConfigRepository configRepository) {
    this.jobRepository = jobRepository;
    this.configRepository = configRepository;
}
```

### 4. Process Isolation

**Purpose**: Fault tolerance

**Benefits**:
- Worker crash doesn't affect main process
- Easy to scale workers
- Resource isolation

---

## Scalability Considerations

### Current Limits

- **Jobs**: 1,000 - 10,000 (file system dependent)
- **Workers**: 1 - 20 (single machine)
- **Throughput**: ~100 jobs/minute (depends on job duration)

### Bottlenecks

1. **File I/O**: Scanning directory for jobs
2. **Polling**: 1-2 second latency
3. **Single Machine**: No distributed support

### Optimization Opportunities

1. **Index by State**: Separate directories per state
2. **In-Memory Cache**: Cache job list with file watcher
3. **Event-Driven**: Replace polling with file system events
4. **Database**: Switch to SQLite for better query performance

---

## Security Considerations

### Command Execution

**Risk**: Arbitrary command execution

**Mitigation**:
- User responsible for safe commands
- Document security implications
- Consider command whitelist (future)

### File System Access

**Risk**: Unauthorized access to job data

**Mitigation**:
- Restrict data directory permissions
- Validate file paths
- Handle symbolic links safely

### Multi-User

**Risk**: Concurrent access by multiple users

**Current**: Single-user design

**Future**: Add user authentication and job ownership

---

## Error Handling Strategy

### Levels of Error Handling

1. **CLI Level**: User input validation
2. **Service Level**: Business rule validation
3. **Repository Level**: Data access errors
4. **Worker Level**: Command execution errors

### Error Categories

| Category | Handling | Recovery |
|----------|----------|----------|
| User Input | Validate, reject with message | User corrects input |
| Business Rule | Throw exception, display error | User adjusts request |
| Storage | Log error, graceful degradation | Retry or manual fix |
| Command Execution | Mark job failed, trigger retry | Automatic retry |

---

## Testing Strategy

### Unit Tests

**Target**: Individual components in isolation

**Coverage**:
- Models: 100%
- Repositories: 95%
- Services: 90%

### Integration Tests

**Target**: Component interactions

**Scenarios**:
- Complete job lifecycle
- Retry workflows
- DLQ operations
- Concurrent processing

### Manual Tests

**Target**: System-level behavior

**Scenarios**:
- CLI commands
- Worker management
- Performance
- Edge cases

---

## Performance Characteristics

### Time Complexity

- **Enqueue**: O(1) - Write single file
- **List Jobs**: O(n) - Scan all job files
- **Find Pending**: O(n) - Scan and filter
- **Status**: O(n) - Count all jobs

### Space Complexity

- **Per Job**: ~1 KB (JSON file)
- **10,000 Jobs**: ~10 MB
- **Worker Memory**: ~50-100 MB per worker

### Latency

- **Enqueue**: < 10ms
- **Status**: < 100ms (1,000 jobs)
- **Job Pickup**: 1-2 seconds (polling interval)

---

## Future Architecture Improvements

### Short Term

1. **Stale Lock Cleanup**: Automatic detection and removal
2. **Job Timeout**: Kill long-running jobs
3. **Better Logging**: Structured logging with levels

### Medium Term

1. **SQLite Backend**: Optional database storage
2. **Job Priority**: Priority queue support
3. **Scheduled Jobs**: Delayed execution (run_at)

### Long Term

1. **Distributed Workers**: Workers on multiple machines
2. **Web Dashboard**: Monitoring UI
3. **Job Dependencies**: Workflow support
4. **Metrics**: Prometheus integration

---

## Conclusion

QueueCTL's architecture prioritizes simplicity and maintainability while providing robust job queue functionality. The layered design ensures clear separation of concerns, making the system easy to understand, test, and extend.

**Key Strengths**:
- Simple, transparent design
- No external dependencies
- Easy to debug and maintain
- Suitable for small to medium workloads

**Trade-offs**:
- File I/O overhead
- Single-machine limitation
- Polling latency

The architecture is well-suited for development, testing, and small-scale production use cases where simplicity and transparency are valued over maximum throughput.
