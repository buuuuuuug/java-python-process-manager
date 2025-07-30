#!/usr/bin/env python3
"""
Test script for testing error handling in dynamic script loading.
"""

import sys

def main():
    print("Error test script started")
    
    # Test accessing script arguments
    if 'SCRIPT_ARGS' in globals():
        print(f"Script arguments: {SCRIPT_ARGS}")
        
        error_type = SCRIPT_ARGS.get('error_type', 'none')
        
        if error_type == 'exception':
            print("Raising a test exception...")
            raise ValueError("This is a test exception from the target script")
        
        elif error_type == 'system_exit':
            exit_code = int(SCRIPT_ARGS.get('exit_code', 42))
            print(f"Calling sys.exit({exit_code})...")
            sys.exit(exit_code)
        
        elif error_type == 'runtime_error':
            print("Causing a runtime error...")
            # This will cause a NameError
            undefined_variable.some_method()
        
        elif error_type == 'none':
            print("No error requested, completing normally")
        
        else:
            print(f"Unknown error type: {error_type}")
    
    print("Error test script completed")

if __name__ == '__main__':
    main()