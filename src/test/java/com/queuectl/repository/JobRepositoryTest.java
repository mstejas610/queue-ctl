package com.queuectl.repository;

import com.queuectl.model.Job;
import com.queuectl.model.JobState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JobRepositoryTest {

    private static final String TEST_DATA_DIR = "./test-queuectl-data";
    private JobRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JobRepository(TEST_DATA_DIR);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up test data directory
        Path testDir = Path.of(TEST_DATA_DIR);
        if (Files.exists(testDir)) {
            Files.walk(testDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    @Test
    void testSaveJob() {
        Instant now = Instant.now();
        Job job = new Job("test-1", "echo test", JobState.PENDING, 0, 3, now, now, null);
        
        Job saved = repository.save(job);
        
        assertNotNull(saved);
        assertEquals("test-1", saved.getId());
        
        // Verify file was created
        File jobFile = new File(TEST_DATA_DIR + "/jobs/job-test-1.json");
        assertTrue(jobFile.exists());
    }

    @Test
    void testFindById() {
        Instant now = Instant.now();
        Job job = new Job("test-2", "echo test", JobState.PENDING, 0, 3, now, now, null);
        repository.save(job);
        
        Optional<Job> found = repository.findById("test-2");
        
        assertTrue(found.isPresent());
        assertEquals("test-2", found.get().getId());
        assertEquals("echo test", found.get().getCommand());
        assertEquals(JobState.PENDING, found.get().getState());
    }

    @Test
    void testFindByIdNotFound() {
        Optional<Job> found = repository.findById("non-existent");
        
        assertFalse(found.isPresent());
    }

    @Test
    void testFindByState() {
        Instant now = Instant.now();
        repository.save(new Job("job-1", "cmd1", JobState.PENDING, 0, 3, now, now, null));
        repository.save(new Job("job-2", "cmd2", JobState.COMPLETED, 0, 3, now, now, null));
        repository.save(new Job("job-3", "cmd3", JobState.PENDING, 0, 3, now, now, null));
        
        List<Job> pendingJobs = repository.findByState(JobState.PENDING);
        
        assertEquals(2, pendingJobs.size());
        assertTrue(pendingJobs.stream().allMatch(j -> j.getState() == JobState.PENDING));
    }

    @Test
    void testFindAll() {
        Instant now = Instant.now();
        repository.save(new Job("job-1", "cmd1", JobState.PENDING, 0, 3, now, now, null));
        repository.save(new Job("job-2", "cmd2", JobState.COMPLETED, 0, 3, now, now, null));
        repository.save(new Job("job-3", "cmd3", JobState.FAILED, 1, 3, now, now, null));
        
        List<Job> allJobs = repository.findAll();
        
        assertEquals(3, allJobs.size());
    }

    @Test
    void testUpdateJob() {
        Instant now = Instant.now();
        Job job = new Job("test-3", "echo test", JobState.PENDING, 0, 3, now, now, null);
        repository.save(job);
        
        job.setState(JobState.COMPLETED);
        job.setUpdatedAt(Instant.now());
        repository.update(job);
        
        Optional<Job> updated = repository.findById("test-3");
        assertTrue(updated.isPresent());
        assertEquals(JobState.COMPLETED, updated.get().getState());
    }

    @Test
    void testAcquireLock() {
        Instant now = Instant.now();
        Job job = new Job("test-4", "echo test", JobState.PENDING, 0, 3, now, now, null);
        repository.save(job);
        
        boolean acquired = repository.acquireLock("test-4");
        
        assertTrue(acquired);
        
        // Verify lock file was created
        File lockFile = new File(TEST_DATA_DIR + "/jobs/job-test-4.lock");
        assertTrue(lockFile.exists());
    }

    @Test
    void testAcquireLockAlreadyLocked() {
        Instant now = Instant.now();
        Job job = new Job("test-5", "echo test", JobState.PENDING, 0, 3, now, now, null);
        repository.save(job);
        
        boolean firstAcquire = repository.acquireLock("test-5");
        assertTrue(firstAcquire);
        
        boolean secondAcquire = repository.acquireLock("test-5");
        assertFalse(secondAcquire);
    }

    @Test
    void testReleaseLock() {
        Instant now = Instant.now();
        Job job = new Job("test-6", "echo test", JobState.PENDING, 0, 3, now, now, null);
        repository.save(job);
        
        repository.acquireLock("test-6");
        File lockFile = new File(TEST_DATA_DIR + "/jobs/job-test-6.lock");
        assertTrue(lockFile.exists());
        
        repository.releaseLock("test-6");
        assertFalse(lockFile.exists());
    }

    @Test
    void testFindPendingJobsReadyForExecution() {
        Instant now = Instant.now();
        
        // Job ready for execution (no retry time)
        repository.save(new Job("job-1", "cmd1", JobState.PENDING, 0, 3, now, now, null));
        
        // Job ready for execution (retry time in past)
        repository.save(new Job("job-2", "cmd2", JobState.PENDING, 1, 3, now, now, now.minusSeconds(10)));
        
        // Job not ready (retry time in future)
        repository.save(new Job("job-3", "cmd3", JobState.PENDING, 1, 3, now, now, now.plusSeconds(10)));
        
        // Not pending
        repository.save(new Job("job-4", "cmd4", JobState.COMPLETED, 0, 3, now, now, null));
        
        List<Job> readyJobs = repository.findPendingJobsReadyForExecution();
        
        assertEquals(2, readyJobs.size());
        assertTrue(readyJobs.stream().anyMatch(j -> j.getId().equals("job-1")));
        assertTrue(readyJobs.stream().anyMatch(j -> j.getId().equals("job-2")));
    }
}
