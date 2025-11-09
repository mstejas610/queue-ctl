package com.queuectl.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.queuectl.model.Job;
import com.queuectl.model.JobState;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JobRepository {
    private final File jobsDir;
    private final ObjectMapper objectMapper;

    public JobRepository(String dataDir) {
        this.jobsDir = new File(dataDir, "jobs");
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.jobsDir.mkdirs();
    }

    public Job save(Job job) {
        File jobFile = new File(jobsDir, "job-" + job.getId() + ".json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jobFile, job);
            return job;
        } catch (IOException e) {
            throw new RuntimeException("Error saving job: " + e.getMessage(), e);
        }
    }

    public Optional<Job> findById(String id) {
        File jobFile = new File(jobsDir, "job-" + id + ".json");
        if (!jobFile.exists()) {
            return Optional.empty();
        }

        try {
            Job job = objectMapper.readValue(jobFile, Job.class);
            return Optional.of(job);
        } catch (IOException e) {
            System.err.println("Error reading job " + id + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public List<Job> findByState(JobState state) {
        List<Job> jobs = new ArrayList<>();
        File[] files = jobsDir.listFiles((dir, name) -> name.startsWith("job-") && name.endsWith(".json"));
        
        if (files != null) {
            for (File file : files) {
                try {
                    Job job = objectMapper.readValue(file, Job.class);
                    if (job.getState() == state) {
                        jobs.add(job);
                    }
                } catch (IOException e) {
                    System.err.println("Error reading job file " + file.getName() + ": " + e.getMessage());
                }
            }
        }
        
        return jobs;
    }

    public List<Job> findAll() {
        List<Job> jobs = new ArrayList<>();
        File[] files = jobsDir.listFiles((dir, name) -> name.startsWith("job-") && name.endsWith(".json"));
        
        if (files != null) {
            for (File file : files) {
                try {
                    Job job = objectMapper.readValue(file, Job.class);
                    jobs.add(job);
                } catch (IOException e) {
                    System.err.println("Error reading job file " + file.getName() + ": " + e.getMessage());
                }
            }
        }
        
        return jobs;
    }

    public Job update(Job job) {
        return save(job);
    }

    public boolean acquireLock(String jobId) {
        File lockFile = new File(jobsDir, "job-" + jobId + ".lock");
        try {
            if (lockFile.createNewFile()) {
                Files.writeString(lockFile.toPath(), String.valueOf(ProcessHandle.current().pid()));
                return true;
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public void releaseLock(String jobId) {
        File lockFile = new File(jobsDir, "job-" + jobId + ".lock");
        lockFile.delete();
    }

    public List<Job> findPendingJobsReadyForExecution() {
        List<Job> jobs = new ArrayList<>();
        File[] files = jobsDir.listFiles((dir, name) -> name.startsWith("job-") && name.endsWith(".json"));
        
        if (files != null) {
            Instant now = Instant.now();
            for (File file : files) {
                try {
                    Job job = objectMapper.readValue(file, Job.class);
                    if (job.getState() == JobState.PENDING) {
                        if (job.getNextRetryAt() == null || job.getNextRetryAt().isBefore(now)) {
                            jobs.add(job);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Error reading job file " + file.getName() + ": " + e.getMessage());
                }
            }
        }
        
        return jobs;
    }
}
