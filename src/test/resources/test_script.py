#!/usr/bin/env python3
"""
Simple test script for bootstrap testing.
"""

import sys
import json

def main():
    print("Test script started")
    print(f"Python version: {sys.version}")
    print(f"Arguments received: {sys.argv}")
    
    # Test script can access environment
    print(f"Current working directory: {sys.path[0]}")
    
    # Output some test data
    test_data = {
        "message": "Test script executed successfully",
        "status": "completed"
    }
    print(f"TEST_OUTPUT: {json.dumps(test_data)}")

if __name__ == '__main__':
    main()