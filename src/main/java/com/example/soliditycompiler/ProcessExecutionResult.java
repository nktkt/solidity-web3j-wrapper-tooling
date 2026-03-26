package com.example.soliditycompiler;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ProcessExecutionResult(
        int exitCode,
        String stdout,
        String stderr,
        List<String> command,
        Path workingDirectory
) {
    public ProcessExecutionResult {
        Objects.requireNonNull(stdout, "stdout must not be null");
        Objects.requireNonNull(stderr, "stderr must not be null");
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(workingDirectory, "workingDirectory must not be null");
        command = List.copyOf(command);
        workingDirectory = workingDirectory.toAbsolutePath().normalize();
    }

    public String commandLine() {
        return String.join(" ", command);
    }
}

