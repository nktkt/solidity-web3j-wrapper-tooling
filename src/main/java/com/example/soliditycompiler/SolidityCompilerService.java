package com.example.soliditycompiler;

import com.example.soliditycompiler.exception.CompilationFailedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SolidityCompilerService {

    private static final String DOCKER_IMAGE = "ghcr.io/argotorg/solc:stable";
    private static final String CONTAINER_WORKDIR = "/workspace";
    private static final Pattern DECLARED_CONTRACT_PATTERN = Pattern.compile(
            "\\b(?:abstract\\s+contract|contract|library)\\s+([A-Za-z_][A-Za-z0-9_]*)"
    );

    private final Path projectRoot;
    private final Path soliditySourceDirectory;
    private final Path abiOutputDirectory;
    private final Path binOutputDirectory;
    private final Path rawOutputDirectory;
    private final SolcLocator solcLocator;
    private final CommandExecutor commandExecutor;
    private final Duration timeout;

    public SolidityCompilerService(
            Path projectRoot,
            SolcLocator solcLocator,
            CommandExecutor commandExecutor,
            Duration timeout
    ) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.soliditySourceDirectory = this.projectRoot.resolve("src/main/solidity");
        this.abiOutputDirectory = this.projectRoot.resolve("build/generated/contracts/abi");
        this.binOutputDirectory = this.projectRoot.resolve("build/generated/contracts/bin");
        this.rawOutputDirectory = this.projectRoot.resolve("build/generated/contracts/raw");
        this.solcLocator = solcLocator;
        this.commandExecutor = commandExecutor;
        this.timeout = timeout;
    }

    public List<CompilationArtifact> compileAll() {
        List<Path> soliditySources = findSoliditySources();
        if (soliditySources.isEmpty()) {
            return List.of();
        }

        SolcExecutionStrategy strategy = solcLocator.locate();

        recreateDirectory(abiOutputDirectory);
        recreateDirectory(binOutputDirectory);
        recreateDirectory(rawOutputDirectory);
        List<CompilationArtifact> artifacts = new ArrayList<>();

        for (Path sourcePath : soliditySources) {
            Path sourceRawOutputDirectory = buildRawOutputDirectory(sourcePath);
            recreateDirectory(sourceRawOutputDirectory);

            List<String> command = strategy.mode() == CompilerMode.LOCAL_SOLC
                    ? buildLocalSolcCommand(sourcePath, sourceRawOutputDirectory)
                    : buildDockerSolcCommand(sourcePath, sourceRawOutputDirectory);

            ProcessExecutionResult result = commandExecutor.execute(
                    new ProcessExecutionRequest(command, projectRoot, timeout)
            );
            if (result.exitCode() != 0) {
                throw new CompilationFailedException(buildFailureMessage(sourcePath, strategy, result));
            }

            artifacts.addAll(collectArtifacts(sourcePath, sourceRawOutputDirectory, strategy.mode(), result));
        }

        return List.copyOf(artifacts);
    }

    List<Path> findSoliditySources() {
        if (!Files.exists(soliditySourceDirectory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(soliditySourceDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sol"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new CompilationFailedException(
                    "Failed to discover Solidity sources under " + soliditySourceDirectory,
                    exception
            );
        }
    }

    List<String> buildLocalSolcCommand(Path sourcePath, Path outputDirectory) {
        return List.of(
                "solc",
                toProjectRelativeString(sourcePath),
                "--bin",
                "--abi",
                "--optimize",
                "--base-path",
                ".",
                "--include-path",
                toProjectRelativeString(soliditySourceDirectory),
                "-o",
                toProjectRelativeString(outputDirectory)
        );
    }

    List<String> buildDockerSolcCommand(Path sourcePath, Path outputDirectory) {
        List<String> command = new ArrayList<>(List.of("docker", "run", "--rm"));
        String userMapping = resolveDockerUserMapping();
        if (userMapping != null) {
            command.add("--user");
            command.add(userMapping);
        }
        command.add("-v");
        command.add(projectRoot + ":" + CONTAINER_WORKDIR);
        command.add("-w");
        command.add(CONTAINER_WORKDIR);
        command.add(DOCKER_IMAGE);
        command.add(toContainerPath(sourcePath));
        command.add("--bin");
        command.add("--abi");
        command.add("--optimize");
        command.add("--base-path");
        command.add(CONTAINER_WORKDIR);
        command.add("--include-path");
        command.add(toContainerPath(soliditySourceDirectory));
        command.add("-o");
        command.add(toContainerPath(outputDirectory));
        return List.copyOf(command);
    }

    public Path getAbiOutputDirectory() {
        return abiOutputDirectory;
    }

    public Path getBinOutputDirectory() {
        return binOutputDirectory;
    }

    private List<CompilationArtifact> collectArtifacts(
            Path sourcePath,
            Path sourceRawOutputDirectory,
            CompilerMode compilerMode,
            ProcessExecutionResult result
    ) {
        Map<String, Path> abiFiles = mapArtifactsByContractName(sourceRawOutputDirectory, ".abi");
        Map<String, Path> binFiles = mapArtifactsByContractName(sourceRawOutputDirectory, ".bin");
        Set<String> declaredContracts = findDeclaredContracts(sourcePath);

        List<String> contractNames = abiFiles.keySet().stream()
                .filter(name -> declaredContracts.isEmpty() || declaredContracts.contains(name))
                .sorted()
                .toList();

        if (contractNames.isEmpty()) {
            throw new CompilationFailedException(
                    "Compilation succeeded but produced no ABI files for " + sourcePath
            );
        }

        List<CompilationArtifact> artifacts = new ArrayList<>();
        for (String contractName : contractNames) {
            Path abiPath = abiFiles.get(contractName);
            Path binPath = binFiles.get(contractName);
            if (binPath == null) {
                throw new CompilationFailedException(
                        "Compilation produced ABI without BIN for contract `" + contractName + "` from " + sourcePath
                );
            }

            Path finalAbiPath = abiOutputDirectory.resolve(contractName + ".abi");
            Path finalBinPath = binOutputDirectory.resolve(contractName + ".bin");
            if (Files.exists(finalAbiPath) || Files.exists(finalBinPath)) {
                throw new CompilationFailedException(
                        "Duplicate contract output detected for `" + contractName + "`. Contract names must be unique."
                );
            }

            moveArtifact(abiPath, finalAbiPath);
            moveArtifact(binPath, finalBinPath);
            artifacts.add(
                    CompilationArtifact.success(
                            contractName,
                            sourcePath,
                            finalAbiPath,
                            finalBinPath,
                            compilerMode,
                            result.exitCode(),
                            result.stdout(),
                            result.stderr()
                    )
            );
        }
        return artifacts;
    }

    private Map<String, Path> mapArtifactsByContractName(Path directory, String extension) {
        try (Stream<Path> paths = Files.walk(directory)) {
            Map<String, Path> artifacts = new LinkedHashMap<>();
            for (Path artifactPath : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(extension))
                    .sorted(Comparator.naturalOrder())
                    .toList()) {
                String contractName = stripExtension(artifactPath.getFileName().toString());
                if (artifacts.putIfAbsent(contractName, artifactPath) != null) {
                    throw new CompilationFailedException(
                            "Duplicate " + extension + " output detected for contract `" + contractName + "`"
                    );
                }
            }
            return artifacts;
        } catch (IOException exception) {
            throw new CompilationFailedException("Failed to inspect compilation output under " + directory, exception);
        }
    }

    private Set<String> findDeclaredContracts(Path sourcePath) {
        try {
            String source = Files.readString(sourcePath);
            Matcher matcher = DECLARED_CONTRACT_PATTERN.matcher(source);
            Set<String> contractNames = new LinkedHashSet<>();
            while (matcher.find()) {
                contractNames.add(matcher.group(1));
            }
            return contractNames;
        } catch (IOException exception) {
            throw new CompilationFailedException("Failed to read Solidity source " + sourcePath, exception);
        }
    }

    private String buildFailureMessage(
            Path sourcePath,
            SolcExecutionStrategy strategy,
            ProcessExecutionResult result
    ) {
        return """
                Solidity compilation failed for %s using %s.
                Working directory: %s
                Command: %s
                Exit code: %d
                Stdout:
                %s
                Stderr:
                %s
                """.formatted(
                sourcePath,
                strategy.description(),
                result.workingDirectory(),
                result.commandLine(),
                result.exitCode(),
                result.stdout().isBlank() ? "<empty>" : result.stdout(),
                result.stderr().isBlank() ? "<empty>" : result.stderr()
        );
    }

    private Path buildRawOutputDirectory(Path sourcePath) {
        Path relativeSource = soliditySourceDirectory.relativize(sourcePath);
        Path relativeParent = relativeSource.getParent();
        String stem = stripExtension(relativeSource.getFileName().toString());
        if (relativeParent == null) {
            return rawOutputDirectory.resolve(stem);
        }
        return rawOutputDirectory.resolve(relativeParent).resolve(stem);
    }

    private void recreateDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                deleteRecursively(directory);
            }
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new CompilationFailedException("Failed to prepare directory " + directory, exception);
        }
    }

    private void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void moveArtifact(Path sourcePath, Path targetPath) {
        try {
            Files.createDirectories(targetPath.getParent());
            Files.move(sourcePath, targetPath);
        } catch (IOException exception) {
            throw new CompilationFailedException(
                    "Failed to move artifact from " + sourcePath + " to " + targetPath,
                    exception
            );
        }
    }

    private String toProjectRelativeString(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(projectRoot)) {
            return projectRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString().replace('\\', '/');
    }

    private String resolveDockerUserMapping() {
        try {
            Object uid = Files.getAttribute(projectRoot, "unix:uid");
            Object gid = Files.getAttribute(projectRoot, "unix:gid");
            return uid + ":" + gid;
        } catch (UnsupportedOperationException | IOException exception) {
            return null;
        }
    }

    private String toContainerPath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(projectRoot)) {
            throw new IllegalArgumentException("Path is outside the project root: " + path);
        }
        String relativePath = projectRoot.relativize(normalized).toString().replace('\\', '/');
        if (relativePath.isEmpty()) {
            return CONTAINER_WORKDIR;
        }
        return CONTAINER_WORKDIR + "/" + relativePath;
    }

    private String stripExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot >= 0 ? fileName.substring(0, lastDot) : fileName;
    }
}
