#!/usr/bin/env python3
"""
Advanced test script for testing dynamic script loading functionality.
"""

import sys
import time

def main():
    print("Advanced test script started")
    
    # Test accessing script arguments
    if 'SCRIPT_ARGS' in globals():
        print(f"Script arguments: {SCRIPT_ARGS}")
        
        # Use arguments if provided
        if 'iterations' in SCRIPT_ARGS:
            iterations = int(SCRIPT_ARGS['iterations'])
        else:
            iterations = 3
            
        if 'delay' in SCRIPT_ARGS:
            delay = float(SCRIPT_ARGS['delay'])
        else:
            delay = 0.5
    else:
        iterations = 3
        delay = 0.5
    
    # Test logging functionality
    if 'get_logger' in globals():
        logger = get_logger('AdvancedTestScript')
        logger.info("Logger is available")
    else:
        print("Logger not available")
    
    # Test communication with Java (if available)
    if 'send_to_java' in globals():
        print("Communication with Java is available")
        send_to_java({"message": "Hello from advanced test script", "status": "started"})
    else:
        print("Communication with Java not available")
    
    # Perform some work
    for i in range(iterations):
        print(f"Advanced test iteration {i + 1}/{iterations}")
        
        if 'send_to_java' in globals():
            send_to_java({
                "iteration": i + 1,
                "total": iterations,
                "progress": (i + 1) / iterations * 100
            })
        
        time.sleep(delay)
    
    # Test getting data from Java (if available)
    if 'get_from_java' in globals():
        print("Attempting to get data from Java...")
        java_data = get_from_java(timeout=2.0)
        if java_data:
            print(f"Received from Java: {java_data}")
        else:
            print("No data received from Java")
    
    print("Advanced test script completed successfully")
    
    # Test explicit exit code
    if 'SCRIPT_ARGS' in globals() and 'exit_code' in SCRIPT_ARGS:
        exit_code = int(SCRIPT_ARGS['exit_code'])
        print(f"Exiting with code: {exit_code}")
        sys.exit(exit_code)

if __name__ == '__main__':
    main()