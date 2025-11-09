package com.queuectl.worker;

import com.queuectl.model.Config;
import com.queuectl.model.Job;
import com.queuectl.model.JobState;
import com.queuectl.repository.ConfigRepository;
import com.queuectl.repository.JobRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class WorkerRunnable {
    private final JobRepository jobRepository;
    private final ConfigRepository configRepository;
    private volatile boolean running = true;

    public WorkerRunnable(String dataDir) {
        this.jobRepository = new JobRepository(dataDir);
        this.configRepository = new ConfigRepository(dataDir);
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: WorkerRunnable <dataDir>");
            System.exit(1);
        }

        String dataDir = args[0];
        WorkerRunnable worker = new WorkerRunnable(dataDir);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            worker.running = false;
        }));

        worker.run();
    }

    public void run() {
        System.out.println("Worker started (PID: " + ProcessHandle.current().pid() + ")");

        while (running) {
            try {
                List<Job> pendingJobs = jobRepository.findPendingJobsReadyForExecution();

                for (Job job : pendingJobs) {
                    if (!running) break;

                    if (jobRepository.acquireLock(job.getId())) {
                        try {
                            processJob(job);
                        } finally {
                            jobRepository.releaseLock(job.getId());
                        }
                    }
                }

                Thread.sleep(1000 + (long)(Math.random() * 1000));
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("Worker error: " + e.getMessage());
            }
        }

        System.out.println("Worker stopped");
    }

    private void processJob(Job job) {
        System.out.println("Processing job: " + job.getId());
        
        job.setState(JobState.PROCESSING);
        job.setUpdatedAt(Instant.now());
        jobRepository.update(job);

        boolean success = executeCommand(job.getCommand());

        if (success) {
            handleJobSuccess(job);
        } else {
            handleJobFailure(job);
        }
    }

    private boolean executeCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }

            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            
            if (!finished) {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            System.err.println("Command execution error: " + e.getMessage());
            return false;
        }
    }

    private void handleJobSuccess(Job job) {
        job.setState(JobState.COMPLETED);
        job.setUpdatedAt(Instant.now());
        jobRepository.update(job);
        System.out.println("Job completed: " + job.getId());
    }

    private void handleJobFailure(Job job) {
        job.setAttempts(job.getAttempts() + 1);

        if (job.getAttempts() >= job.getMaxRetries()) {
            job.setState(JobState.DEAD);
            job.setUpdatedAt(Instant.now());
            System.out.println("Job moved to DLQ: " + job.getId());
        } else {
            Config config = configRepository.load();
            long delaySeconds = calculateBackoffDelay(job.getAttempts(), config.getBackoffBase());
            job.setNextRetryAt(Instant.now().plusSeconds(delaySeconds));
            job.setState(JobState.PENDING);
            job.setUpdatedAt(Instant.now());
            System.out.println("Job failed, retry in " + delaySeconds + "s: " + job.getId());
        }

        jobRepository.update(job);
    }

    private long calculateBackoffDelay(int attempts, int base) {
        return (long) Math.pow(base, attempts);
    }
}
