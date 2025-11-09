package com.queuectl.integration;

import com.queuectl.model.Job;
import com.queuectl.model.JobState;
import com.queuectl.repository.ConfigRepository;
import com.queuectl.repository.JobRepository;
import com.queuectl.service.JobService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that verify end-to-end workflows
 */
class EndToEndTest {

    private static final String TEST_DATA_DIR = "./test-integration-data";
    private JobService jobService;
    private JobRepository jobRepository;

    @BeforeEach
    void setUp() {
        jobRepository = new JobRepository(TEST_DATA_DIR);
        ConfigRepository configRepository = new ConfigRepository(TEST_DATA_DIR);
        jobService = new JobService(jobRepository, configRepository);
    }

    @AfterEach
    void tearDown() throws IOException {
        Path testDir = Path.of(TEST_DATA_DIR);
        if (Files.exists(testDir)) {
            Files.walk(testDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    @Test
    void testCompleteJobLifecycle() {
        // 1. Enqueue a job
        Job job = jobService.enqueueJob("lifecycle-test", "echo test");
        assertEquals(JobState.PENDING, job.getState());
        assertEquals(0, job.getAttempts());

        // 2. Simulate worker picking up job
        job.setState(JobState.PROCESSING);
        jobRepository.update(job);
        
        Job processing = jobRepository.findById("lifecycle-test").get();
        assertEquals(JobState.PROCESSING, processing.getState());

        // 3. Simulate successful completion
        processing.setState(JobState.COMPLETED);
        jobRepository.update(processing);
        
        Job completed = jobRepository.findById("lifecycle-test").get();
        assertEquals(JobState.COMPLETED, completed.getState());
    }

    @Test
    void testFailedJobWithRetry() {
        // 1. Enqueue a job
        Job job = jobService.enqueueJob("retry-test", "exit 1");
        
        // 2. Simulate first failure
        job.setState(JobState.FAILED);
        job.setAttempts(1);
        job.setNextRetryAt(job.getUpdatedAt().plusSeconds(2));
        job.setState(JobState.PENDING);
        jobRepository.update(job);
        
        Job afterFirstFail = jobRepository.findById("retry-test").get();
        assertEquals(JobState.PENDING, afterFirstFail.getState());
        assertEquals(1, afterFirstFail.getAttempts());
        assertNotNull(afterFirstFail.getNextRetryAt());
    }

    @Test
    void testJobMovedToDLQ() {
        // 1. Enqueue a job
        Job job = jobService.enqueueJob("dlq-test", "exit 1");
        
        // 2. Simulate max retries exhausted
        job.setAttempts(3);
        job.setState(JobState.DEAD);
        jobRepository.update(job);
        
        // 3. Verify in DLQ
        List<Job> dlqJobs = jobService.getDLQJobs();
        assertEquals(1, dlqJobs.size());
        assertEquals("dlq-test", dlqJobs.get(0).getId());
        assertEquals(JobState.DEAD, dlqJobs.get(0).getState());
    }

    @Test
    void testDLQRetryWorkflow() {
        // 1. Create a job in DLQ
        Job job = jobService.enqueueJob("dlq-retry-test", "exit 1");
        job.setAttempts(3);
        job.setState(JobState.DEAD);
        jobRepository.update(job);
        
        // 2. Retry from DLQ
        Job retried = jobService.retryDLQJob("dlq-retry-test");
        
        // 3. Verify reset
        assertEquals(JobState.PENDING, retried.getState());
        assertEquals(0, retried.getAttempts());
        assertNull(retried.getNextRetryAt());
    }

    @Test
    void testMultipleJobsProcessing() {
        // Enqueue multiple jobs
        jobService.enqueueJob("job-1", "echo 1");
        jobService.enqueueJob("job-2", "echo 2");
        jobService.enqueueJob("job-3", "echo 3");
        
        // Verify all pending
        JobService.JobStats stats = jobService.getStats();
        assertEquals(3, stats.pending);
        
        // Simulate processing
        List<Job> pendingJobs = jobRepository.findPendingJobsReadyForExecution();
        assertEquals(3, pendingJobs.size());
        
        // Process first job
        Job job1 = pendingJobs.get(0);
        job1.setState(JobState.COMPLETED);
        jobRepository.update(job1);
        
        // Verify stats updated
        stats = jobService.getStats();
        assertEquals(2, stats.pending);
        assertEquals(1, stats.completed);
    }

    @Test
    void testJobPersistenceAcrossRepositoryInstances() {
        // Create job with first repository instance
        jobService.enqueueJob("persist-test", "echo test");
        
        // Create new repository instance
        JobRepository newRepository = new JobRepository(TEST_DATA_DIR);
        
        // Verify job still exists
        assertTrue(newRepository.findById("persist-test").isPresent());
    }

    @Test
    void testConcurrentLockAcquisition() {
        // Create a job
        Job job = jobService.enqueueJob("lock-test", "echo test");
        
        // First lock should succeed
        boolean firstLock = jobRepository.acquireLock("lock-test");
        assertTrue(firstLock);
        
        // Second lock should fail
        boolean secondLock = jobRepository.acquireLock("lock-test");
        assertFalse(secondLock);
        
        // Release and try again
        jobRepository.releaseLock("lock-test");
        boolean thirdLock = jobRepository.acquireLock("lock-test");
        assertTrue(thirdLock);
    }

    @Test
    void testStatsCalculation() {
        // Create jobs in various states
        jobService.enqueueJob("pending-1", "echo 1");
        jobService.enqueueJob("pending-2", "echo 2");
        
        Job completed = jobService.enqueueJob("completed-1", "echo 3");
        completed.setState(JobState.COMPLETED);
        jobRepository.update(completed);
        
        Job failed = jobService.enqueueJob("failed-1", "exit 1");
        failed.setState(JobState.FAILED);
        failed.setAttempts(1);
        jobRepository.update(failed);
        
        Job dead = jobService.enqueueJob("dead-1", "exit 1");
        dead.setState(JobState.DEAD);
        dead.setAttempts(3);
        jobRepository.update(dead);
        
        // Verify stats
        JobService.JobStats stats = jobService.getStats();
        assertEquals(2, stats.pending);
        assertEquals(1, stats.completed);
        assertEquals(1, stats.failed);
        assertEquals(1, stats.dead);
    }
}
