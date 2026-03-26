package com.example.soliditycompiler;

import java.util.Objects;

public record SolcExecutionStrategy(
        CompilerMode mode,
        String description
) {
    public SolcExecutionStrategy {
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(description, "description must not be null");
    }

    public static SolcExecutionStrategy local() {
        return new SolcExecutionStrategy(CompilerMode.LOCAL_SOLC, "local solc on PATH");
    }

    public static SolcExecutionStrategy docker() {
        return new SolcExecutionStrategy(
                CompilerMode.DOCKER_SOLC,
                "Docker image ghcr.io/argotorg/solc:stable"
        );
    }
}

