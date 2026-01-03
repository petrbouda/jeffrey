/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pbouda.jeffrey.tools.impl.jdk;

import pbouda.jeffrey.tools.api.JfrTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class JdkJfrTool implements JfrTool {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    private final boolean configEnabled;
    private final Path configPath;
    private final Duration timeout;

    private Path resolvedPath = null;
    private boolean resolvedEnabled = false;

    public JdkJfrTool() {
        this(true, null, DEFAULT_TIMEOUT);
    }

    public JdkJfrTool(boolean configEnabled, Path configPath) {
        this(configEnabled, configPath, DEFAULT_TIMEOUT);
    }

    public JdkJfrTool(boolean configEnabled, Path configPath, Duration timeout) {
        this.configEnabled = configEnabled;
        this.configPath = configPath;
        this.timeout = timeout;
    }

    @Override
    public void disassemble(Path jfrPath, Path outputDir) {
        ProcessBuilder command = new ProcessBuilder()
                .command(resolvedPath.toString(), "disassemble", "--max-chunks", "1",
                        "--output", outputDir.toString(), jfrPath.toString());

        startAndWait(command);
    }

    @Override
    public void scrub(ScrubOperation scrubOperation, Path jfrPath, Path output) {
        throw new UnsupportedOperationException("Not implemented yet: Scrubbing");
    }

    @Override
    public Summary summary(Path jfrPath) {
        ProcessBuilder command = new ProcessBuilder()
                .command(resolvedPath.toString(), "summary", jfrPath.toString());

        Process process = startAndWait(command);
        try {
            String result = new String(process.getInputStream().readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        throw new UnsupportedOperationException("Not implemented yet: Parsing");
    }

    private Process startAndWait(ProcessBuilder processBuilder) {
        try {
            Process process = processBuilder.start();
            boolean result = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!result) {
                throw new RuntimeException("Timeout while disassembling JFR recording");
            }
            if (process.exitValue() != 0) {
                String output = readProcessErrorOutput(process);
                throw new RuntimeException("Cannot disassemble JFR recording: " + processBuilder.command() + " - " + output);
            }
            return process;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException("Cannot execute a sub-process: " + processBuilder.command(), e);
        }
    }

    private static String readProcessErrorOutput(Process process) {
        try {
            return new String(process.getErrorStream().readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initialize() {
        if (!configEnabled) {
            return;
        }

        Optional<Path> javaHome = fromConfiguration(configPath)
                .or(JdkJfrTool::currentCommand)
                .or(JdkJfrTool::javaHomeEnv)
                .or(JdkJfrTool::javaHomeProperty)
                .or(JdkJfrTool::directJfrCommand);

        if (javaHome.isPresent()) {
            resolvedPath = javaHome.get();
            resolvedEnabled = true;
        }
    }

    @Override
    public boolean enabled() {
        return resolvedEnabled;
    }

    @Override
    public Path path() {
        return resolvedPath;
    }

    private static Optional<Path> fromConfiguration(Path configPath) {
        return Optional.ofNullable(configPath)
                .filter(JdkJfrTool::isExecutableFile)
                .filter(JdkJfrTool::isCorrectJfrCommand);
    }

    private static Optional<Path> currentCommand() {
        return ProcessHandle.current()
                .info()
                .command()
                .map(Path::of)
                .map(JdkJfrTool::removeBinJava)
                .map(JdkJfrTool::addJfrPathSuffix)
                .filter(JdkJfrTool::isExecutableFile)
                .filter(JdkJfrTool::isCorrectJfrCommand);
    }

    private static Optional<Path> javaHomeProperty() {
        return checkJavaHome(System.getProperty("java.home"));
    }

    private static Optional<Path> javaHomeEnv() {
        return checkJavaHome(System.getenv("JAVA_HOME"));
    }

    private static Optional<Path> directJfrCommand() {
        return Optional.of(Path.of("jfr"))
                .filter(JdkJfrTool::isCorrectJfrCommand);
    }

    private static Optional<Path> checkJavaHome(String javaHome) {
        return Optional.ofNullable(javaHome)
                .map(Path::of)
                .map(JdkJfrTool::addJfrPathSuffix)
                .filter(JdkJfrTool::isExecutableFile)
                .filter(JdkJfrTool::isCorrectJfrCommand);
    }

    private static boolean isExecutableFile(Path path) {
        return Files.exists(path) && Files.isExecutable(path);
    }

    private static boolean isCorrectJfrCommand(Path path) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(path.toString(), "--version");
            Process process = processBuilder.start();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static Path addJfrPathSuffix(Path homePath) {
        if (System.getProperty("os.name").startsWith("Win")) {
            return homePath.resolve("bin").resolve("jfr.exe");
        } else {
            return homePath.resolve("bin").resolve("jfr");
        }
    }

    private static Path removeBinJava(Path path) {
        return path.getParent().getParent();
    }
}
