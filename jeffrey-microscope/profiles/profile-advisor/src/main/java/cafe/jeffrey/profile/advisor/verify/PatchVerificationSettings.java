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

package cafe.jeffrey.profile.advisor.verify;

import java.time.Duration;
import java.util.List;

/**
 * How far the operator wants patch verification to go, and with what commands.
 *
 * <p>The apply check needs only a {@code git} binary, so it is on by default. Compiling and testing an
 * arbitrary repository means running that repository's build, which is a decision only the operator can
 * make — so both stay off until a command is configured, and report {@link PatchCheckStatus#SKIPPED}
 * rather than quietly guessing at {@code mvn} or {@code gradle}.</p>
 *
 * @param gitPath        the git executable used for the apply check
 * @param compileCommand the command that compiles the checkout, or empty to skip the level
 * @param testCommand    the command that runs the checkout's tests, or empty to skip the level
 * @param commandTimeout the per-command ceiling; a build that hangs must not hold a recommendation open
 */
public record PatchVerificationSettings(
        String gitPath,
        List<String> compileCommand,
        List<String> testCommand,
        Duration commandTimeout) {

    private static final String DEFAULT_GIT_PATH = "git";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

    public PatchVerificationSettings {
        gitPath = (gitPath == null || gitPath.isBlank()) ? DEFAULT_GIT_PATH : gitPath;
        compileCommand = compileCommand == null ? List.of() : List.copyOf(compileCommand);
        testCommand = testCommand == null ? List.of() : List.copyOf(testCommand);
        commandTimeout = commandTimeout == null ? DEFAULT_TIMEOUT : commandTimeout;
    }

    public static PatchVerificationSettings applyOnly() {
        return new PatchVerificationSettings(DEFAULT_GIT_PATH, List.of(), List.of(), DEFAULT_TIMEOUT);
    }

    public boolean hasCompileCommand() {
        return !compileCommand.isEmpty();
    }

    public boolean hasTestCommand() {
        return !testCommand.isEmpty();
    }
}
