package com.example.soliditycompiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

final class TestSupport {

    private TestSupport() {
    }

    static Path createFile(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            return Files.writeString(path, content);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    static final class ScriptedCommandExecutor implements CommandExecutor {
        private final Deque<Function<ProcessExecutionRequest, ProcessExecutionResult>> handlers = new ArrayDeque<>();
        private final List<ProcessExecutionRequest> requests = new ArrayList<>();

        void enqueueResult(int exitCode, String stdout, String stderr) {
            handlers.addLast(request -> new ProcessExecutionResult(
                    exitCode,
                    stdout,
                    stderr,
                    request.command(),
                    request.workingDirectory()
            ));
        }

        void enqueueHandler(Function<ProcessExecutionRequest, ProcessExecutionResult> handler) {
            handlers.addLast(handler);
        }

        void enqueueException(RuntimeException exception) {
            handlers.addLast(request -> {
                throw exception;
            });
        }

        List<ProcessExecutionRequest> requests() {
            return requests;
        }

        @Override
        public ProcessExecutionResult execute(ProcessExecutionRequest request) {
            requests.add(request);
            Function<ProcessExecutionRequest, ProcessExecutionResult> handler = handlers.pollFirst();
            if (handler == null) {
                throw new IllegalStateException("No scripted response left for request: " + request.commandLine());
            }
            return handler.apply(request);
        }
    }
}

