package com.queuectl.model;

public class Config {
    private int maxRetries;
    private int backoffBase;

    public Config() {
        this.maxRetries = 3;
        this.backoffBase = 2;
    }

    public Config(int maxRetries, int backoffBase) {
        this.maxRetries = maxRetries;
        this.backoffBase = backoffBase;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getBackoffBase() {
        return backoffBase;
    }

    public void setBackoffBase(int backoffBase) {
        this.backoffBase = backoffBase;
    }
}
