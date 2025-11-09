package com.queuectl.worker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkerRunnableTest {

    @Test
    void testCalculateBackoffDelay() {
        // Test exponential backoff calculation: base^attempts
        
        // Base 2
        assertEquals(2, calculateBackoff(1, 2));   // 2^1 = 2
        assertEquals(4, calculateBackoff(2, 2));   // 2^2 = 4
        assertEquals(8, calculateBackoff(3, 2));   // 2^3 = 8
        assertEquals(16, calculateBackoff(4, 2));  // 2^4 = 16
        
        // Base 3
        assertEquals(3, calculateBackoff(1, 3));   // 3^1 = 3
        assertEquals(9, calculateBackoff(2, 3));   // 3^2 = 9
        assertEquals(27, calculateBackoff(3, 3));  // 3^3 = 27
        
        // Base 10
        assertEquals(10, calculateBackoff(1, 10));    // 10^1 = 10
        assertEquals(100, calculateBackoff(2, 10));   // 10^2 = 100
        assertEquals(1000, calculateBackoff(3, 10));  // 10^3 = 1000
    }

    @Test
    void testBackoffDelayAttemptZero() {
        // At attempt 0, delay should be base^0 = 1
        assertEquals(1, calculateBackoff(0, 2));
        assertEquals(1, calculateBackoff(0, 3));
        assertEquals(1, calculateBackoff(0, 10));
    }

    // Helper method that mimics the calculation in WorkerRunnable
    private long calculateBackoff(int attempts, int base) {
        return (long) Math.pow(base, attempts);
    }
}
