package com.queuectl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuectl.model.Config;
import com.queuectl.model.Job;
import com.queuectl.model.JobState;
import com.queuectl.repository.ConfigRepository;
import com.queuectl.repository.JobRepository;
import com.queuectl.service.JobService;
import com.queuectl.service.WorkerService;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class CLI {
    private static final String DATA_DIR = "./queuectl-data";
    private final JobService jobService;
    private final WorkerService workerService;
    private final ConfigRepository configRepository;
    private final ObjectMapper objectMapper;

    public CLI() {
        JobRepository jobRepository = new JobRepository(DATA_DIR);
        this.configRepository = new ConfigRepository(DATA_DIR);
        this.jobService = new JobService(jobRepository, configRepository);
        
        String jarPath = getJarPath();
        this.workerService = new WorkerService(jarPath, DATA_DIR);
        this.objectMapper = new ObjectMapper();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            System.exit(0);
        }

        CLI cli = new CLI();
        
        try {
            String command = args[0];
            
            switch (command) {
                case "enqueue" -> cli.handleEnqueue(args);
                case "worker" -> cli.handleWorker(args);
                case "status" -> cli.handleStatus(args);
                case "list" -> cli.handleList(args);
                case "dlq" -> cli.handleDLQ(args);
                case "config" -> cli.handleConfig(args);
                case "help" -> printHelp();
                default -> {
                    System.err.println("Unknown command: " + command);
                    printHelp();
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private void handleEnqueue(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: queuectl enqueue '<json>'");
            System.err.println("Example: queuectl enqueue '{\"id\":\"job1\",\"command\":\"echo hello\"}'");
            System.exit(1);
        }

        try {
            JsonNode json = objectMapper.readTree(args[1]);
            String id = json.has("id") ? json.get("id").asText() : null;
            String command = json.get("command").asText();

            Job job = jobService.enqueueJob(id, command);
            System.out.println("Job enqueued: " + job.getId());
        } catch (Exception e) {
            System.err.println("Invalid JSON or missing required fields: " + e.getMessage());
            System.exit(1);
        }
    }

    private void handleWorker(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: queuectl worker <start|stop>");
            System.exit(1);
        }

        String subcommand = args[1];
        
        switch (subcommand) {
            case "start" -> {
                int count = 1;
                for (int i = 2; i < args.length; i++) {
                    if (args[i].equals("--count") && i + 1 < args.length) {
                        count = Integer.parseInt(args[i + 1]);
                        break;
                    }
                }
                workerService.startWorkers(count);
                System.out.println("Started " + count + " worker(s)");
            }
            case "stop" -> {
                workerService.stopWorkers();
                System.out.println("Workers stopped");
            }
            default -> {
                System.err.println("Unknown worker subcommand: " + subcommand);
                System.exit(1);
            }
        }
    }

    private void handleStatus(String[] args) {
        JobService.JobStats stats = jobService.getStats();
        stats.activeWorkers = workerService.getActiveWorkerCount();

        System.out.println("Queue Status:");
        System.out.println("  Pending:    " + stats.pending);
        System.out.println("  Processing: " + stats.processing);
        System.out.println("  Completed:  " + stats.completed);
        System.out.println("  Failed:     " + stats.failed);
        System.out.println("  Dead (DLQ): " + stats.dead);
        System.out.println("  Active Workers: " + stats.activeWorkers);
    }

    private void handleList(String[] args) {
        JobState state = null;
        
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--state") && i + 1 < args.length) {
                state = JobState.valueOf(args[i + 1].toUpperCase());
                break;
            }
        }

        List<Job> jobs = jobService.listJobs(state);
        
        if (jobs.isEmpty()) {
            System.out.println("No jobs found");
            return;
        }

        System.out.println("Jobs:");
        for (Job job : jobs) {
            System.out.printf("  [%s] %s - %s (attempts: %d/%d)%n",
                job.getState(),
                job.getId(),
                job.getCommand(),
                job.getAttempts(),
                job.getMaxRetries()
            );
        }
    }

    private void handleDLQ(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: queuectl dlq <list|retry>");
            System.exit(1);
        }

        String subcommand = args[1];
        
        switch (subcommand) {
            case "list" -> {
                List<Job> dlqJobs = jobService.getDLQJobs();
                if (dlqJobs.isEmpty()) {
                    System.out.println("DLQ is empty");
                    return;
                }
                System.out.println("Dead Letter Queue:");
                for (Job job : dlqJobs) {
                    System.out.printf("  %s - %s (attempts: %d)%n",
                        job.getId(),
                        job.getCommand(),
                        job.getAttempts()
                    );
                }
            }
            case "retry" -> {
                if (args.length < 3) {
                    System.err.println("Usage: queuectl dlq retry <jobId>");
                    System.exit(1);
                }
                String jobId = args[2];
                Job job = jobService.retryDLQJob(jobId);
                System.out.println("Job retried: " + job.getId());
            }
            default -> {
                System.err.println("Unknown dlq subcommand: " + subcommand);
                System.exit(1);
            }
        }
    }

    private void handleConfig(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: queuectl config <set>");
            System.exit(1);
        }

        String subcommand = args[1];
        
        if (subcommand.equals("set")) {
            if (args.length < 4) {
                System.err.println("Usage: queuectl config set <key> <value>");
                System.err.println("Keys: max-retries, backoff-base");
                System.exit(1);
            }

            String key = args[2];
            String value = args[3];
            Config config = configRepository.load();

            switch (key) {
                case "max-retries" -> {
                    int maxRetries = Integer.parseInt(value);
                    if (maxRetries <= 0) {
                        System.err.println("max-retries must be greater than 0");
                        System.exit(1);
                    }
                    config.setMaxRetries(maxRetries);
                    configRepository.save(config);
                    System.out.println("max-retries set to " + maxRetries);
                }
                case "backoff-base" -> {
                    int backoffBase = Integer.parseInt(value);
                    if (backoffBase <= 1) {
                        System.err.println("backoff-base must be greater than 1");
                        System.exit(1);
                    }
                    config.setBackoffBase(backoffBase);
                    configRepository.save(config);
                    System.out.println("backoff-base set to " + backoffBase);
                }
                default -> {
                    System.err.println("Unknown config key: " + key);
                    System.exit(1);
                }
            }
        } else {
            System.err.println("Unknown config subcommand: " + subcommand);
            System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println("QueueCTL - CLI-based Job Queue System");
        System.out.println();
        System.out.println("Usage: queuectl <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  enqueue '<json>'           Enqueue a new job");
        System.out.println("                             Example: queuectl enqueue '{\"command\":\"echo hello\"}'");
        System.out.println("  worker start [--count N]   Start N worker processes (default: 1)");
        System.out.println("  worker stop                Stop all workers");
        System.out.println("  status                     Show queue status");
        System.out.println("  list [--state STATE]       List jobs (optionally filter by state)");
        System.out.println("  dlq list                   List jobs in Dead Letter Queue");
        System.out.println("  dlq retry <jobId>          Retry a job from DLQ");
        System.out.println("  config set <key> <value>   Set configuration (max-retries, backoff-base)");
        System.out.println("  help                       Show this help message");
    }

    private String getJarPath() {
        try {
            String path = CLI.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            return new File(path).getAbsolutePath();
        } catch (Exception e) {
            return "target/queuectl-1.0-SNAPSHOT.jar";
        }
    }
}
