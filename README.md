# Java-Python Process Manager

A comprehensive system for managing Python processes from Java applications, providing lifecycle management, inter-process communication, and monitoring capabilities.

## 🚀 Features

- **Process Management**: Create, monitor, and terminate Python processes using Java ProcessBuilder
- **Inter-Process Communication**: Bidirectional data exchange between Java and Python processes
- **Log Collection**: Real-time log aggregation from Python stdout/stderr streams
- **Metrics Monitoring**: CPU usage, memory consumption, and execution time tracking
- **Resource Control**: Python process resource limits and constraints
- **Dynamic Script Loading**: Runtime loading and execution of Python scripts
- **Error Handling**: Comprehensive error detection and recovery mechanisms
- **Cross-Platform**: Works on Windows, Linux, and macOS

## 📋 Requirements

- Java 21+ (GraalVM-24 recommended)
- Python 3.8+
- Gradle 8.0+

## 🏗️ Project Structure

```
src/
├── main/java/com/processmanager/
│   ├── core/                           # Core interfaces
│   │   ├── ProcessManager.java
│   │   ├── CommunicationManager.java
│   │   └── LogManager.java
│   ├── impl/                           # Implementation classes
│   │   ├── PythonProcessManagerImpl.java
│   │   ├── ProcessCommunicationManagerImpl.java
│   │   └── ProcessLogManagerImpl.java
│   ├── communication/                  # Communication layer
│   │   ├── CommunicationChannel.java
│   │   ├── SocketChannel.java
│   │   ├── NamedPipeChannel.java
│   │   └── MessageFraming.java
│   ├── monitoring/                     # Monitoring and metrics
│   │   └── ProcessMetricsCollector.java
│   ├── model/                          # Data models
│   │   ├── ProcessStatus.java
│   │   ├── ProcessMetrics.java
│   │   ├── LogEntry.java
│   │   └── CommunicationMessage.java
│   ├── exception/                      # Exception classes
│   │   ├── ProcessManagerException.java
│   │   ├── ProcessCreationException.java
│   │   ├── ProcessTerminationException.java
│   │   ├── CommunicationException.java
│   │   └── LogCollectionException.java
│   └── Main.java                       # Application entry point
├── main/resources/
│   ├── pythonBootstrap.py              # Python bootstrap script
│   └── logback.xml                     # Logging configuration
├── test/java/                          # Java unit tests
├── test/python/                        # Python unit tests
└── test/resources/                     # Test scripts
```

## 🔧 Building

```bash
./gradlew build
```

## 🧪 Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "ProcessManagerTest"

# Run Python tests
python3 -m unittest src.test.python.test_python_bootstrap -v
```

## ▶️ Running the Application

```bash
./gradlew run
```

## 📖 Usage Examples

### Basic Process Management

```java
// Create process manager
PythonProcessManagerImpl processManager = new PythonProcessManagerImpl();

// Create and start a Python process
Map<String, String> args = Map.of("iterations", "5", "delay", "1.0");
ProcessHandle handle = processManager.createProcess("script.py", args);

// Monitor process status
ProcessStatus status = processManager.getProcessStatus(handle);
ProcessMetrics metrics = processManager.getProcessMetrics(handle);

// Get process logs
List<LogEntry> logs = processManager.getProcessLogs(handle);

// Send message to Python process
processManager.sendMessageToProcess(handle, Map.of("command", "status"));

// Terminate process
processManager.terminateProcess(handle, Duration.ofSeconds(10));
```

### Python Script with Communication

```python
# In your Python script
def main():
    # Access script arguments
    if 'SCRIPT_ARGS' in globals():
        args = SCRIPT_ARGS
        print(f"Received args: {args}")
    
    # Send data to Java
    if 'send_to_java' in globals():
        send_to_java({"status": "processing", "progress": 50})
    
    # Get data from Java
    if 'get_from_java' in globals():
        java_data = get_from_java(timeout=5.0)
        if java_data:
            print(f"Received from Java: {java_data}")
    
    # Use logger
    if 'get_logger' in globals():
        logger = get_logger('MyScript')
        logger.info("Script completed successfully")

if __name__ == '__main__':
    main()
```

## 🏛️ Architecture

The system follows a layered architecture with clear separation of concerns:

1. **Core Layer**: Defines interfaces for process management, communication, and logging
2. **Implementation Layer**: Contains concrete implementations of core interfaces
3. **Communication Layer**: Handles inter-process communication with message framing
4. **Monitoring Layer**: Collects and manages process metrics and health data
5. **Model Layer**: Contains data transfer objects and enums
6. **Exception Layer**: Provides specific exception types for different failure scenarios

## 🔒 Security Features

- **Isolated Execution Environment**: Each Python script runs in a separate namespace
- **Resource Limits**: Configurable memory, CPU, and file descriptor limits
- **Controlled Communication**: Secure message passing between processes
- **Error Isolation**: Process failures don't affect the Java application

## 📊 Monitoring and Metrics

- Real-time CPU and memory usage tracking
- Process execution time monitoring
- Log collection and aggregation
- Health status monitoring with heartbeat mechanism
- System-wide resource monitoring

## 🧪 Testing

The project includes comprehensive test coverage:

- **84 Java unit tests** covering all major functionality
- **32 Python unit tests** for the bootstrap script
- Integration tests for end-to-end functionality
- Performance and stress tests

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🔄 Development Status

✅ **Completed Features:**
- Core interfaces and data models
- Process creation and lifecycle management
- Inter-process communication system
- Log collection and aggregation
- Process metrics monitoring
- Dynamic script loading
- Comprehensive error handling
- Python bootstrap script with resource management

🚧 **In Progress:**
- Advanced configuration management
- Performance optimizations
- Additional communication protocols

## 📚 Documentation

For detailed documentation, see the `/docs` directory (coming soon).

## 🐛 Known Issues

- Memory limit enforcement may not work on all systems due to OS restrictions
- Named pipe communication is currently Unix/Linux only

## 🆘 Support

If you encounter any issues or have questions, please open an issue on GitHub.