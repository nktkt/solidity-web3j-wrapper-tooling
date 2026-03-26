package com.example.soliditycompiler.exception;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class ExternalToolExecutionException extends RuntimeException {

    private final List<String> command;
    private final Path workingDirectory;
    private final String stdout;
    private final String stderr;
    private final Duration timeout;

    private ExternalToolExecutionException(
            String message,
            List<String> command,
            Path workingDirectory,
            String stdout,
            String stderr,
            Duration timeout,
            Throwable cause
    ) {
        super(message, cause);
        this.command = List.copyOf(command);
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
        this.timeout = timeout;
    }

    public static ExternalToolExecutionException timeout(
            List<String> command,
            Path workingDirectory,
            Duration timeout,
            String stdout,
            String stderr
    ) {
        return new ExternalToolExecutionException(
                "Command timed out after " + timeout + ": " + String.join(" ", command)
                        + " (working directory: " + workingDirectory + ")",
                command,
                workingDirectory,
                stdout,
                stderr,
                timeout,
                null
        );
    }

    public static ExternalToolExecutionException startFailure(
            List<String> command,
            Path workingDirectory,
            Throwable cause
    ) {
        return new ExternalToolExecutionException(
                "Failed to start command: " + String.join(" ", command)
                        + " (working directory: " + workingDirectory + ")",
                command,
                workingDirectory,
                "",
                "",
                null,
                cause
        );
    }

    public static ExternalToolExecutionException interrupted(
            List<String> command,
            Path workingDirectory,
            Throwable cause
    ) {
        return new ExternalToolExecutionException(
                "Interrupted while executing command: " + String.join(" ", command)
                        + " (working directory: " + workingDirectory + ")",
                command,
                workingDirectory,
                "",
                "",
                null,
                cause
        );
    }

    public List<String> getCommand() {
        return command;
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public Duration getTimeout() {
        return timeout;
    }
}

