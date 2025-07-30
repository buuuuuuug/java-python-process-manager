# Java-Python Process Manager

A comprehensive system for managing Python processes from Java applications, providing lifecycle management, inter-process communication, and monitoring capabilities.

## Features

- **Process Management**: Create, monitor, and terminate Python processes using Java ProcessBuilder
- **Inter-Process Communication**: Bidirectional data exchange between Java and Python processes
- **Log Collection**: Real-time log aggregation from Python stdout/stderr streams
- **Metrics Monitoring**: CPU usage, memory consumption, and execution time tracking
- **Resource Control**: Python process resource limits and constraints
- **Error Handling**: Comprehensive error detection and recovery mechanisms

## Requirements

- Java 21+ (GraalVM-24 recommended)
- Python 3.8+
- Gradle 8.0+

## Project Structure

```
src/
├── main/java/com/processmanager/
│   ├── core/                    # Core interfaces
│   │   ├── ProcessManager.java
│   │   ├── CommunicationManager.java
│   │   └── LogManager.java
│   ├── model/                   # Data models
│   │   ├── ProcessStatus.java
│   │   ├── ProcessMetrics.java
│   │   ├── LogEntry.java
│   │   └── CommunicationMessage.java
│   ├── exception/               # Exception classes
│   │   ├── ProcessManagerException.java
│   │   ├── ProcessCreationException.java
│   │   ├── ProcessTerminationException.java
│   │   ├── CommunicationException.java
│   │   └── LogCollectionException.java
│   └── Main.java               # Application entry point
├── main/resources/
│   └── logback.xml             # Logging configuration
└── test/java/                  # Unit tests
```

## Building

```bash
./gradlew build
```

## Running Tests

```bash
./gradlew test
```

## Running the Application

```bash
./gradlew run
```

## Development Status

This project is currently under development. The core interfaces and data models have been established. Implementation of the actual process management functionality is in progress.

## Architecture

The system follows a layered architecture with clear separation of concerns:

1. **Core Layer**: Defines interfaces for process management, communication, and logging
2. **Model Layer**: Contains data transfer objects and enums
3. **Exception Layer**: Provides specific exception types for different failure scenarios
4. **Implementation Layer**: (To be implemented) Contains concrete implementations of core interfaces

## Next Steps

- Implement Python bootstrap script
- Create concrete implementations of core interfaces
- Add process metrics collection
- Implement inter-process communication
- Add comprehensive error handling and recovery