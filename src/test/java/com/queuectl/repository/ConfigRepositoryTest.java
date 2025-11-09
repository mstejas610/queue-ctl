package com.queuectl.repository;

import com.queuectl.model.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class ConfigRepositoryTest {

    private static final String TEST_DATA_DIR = "./test-config-data";
    private ConfigRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ConfigRepository(TEST_DATA_DIR);
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
    void testLoadDefaultConfig() {
        Config config = repository.load();
        
        assertNotNull(config);
        assertEquals(3, config.getMaxRetries());
        assertEquals(2, config.getBackoffBase());
    }

    @Test
    void testSaveAndLoadConfig() {
        Config config = new Config(5, 3);
        repository.save(config);
        
        Config loaded = repository.load();
        
        assertEquals(5, loaded.getMaxRetries());
        assertEquals(3, loaded.getBackoffBase());
    }

    @Test
    void testConfigPersistence() {
        Config config = new Config(10, 4);
        repository.save(config);
        
        // Create new repository instance to test persistence
        ConfigRepository newRepository = new ConfigRepository(TEST_DATA_DIR);
        Config loaded = newRepository.load();
        
        assertEquals(10, loaded.getMaxRetries());
        assertEquals(4, loaded.getBackoffBase());
    }
}
