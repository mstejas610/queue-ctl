package com.queuectl.model;

import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JobTest {

    @Test
    void testJobCreation() {
        Instant now = Instant.now();
        Job job = new Job(
            "test-id",
            "echo test",
            JobState.PENDING,
            0,
            3,
            now,
            now,
            null
        );

        assertEquals("test-id", job.getId());
        assertEquals("echo test", job.getCommand());
        assertEquals(JobState.PENDING, job.getState());
        assertEquals(0, job.getAttempts());
        assertEquals(3, job.getMaxRetries());
        assertEquals(now, job.getCreatedAt());
        assertEquals(now, job.getUpdatedAt());
        assertNull(job.getNextRetryAt());
    }

    @Test
    void testJobStateTransitions() {
        Job job = new Job("id", "cmd", JobState.PENDING, 0, 3, Instant.now(), Instant.now(), null);
        
        job.setState(JobState.PROCESSING);
        assertEquals(JobState.PROCESSING, job.getState());
        
        job.setState(JobState.COMPLETED);
        assertEquals(JobState.COMPLETED, job.getState());
    }

    @Test
    void testJobAttemptsIncrement() {
        Job job = new Job("id", "cmd", JobState.PENDING, 0, 3, Instant.now(), Instant.now(), null);
        
        assertEquals(0, job.getAttempts());
        
        job.setAttempts(1);
        assertEquals(1, job.getAttempts());
        
        job.setAttempts(job.getAttempts() + 1);
        assertEquals(2, job.getAttempts());
    }

    @Test
    void testNextRetryAt() {
        Instant now = Instant.now();
        Instant retryTime = now.plusSeconds(10);
        
        Job job = new Job("id", "cmd", JobState.FAILED, 1, 3, now, now, retryTime);
        
        assertEquals(retryTime, job.getNextRetryAt());
        assertTrue(job.getNextRetryAt().isAfter(now));
    }
}
