package com.processmanager.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class SimpleLogTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    void testBasicLogCollection() throws IOException, InterruptedException {
        // Create simple test script
        Path testScript = tempDir.resolve("simple_test.py");
        Files.write(testScript, """
            #!/usr/bin/env python3
            print("Hello from Python")
            print("This is a test message")
            """.getBytes());
        
        // Start process
        ProcessBuilder pb = new ProcessBuilder("python3", testScript.toString());
        Process process = pb.start();
        ProcessHandle handle = process.toHandle();
        
        // Create log manager
        ProcessLogManagerImpl logManager = new ProcessLogManagerImpl();
        
        try {
            // Start log collection
            logManager.startLogCollection(handle, process);
            
            // Wait for process to complete
            process.waitFor();
            
            // Wait a bit for log collection
            Thread.sleep(1000);
            
            // Check logs
            var logs = logManager.getAllLogEntries(handle);
            System.out.println("Collected " + logs.size() + " log entries:");
            logs.forEach(entry -> System.out.println("  " + entry));
            
        } finally {
            logManager.shutdown();
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
        }
    }
}