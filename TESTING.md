# QueueCTL Testing Guide

Complete testing documentation for the QueueCTL job queue system.

## Overview

QueueCTL has comprehensive test coverage including:
- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test component interactions and workflows
- **Manual Tests**: Test CLI, process management, and end-to-end scenarios

## Quick Start

### Run All Automated Tests
```bash
mvn test
```

### Run Specific Test Suite
```bash
# Unit tests only
mvn test -Dtest=*Test

# Integration tests only
mvn test -Dtest=*IntegrationTest,EndToEndTest

# Specific test class
mvn test -Dtest=JobServiceTest
```

### Generate Coverage Report
```bash
mvn test jacoco:report
```
View report at: `target/site/jacoco/index.html`

## Test Structure

```
src/test/java/com/queuectl/
├── model/
│   ├── ConfigTest.java          # Config model tests
│   └── JobTest.java              # Job model tests
├── repository/
│   ├── ConfigRepositoryTest.java # Config persistence tests
│   └── JobRepositoryTest.java    # Job persistence tests
├── service/
│   └── JobServiceTest.java       # Business logic tests
├── worker/
│   └── WorkerRunnableTest.java   # Worker logic tests
└── integration/
    └── EndToEndTest.java         # End-to-end workflow tests
```

## Test Coverage by Component

### Models (100% coverage)
- ✅ Job creation and field access
- ✅ Job state transitions
- ✅ Config defaults and custom values

### Repositories (95% coverage)
- ✅ Job CRUD operations
- ✅ File-based persistence
- ✅ Lock acquisition and release
- ✅ Concurrent lock handling
- ✅ Query operations (by state, ready for execution)
- ✅ Config persistence

### Services (90% coverage)
- ✅ Job enqueueing with validation
- ✅ ID generation and duplicate detection
- ✅ Job listing and filtering
- ✅ DLQ operations
- ✅ Statistics calculation
- ✅ Configuration application

### Workers (80% coverage)
- ✅ Exponential backoff calculation
- ⚠️ Command execution (requires manual testing)
- ⚠️ Process management (requires manual testing)

### Integration (85% coverage)
- ✅ Complete job lifecycle
- ✅ Retry workflows
- ✅ DLQ workflows
- ✅ Multi-job processing
- ✅ Persistence verification
- ✅ Lock coordination

## Requirements Coverage

See [TEST-COVERAGE.md](TEST-COVERAGE.md) for detailed mapping of tests to requirements.

### Summary
- **Requirement 1 (Job Management)**: 100% covered
- **Requirement 2 (Worker Management)**: 20% covered (manual testing required)
- **Requirement 3 (Job Execution)**: 80% covered
- **Requirement 4 (Retry Mechanism)**: 100% covered
- **Requirement 5 (DLQ)**: 100% covered
- **Requirement 6 (Persistence)**: 90% covered
- **Requirement 7 (Configuration)**: 100% covered
- **Requirement 8 (Status/Monitoring)**: 80% covered
- **Requirement 9 (CLI Interface)**: 20% covered (manual testing required)
- **Requirement 10 (Error Handling)**: 60% covered

**Overall Coverage**: ~75% automated, 25% manual

## Manual Testing

Manual testing is required for:
1. CLI command parsing and output
2. Worker process management
3. Shell command execution
4. Graceful shutdown behavior
5. Performance under load

### Run Manual Tests

Follow the step-by-step guide in [manual-test-script.md](manual-test-script.md).

The manual test script covers:
- ✅ Basic job lifecycle
- ✅ Multiple workers
- ✅ Retry with exponential backoff
- ✅ DLQ operations
- ✅ Configuration changes
- ✅ Persistence
- ✅ Error handling
- ✅ Concurrent processing
- ✅ CLI commands

## Test Data Management

### Automated Tests
All automated tests use temporary directories that are automatically cleaned up:
- `./test-queuectl-data`
- `./test-service-data`
- `./test-config-data`
- `./test-integration-data`

### Manual Tests
Manual tests use the standard data directory:
- `./queuectl-data`

Clean up after manual testing:
```bash
# Windows
rmdir /s /q queuectl-data

# Linux/Mac
rm -rf queuectl-data
```

## Continuous Integration

### GitHub Actions Example
```yaml
name: Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK 17
        uses: actions/setup-java@v2
        with:
          java-version: '17'
          distribution: 'adopt'
      - name: Run tests
        run: mvn test
      - name: Generate coverage report
        run: mvn jacoco:report
      - name: Upload coverage
        uses: codecov/codecov-action@v2
```

## Performance Testing

### Load Test (10,000 jobs)
```bash
# Generate 10,000 jobs
for i in {1..10000}; do
  java -jar target/queuectl-1.0-SNAPSHOT.jar enqueue "{\"id\":\"load-$i\",\"command\":\"echo $i\"}"
done

# Measure status command performance
time java -jar target/queuectl-1.0-SNAPSHOT.jar status
```

**Expected**: < 2 seconds (Requirement 8.4)

### Concurrent Worker Test
```bash
# Start 10 workers
java -jar target/queuectl-1.0-SNAPSHOT.jar worker start --count 10

# Monitor CPU and memory usage
# Verify no duplicate job processing
```

## Debugging Failed Tests

### View Test Output
```bash
# Verbose output
mvn test -X

# Specific test with stack traces
mvn test -Dtest=JobServiceTest -Dmaven.surefire.debug=true
```

### Common Issues

**Issue**: Tests fail with "file already exists"
**Solution**: Ensure test cleanup is working, manually delete test directories

**Issue**: Lock acquisition tests fail intermittently
**Solution**: File system timing issue, add small delays or retry logic

**Issue**: Integration tests timeout
**Solution**: Increase test timeout or check for deadlocks

## Test Best Practices

1. **Isolation**: Each test should be independent
2. **Cleanup**: Always clean up test data in `@AfterEach`
3. **Assertions**: Use descriptive assertion messages
4. **Coverage**: Aim for 80%+ coverage on business logic
5. **Performance**: Keep unit tests fast (< 100ms each)
6. **Documentation**: Document complex test scenarios

## Adding New Tests

### Unit Test Template
```java
@Test
void testFeatureName() {
    // Arrange
    // Set up test data and dependencies
    
    // Act
    // Execute the code under test
    
    // Assert
    // Verify expected behavior
    assertEquals(expected, actual, "Description of what's being tested");
}
```

### Integration Test Template
```java
@Test
void testWorkflowName() {
    // 1. Setup initial state
    
    // 2. Execute workflow steps
    
    // 3. Verify intermediate states
    
    // 4. Verify final state
}
```

## Test Metrics

Target metrics for QueueCTL:
- **Line Coverage**: > 80%
- **Branch Coverage**: > 75%
- **Test Execution Time**: < 30 seconds
- **Test Success Rate**: 100%
- **Manual Test Pass Rate**: > 95%

## Resources

- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [Maven Surefire Plugin](https://maven.apache.org/surefire/maven-surefire-plugin/)
- [JaCoCo Coverage](https://www.jacoco.org/jacoco/trunk/doc/)
- [TEST-COVERAGE.md](TEST-COVERAGE.md) - Detailed coverage mapping
- [manual-test-script.md](manual-test-script.md) - Step-by-step manual tests

## Support

For test-related issues:
1. Check test output for error messages
2. Review [TEST-COVERAGE.md](TEST-COVERAGE.md) for expected behavior
3. Run manual tests to isolate issues
4. Check GitHub issues for known problems
