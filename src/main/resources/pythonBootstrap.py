#!/usr/bin/env python3
"""
Python Bootstrap Script for Java-Python Process Manager

This script serves as the entry point for all Python processes managed by the Java application.
It handles resource limits, logging configuration, and dynamic script loading.
"""

import argparse
import json
import logging
import os
import resource
import socket
import struct
import sys
import threading
import time
import traceback
from pathlib import Path
from typing import Dict, Any, Optional


class PythonBootstrap:
    """Main bootstrap class for Python process initialization."""
    
    def __init__(self):
        self.logger = None
        self.target_script_path = None
        self.script_args = {}
        self.resource_limits = {}
        self.log_config = {}
        self.communication_socket = None
        self.communication_thread = None
        self.is_communicating = False
        self.message_queue = []
        self.message_lock = threading.Lock()
        
    def parse_arguments(self) -> argparse.Namespace:
        """Parse command line arguments."""
        parser = argparse.ArgumentParser(
            description='Python Bootstrap for Java Process Manager'
        )
        
        parser.add_argument(
            '--script', 
            required=True,
            help='Path to the target Python script to execute'
        )
        
        parser.add_argument(
            '--args',
            default='{}',
            help='JSON string of arguments to pass to the target script'
        )
        
        parser.add_argument(
            '--memory-limit-mb',
            type=int,
            default=512,
            help='Memory limit in MB (default: 512)'
        )
        
        parser.add_argument(
            '--cpu-limit-percent',
            type=float,
            default=80.0,
            help='CPU usage limit as percentage (default: 80.0)'
        )
        
        parser.add_argument(
            '--log-level',
            choices=['DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL'],
            default='INFO',
            help='Logging level (default: INFO)'
        )
        
        parser.add_argument(
            '--log-format',
            default='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
            help='Log format string'
        )
        
        parser.add_argument(
            '--working-dir',
            help='Working directory for the target script'
        )
        
        parser.add_argument(
            '--communication-port',
            type=int,
            help='Port for communication with Java process'
        )
        
        parser.add_argument(
            '--communication-host',
            default='localhost',
            help='Host for communication with Java process (default: localhost)'
        )
        
        return parser.parse_args()
    
    def setup_resource_limits(self, memory_mb: int, cpu_percent: float) -> None:
        """Configure process resource constraints."""
        try:
            # Set memory limit (virtual memory) - use soft limit approach
            memory_bytes = memory_mb * 1024 * 1024
            
            # Get current limits to avoid exceeding system maximums
            current_mem_soft, current_mem_hard = resource.getrlimit(resource.RLIMIT_AS)
            
            # Only set memory limit if it's reasonable and doesn't exceed current hard limit
            if current_mem_hard == resource.RLIM_INFINITY or memory_bytes < current_mem_hard:
                try:
                    resource.setrlimit(resource.RLIMIT_AS, (memory_bytes, current_mem_hard))
                    self.logger.info(f"Memory limit set to {memory_mb}MB")
                except (OSError, ValueError) as e:
                    self.logger.warning(f"Could not set memory limit: {e}")
            else:
                self.logger.warning(f"Requested memory limit {memory_mb}MB exceeds system limit")
            
            # Set CPU time limit (soft limit for graceful handling)
            cpu_time_limit = 3600  # 1 hour default
            current_cpu_soft, current_cpu_hard = resource.getrlimit(resource.RLIMIT_CPU)
            
            if current_cpu_hard == resource.RLIM_INFINITY or cpu_time_limit < current_cpu_hard:
                try:
                    resource.setrlimit(resource.RLIMIT_CPU, (cpu_time_limit, current_cpu_hard))
                    self.logger.info(f"CPU time limit set to {cpu_time_limit}s")
                except (OSError, ValueError) as e:
                    self.logger.warning(f"Could not set CPU time limit: {e}")
            
            # Set file descriptor limit
            current_fd_soft, current_fd_hard = resource.getrlimit(resource.RLIMIT_NOFILE)
            fd_limit = min(1024, current_fd_hard) if current_fd_hard != resource.RLIM_INFINITY else 1024
            
            try:
                resource.setrlimit(resource.RLIMIT_NOFILE, (fd_limit, current_fd_hard))
                self.logger.info(f"File descriptor limit set to {fd_limit}")
            except (OSError, ValueError) as e:
                self.logger.warning(f"Could not set file descriptor limit: {e}")
            
            self.resource_limits = {
                'memory_mb': memory_mb,
                'cpu_percent': cpu_percent,
                'memory_bytes': memory_bytes,
                'cpu_time_limit': cpu_time_limit,
                'fd_limit': fd_limit
            }
            
            self.logger.info("Resource limits configuration completed")
            
        except Exception as e:
            self.logger.error(f"Failed to configure resource limits: {e}")
            # Don't fail the entire bootstrap for resource limit issues
            self.logger.warning("Continuing without resource limits")
    
    def configure_logging(self, log_level: str, log_format: str) -> None:
        """Set up Python logging with custom handlers for Java integration."""
        # Create logger
        self.logger = logging.getLogger('PythonBootstrap')
        self.logger.setLevel(getattr(logging, log_level.upper()))
        
        # Clear any existing handlers
        self.logger.handlers.clear()
        
        # Create console handler for stdout (will be captured by Java)
        console_handler = logging.StreamHandler(sys.stdout)
        console_handler.setLevel(getattr(logging, log_level.upper()))
        
        # Create formatter
        formatter = logging.Formatter(log_format)
        console_handler.setFormatter(formatter)
        
        # Add handler to logger
        self.logger.addHandler(console_handler)
        
        # Configure root logger to forward to our logger
        root_logger = logging.getLogger()
        root_logger.setLevel(getattr(logging, log_level.upper()))
        root_logger.handlers.clear()
        root_logger.addHandler(console_handler)
        
        self.log_config = {
            'level': log_level,
            'format': log_format
        }
        
        self.logger.info("Logging configuration completed")
    
    def validate_target_script(self, script_path: str) -> Path:
        """Validate that the target script exists and is readable."""
        script_file = Path(script_path)
        
        if not script_file.exists():
            raise FileNotFoundError(f"Target script not found: {script_path}")
        
        if not script_file.is_file():
            raise ValueError(f"Target script is not a file: {script_path}")
        
        if not os.access(script_file, os.R_OK):
            raise PermissionError(f"Target script is not readable: {script_path}")
        
        return script_file
    
    def load_script_arguments(self, args_json: str) -> Dict[str, Any]:
        """Parse and validate script arguments from JSON string."""
        try:
            args_dict = json.loads(args_json)
            if not isinstance(args_dict, dict):
                raise ValueError("Arguments must be a JSON object")
            return args_dict
        except json.JSONDecodeError as e:
            raise ValueError(f"Invalid JSON in arguments: {e}")
    
    def setup_working_directory(self, working_dir: Optional[str]) -> None:
        """Set up the working directory for script execution."""
        if working_dir:
            try:
                os.chdir(working_dir)
                self.logger.info(f"Changed working directory to: {working_dir}")
            except OSError as e:
                self.logger.error(f"Failed to change working directory: {e}")
                raise RuntimeError(f"Working directory setup failed: {e}")
    
    def report_startup_status(self) -> None:
        """Report bootstrap initialization status."""
        status_info = {
            'status': 'initialized',
            'pid': os.getpid(),
            'resource_limits': self.resource_limits,
            'log_config': self.log_config,
            'target_script': str(self.target_script_path),
            'working_directory': os.getcwd()
        }
        
        # Output status as JSON for Java to parse
        print(f"BOOTSTRAP_STATUS: {json.dumps(status_info)}", flush=True)
        self.logger.info("Bootstrap initialization completed successfully")
    
    def establish_communication(self, host: str, port: int) -> bool:
        """Initialize IPC channel with Java process."""
        if not port:
            self.logger.info("No communication port specified, skipping communication setup")
            return False
            
        try:
            self.logger.info(f"Establishing communication with {host}:{port}")
            
            # Create socket connection to Java server
            self.communication_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.communication_socket.connect((host, port))
            
            self.is_communicating = True
            
            # Start communication thread
            self.communication_thread = threading.Thread(
                target=self._communication_loop,
                daemon=True
            )
            self.communication_thread.start()
            
            self.logger.info("Communication channel established successfully")
            
            # Send initial connection message
            self.send_message("connection", {"status": "connected", "pid": os.getpid()})
            
            return True
            
        except Exception as e:
            self.logger.error(f"Failed to establish communication: {e}")
            self.is_communicating = False
            if self.communication_socket:
                try:
                    self.communication_socket.close()
                except:
                    pass
                self.communication_socket = None
            return False
    
    def send_message(self, message_type: str, payload: Any) -> bool:
        """Send a message to the Java process."""
        if not self.is_communicating or not self.communication_socket:
            self.logger.warning("Cannot send message: communication not established")
            return False
            
        try:
            # Create message
            message = {
                "messageId": f"py-{os.getpid()}-{int(time.time() * 1000)}",
                "messageType": message_type,
                "payload": payload,
                "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S.%fZ")
            }
            
            # Serialize to JSON
            message_json = json.dumps(message)
            message_bytes = message_json.encode('utf-8')
            
            # Frame message with length prefix (4 bytes, big-endian)
            length_prefix = struct.pack('>I', len(message_bytes))
            framed_message = length_prefix + message_bytes
            
            # Send message
            self.communication_socket.sendall(framed_message)
            
            self.logger.debug(f"Message sent: {message['messageId']}")
            return True
            
        except Exception as e:
            self.logger.error(f"Failed to send message: {e}")
            return False
    
    def receive_message(self) -> Optional[Dict[str, Any]]:
        """Receive a message from the Java process."""
        if not self.is_communicating or not self.communication_socket:
            return None
            
        try:
            # Read length prefix (4 bytes)
            length_data = self._receive_exact(4)
            if not length_data:
                return None
                
            message_length = struct.unpack('>I', length_data)[0]
            
            # Read message data
            message_data = self._receive_exact(message_length)
            if not message_data:
                return None
                
            # Parse JSON
            message_json = message_data.decode('utf-8')
            message = json.loads(message_json)
            
            self.logger.debug(f"Message received: {message.get('messageId', 'unknown')}")
            return message
            
        except Exception as e:
            self.logger.error(f"Failed to receive message: {e}")
            return None
    
    def _receive_exact(self, num_bytes: int) -> Optional[bytes]:
        """Receive exactly num_bytes from the socket."""
        data = b''
        while len(data) < num_bytes:
            chunk = self.communication_socket.recv(num_bytes - len(data))
            if not chunk:
                return None
            data += chunk
        return data
    
    def _communication_loop(self):
        """Main communication loop running in background thread."""
        self.logger.debug("Communication loop started")
        
        while self.is_communicating:
            try:
                message = self.receive_message()
                if message:
                    self._handle_received_message(message)
                else:
                    # No message received, small delay to prevent busy waiting
                    time.sleep(0.1)
                    
            except Exception as e:
                self.logger.error(f"Error in communication loop: {e}")
                break
        
        self.logger.debug("Communication loop ended")
    
    def _handle_received_message(self, message: Dict[str, Any]):
        """Handle a received message from Java."""
        message_type = message.get('messageType', 'unknown')
        payload = message.get('payload')
        
        self.logger.debug(f"Handling message type: {message_type}")
        
        if message_type == 'heartbeat':
            # Respond to heartbeat
            self.send_message('heartbeat_response', 'pong')
            
        elif message_type == 'command':
            # Handle command messages
            self._handle_command(payload)
            
        elif message_type == 'data':
            # Store data message in queue for processing
            with self.message_lock:
                self.message_queue.append(message)
                
        else:
            self.logger.warning(f"Unknown message type: {message_type}")
    
    def _handle_command(self, command: Any):
        """Handle command messages from Java."""
        if isinstance(command, dict):
            cmd_type = command.get('type')
            
            if cmd_type == 'ping':
                self.send_message('command_response', {'type': 'pong', 'status': 'ok'})
                
            elif cmd_type == 'status':
                status_info = {
                    'type': 'status_response',
                    'pid': os.getpid(),
                    'status': 'running',
                    'resource_limits': self.resource_limits,
                    'message_queue_size': len(self.message_queue)
                }
                self.send_message('command_response', status_info)
                
            else:
                self.logger.warning(f"Unknown command type: {cmd_type}")
        else:
            self.logger.warning(f"Invalid command format: {command}")
    
    def get_queued_messages(self) -> list:
        """Get all queued messages and clear the queue."""
        with self.message_lock:
            messages = self.message_queue.copy()
            self.message_queue.clear()
            return messages
    
    def close_communication(self):
        """Close the communication channel."""
        if self.is_communicating:
            self.logger.info("Closing communication channel")
            
            # Send disconnect message
            try:
                self.send_message("disconnect", {"status": "disconnecting"})
            except:
                pass
            
            self.is_communicating = False
            
            # Close socket
            if self.communication_socket:
                try:
                    self.communication_socket.close()
                except:
                    pass
                self.communication_socket = None
            
            # Wait for communication thread to finish
            if self.communication_thread and self.communication_thread.is_alive():
                self.communication_thread.join(timeout=2.0)
            
            self.logger.info("Communication channel closed")
    
    def load_and_execute_script(self, script_path: Path, script_args: Dict[str, Any]) -> int:
        """Dynamically load and run target Python script."""
        try:
            self.logger.info(f"Loading target script: {script_path}")
            
            # Report script loading status
            if self.is_communicating:
                self.send_message("script_status", {
                    "status": "loading",
                    "script_path": str(script_path),
                    "args": script_args
                })
            
            # Prepare script execution environment
            script_globals = self._prepare_script_environment(script_args)
            
            # Read and compile the script
            script_code = self._load_script_code(script_path)
            compiled_code = compile(script_code, str(script_path), 'exec')
            
            self.logger.info("Executing target script...")
            
            # Report script execution start
            if self.is_communicating:
                self.send_message("script_status", {
                    "status": "executing",
                    "script_path": str(script_path)
                })
            
            # Execute the script
            exec(compiled_code, script_globals)
            
            self.logger.info("Target script execution completed successfully")
            
            # Report successful completion
            if self.is_communicating:
                self.send_message("script_status", {
                    "status": "completed",
                    "script_path": str(script_path),
                    "exit_code": 0
                })
            
            return 0
            
        except SystemExit as e:
            # Handle explicit sys.exit() calls from the script
            exit_code = e.code if e.code is not None else 0
            self.logger.info(f"Target script exited with code: {exit_code}")
            
            if self.is_communicating:
                self.send_message("script_status", {
                    "status": "exited",
                    "script_path": str(script_path),
                    "exit_code": exit_code
                })
            
            return exit_code
            
        except Exception as e:
            self.logger.error(f"Error executing target script: {e}")
            self.logger.error(f"Traceback: {traceback.format_exc()}")
            
            # Report script execution error
            if self.is_communicating:
                self.send_message("script_status", {
                    "status": "failed",
                    "script_path": str(script_path),
                    "error": str(e),
                    "traceback": traceback.format_exc()
                })
            
            return 1
    
    def _prepare_script_environment(self, script_args: Dict[str, Any]) -> Dict[str, Any]:
        """Prepare the execution environment for the target script."""
        # Create a clean globals environment for the script
        script_globals = {
            '__builtins__': __builtins__,
            '__name__': '__main__',
            '__file__': str(self.target_script_path),
            '__doc__': None,
            '__package__': None,
        }
        
        # Add script arguments as a global variable
        script_globals['SCRIPT_ARGS'] = script_args
        
        # Add communication helper functions if communication is available
        if self.is_communicating:
            script_globals['send_to_java'] = self._create_send_to_java_function()
            script_globals['get_from_java'] = self._create_get_from_java_function()
        
        # Add logging helper
        script_globals['get_logger'] = lambda name='TargetScript': logging.getLogger(name)
        
        return script_globals
    
    def _load_script_code(self, script_path: Path) -> str:
        """Load the script code from file."""
        try:
            with open(script_path, 'r', encoding='utf-8') as f:
                return f.read()
        except FileNotFoundError:
            raise FileNotFoundError(f"Target script not found: {script_path}")
        except PermissionError:
            raise PermissionError(f"Permission denied reading script: {script_path}")
        except UnicodeDecodeError as e:
            raise ValueError(f"Script encoding error: {e}")
    
    def _create_send_to_java_function(self):
        """Create a helper function for the target script to send messages to Java."""
        def send_to_java(data, message_type="script_data"):
            """Send data from the target script to the Java process."""
            if self.is_communicating:
                result = self.send_message(message_type, {
                    "source": "target_script",
                    "data": data,
                    "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S.%fZ")
                })
                return result
            else:
                self.logger.warning("Cannot send to Java: communication not established")
                return False
        return send_to_java
    
    def _create_get_from_java_function(self):
        """Create a helper function for the target script to get messages from Java."""
        def get_from_java(timeout=5.0):
            """Get queued messages from Java process."""
            if not self.is_communicating:
                self.logger.warning("Cannot get from Java: communication not established")
                return None
            
            # Wait for messages with timeout
            start_time = time.time()
            while time.time() - start_time < timeout:
                messages = self.get_queued_messages()
                if messages:
                    # Return the first data message
                    for msg in messages:
                        if msg.get('messageType') == 'data':
                            return msg.get('payload')
                time.sleep(0.1)
            
            return None
        return get_from_java
    
    def run(self) -> int:
        """Main bootstrap execution method."""
        exit_code = 0
        
        try:
            # Parse command line arguments
            args = self.parse_arguments()
            
            # Configure logging first
            self.configure_logging(args.log_level, args.log_format)
            
            self.logger.info("Python Bootstrap starting...")
            self.logger.info(f"Python version: {sys.version}")
            self.logger.info(f"Process ID: {os.getpid()}")
            
            # Set up resource limits
            self.setup_resource_limits(args.memory_limit_mb, args.cpu_limit_percent)
            
            # Validate target script
            self.target_script_path = self.validate_target_script(args.script)
            
            # Parse script arguments
            self.script_args = self.load_script_arguments(args.args)
            
            # Set up working directory
            self.setup_working_directory(args.working_dir)
            
            # Establish communication with Java process
            communication_established = False
            if args.communication_port:
                communication_established = self.establish_communication(
                    args.communication_host, args.communication_port)
            
            # Report initialization status
            self.report_startup_status()
            
            # Load and execute target script
            script_exit_code = self.load_and_execute_script(
                self.target_script_path, self.script_args)
            
            if script_exit_code != 0:
                self.logger.error(f"Target script failed with exit code: {script_exit_code}")
                exit_code = script_exit_code
            else:
                self.logger.info("Target script completed successfully")
            
        except Exception as e:
            if self.logger:
                self.logger.error(f"Bootstrap failed: {e}")
                self.logger.error(f"Traceback: {traceback.format_exc()}")
            else:
                print(f"BOOTSTRAP_ERROR: {str(e)}", file=sys.stderr, flush=True)
                traceback.print_exc()
            exit_code = 1
        
        finally:
            # Clean up communication
            try:
                self.close_communication()
            except Exception as e:
                if self.logger:
                    self.logger.warning(f"Error during communication cleanup: {e}")
        
        return exit_code


def main():
    """Entry point for the bootstrap script."""
    bootstrap = PythonBootstrap()
    exit_code = bootstrap.run()
    sys.exit(exit_code)


if __name__ == '__main__':
    main()