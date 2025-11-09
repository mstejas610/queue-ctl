package com.queuectl.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class WorkerService {
    private final List<Process> workerProcesses = new ArrayList<>();
    private final String jarPath;
    private final String dataDir;

    public WorkerService(String jarPath, String dataDir) {
        this.jarPath = jarPath;
        this.dataDir = dataDir;
    }

    public void startWorkers(int count) {
        if (areWorkersRunning()) {
            throw new IllegalStateException("Workers are already running");
        }

        for (int i = 0; i < count; i++) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    "java",
                    "-cp",
                    jarPath,
                    "com.queuectl.worker.WorkerRunnable",
                    dataDir
                );
                pb.inheritIO();
                Process process = pb.start();
                workerProcesses.add(process);
            } catch (IOException e) {
                System.err.println("Error starting worker: " + e.getMessage());
            }
        }
    }

    public void stopWorkers() {
        if (workerProcesses.isEmpty()) {
            throw new IllegalStateException("No workers are running");
        }

        for (Process process : workerProcesses) {
            process.destroy();
            try {
                process.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                process.destroyForcibly();
            }
        }

        workerProcesses.clear();
    }

    public int getActiveWorkerCount() {
        return (int) workerProcesses.stream().filter(Process::isAlive).count();
    }

    public boolean areWorkersRunning() {
        return !workerProcesses.isEmpty() && workerProcesses.stream().anyMatch(Process::isAlive);
    }
}
