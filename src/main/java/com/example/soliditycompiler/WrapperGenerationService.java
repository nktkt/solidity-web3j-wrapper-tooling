package com.example.soliditycompiler;

import com.example.soliditycompiler.exception.ExternalToolExecutionException;
import com.example.soliditycompiler.exception.WrapperGenerationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class WrapperGenerationService {

    private static final String WRAPPER_PACKAGE = "com.example.soliditycompiler.generated";

    private final Path projectRoot;
    private final Path abiOutputDirectory;
    private final Path binOutputDirectory;
    private final Path javaOutputDirectory;
    private final CommandExecutor commandExecutor;
    private final Duration timeout;

    public WrapperGenerationService(Path projectRoot, CommandExecutor commandExecutor, Duration timeout) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.abiOutputDirectory = this.projectRoot.resolve("build/generated/contracts/abi");
        this.binOutputDirectory = this.projectRoot.resolve("build/generated/contracts/bin");
        this.javaOutputDirectory = this.projectRoot.resolve("build/generated/source/web3j/main/java");
        this.commandExecutor = commandExecutor;
        this.timeout = timeout;
    }

    public List<Path> generateWrappers() {
        List<Path> abiFiles = discoverAbiFiles();
        if (abiFiles.isEmpty()) {
            throw new WrapperGenerationException(
                    "No ABI files were found under " + abiOutputDirectory + ". Run compile first."
            );
        }

        List<ArtifactPair> artifactPairs = abiFiles.stream()
                .map(this::toArtifactPair)
                .toList();

        verifyWeb3jAvailable();
        recreateDirectory(javaOutputDirectory);
        for (ArtifactPair artifactPair : artifactPairs) {
            ProcessExecutionResult result = commandExecutor.execute(
                    new ProcessExecutionRequest(
                            buildWeb3jCommand(artifactPair.binPath(), artifactPair.abiPath()),
                            projectRoot,
                            timeout
                    )
            );

            if (result.exitCode() != 0) {
                throw new WrapperGenerationException(buildFailureMessage(artifactPair.contractName(), result));
            }
        }

        List<Path> generatedFiles = discoverGeneratedJavaFiles();
        if (generatedFiles.isEmpty()) {
            throw new WrapperGenerationException(
                    "Wrapper generation completed without producing Java files under " + javaOutputDirectory
            );
        }
        return generatedFiles;
    }

    List<String> buildWeb3jCommand(Path binPath, Path abiPath) {
        return List.of(
                "web3j",
                "generate",
                "solidity",
                "-b",
                toProjectRelativeString(binPath),
                "-a",
                toProjectRelativeString(abiPath),
                "-o",
                toProjectRelativeString(javaOutputDirectory),
                "-p",
                WRAPPER_PACKAGE
        );
    }

    public Path getJavaOutputDirectory() {
        return javaOutputDirectory;
    }

    private ArtifactPair toArtifactPair(Path abiPath) {
        String contractName = stripExtension(abiPath.getFileName().toString());
        Path binPath = binOutputDirectory.resolve(contractName + ".bin");
        validateArtifactPair(abiPath, binPath, contractName);
        return new ArtifactPair(contractName, abiPath, binPath);
    }

    private List<Path> discoverAbiFiles() {
        if (!Files.exists(abiOutputDirectory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(abiOutputDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".abi"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new WrapperGenerationException("Failed to inspect ABI directory " + abiOutputDirectory, exception);
        }
    }

    private List<Path> discoverGeneratedJavaFiles() {
        if (!Files.exists(javaOutputDirectory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(javaOutputDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new WrapperGenerationException(
                    "Failed to inspect generated wrapper directory " + javaOutputDirectory,
                    exception
            );
        }
    }

    private void validateArtifactPair(Path abiPath, Path binPath, String contractName) {
        if (!Files.exists(abiPath)) {
            throw new WrapperGenerationException("Missing ABI for contract `" + contractName + "`: " + abiPath);
        }
        if (!Files.exists(binPath)) {
            throw new WrapperGenerationException("Missing BIN for contract `" + contractName + "`: " + binPath);
        }
    }

    private void verifyWeb3jAvailable() {
        ProcessExecutionResult result;
        try {
            result = commandExecutor.execute(
                    new ProcessExecutionRequest(
                            List.of("web3j", "-V"),
                            projectRoot,
                            timeout
                    )
            );
        } catch (ExternalToolExecutionException exception) {
            if (exception.getTimeout() != null) {
                throw exception;
            }
            throw new WrapperGenerationException(
                    "Web3j CLI verification failed. Ensure `web3j` is installed and available on PATH.",
                    exception
            );
        }

        if (result.exitCode() != 0) {
            throw new WrapperGenerationException(
                    """
                            Web3j CLI verification failed.
                            Working directory: %s
                            Command: %s
                            Exit code: %d
                            Stdout:
                            %s
                            Stderr:
                            %s
                            """.formatted(
                            result.workingDirectory(),
                            result.commandLine(),
                            result.exitCode(),
                            result.stdout().isBlank() ? "<empty>" : result.stdout(),
                            result.stderr().isBlank() ? "<empty>" : result.stderr()
                    )
            );
        }
    }

    private String buildFailureMessage(String contractName, ProcessExecutionResult result) {
        return """
                Web3j wrapper generation failed for %s.
                Working directory: %s
                Command: %s
                Exit code: %d
                Stdout:
                %s
                Stderr:
                %s
                """.formatted(
                contractName,
                result.workingDirectory(),
                result.commandLine(),
                result.exitCode(),
                result.stdout().isBlank() ? "<empty>" : result.stdout(),
                result.stderr().isBlank() ? "<empty>" : result.stderr()
        );
    }

    private void recreateDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                deleteRecursively(directory);
            }
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new WrapperGenerationException("Failed to prepare directory " + directory, exception);
        }
    }

    private void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private String toProjectRelativeString(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(projectRoot)) {
            return projectRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString().replace('\\', '/');
    }

    private String stripExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot >= 0 ? fileName.substring(0, lastDot) : fileName;
    }

    private record ArtifactPair(String contractName, Path abiPath, Path binPath) {
    }
}
