package com.example.soliditycompiler;

import com.example.soliditycompiler.exception.CompilationFailedException;
import com.example.soliditycompiler.exception.CompilerNotFoundException;
import com.example.soliditycompiler.exception.ExternalToolExecutionException;
import com.example.soliditycompiler.exception.WrapperGenerationException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

public final class Main {

    private static final Duration TOOL_DISCOVERY_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(2);

    public static void main(String[] args) {
        int exitCode = new Main().run(args, Paths.get("").toAbsolutePath().normalize());
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    int run(String[] args, Path projectRoot) {
        if (args.length != 1) {
            printUsage();
            return 1;
        }

        CommandExecutor commandExecutor = new ProcessExecutor();
        SolcLocator solcLocator = new SolcLocator(commandExecutor, TOOL_DISCOVERY_TIMEOUT);
        SolidityCompilerService compilerService =
                new SolidityCompilerService(projectRoot, solcLocator, commandExecutor, COMMAND_TIMEOUT);
        WrapperGenerationService wrapperService =
                new WrapperGenerationService(projectRoot, commandExecutor, COMMAND_TIMEOUT);

        try {
            return switch (args[0]) {
                case "compile" -> runCompile(compilerService);
                case "generate-wrappers" -> runGenerateWrappers(wrapperService);
                case "build-all" -> runBuildAll(compilerService, wrapperService);
                default -> {
                    printUsage();
                    yield 1;
                }
            };
        } catch (CompilerNotFoundException
                 | CompilationFailedException
                 | WrapperGenerationException
                 | ExternalToolExecutionException exception) {
            System.err.println("ERROR: " + exception.getMessage());
            return 1;
        } catch (RuntimeException exception) {
            System.err.println("ERROR: Unexpected failure: " + exception.getMessage());
            return 1;
        }
    }

    private int runCompile(SolidityCompilerService compilerService) {
        List<CompilationArtifact> artifacts = compilerService.compileAll();
        if (artifacts.isEmpty()) {
            System.out.println("No Solidity sources were found under src/main/solidity.");
            return 0;
        }

        System.out.println("Solidity compilation completed successfully.");
        System.out.println("ABI output: " + compilerService.getAbiOutputDirectory());
        System.out.println("BIN output: " + compilerService.getBinOutputDirectory());
        for (CompilationArtifact artifact : artifacts) {
            System.out.printf(
                    " - %s (%s)%n",
                    artifact.contractName(),
                    artifact.compilerMode()
            );
        }
        return 0;
    }

    private int runGenerateWrappers(WrapperGenerationService wrapperService) {
        List<Path> wrappers = wrapperService.generateWrappers();
        System.out.println("Web3j wrapper generation completed successfully.");
        System.out.println("Wrapper output: " + wrapperService.getJavaOutputDirectory());
        for (Path wrapper : wrappers) {
            System.out.println(" - " + wrapper);
        }
        return 0;
    }

    private int runBuildAll(
            SolidityCompilerService compilerService,
            WrapperGenerationService wrapperService
    ) {
        List<CompilationArtifact> artifacts = compilerService.compileAll();
        List<Path> wrappers = wrapperService.generateWrappers();

        System.out.println("Build-all preparation completed successfully.");
        System.out.println("ABI output: " + compilerService.getAbiOutputDirectory());
        System.out.println("BIN output: " + compilerService.getBinOutputDirectory());
        System.out.println("Wrapper output: " + wrapperService.getJavaOutputDirectory());
        System.out.println("Compiled contracts: " + artifacts.size());
        System.out.println("Generated wrappers: " + wrappers.size());
        return 0;
    }

    private void printUsage() {
        System.out.println("Usage: Main <compile|generate-wrappers|build-all>");
    }
}

