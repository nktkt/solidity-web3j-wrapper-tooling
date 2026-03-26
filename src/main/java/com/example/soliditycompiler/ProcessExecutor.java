package com.example.soliditycompiler;

import com.example.soliditycompiler.exception.ExternalToolExecutionException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ProcessExecutor implements CommandExecutor {

    @Override
    public ProcessExecutionResult execute(ProcessExecutionRequest request) {
        ProcessBuilder processBuilder = new ProcessBuilder(request.command());
        processBuilder.directory(request.workingDirectory().toFile());

        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException exception) {
            throw ExternalToolExecutionException.startFailure(
                    request.command(),
                    request.workingDirectory(),
                    exception
            );
        }

        ExecutorService outputReaders = Executors.newFixedThreadPool(2);
        try {
            Future<String> stdoutFuture = outputReaders.submit(() -> readStream(process.getInputStream()));
            Future<String> stderrFuture = outputReaders.submit(() -> readStream(process.getErrorStream()));

            boolean finished = process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw ExternalToolExecutionException.timeout(
                        request.command(),
                        request.workingDirectory(),
                        request.timeout(),
                        awaitOutput(stdoutFuture),
                        awaitOutput(stderrFuture)
                );
            }

            return new ProcessExecutionResult(
                    process.exitValue(),
                    awaitOutput(stdoutFuture),
                    awaitOutput(stderrFuture),
                    request.command(),
                    request.workingDirectory()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw ExternalToolExecutionException.interrupted(
                    request.command(),
                    request.workingDirectory(),
                    exception
            );
        } finally {
            outputReaders.shutdownNow();
        }
    }

    private String readStream(InputStream stream) {
        try (InputStream inputStream = stream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read process output", exception);
        }
    }

    private String awaitOutput(Future<String> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading process output", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to read process output", cause);
        }
    }
}

