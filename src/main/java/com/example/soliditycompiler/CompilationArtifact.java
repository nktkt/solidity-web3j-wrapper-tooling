package com.example.soliditycompiler;

import java.nio.file.Path;
import java.util.Objects;

public record CompilationArtifact(
        String contractName,
        Path sourcePath,
        Path abiPath,
        Path binPath,
        CompilerMode compilerMode,
        boolean successful,
        int exitCode,
        String stdout,
        String stderr
) {
    public CompilationArtifact {
        Objects.requireNonNull(contractName, "contractName must not be null");
        Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        Objects.requireNonNull(compilerMode, "compilerMode must not be null");
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
    }

    public static CompilationArtifact success(
            String contractName,
            Path sourcePath,
            Path abiPath,
            Path binPath,
            CompilerMode compilerMode,
            int exitCode,
            String stdout,
            String stderr
    ) {
        Objects.requireNonNull(abiPath, "abiPath must not be null for a successful artifact");
        Objects.requireNonNull(binPath, "binPath must not be null for a successful artifact");
        return new CompilationArtifact(
                contractName,
                sourcePath,
                abiPath,
                binPath,
                compilerMode,
                true,
                exitCode,
                stdout,
                stderr
        );
    }

    public static CompilationArtifact failure(
            String contractName,
            Path sourcePath,
            CompilerMode compilerMode,
            int exitCode,
            String stdout,
            String stderr
    ) {
        return new CompilationArtifact(
                contractName,
                sourcePath,
                null,
                null,
                compilerMode,
                false,
                exitCode,
                stdout,
                stderr
        );
    }
}

