package com.example.soliditycompiler;

import com.example.soliditycompiler.exception.CompilerNotFoundException;
import com.example.soliditycompiler.exception.ExternalToolExecutionException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

public final class SolcLocator {

    private final CommandExecutor commandExecutor;
    private final Duration timeout;
    private final Path workingDirectory;

    public SolcLocator(CommandExecutor commandExecutor, Duration timeout) {
        this(commandExecutor, timeout, Paths.get("").toAbsolutePath().normalize());
    }

    public SolcLocator(CommandExecutor commandExecutor, Duration timeout, Path workingDirectory) {
        this.commandExecutor = commandExecutor;
        this.timeout = timeout;
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
    }

    public SolcExecutionStrategy locate() {
        if (isCommandSuccessful(List.of("solc", "--version"))) {
            return SolcExecutionStrategy.local();
        }

        ProcessExecutionResult dockerVersionResult = tryExecute(List.of("docker", "--version"));
        if (dockerVersionResult == null || dockerVersionResult.exitCode() != 0) {
            throw new CompilerNotFoundException(
                    "Neither `solc` nor Docker is available. Install `solc` on PATH or install Docker."
            );
        }

        ProcessExecutionResult dockerInfoResult = tryExecute(List.of("docker", "info"));
        if (dockerInfoResult != null && dockerInfoResult.exitCode() == 0) {
            return SolcExecutionStrategy.docker();
        }

        throw new CompilerNotFoundException(
                "`solc` was not found and Docker is installed, but the Docker daemon is unavailable. "
                        + "Start Docker and retry.\n"
                        + "Docker stdout:\n"
                        + formatOutput(dockerInfoResult == null ? "" : dockerInfoResult.stdout())
                        + "\nDocker stderr:\n"
                        + formatOutput(dockerInfoResult == null ? "" : dockerInfoResult.stderr())
        );
    }

    private boolean isCommandSuccessful(List<String> command) {
        ProcessExecutionResult result = tryExecute(command);
        return result != null && result.exitCode() == 0;
    }

    private ProcessExecutionResult tryExecute(List<String> command) {
        try {
            return commandExecutor.execute(
                    new ProcessExecutionRequest(
                            command,
                            workingDirectory,
                            timeout
                    )
            );
        } catch (ExternalToolExecutionException exception) {
            return null;
        }
    }

    private String formatOutput(String output) {
        return output == null || output.isBlank() ? "<empty>" : output;
    }
}
