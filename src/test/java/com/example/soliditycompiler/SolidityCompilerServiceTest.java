package com.example.soliditycompiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.soliditycompiler.TestSupport.ScriptedCommandExecutor;
import com.example.soliditycompiler.exception.CompilationFailedException;
import com.example.soliditycompiler.exception.CompilerNotFoundException;
import com.example.soliditycompiler.exception.ExternalToolExecutionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SolidityCompilerServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsExpectedLocalSolcCommand() {
        ScriptedCommandExecutor commandExecutor = new ScriptedCommandExecutor();
        SolidityCompilerService service = new SolidityCompilerService(
                tempDir,
                new SolcLocator(commandExecutor, Duration.ofSeconds(5), tempDir),
                commandExecutor,
                Duration.ofSeconds(30)
        );

        Path sourcePath = tempDir.resolve("src/main/solidity/HelloWorld.sol");
        Path outputDirectory = tempDir.resolve("build/generated/contracts/raw/HelloWorld");

        List<String> command = service.buildLocalSolcCommand(sourcePath, outputDirectory);

        assertEquals(
                List.of(
                        "solc",
                        "src/main/solidity/HelloWorld.sol",
                        "--bin",
                        "--abi",
                        "--optimize",
                        "--base-path",
                        ".",
                        "--include-path",
                        "src/main/solidity",
                        "-o",
                        "build/generated/contracts/raw/HelloWorld"
                ),
                command
        );
    }

    @Test
    void buildsExpectedDockerCommand() {
        ScriptedCommandExecutor commandExecutor = new ScriptedCommandExecutor();
        SolidityCompilerService service = new SolidityCompilerService(
                tempDir,
                new SolcLocator(commandExecutor, Duration.ofSeconds(5), tempDir),
                commandExecutor,
                Duration.ofSeconds(30)
        );

        Path sourcePath = tempDir.resolve("src/main/solidity/HelloWorld.sol");
        Path outputDirectory = tempDir.resolve("build/generated/contracts/raw/HelloWorld");

        List<String> command = service.buildDockerSolcCommand(sourcePath, outputDirectory);

        assertEquals(expectedDockerCommand(), command);
    }

    @Test
    void discoversSolidityFilesRecursively() {
        TestSupport.createFile(tempDir.resolve("src/main/solidity/A.sol"), "contract A {}");
        TestSupport.createFile(tempDir.resolve("src/main/solidity/nested/B.sol"), "contract B {}");
        TestSupport.createFile(tempDir.resolve("src/main/solidity/ignore.txt"), "skip");

        ScriptedCommandExecutor commandExecutor = new ScriptedCommandExecutor();
        SolidityCompilerService service = new SolidityCompilerService(
                tempDir,
                new SolcLocator(commandExecutor, Duration.ofSeconds(5), tempDir),
                commandExecutor,
                Duration.ofSeconds(30)
        );

        List<Path> sources = service.findSoliditySources();

        assertEquals(
                List.of(
                        tempDir.resolve("src/main/solidity/A.sol"),
                        tempDir.resolve("src/main/solidity/nested/B.sol")
                ),
                sources
        );
    }

    @Test
    void compilesAndMovesAbiAndBinArtifacts() {
        Path sourcePath = TestSupport.createFile(
                tempDir.resolve("src/main/solidity/HelloWorld.sol"),
                "pragma solidity ^0.8.24; contract HelloWorld {}"
        );

        ScriptedCommandExecutor commandExecutor = new ScriptedCommandExecutor();
        commandExecutor.enqueueResult(0, "solc 0.8.x", "");
        commandExecutor.enqueueHandler(request -> {
            Path rawDirectory = tempDir.resolve("build/generated/contracts/raw/HelloWorld");
            TestSupport.createFile(rawDirectory.resolve("HelloWorld.abi"), "[]");
            TestSupport.createFile(rawDirectory.resolve("HelloWorld.bin"), "6000");
            return new ProcessExecutionResult(0, "compiled", "", request.command(), request.workingDirectory());
        });

        SolidityCompilerService service = new SolidityCompilerService(
                tempDir,
                new SolcLocator(commandExecutor, Duration.ofSeconds(5), tempDir),
                commandExecutor,
                Duration.ofSeconds(30)
        );

        List<CompilationArtifact> artifacts = service.compileAll();

        assertEquals(1, artifacts.size());
        CompilationArtifact artifact = artifacts.getFirst();
        assertEquals(sourcePath, artifact.sourcePath());
        assertEquals(CompilerMode.LOCAL_SOLC, artifact.compilerMode());
        assertTrue(Files.exists(tempDir.resolve("build/generated/contracts/abi/HelloWorld.abi")));
        assertTrue(Files.exists(tempDir.resolve("build/generated/contracts/bin/HelloWorld.bin")));
    }

    @Test
    void throwsCompilationFailedExceptionOnNonZeroExitCode() {
        TestSupport.createFile(
                tempDir.resolve("src/main/solidity/HelloWorld.sol"),
                "pragma solidity ^0.8.24; contract HelloWorld {}"
        );

        ScriptedCommandExecutor commandExecutor = new ScriptedCommandExecutor();
        commandExecutor.enqueueResult(0, "solc 0.8.x", "");
        commandExecutor.enqueueResult(1, "", "Parser error");

        SolidityCompilerService service = new SolidityCompilerService(
                tempDir,
                new SolcLocator(commandExecutor, Duration.ofSeconds(5), tempDir),
                commandExecutor,
                Duration.ofSeconds(30)
        );

        CompilationFailedException exception = assertThrows(CompilationFailedException.class, service::compileAll);

        assertTrue(exception.getMessage().contains("HelloWorld.sol"));
        assertTrue(exception.getMessage().contains("Parser error"));
    }

    @Test
    void propagatesTimeoutFailures() {
        TestSupport.createFile(
                tempDir.resolve("src/main/solidity/HelloWorld.sol"),
                "pragma solidity ^0.8.24; contract HelloWorld {}"
        );

        ScriptedCommandExecutor commandExecutor = new ScriptedCommandExecutor();
        commandExecutor.enqueueResult(0, "solc 0.8.x", "");
        commandExecutor.enqueueException(
                ExternalToolExecutionException.timeout(
                        List.of("solc", "HelloWorld.sol"),
                        tempDir,
                        Duration.ofSeconds(1),
                        "",
                        ""
                )
        );

        SolidityCompilerService service = new SolidityCompilerService(
                tempDir,
                new SolcLocator(commandExecutor, Duration.ofSeconds(5), tempDir),
                commandExecutor,
                Duration.ofSeconds(30)
        );

        RuntimeException exception = assertThrows(RuntimeException.class, service::compileAll);

        assertInstanceOf(ExternalToolExecutionException.class, exception);
    }

    @Test
    void preservesExistingArtifactsWhenCompilerCannotBeLocated() {
        TestSupport.createFile(
                tempDir.resolve("src/main/solidity/HelloWorld.sol"),
                "pragma solidity ^0.8.24; contract HelloWorld {}"
        );
        Path existingAbi = TestSupport.createFile(
                tempDir.resolve("build/generated/contracts/abi/Existing.abi"),
                "[]"
        );

        ScriptedCommandExecutor commandExecutor = new ScriptedCommandExecutor();
        commandExecutor.enqueueResult(127, "", "solc not found");
        commandExecutor.enqueueResult(127, "", "docker not found");

        SolidityCompilerService service = new SolidityCompilerService(
                tempDir,
                new SolcLocator(commandExecutor, Duration.ofSeconds(5), tempDir),
                commandExecutor,
                Duration.ofSeconds(30)
        );

        assertThrows(CompilerNotFoundException.class, service::compileAll);
        assertTrue(Files.exists(existingAbi));
    }

    private List<String> expectedDockerCommand() {
        List<String> expected = new ArrayList<>(List.of("docker", "run", "--rm"));
        String userMapping = currentUserMapping();
        if (userMapping != null) {
            expected.add("--user");
            expected.add(userMapping);
        }
        expected.add("-v");
        expected.add(tempDir.toAbsolutePath().normalize() + ":/workspace");
        expected.add("-w");
        expected.add("/workspace");
        expected.add("ghcr.io/argotorg/solc:stable");
        expected.add("/workspace/src/main/solidity/HelloWorld.sol");
        expected.add("--bin");
        expected.add("--abi");
        expected.add("--optimize");
        expected.add("--base-path");
        expected.add("/workspace");
        expected.add("--include-path");
        expected.add("/workspace/src/main/solidity");
        expected.add("-o");
        expected.add("/workspace/build/generated/contracts/raw/HelloWorld");
        return expected;
    }

    private String currentUserMapping() {
        try {
            Object uid = Files.getAttribute(tempDir, "unix:uid");
            Object gid = Files.getAttribute(tempDir, "unix:gid");
            return uid + ":" + gid;
        } catch (UnsupportedOperationException | IOException exception) {
            return null;
        }
    }
}
