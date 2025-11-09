package com.queuectl.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuectl.model.Config;

import java.io.File;
import java.io.IOException;

public class ConfigRepository {
    private final File configFile;
    private final ObjectMapper objectMapper;

    public ConfigRepository(String dataDir) {
        this.configFile = new File(dataDir, "config.json");
        this.objectMapper = new ObjectMapper();
    }

    public Config load() {
        if (!configFile.exists()) {
            Config defaultConfig = new Config();
            save(defaultConfig);
            return defaultConfig;
        }

        try {
            return objectMapper.readValue(configFile, Config.class);
        } catch (IOException e) {
            System.err.println("Error loading config: " + e.getMessage());
            return new Config();
        }
    }

    public void save(Config config) {
        try {
            configFile.getParentFile().mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config);
        } catch (IOException e) {
            System.err.println("Error saving config: " + e.getMessage());
        }
    }
}
