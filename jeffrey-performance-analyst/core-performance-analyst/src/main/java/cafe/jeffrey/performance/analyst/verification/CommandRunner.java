/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.performance.analyst.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs a build or VCS command inside a checkout and captures what it said. Verification is the only
 * part of the analyst that executes anything, and it executes only commands the operator configured —
 * never anything the model produced.
 */
public class CommandRunner {

    private static final Logger LOG = LoggerFactory.getLogger(CommandRunner.class);

    private static final int TIMEOUT_EXIT_CODE = -1;
    private static final int LAUNCH_FAILURE_EXIT_CODE = -2;

    /**
     * @param exitCode the process exit code, or a negative sentinel when it timed out or never started
     * @param output   stdout and stderr combined, as the operator would see them
     */
    public record CommandResult(int exitCode, String output) {

        public boolean succeeded() {
            return exitCode == 0;
        }
    }

    public CommandResult run(List<String> command, Path workingDirectory, Duration timeout) {
        LOG.debug("Running verification command: command={} dir={}", command, workingDirectory);

        Process process;
        try {
            process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            LOG.debug("Verification command could not start: command={} message={}", command, e.getMessage());
            return new CommandResult(LAUNCH_FAILURE_EXIT_CODE, e.getMessage());
        }

        try {
            String output = readAll(process.getInputStream());
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                LOG.warn("Verification command timed out: command={} timeout_in_sec={}", command, timeout.toSeconds());
                return new CommandResult(TIMEOUT_EXIT_CODE, "Timed out after " + timeout.toSeconds() + " seconds");
            }
            return new CommandResult(process.exitValue(), output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new CommandResult(TIMEOUT_EXIT_CODE, "Interrupted");
        }
    }

    /**
     * Reads the merged stream to completion before waiting on the process, so a chatty build cannot
     * fill the pipe buffer and deadlock against its own exit.
     */
    private static String readAll(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
