package com.example.soliditycompiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.soliditycompiler.TestSupport.ScriptedCommandExecutor;
import com.example.soliditycompiler.exception.CompilerNotFoundException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SolcLocatorTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsLocalSolcWhenVersionCommandSucceeds() {
        ScriptedCommandExecutor commandExecutor = new ScriptedCommandExecutor();
        commandExecutor.enqueueResult(0, "solc, the solidity compiler", "");

        SolcLocator locator = new SolcLocator(commandExecutor, Duration.ofSeconds(5), tempDir);

        SolcExecutionStrategy strategy = locator.locate();

        assertEquals(CompilerMode.LOCAL_SOLC, strategy.mode());
        assertEquals(List.of("solc", "--version"), commandExecutor.requests().getFirst().command());
    }

    @Test
    void fallsBackToDockerWhenLocalSolcIsMissing() {
        ScriptedCommandExecutor commandExecutor = new ScriptedCommandExecutor();
        commandExecutor.enqueueResult(127, "", "solc not found");
        commandExecutor.enqueueResult(0, "Docker version", "");
        commandExecutor.enqueueResult(0, "Docker info", "");

        SolcLocator locator = new SolcLocator(commandExecutor, Duration.ofSeconds(5), tempDir);

        SolcExecutionStrategy strategy = locator.locate();

        assertEquals(CompilerMode.DOCKER_SOLC, strategy.mode());
        assertEquals(List.of("solc", "--version"), commandExecutor.requests().get(0).command());
        assertEquals(List.of("docker", "--version"), commandExecutor.requests().get(1).command());
        assertEquals(List.of("docker", "info"), commandExecutor.requests().get(2).command());
    }

    @Test
    void throwsWhenNeitherLocalSolcNorDockerIsAvailable() {
        ScriptedCommandExecutor commandExecutor = new ScriptedCommandExecutor();
        commandExecutor.enqueueResult(127, "", "solc not found");
        commandExecutor.enqueueResult(127, "", "docker not found");

        SolcLocator locator = new SolcLocator(commandExecutor, Duration.ofSeconds(5), tempDir);

        assertThrows(CompilerNotFoundException.class, locator::locate);
    }

    @Test
    void throwsWhenDockerDaemonIsUnavailable() {
        ScriptedCommandExecutor commandExecutor = new ScriptedCommandExecutor();
        commandExecutor.enqueueResult(127, "", "solc not found");
        commandExecutor.enqueueResult(0, "Docker version", "");
        commandExecutor.enqueueResult(1, "", "Cannot connect to the Docker daemon");

        SolcLocator locator = new SolcLocator(commandExecutor, Duration.ofSeconds(5), tempDir);

        CompilerNotFoundException exception = assertThrows(CompilerNotFoundException.class, locator::locate);

        assertEquals(List.of("docker", "info"), commandExecutor.requests().get(2).command());
        assertTrue(exception.getMessage().contains("Docker daemon"));
    }
}
