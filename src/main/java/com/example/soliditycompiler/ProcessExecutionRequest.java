package com.example.soliditycompiler;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record ProcessExecutionRequest(
        List<String> command,
        Path workingDirectory,
        Duration timeout
) {
    public ProcessExecutionRequest {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(workingDirectory, "workingDirectory must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        command = List.copyOf(command);
        workingDirectory = workingDirectory.toAbsolutePath().normalize();
    }

    public String commandLine() {
        return String.join(" ", command);
    }
}

