package com.queuectl.service;

import com.queuectl.model.Config;
import com.queuectl.model.Job;
import com.queuectl.model.JobState;
import com.queuectl.repository.ConfigRepository;
import com.queuectl.repository.JobRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JobService {
    private final JobRepository jobRepository;
    private final ConfigRepository configRepository;

    public JobService(JobRepository jobRepository, ConfigRepository configRepository) {
        this.jobRepository = jobRepository;
        this.configRepository = configRepository;
    }

    public Job enqueueJob(String id, String command) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Command cannot be empty");
        }

        String jobId = (id == null || id.trim().isEmpty()) ? UUID.randomUUID().toString() : id;

        Optional<Job> existing = jobRepository.findById(jobId);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Job with id " + jobId + " already exists");
        }

        Config config = configRepository.load();
        Instant now = Instant.now();

        Job job = new Job(
            jobId,
            command,
            JobState.PENDING,
            0,
            config.getMaxRetries(),
            now,
            now,
            null
        );

        return jobRepository.save(job);
    }

    public List<Job> listJobs(JobState state) {
        if (state == null) {
            return jobRepository.findAll();
        }
        return jobRepository.findByState(state);
    }

    public Optional<Job> getJobById(String id) {
        return jobRepository.findById(id);
    }

    public List<Job> getDLQJobs() {
        return jobRepository.findByState(JobState.DEAD);
    }

    public Job retryDLQJob(String jobId) {
        Optional<Job> optionalJob = jobRepository.findById(jobId);
        if (optionalJob.isEmpty()) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }

        Job job = optionalJob.get();
        if (job.getState() != JobState.DEAD) {
            throw new IllegalArgumentException("Job is not in DLQ (state: " + job.getState() + ")");
        }

        job.setAttempts(0);
        job.setState(JobState.PENDING);
        job.setUpdatedAt(Instant.now());
        job.setNextRetryAt(null);

        return jobRepository.update(job);
    }

    public JobStats getStats() {
        List<Job> allJobs = jobRepository.findAll();
        JobStats stats = new JobStats();

        for (Job job : allJobs) {
            switch (job.getState()) {
                case PENDING -> stats.pending++;
                case PROCESSING -> stats.processing++;
                case COMPLETED -> stats.completed++;
                case FAILED -> stats.failed++;
                case DEAD -> stats.dead++;
            }
        }

        return stats;
    }

    public static class JobStats {
        public int pending = 0;
        public int processing = 0;
        public int completed = 0;
        public int failed = 0;
        public int dead = 0;
        public int activeWorkers = 0;
    }
}
