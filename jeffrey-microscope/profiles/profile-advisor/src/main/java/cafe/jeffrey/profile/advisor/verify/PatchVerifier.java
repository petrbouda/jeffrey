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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.advisor.verify.CommandRunner.CommandResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Climbs the {@link PatchCheckLevel} ladder against the checkout the model just read, while that
 * checkout is still on disk and still at the revision the recommendation was written for. This is the
 * only moment the patch can be checked cheaply and honestly; once the clone is deleted, every claim
 * about the patch becomes a claim about a repository nobody has.
 *
 * <p>Each level runs only if the one below it passed, and a level that cannot run is reported as
 * skipped with the reason. Nothing here ever executes model-authored commands — the patch is data fed
 * to {@code git apply}, and the build commands come from operator configuration.</p>
 */
public class PatchVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(PatchVerifier.class);

    private static final String PATCH_FILE_NAME = "recommendation.patch";
    private static final String ARG_APPLY = "apply";
    private static final String ARG_CHECK = "--check";
    private static final String ARG_STRIP = "-p1";

    private static final String SKIP_NO_PATCH = "The model proposed no code change";
    private static final String SKIP_APPLY_FAILED = "Skipped because the patch does not apply";
    private static final String SKIP_NO_COMPILE_COMMAND = "No compile command configured";
    private static final String SKIP_NO_TEST_COMMAND = "No test command configured";
    private static final String SKIP_COMPILE_FAILED = "Skipped because the patched checkout does not compile";

    private final CommandRunner commandRunner;
    private final PatchVerificationSettings settings;

    public PatchVerifier(CommandRunner commandRunner, PatchVerificationSettings settings) {
        this.commandRunner = commandRunner;
        this.settings = settings;
    }

    /**
     * Verifies {@code patch} against {@code repositoryRoot}. Returns the ladder of results; a null or
     * blank patch yields a single skipped entry rather than an empty verification, so the UI can tell
     * "nothing to check" apart from "not checked".
     */
    public PatchVerification verify(String patch, Path repositoryRoot, String verifiedAgainstRef) {
        if (patch == null || patch.isBlank()) {
            return new PatchVerification(
                    List.of(PatchCheck.skipped(PatchCheckLevel.APPLIES, SKIP_NO_PATCH)), verifiedAgainstRef);
        }

        Path patchFile = writePatchFile(patch, repositoryRoot);
        List<PatchCheck> checks = new ArrayList<>();
        try {
            PatchCheck applies = checkApplies(patchFile, repositoryRoot);
            checks.add(applies);

            if (!applies.isPassed()) {
                checks.add(PatchCheck.skipped(PatchCheckLevel.COMPILES, SKIP_APPLY_FAILED));
                checks.add(PatchCheck.skipped(PatchCheckLevel.TESTS_PASS, SKIP_APPLY_FAILED));
                return new PatchVerification(checks, verifiedAgainstRef);
            }

            applyForReal(patchFile, repositoryRoot);

            PatchCheck compiles = runBuildLevel(
                    PatchCheckLevel.COMPILES, settings.compileCommand(), repositoryRoot,
                    settings.hasCompileCommand(), SKIP_NO_COMPILE_COMMAND);
            checks.add(compiles);

            if (!compiles.isPassed()) {
                String reason = settings.hasCompileCommand() ? SKIP_COMPILE_FAILED : SKIP_NO_COMPILE_COMMAND;
                checks.add(PatchCheck.skipped(PatchCheckLevel.TESTS_PASS, reason));
                return new PatchVerification(checks, verifiedAgainstRef);
            }

            checks.add(runBuildLevel(
                    PatchCheckLevel.TESTS_PASS, settings.testCommand(), repositoryRoot,
                    settings.hasTestCommand(), SKIP_NO_TEST_COMMAND));
            return new PatchVerification(checks, verifiedAgainstRef);
        } finally {
            deleteQuietly(patchFile);
        }
    }

    private PatchCheck checkApplies(Path patchFile, Path repositoryRoot) {
        CommandResult result = commandRunner.run(
                List.of(settings.gitPath(), ARG_APPLY, ARG_CHECK, ARG_STRIP, patchFile.toString()),
                repositoryRoot,
                settings.commandTimeout());

        if (result.succeeded()) {
            LOG.info("Patch verification passed: level={}", PatchCheckLevel.APPLIES);
            return PatchCheck.passed(PatchCheckLevel.APPLIES);
        }
        LOG.info("Patch verification failed: level={} exit_code={}", PatchCheckLevel.APPLIES, result.exitCode());
        return PatchCheck.failed(PatchCheckLevel.APPLIES, result.output());
    }

    /**
     * Applies the patch to the checkout so the compile and test levels see the proposed code. The
     * checkout is a throwaway clone that is deleted moments later, so mutating it costs nothing and
     * avoids paying for a second copy of the repository.
     */
    private void applyForReal(Path patchFile, Path repositoryRoot) {
        commandRunner.run(
                List.of(settings.gitPath(), ARG_APPLY, ARG_STRIP, patchFile.toString()),
                repositoryRoot,
                settings.commandTimeout());
    }

    private PatchCheck runBuildLevel(
            PatchCheckLevel level,
            List<String> command,
            Path repositoryRoot,
            boolean configured,
            String skipReason) {

        if (!configured) {
            return PatchCheck.skipped(level, skipReason);
        }

        CommandResult result = commandRunner.run(command, repositoryRoot, settings.commandTimeout());
        if (result.succeeded()) {
            LOG.info("Patch verification passed: level={}", level);
            return PatchCheck.passed(level);
        }
        LOG.info("Patch verification failed: level={} exit_code={}", level, result.exitCode());
        return PatchCheck.failed(level, tail(result.output()));
    }

    private static Path writePatchFile(String patch, Path repositoryRoot) {
        Path patchFile = repositoryRoot.resolve(PATCH_FILE_NAME);
        try {
            Files.writeString(patchFile, patch, StandardCharsets.UTF_8);
            return patchFile;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to stage the patch for verification", e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.debug("Could not delete the staged patch file: path={} message={}", path, e.getMessage());
        }
    }

    /**
     * Build failures put the useful part — the first error — at the end of a long log, so the tail is
     * what gets stored.
     */
    private static String tail(String output) {
        if (output == null) {
            return "";
        }
        String[] lines = output.split("\n");
        int from = Math.max(0, lines.length - 40);
        return String.join("\n", List.of(lines).subList(from, lines.length));
    }
}
