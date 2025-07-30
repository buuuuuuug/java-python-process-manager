#!/usr/bin/env python3
"""
Unit tests for the Python Bootstrap script.
"""

import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch, MagicMock

# Add the bootstrap script to the path
bootstrap_path = Path(__file__).parent.parent.parent / 'main' / 'resources'
sys.path.insert(0, str(bootstrap_path))

from pythonBootstrap import PythonBootstrap


class TestPythonBootstrap(unittest.TestCase):
    """Test cases for PythonBootstrap class."""
    
    def setUp(self):
        """Set up test fixtures."""
        self.bootstrap = PythonBootstrap()
        
    def test_parse_arguments_with_required_script(self):
        """Test argument parsing with required script parameter."""
        test_args = ['--script', '/path/to/script.py']
        
        with patch('sys.argv', ['pythonBootstrap.py'] + test_args):
            args = self.bootstrap.parse_arguments()
            
        self.assertEqual(args.script, '/path/to/script.py')
        self.assertEqual(args.memory_limit_mb, 512)  # default
        self.assertEqual(args.cpu_limit_percent, 80.0)  # default
        self.assertEqual(args.log_level, 'INFO')  # default
    
    def test_parse_arguments_with_all_parameters(self):
        """Test argument parsing with all parameters specified."""
        test_args = [
            '--script', '/path/to/script.py',
            '--args', '{"key": "value"}',
            '--memory-limit-mb', '1024',
            '--cpu-limit-percent', '50.0',
            '--log-level', 'DEBUG',
            '--log-format', 'custom format',
            '--working-dir', '/tmp'
        ]
        
        with patch('sys.argv', ['pythonBootstrap.py'] + test_args):
            args = self.bootstrap.parse_arguments()
            
        self.assertEqual(args.script, '/path/to/script.py')
        self.assertEqual(args.args, '{"key": "value"}')
        self.assertEqual(args.memory_limit_mb, 1024)
        self.assertEqual(args.cpu_limit_percent, 50.0)
        self.assertEqual(args.log_level, 'DEBUG')
        self.assertEqual(args.log_format, 'custom format')
        self.assertEqual(args.working_dir, '/tmp')
    
    @patch('resource.getrlimit')
    @patch('resource.setrlimit')
    def test_setup_resource_limits_success(self, mock_setrlimit, mock_getrlimit):
        """Test successful resource limit setup."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Mock getrlimit to return reasonable values
        mock_getrlimit.side_effect = [
            (1024*1024*1024, 2*1024*1024*1024),  # RLIMIT_AS
            (3600, 7200),  # RLIMIT_CPU
            (256, 1024)    # RLIMIT_NOFILE
        ]
        
        # Test resource limit setup
        self.bootstrap.setup_resource_limits(256, 60.0)
        
        # Check resource limits were stored
        self.assertEqual(self.bootstrap.resource_limits['memory_mb'], 256)
        self.assertEqual(self.bootstrap.resource_limits['cpu_percent'], 60.0)
        self.assertEqual(self.bootstrap.resource_limits['memory_bytes'], 256 * 1024 * 1024)
    
    @patch('resource.getrlimit')
    def test_setup_resource_limits_failure(self, mock_getrlimit):
        """Test resource limit setup failure handling."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Mock getrlimit to raise an exception
        mock_getrlimit.side_effect = OSError("Permission denied")
        
        # Test that the method handles the exception gracefully
        # (it should not raise an exception but log a warning)
        self.bootstrap.setup_resource_limits(256, 60.0)
        
        # The method should complete without raising an exception
    
    def test_configure_logging(self):
        """Test logging configuration."""
        self.bootstrap.configure_logging('DEBUG', '%(levelname)s: %(message)s')
        
        # Verify logger was created and configured
        self.assertIsNotNone(self.bootstrap.logger)
        self.assertEqual(self.bootstrap.logger.level, 10)  # DEBUG level
        self.assertEqual(len(self.bootstrap.logger.handlers), 1)
        
        # Verify log config was stored
        self.assertEqual(self.bootstrap.log_config['level'], 'DEBUG')
        self.assertEqual(self.bootstrap.log_config['format'], '%(levelname)s: %(message)s')
    
    def test_validate_target_script_success(self):
        """Test successful target script validation."""
        # Create a temporary script file
        with tempfile.NamedTemporaryFile(mode='w', suffix='.py', delete=False) as temp_file:
            temp_file.write('print("Hello, World!")')
            temp_script_path = temp_file.name
        
        try:
            result = self.bootstrap.validate_target_script(temp_script_path)
            self.assertEqual(str(result), temp_script_path)
        finally:
            os.unlink(temp_script_path)
    
    def test_validate_target_script_not_found(self):
        """Test target script validation with non-existent file."""
        with self.assertRaises(FileNotFoundError) as context:
            self.bootstrap.validate_target_script('/nonexistent/script.py')
        
        self.assertIn("Target script not found", str(context.exception))
    
    def test_validate_target_script_is_directory(self):
        """Test target script validation with directory instead of file."""
        with tempfile.TemporaryDirectory() as temp_dir:
            with self.assertRaises(ValueError) as context:
                self.bootstrap.validate_target_script(temp_dir)
            
            self.assertIn("Target script is not a file", str(context.exception))
    
    def test_load_script_arguments_valid_json(self):
        """Test loading valid JSON script arguments."""
        args_json = '{"name": "test", "count": 42, "enabled": true}'
        result = self.bootstrap.load_script_arguments(args_json)
        
        expected = {"name": "test", "count": 42, "enabled": True}
        self.assertEqual(result, expected)
    
    def test_load_script_arguments_empty_json(self):
        """Test loading empty JSON script arguments."""
        result = self.bootstrap.load_script_arguments('{}')
        self.assertEqual(result, {})
    
    def test_load_script_arguments_invalid_json(self):
        """Test loading invalid JSON script arguments."""
        with self.assertRaises(ValueError) as context:
            self.bootstrap.load_script_arguments('invalid json')
        
        self.assertIn("Invalid JSON in arguments", str(context.exception))
    
    def test_load_script_arguments_non_object(self):
        """Test loading JSON that's not an object."""
        with self.assertRaises(ValueError) as context:
            self.bootstrap.load_script_arguments('["array", "not", "object"]')
        
        self.assertIn("Arguments must be a JSON object", str(context.exception))
    
    @patch('os.chdir')
    def test_setup_working_directory_success(self, mock_chdir):
        """Test successful working directory setup."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        self.bootstrap.setup_working_directory('/tmp')
        mock_chdir.assert_called_once_with('/tmp')
    
    @patch('os.chdir')
    def test_setup_working_directory_failure(self, mock_chdir):
        """Test working directory setup failure."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        mock_chdir.side_effect = OSError("Permission denied")
        
        with self.assertRaises(RuntimeError) as context:
            self.bootstrap.setup_working_directory('/invalid/path')
        
        self.assertIn("Working directory setup failed", str(context.exception))
    
    def test_setup_working_directory_none(self):
        """Test working directory setup with None (should do nothing)."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Should not raise any exception
        self.bootstrap.setup_working_directory(None)
    
    def test_send_message_without_communication(self):
        """Test sending message when communication is not established."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # When
        result = self.bootstrap.send_message('test', {'data': 'test'})
        
        # Then
        self.assertFalse(result)
    
    def test_receive_message_without_communication(self):
        """Test receiving message when communication is not established."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # When
        message = self.bootstrap.receive_message()
        
        # Then
        self.assertIsNone(message)
    
    def test_get_queued_messages(self):
        """Test getting queued messages."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # When
        messages = self.bootstrap.get_queued_messages()
        
        # Then
        self.assertEqual(messages, [])
    
    def test_close_communication_without_establishment(self):
        """Test closing communication when it was never established."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Should not raise any exception
        self.bootstrap.close_communication()
    
    def test_handle_command_ping(self):
        """Test handling ping command."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Mock send_message to capture the response
        sent_messages = []
        original_send = self.bootstrap.send_message
        self.bootstrap.send_message = lambda msg_type, payload: sent_messages.append((msg_type, payload))
        
        # When
        self.bootstrap._handle_command({'type': 'ping'})
        
        # Then
        self.assertEqual(len(sent_messages), 1)
        self.assertEqual(sent_messages[0][0], 'command_response')
        self.assertEqual(sent_messages[0][1]['type'], 'pong')
        
        # Restore original method
        self.bootstrap.send_message = original_send
    
    def test_handle_command_status(self):
        """Test handling status command."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Set up some test data
        self.bootstrap.resource_limits = {'memory_mb': 512}
        
        # Mock send_message to capture the response
        sent_messages = []
        original_send = self.bootstrap.send_message
        self.bootstrap.send_message = lambda msg_type, payload: sent_messages.append((msg_type, payload))
        
        # When
        self.bootstrap._handle_command({'type': 'status'})
        
        # Then
        self.assertEqual(len(sent_messages), 1)
        self.assertEqual(sent_messages[0][0], 'command_response')
        response = sent_messages[0][1]
        self.assertEqual(response['type'], 'status_response')
        self.assertEqual(response['status'], 'running')
        self.assertIn('pid', response)
        
        # Restore original method
        self.bootstrap.send_message = original_send
    
    @patch('os.getpid')
    @patch('os.getcwd')
    @patch('builtins.print')
    def test_report_startup_status(self, mock_print, mock_getcwd, mock_getpid):
        """Test startup status reporting."""
        # Configure logging and set up test data
        self.bootstrap.configure_logging('INFO', '%(message)s')
        self.bootstrap.resource_limits = {'memory_mb': 512}
        self.bootstrap.log_config = {'level': 'INFO'}
        self.bootstrap.target_script_path = Path('/test/script.py')
        
        mock_getpid.return_value = 12345
        mock_getcwd.return_value = '/test/dir'
        
        self.bootstrap.report_startup_status()
        
        # Verify print was called with JSON status
        mock_print.assert_called_once()
        call_args = mock_print.call_args[0][0]
        self.assertTrue(call_args.startswith('BOOTSTRAP_STATUS: '))
        
        # Parse and verify the JSON content
        json_str = call_args.replace('BOOTSTRAP_STATUS: ', '')
        status_data = json.loads(json_str)
        
        self.assertEqual(status_data['status'], 'initialized')
        self.assertEqual(status_data['pid'], 12345)
        self.assertEqual(status_data['working_directory'], '/test/dir')
    
    def test_load_script_code_success(self):
        """Test successful script code loading."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Create a temporary script file
        with tempfile.NamedTemporaryFile(mode='w', suffix='.py', delete=False) as temp_file:
            temp_file.write('print("Hello, World!")\n')
            temp_script_path = Path(temp_file.name)
        
        try:
            # When
            code = self.bootstrap._load_script_code(temp_script_path)
            
            # Then
            self.assertEqual(code, 'print("Hello, World!")\n')
        finally:
            os.unlink(temp_script_path)
    
    def test_load_script_code_file_not_found(self):
        """Test script code loading with non-existent file."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # When/Then
        with self.assertRaises(FileNotFoundError):
            self.bootstrap._load_script_code(Path('/nonexistent/script.py'))
    
    def test_prepare_script_environment(self):
        """Test script environment preparation."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Set up test data
        self.bootstrap.target_script_path = Path('/test/script.py')
        script_args = {'key': 'value', 'number': 42}
        
        # When
        env = self.bootstrap._prepare_script_environment(script_args)
        
        # Then
        self.assertIn('__builtins__', env)
        self.assertEqual(env['__name__'], '__main__')
        self.assertEqual(env['__file__'], '/test/script.py')
        self.assertEqual(env['SCRIPT_ARGS'], script_args)
        self.assertIn('get_logger', env)
    
    def test_prepare_script_environment_with_communication(self):
        """Test script environment preparation with communication enabled."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Set up test data
        self.bootstrap.target_script_path = Path('/test/script.py')
        self.bootstrap.is_communicating = True
        script_args = {'test': 'data'}
        
        # When
        env = self.bootstrap._prepare_script_environment(script_args)
        
        # Then
        self.assertIn('send_to_java', env)
        self.assertIn('get_from_java', env)
        self.assertTrue(callable(env['send_to_java']))
        self.assertTrue(callable(env['get_from_java']))
    
    def test_load_and_execute_script_success(self):
        """Test successful script loading and execution."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Create a simple test script
        with tempfile.NamedTemporaryFile(mode='w', suffix='.py', delete=False) as temp_file:
            temp_file.write('''
# Simple test script
result = 2 + 2
print(f"Result: {result}")
''')
            temp_script_path = Path(temp_file.name)
        
        try:
            # When
            exit_code = self.bootstrap.load_and_execute_script(temp_script_path, {})
            
            # Then
            self.assertEqual(exit_code, 0)
        finally:
            os.unlink(temp_script_path)
    
    def test_load_and_execute_script_with_sys_exit(self):
        """Test script execution with sys.exit()."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Create a script that calls sys.exit()
        with tempfile.NamedTemporaryFile(mode='w', suffix='.py', delete=False) as temp_file:
            temp_file.write('''
import sys
print("Script running")
sys.exit(42)
''')
            temp_script_path = Path(temp_file.name)
        
        try:
            # When
            exit_code = self.bootstrap.load_and_execute_script(temp_script_path, {})
            
            # Then
            self.assertEqual(exit_code, 42)
        finally:
            os.unlink(temp_script_path)
    
    def test_load_and_execute_script_with_exception(self):
        """Test script execution with exception."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Create a script that raises an exception
        with tempfile.NamedTemporaryFile(mode='w', suffix='.py', delete=False) as temp_file:
            temp_file.write('''
print("Script starting")
raise ValueError("Test error")
''')
            temp_script_path = Path(temp_file.name)
        
        try:
            # When
            exit_code = self.bootstrap.load_and_execute_script(temp_script_path, {})
            
            # Then
            self.assertEqual(exit_code, 1)
        finally:
            os.unlink(temp_script_path)
    
    def test_load_and_execute_script_with_args(self):
        """Test script execution with arguments."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Create a script that uses SCRIPT_ARGS
        with tempfile.NamedTemporaryFile(mode='w', suffix='.py', delete=False) as temp_file:
            temp_file.write('''
# Access script arguments
name = SCRIPT_ARGS.get('name', 'World')
count = SCRIPT_ARGS.get('count', 1)
for i in range(count):
    print(f"Hello, {name}! ({i+1})")
''')
            temp_script_path = Path(temp_file.name)
        
        try:
            # When
            script_args = {'name': 'Test', 'count': 2}
            exit_code = self.bootstrap.load_and_execute_script(temp_script_path, script_args)
            
            # Then
            self.assertEqual(exit_code, 0)
        finally:
            os.unlink(temp_script_path)
    
    def test_send_to_java_function(self):
        """Test the send_to_java helper function."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Set up communication
        self.bootstrap.is_communicating = True
        
        # Mock send_message to capture calls
        sent_messages = []
        original_send = self.bootstrap.send_message
        def mock_send_message(msg_type, payload):
            sent_messages.append((msg_type, payload))
            return True
        self.bootstrap.send_message = mock_send_message
        
        try:
            # Create the helper function
            send_to_java = self.bootstrap._create_send_to_java_function()
            
            # When
            result = send_to_java("test data", "custom_type")
            
            # Then
            self.assertTrue(result)
            self.assertEqual(len(sent_messages), 1)
            self.assertEqual(sent_messages[0][0], "custom_type")
            self.assertEqual(sent_messages[0][1]["source"], "target_script")
            self.assertEqual(sent_messages[0][1]["data"], "test data")
        finally:
            # Restore original method
            self.bootstrap.send_message = original_send
    
    def test_get_from_java_function(self):
        """Test the get_from_java helper function."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Set up communication
        self.bootstrap.is_communicating = True
        
        # Add a test message to the queue
        test_message = {
            'messageType': 'data',
            'payload': {'test': 'data from java'}
        }
        with self.bootstrap.message_lock:
            self.bootstrap.message_queue.append(test_message)
        
        # Create the helper function
        get_from_java = self.bootstrap._create_get_from_java_function()
        
        # When
        result = get_from_java(timeout=1.0)
        
        # Then
        self.assertEqual(result, {'test': 'data from java'})


if __name__ == '__main__':
    unittest.main()  
  
    def test_load_and_execute_script_success(self):
        """Test successful script loading and execution."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Create a simple test script
        with tempfile.NamedTemporaryFile(mode='w', suffix='.py', delete=False) as temp_file:
            temp_file.write('''
print("Test script executed successfully")
result = 42
''')
            temp_script_path = temp_file.name
        
        try:
            # Set up bootstrap state
            self.bootstrap.target_script_path = Path(temp_script_path)
            
            # When
            exit_code = self.bootstrap.load_and_execute_script(
                Path(temp_script_path), {'test': 'value'})
            
            # Then
            self.assertEqual(exit_code, 0)
            
        finally:
            os.unlink(temp_script_path)
    
    def test_load_and_execute_script_with_sys_exit(self):
        """Test script execution with sys.exit()."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Create a test script that calls sys.exit()
        with tempfile.NamedTemporaryFile(mode='w', suffix='.py', delete=False) as temp_file:
            temp_file.write('''
import sys
print("Script calling sys.exit(5)")
sys.exit(5)
''')
            temp_script_path = temp_file.name
        
        try:
            # Set up bootstrap state
            self.bootstrap.target_script_path = Path(temp_script_path)
            
            # When
            exit_code = self.bootstrap.load_and_execute_script(
                Path(temp_script_path), {})
            
            # Then
            self.assertEqual(exit_code, 5)
            
        finally:
            os.unlink(temp_script_path)
    
    def test_load_and_execute_script_with_exception(self):
        """Test script execution with exception."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Create a test script that raises an exception
        with tempfile.NamedTemporaryFile(mode='w', suffix='.py', delete=False) as temp_file:
            temp_file.write('''
print("Script about to raise exception")
raise ValueError("Test exception")
''')
            temp_script_path = temp_file.name
        
        try:
            # Set up bootstrap state
            self.bootstrap.target_script_path = Path(temp_script_path)
            
            # When
            exit_code = self.bootstrap.load_and_execute_script(
                Path(temp_script_path), {})
            
            # Then
            self.assertEqual(exit_code, 1)
            
        finally:
            os.unlink(temp_script_path)
    
    def test_load_script_code_file_not_found(self):
        """Test loading non-existent script file."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # When/Then
        with self.assertRaises(FileNotFoundError):
            self.bootstrap._load_script_code(Path('/nonexistent/script.py'))
    
    def test_prepare_script_environment(self):
        """Test script environment preparation."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Set up bootstrap state
        self.bootstrap.target_script_path = Path('/test/script.py')
        
        # When
        script_globals = self.bootstrap._prepare_script_environment({'key': 'value'})
        
        # Then
        self.assertIn('__builtins__', script_globals)
        self.assertEqual(script_globals['__name__'], '__main__')
        self.assertEqual(script_globals['__file__'], '/test/script.py')
        self.assertEqual(script_globals['SCRIPT_ARGS'], {'key': 'value'})
        self.assertIn('get_logger', script_globals)
    
    def test_prepare_script_environment_with_communication(self):
        """Test script environment preparation with communication enabled."""
        # Configure logging first
        self.bootstrap.configure_logging('INFO', '%(message)s')
        
        # Set up bootstrap state
        self.bootstrap.target_script_path = Path('/test/script.py')
        self.bootstrap.is_communicating = True
        
        # When
        script_globals = self.bootstrap._prepare_script_environment({'key': 'value'})
        
        # Then
        self.assertIn('send_to_java', script_globals)
        self.assertIn('get_from_java', script_globals)
        
        # Test the helper functions
        send_func = script_globals['send_to_java']
        get_func = script_globals['get_from_java']
        
        self.assertTrue(callable(send_func))
        self.assertTrue(callable(get_func))