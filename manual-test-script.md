# QueueCTL Manual Test Script

This script provides step-by-step instructions for manually testing all QueueCTL functionality.

## Prerequisites

1. Build the project: `mvn clean package`
2. Ensure no workers are running
3. Clean data directory: `rm -rf queuectl-data` (or `rmdir /s /q queuectl-data` on Windows)

## Test 1: Basic Job Enqueue and Execution

**Objective**: Verify basic job lifecycle

### Steps

1. Enqueue a simple job:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"test1","command":"echo Hello World"}'
```

**Expected**: `Job enqueued: test1`

2. Check status:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar status
```

**Expected**:
```
Queue Status:
  Pending:    1
  Processing: 0
  Completed:  0
  Failed:     0
  Dead (DLQ): 0
  Active Workers: 0
```

3. List jobs:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar list
```

**Expected**: Shows test1 in PENDING state

4. Start a worker:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker start
```

**Expected**: `Started 1 worker(s)` and worker console output showing job processing

5. Wait 2 seconds, then check status:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar status
```

**Expected**: Completed: 1

6. Stop worker:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker stop
```

**Expected**: `Workers stopped`

**Result**: ✅ PASS / ❌ FAIL

---

## Test 2: Multiple Workers

**Objective**: Verify multiple workers can process jobs concurrently

### Steps

1. Enqueue 5 jobs:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"multi1","command":"echo Job 1"}'
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"multi2","command":"echo Job 2"}'
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"multi3","command":"echo Job 3"}'
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"multi4","command":"echo Job 4"}'
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"multi5","command":"echo Job 5"}'
```

2. Start 3 workers:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker start --count 3
```

**Expected**: `Started 3 worker(s)`

3. Check status immediately:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar status
```

**Expected**: Active Workers: 3

4. Wait 3 seconds, check status:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar status
```

**Expected**: All 5 jobs completed

5. Stop workers:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker stop
```

**Result**: ✅ PASS / ❌ FAIL

---

## Test 3: Failed Job with Retry

**Objective**: Verify exponential backoff retry mechanism

### Steps

1. Enqueue a failing job:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"failing","command":"exit 1"}'
```

2. Start worker:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker start
```

3. Watch worker output and note retry times:
   - Attempt 1: Immediate
   - Attempt 2: After ~2 seconds
   - Attempt 3: After ~4 seconds
   - Attempt 4: After ~8 seconds

4. After ~15 seconds total, check status:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar status
```

**Expected**: Dead (DLQ): 1

5. Check DLQ:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar dlq list
```

**Expected**: Shows failing job with 3 attempts

6. Stop worker:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker stop
```

**Result**: ✅ PASS / ❌ FAIL

---

## Test 4: DLQ Retry

**Objective**: Verify jobs can be retried from DLQ

### Steps

1. Retry the failed job from Test 3:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar dlq retry failing
```

**Expected**: `Job retried: failing`

2. Check status:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar status
```

**Expected**: Pending: 1, Dead (DLQ): 0

3. List jobs:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar list --state PENDING
```

**Expected**: Shows failing job with attempts: 0/3

**Result**: ✅ PASS / ❌ FAIL

---

## Test 5: Configuration Changes

**Objective**: Verify configuration can be updated

### Steps

1. Check current config:
```bash
cat queuectl-data/config.json
```

**Expected**: maxRetries: 3, backoffBase: 2

2. Update max retries:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar config set max-retries 5
```

**Expected**: `max-retries set to 5`

3. Update backoff base:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar config set backoff-base 3
```

**Expected**: `backoff-base set to 3`

4. Verify config file:
```bash
cat queuectl-data/config.json
```

**Expected**: maxRetries: 5, backoffBase: 3

5. Enqueue new job:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"config-test","command":"exit 1"}'
```

6. Check job file:
```bash
cat queuectl-data/jobs/job-config-test.json
```

**Expected**: maxRetries: 5

**Result**: ✅ PASS / ❌ FAIL

---

## Test 6: Persistence Across Restart

**Objective**: Verify jobs persist across application restarts

### Steps

1. Clean start - remove data directory:
```bash
rm -rf queuectl-data
```

2. Enqueue jobs:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"persist1","command":"echo Test 1"}'
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"persist2","command":"echo Test 2"}'
```

3. Check status:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar status
```

**Expected**: Pending: 2

4. "Restart" by running status again (simulates app restart):
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar status
```

**Expected**: Still shows Pending: 2

5. List jobs:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar list
```

**Expected**: Both jobs still present

**Result**: ✅ PASS / ❌ FAIL

---

## Test 7: Duplicate Job ID Rejection

**Objective**: Verify duplicate IDs are rejected

### Steps

1. Enqueue a job:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"duplicate","command":"echo First"}'
```

**Expected**: `Job enqueued: duplicate`

2. Try to enqueue with same ID:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"duplicate","command":"echo Second"}'
```

**Expected**: Error message about duplicate ID

**Result**: ✅ PASS / ❌ FAIL

---

## Test 8: Invalid Commands

**Objective**: Verify error handling for invalid inputs

### Steps

1. Try empty command:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"command":""}'
```

**Expected**: Error message

2. Try invalid JSON:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{invalid json}'
```

**Expected**: JSON parsing error

3. Try missing command field:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"test"}'
```

**Expected**: Error about missing command

4. Try unknown CLI command:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar unknown
```

**Expected**: Unknown command error and help text

**Result**: ✅ PASS / ❌ FAIL

---

## Test 9: Worker Already Running

**Objective**: Verify workers can't be started twice

### Steps

1. Start workers:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker start
```

**Expected**: `Started 1 worker(s)`

2. Try to start again:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker start
```

**Expected**: Error: "Workers are already running"

3. Stop workers:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker stop
```

4. Try to stop again:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker stop
```

**Expected**: Error: "No workers are running"

**Result**: ✅ PASS / ❌ FAIL

---

## Test 10: Help Command

**Objective**: Verify help is displayed correctly

### Steps

1. Run help command:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar help
```

**Expected**: Displays all commands with usage examples

2. Run with no arguments:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar
```

**Expected**: Displays help text

**Result**: ✅ PASS / ❌ FAIL

---

## Test 11: List by State Filter

**Objective**: Verify filtering jobs by state

### Steps

1. Create jobs in different states:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"pending1","command":"echo P1"}'
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"pending2","command":"echo P2"}'
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"complete1","command":"echo C1"}'
```

2. Process one job:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker start
# Wait for complete1 to finish
java -jar target/queuectl-1.0-SNAPSHOT.jar worker stop
```

3. List pending jobs:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar list --state PENDING
```

**Expected**: Shows only pending1 and pending2

4. List completed jobs:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar list --state COMPLETED
```

**Expected**: Shows only complete1

**Result**: ✅ PASS / ❌ FAIL

---

## Test 12: Concurrent Job Processing

**Objective**: Verify no duplicate processing with file locking

### Steps

1. Enqueue a slow job:
```bash
# Windows
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"slow","command":"timeout /t 5"}'

# Linux/Mac
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"slow","command":"sleep 5"}'
```

2. Start 3 workers:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker start --count 3
```

3. Immediately check for lock file:
```bash
# Windows
dir queuectl-data\jobs\job-slow.lock

# Linux/Mac
ls -la queuectl-data/jobs/job-slow.lock
```

**Expected**: Lock file exists

4. Check worker output - should only see ONE worker processing the job

5. Wait for completion and stop workers:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker stop
```

6. Verify job completed only once:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar list --state COMPLETED
```

**Expected**: slow job appears once with attempts: 0/3

**Result**: ✅ PASS / ❌ FAIL

---

## Test Summary

| Test | Description | Result |
|------|-------------|--------|
| 1 | Basic Job Enqueue and Execution | |
| 2 | Multiple Workers | |
| 3 | Failed Job with Retry | |
| 4 | DLQ Retry | |
| 5 | Configuration Changes | |
| 6 | Persistence Across Restart | |
| 7 | Duplicate Job ID Rejection | |
| 8 | Invalid Commands | |
| 9 | Worker Already Running | |
| 10 | Help Command | |
| 11 | List by State Filter | |
| 12 | Concurrent Job Processing | |

## Notes

- Record any unexpected behavior
- Note performance issues
- Document any error messages that are unclear
- Check log output for any exceptions

## Cleanup

After testing:
```bash
# Stop any running workers
java -jar target/queuectl-1.0-SNAPSHOT.jar worker stop

# Remove test data
rm -rf queuectl-data
```
