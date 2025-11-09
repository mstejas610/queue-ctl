package com.queuectl.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @Test
    void testDefaultConfig() {
        Config config = new Config();
        
        assertEquals(3, config.getMaxRetries());
        assertEquals(2, config.getBackoffBase());
    }

    @Test
    void testCustomConfig() {
        Config config = new Config(5, 3);
        
        assertEquals(5, config.getMaxRetries());
        assertEquals(3, config.getBackoffBase());
    }

    @Test
    void testConfigSetters() {
        Config config = new Config();
        
        config.setMaxRetries(10);
        config.setBackoffBase(4);
        
        assertEquals(10, config.getMaxRetries());
        assertEquals(4, config.getBackoffBase());
    }
}
