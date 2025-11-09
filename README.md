# QueueCTL - Job Queue System

A simple yet powerful background job queue system built with Java. Think of it as your personal task manager that handles retries, tracks failures, and keeps everything organized - all from the command line.

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.6+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Setup Instructions](#setup-instructions)
- [Usage Examples](#usage-examples)
- [Architecture Overview](#architecture-overview)
- [Job Lifecycle](#job-lifecycle)
- [Testing Instructions](#testing-instructions)
- [Demo Video](#demo-video)
- [Assumptions & Trade-offs](#assumptions--trade-offs)
- [Submission Checklist](#submission-checklist)

---

## What is QueueCTL?

Ever needed to run background tasks that might fail and need retrying? That's exactly what QueueCTL does. It's a job queue system that:

- Remembers your jobs even if you restart your computer
- Runs multiple tasks at the same time with worker processes
- Automatically retries failed jobs with smart delays (exponential backoff)
- Keeps track of permanently failed jobs in a "Dead Letter Queue"
- Makes sure the same job doesn't run twice
- Lets you configure how many times to retry and how long to wait

All of this without needing any external databases or services - just Java and your file system.

---

## Features

Here's what you can do with QueueCTL:

- **Add Jobs**: Queue up any shell command you want to run
- **Manage Workers**: Start or stop multiple workers to process jobs in parallel
- **Smart Retries**: Failed jobs automatically retry with increasing delays (2s, 4s, 8s...)
- **Track Failures**: Jobs that fail too many times go to a Dead Letter Queue for review
- **Persistent**: Your jobs are saved to disk, so nothing gets lost if you restart
- **Safe Concurrency**: File locking ensures each job runs exactly once
- **Configurable**: Adjust retry limits and delay patterns to fit your needs
- **Monitor Everything**: Check job status and worker activity anytime

---

## Built With

- Java 17 (the language)
- Maven (for building)
- Jackson (for JSON handling)
- JUnit 5 (for testing)
- Your file system (for storage)

---

## Getting Started

### What You'll Need

- Java 17 or newer (check with `java -version`)
- Maven 3.6+ (check with `mvn -version`)

### Installation

1. Clone this repository and navigate into it
2. Build the project:
   ```bash
   mvn clean package
   ```
3. You'll find the executable JAR at `target/queuectl-1.0-SNAPSHOT.jar`

4. Test it works:
   ```bash
   java -jar target/queuectl-1.0-SNAPSHOT.jar help
   ```

### Pro Tip: Create an Alias

Instead of typing the full `java -jar` command every time, create a shortcut:

**On Linux/Mac:**
```bash
alias queuectl='java -jar $(pwd)/target/queuectl-1.0-SNAPSHOT.jar'
```

**On Windows PowerShell:**
```powershell
Set-Alias queuectl "java -jar $PWD\target\queuectl-1.0-SNAPSHOT.jar"
```

---

## How to Use It

### Adding a Job

**On Linux/Mac/Git Bash:**
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"job1","command":"echo Hello World"}'
```

**On Windows PowerShell:**
```powershell
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue --% "{\"id\":\"job1\",\"command\":\"echo Hello World\"}"
```

You'll see:
```
Job enqueued: job1
```

### Starting Workers

Workers are the processes that actually run your jobs. Start one:

```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker start
```

Or start multiple workers to process jobs faster:

```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker start --count 3
```

You'll see something like:
```
Started 3 worker(s)
Worker started (PID: 12345)
Worker started (PID: 12346)
Worker started (PID: 12347)
```

### Checking Status

Want to see what's happening?

```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar status
```

This shows you:
```
Queue Status:
  Pending:    5
  Processing: 2
  Completed:  10
  Failed:     1
  Dead (DLQ): 0
  Active Workers: 3
```

### Listing Jobs

See all your jobs:

```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar list
```

Or filter by status:

```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar list --state pending
java -jar target/queuectl-1.0-SNAPSHOT.jar list --state completed
```

Example output:
```
Jobs:
  [PENDING] job1 - echo Hello World (attempts: 0/3)
  [COMPLETED] job2 - echo Done (attempts: 0/3)
  [DEAD] job3 - exit 1 (attempts: 3/3)
```

### Working with Failed Jobs

Jobs that fail too many times end up in the Dead Letter Queue (DLQ). You can review them:

```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar dlq list
```

And give them another chance:

```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar dlq retry job3
```

### Configuring Retry Behavior

Change how many times jobs retry (default is 3):

```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar config set max-retries 5
```

Adjust the delay pattern (default is 2, which gives delays of 2s, 4s, 8s...):

```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar config set backoff-base 3
```

### Stopping Workers

When you're done:

```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker stop
```

---

## How It Works

QueueCTL is organized in layers, each with a specific job:

```
┌─────────────────────────────────────────┐
│           CLI Layer                      │
│  Handles your commands                   │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│         Service Layer                    │
│  Business logic and coordination         │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│       Repository Layer                   │
│  Saves and loads data                    │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│         Storage Layer                    │
│  JSON files on your disk                 │
│  queuectl-data/                         │
│    ├── jobs/                            │
│    │   ├── job-{id}.json                │
│    │   └── job-{id}.lock                │
│    └── config.json                      │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│      Worker Processes                    │
│  Separate processes that run your jobs   │
└─────────────────────────────────────────┘
```

### The Main Parts

1. **CLI Layer**: Takes your commands and makes sense of them
2. **Service Layer**: The brain that manages jobs and workers
3. **Repository Layer**: Saves and loads job data
4. **Storage Layer**: JSON files with smart locking to prevent conflicts
5. **Worker Processes**: Separate programs that actually run your jobs

### Where Your Data Lives

Everything is stored in simple JSON files in the `queuectl-data` folder:

```
queuectl-data/
├── jobs/
│   ├── job-abc123.json      (your job data)
│   ├── job-abc123.lock      (prevents duplicate processing)
│   └── ...
└── config.json              (your settings)
```

Here's what a job file looks like:
```json
{
  "id": "job1",
  "command": "echo Hello",
  "state": "COMPLETED",
  "attempts": 0,
  "maxRetries": 3,
  "createdAt": "2025-11-08T10:30:00Z",
  "updatedAt": "2025-11-08T10:30:05Z",
  "nextRetryAt": null
}
```

### Preventing Duplicate Work

QueueCTL uses file locking to make sure the same job doesn't run twice:
- Before processing a job, a worker creates a `.lock` file
- If the lock file already exists, another worker is handling it
- When the job finishes, the lock is removed
- This works even with multiple workers running at once

---

## Job Lifecycle

### How Jobs Move Through the System

```
PENDING → PROCESSING → COMPLETED ✓
    ↓                      ↑
    ↓ (if it fails)        ↑
    ↓                      ↑
  FAILED ─────────────────┘ (retry with delays)
    ↓
    ↓ (after too many failures)
    ↓
  DEAD (goes to DLQ)
```

### What Each State Means

- **PENDING**: Waiting for a worker to pick it up
- **PROCESSING**: A worker is running it right now
- **COMPLETED**: Finished successfully
- **FAILED**: Didn't work, but we'll try again
- **DEAD**: Failed too many times, now in the Dead Letter Queue

### How Retries Work

When a job fails, QueueCTL waits before trying again. The wait time increases each time:

```
delay = base ^ attempts seconds
```

**Example with default settings (base=2, max_retries=3):**
- First failure → wait 2 seconds, try again
- Second failure → wait 4 seconds, try again
- Third failure → wait 8 seconds, try again
- Fourth failure → give up, move to DLQ

**If you change to base=3, max_retries=5:**
- Delays: 3s, 9s, 27s, 81s, 243s
- Total: about 6 minutes before giving up

This "exponential backoff" pattern is smart because it gives temporary problems time to resolve without hammering a failing service.

---

## Testing

### Running the Tests

```bash
# Run all tests
mvn test

# Generate a coverage report
mvn test jacoco:report

# View the report (opens in your browser)
open target/site/jacoco/index.html
```

We've got solid test coverage:
- 40+ unit tests covering individual components
- 10+ integration tests for complete workflows
- About 75% code coverage overall

### Try It Yourself

Want to see it in action? Here's a quick test:

```bash
# Test 1: Run a simple job
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"test1","command":"echo Success"}'
java -jar target/queuectl-1.0-SNAPSHOT.jar worker start
# Wait a couple seconds
java -jar target/queuectl-1.0-SNAPSHOT.jar status
# You should see: Completed: 1

# Test 2: Watch a job retry and fail
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"test2","command":"exit 1"}'
# Watch the worker output - you'll see it retry with delays: 2s, 4s, 8s
# After about 15 seconds, check the DLQ
java -jar target/queuectl-1.0-SNAPSHOT.jar dlq list
# You should see test2 there

# Test 3: Multiple workers working together
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"job1","command":"echo 1"}'
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"job2","command":"echo 2"}'
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"job3","command":"echo 3"}'
java -jar target/queuectl-1.0-SNAPSHOT.jar worker start --count 3
# All three jobs should finish quickly

# Clean up
java -jar target/queuectl-1.0-SNAPSHOT.jar worker stop
rm -rf queuectl-data
```

For more detailed tests, check out [manual-test-script.md](manual-test-script.md)

### What We've Tested

✅ Jobs complete successfully  
✅ Failed jobs retry with increasing delays and eventually go to DLQ  
✅ Multiple workers don't step on each other's toes  
✅ Bad commands are handled gracefully  
✅ Jobs survive restarts  
✅ Configuration changes work  
✅ Duplicate job IDs are rejected  
✅ File locking prevents duplicate processing  

---

## Demo Video

**[Watch the demo here](https://drive.google.com/your-demo-link)** *(Update this link with your actual video)*

The video walks through:
1. Building and running QueueCTL
2. Adding jobs to the queue
3. Starting workers
4. Checking job status
5. Watching a job fail and retry with delays
6. Working with the Dead Letter Queue
7. Changing configuration
8. Showing that jobs survive restarts

---

## Design Decisions & Trade-offs

### Why I Built It This Way

**File-Based Storage**
- Chose JSON files because they're simple and you can see what's happening
- No need for a database server
- Works great for thousands of jobs, but wouldn't scale to millions
- Perfect for development and small production workloads

**Separate Worker Processes**
- Each worker runs in its own process
- If a worker crashes, it doesn't take down the whole system
- Uses more memory than threads, but much safer

**File Locking**
- Uses the file system to prevent duplicate work
- Simple and reliable across processes
- No need for Redis or another external service

**JSON for Everything**
- You can open job files and see exactly what's happening
- Easy to debug when something goes wrong
- A bit slower than binary formats, but the clarity is worth it

**Workers Poll for Jobs**
- Workers check for new jobs every 1-2 seconds
- Simple to implement and understand
- Adds a small delay, but keeps the code clean

### What QueueCTL Doesn't Do

- **Single Machine Only**: Not built for distributed systems
- **Limited Scale**: Works best with thousands of jobs, not millions
- **No Priority**: All jobs are treated equally
- **No Timeouts**: Long-running jobs will keep running

### When to Use This

**Great for:**
- Learning how job queues work
- Development and testing
- Small batch processing tasks
- Single-server deployments

**Not ideal for:**
- High-traffic production systems
- Distributed architectures
- Real-time processing needs
- Mission-critical applications

---

## Assignment Requirements

All the required features are implemented:

✅ Working CLI with all commands  
✅ Jobs saved to disk (survive restarts)  
✅ Multiple workers can run in parallel  
✅ Smart retry with exponential backoff  
✅ Dead Letter Queue for failed jobs  
✅ Configurable settings  
✅ Clean, documented code  
✅ Comprehensive tests  
✅ This README with examples and architecture

## More Documentation

- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Deep dive into the system design
- **[TESTING.md](TESTING.md)** - Complete testing guide
- **[manual-test-script.md](manual-test-script.md)** - Step-by-step manual tests

## License

MIT License - See [LICENSE](LICENSE) for details


## Troubleshooting

**Windows PowerShell JSON issues?**
Use the `--% ` token before your JSON:
```powershell
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue --% "{\"id\":\"job1\",\"command\":\"echo Hello\"}"
```

**Workers won't start?**
Stop any existing workers first:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker stop
```

**Jobs stuck in PROCESSING?**
A worker probably crashed. Stop workers, remove lock files, and restart:
```bash
java -jar target/queuectl-1.0-SNAPSHOT.jar worker stop
rm queuectl-data/jobs/*.lock  # Linux/Mac
del queuectl-data\jobs\*.lock  # Windows
java -jar target/queuectl-1.0-SNAPSHOT.jar worker start
```

## Quick Reference

```bash
# Build the project
mvn clean package

# Add a job
java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue '{"command":"echo test"}'

# Start workers
java -jar target/queuectl-1.0-SNAPSHOT.jar worker start --count 3

# Check status
java -jar target/queuectl-1.0-SNAPSHOT.jar status

# Stop workers
java -jar target/queuectl-1.0-SNAPSHOT.jar worker stop

# Get help
java -jar target/queuectl-1.0-SNAPSHOT.jar help
```

## Future Ideas

Things that could be added (but aren't implemented yet):
- Job priorities
- Timeouts for long-running jobs
- Scheduled jobs (run at a specific time)
- Job output logging
- Metrics and statistics
- Web dashboard
- SQLite storage option
- Job dependencies

---

**Built for the Backend Developer Internship Assignment**  
November 2025
