package com.example.soliditycompiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.soliditycompiler.TestSupport.ScriptedCommandExecutor;
import com.example.soliditycompiler.exception.ExternalToolExecutionException;
import com.example.soliditycompiler.exception.WrapperGenerationException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WrapperGenerationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void throwsWhenAbiFilesAreMissing() {
        WrapperGenerationService service = new WrapperGenerationService(
                tempDir,
                new ScriptedCommandExecutor(),
                Duration.ofSeconds(30)
        );

        WrapperGenerationException exception = assertThrows(WrapperGenerationException.class, service::generateWrappers);

        assertTrue(exception.getMessage().contains("No ABI files"));
    }

    @Test
    void throwsWhenMatchingBinFileIsMissing() {
        TestSupport.createFile(tempDir.resolve("build/generated/contracts/abi/HelloWorld.abi"), "[]");

        WrapperGenerationService service = new WrapperGenerationService(
                tempDir,
                new ScriptedCommandExecutor(),
                Duration.ofSeconds(30)
        );

        WrapperGenerationException exception = assertThrows(WrapperGenerationException.class, service::generateWrappers);

        assertTrue(exception.getMessage().contains("Missing BIN"));
    }

    @Test
    void generatesWrapperSourcesWhenAbiAndBinExist() {
        TestSupport.createFile(tempDir.resolve("build/generated/contracts/abi/HelloWorld.abi"), "[]");
        TestSupport.createFile(tempDir.resolve("build/generated/contracts/bin/HelloWorld.bin"), "6000");

        ScriptedCommandExecutor commandExecutor = new ScriptedCommandExecutor();
        commandExecutor.enqueueResult(0, "Version: 1.0.0", "");
        commandExecutor.enqueueHandler(request -> {
            Path generatedFile = tempDir.resolve(
                    "build/generated/source/web3j/main/java/com/example/soliditycompiler/generated/HelloWorld.java"
            );
            TestSupport.createFile(
                    generatedFile,
                    "package com.example.soliditycompiler.generated; public class HelloWorld {}"
            );
            return new ProcessExecutionResult(0, "generated", "", request.command(), request.workingDirectory());
        });

        WrapperGenerationService service = new WrapperGenerationService(
                tempDir,
                commandExecutor,
                Duration.ofSeconds(30)
        );

        List<Path> generatedFiles = service.generateWrappers();

        assertEquals(1, generatedFiles.size());
        assertEquals(List.of("web3j", "-V"), commandExecutor.requests().get(0).command());
        assertEquals(
                List.of(
                        "web3j",
                        "generate",
                        "solidity",
                        "-b",
                        "build/generated/contracts/bin/HelloWorld.bin",
                        "-a",
                        "build/generated/contracts/abi/HelloWorld.abi",
                        "-o",
                        "build/generated/source/web3j/main/java",
                        "-p",
                        "com.example.soliditycompiler.generated"
                ),
                commandExecutor.requests().get(1).command()
        );
    }

    @Test
    void propagatesTimeoutFailures() {
        TestSupport.createFile(tempDir.resolve("build/generated/contracts/abi/HelloWorld.abi"), "[]");
        TestSupport.createFile(tempDir.resolve("build/generated/contracts/bin/HelloWorld.bin"), "6000");

        ScriptedCommandExecutor commandExecutor = new ScriptedCommandExecutor();
        commandExecutor.enqueueException(
                ExternalToolExecutionException.timeout(
                        List.of("web3j", "-V"),
                        tempDir,
                        Duration.ofSeconds(1),
                        "",
                        ""
                )
        );

        WrapperGenerationService service = new WrapperGenerationService(
                tempDir,
                commandExecutor,
                Duration.ofSeconds(30)
        );

        RuntimeException exception = assertThrows(RuntimeException.class, service::generateWrappers);

        if (exception instanceof WrapperGenerationException wrapper) {
            assertInstanceOf(ExternalToolExecutionException.class, wrapper.getCause());
            return;
        }
        assertInstanceOf(ExternalToolExecutionException.class, exception);
    }

    @Test
    void preservesExistingWrappersWhenWeb3jIsUnavailable() {
        TestSupport.createFile(tempDir.resolve("build/generated/contracts/abi/HelloWorld.abi"), "[]");
        TestSupport.createFile(tempDir.resolve("build/generated/contracts/bin/HelloWorld.bin"), "6000");
        Path existingWrapper = TestSupport.createFile(
                tempDir.resolve("build/generated/source/web3j/main/java/com/example/soliditycompiler/generated/Old.java"),
                "package com.example.soliditycompiler.generated; public class Old {}"
        );

        ScriptedCommandExecutor commandExecutor = new ScriptedCommandExecutor();
        commandExecutor.enqueueResult(1, "", "web3j not found");

        WrapperGenerationService service = new WrapperGenerationService(
                tempDir,
                commandExecutor,
                Duration.ofSeconds(30)
        );

        assertThrows(WrapperGenerationException.class, service::generateWrappers);
        assertTrue(Files.exists(existingWrapper));
    }
}
