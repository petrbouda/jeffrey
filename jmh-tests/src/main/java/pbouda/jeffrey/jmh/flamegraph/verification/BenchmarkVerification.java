/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.jmh.flamegraph.verification;

import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.jmh.flamegraph.utils.FrameJsonSerializer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Verifies Frame output against a baseline stored in the repository.
 * Baselines are located at: jmh-tests/data/baseline/{benchmarkName}/baseline-frame.sha256
 */
public class BenchmarkVerification {

    private static final Path BASELINE_ROOT = Path.of("jmh-tests/data/baseline");
    private static final Path OUTPUT_ROOT = Path.of("jmh-tests/target/verification-output");

    private final String benchmarkName;
    private final String baselineHash;

    public BenchmarkVerification(String benchmarkName) {
        this.benchmarkName = benchmarkName;
        this.baselineHash = loadBaselineHash(benchmarkName);
    }

    private static String loadBaselineHash(String benchmarkName) {
        Path baselineHashFile = BASELINE_ROOT.resolve(benchmarkName).resolve("baseline-frame.sha256");
        try {
            if (!Files.exists(baselineHashFile)) {
                throw new IllegalStateException(
                        "Baseline not found at: " + baselineHashFile.toAbsolutePath());
            }
            return Files.readString(baselineHashFile).trim();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load baseline for " + benchmarkName, e);
        }
    }

    /**
     * Verifies that the given Frame matches the baseline using HASH_ONLY mode.
     */
    public void verify(Frame frame) {
        verify(frame, VerificationMode.HASH_ONLY);
    }

    /**
     * Verifies that the given Frame matches the baseline.
     *
     * @param frame the frame to verify
     * @param mode  the verification mode (HASH_ONLY or FULL_OUTPUT)
     */
    public void verify(Frame frame, VerificationMode mode) {
        if (frame == null) {
            return;
        }

        byte[] currentJson = FrameJsonSerializer.toJsonBytes(frame);
        String currentHash = sha256(currentJson);

        if (!baselineHash.equals(currentHash)) {
            if (mode == VerificationMode.FULL_OUTPUT) {
                saveCurrentOutput(currentJson, currentHash);
            }

            FrameJsonSerializer.FrameSummary summary = FrameJsonSerializer.summarize(frame);
            String message = "VERIFICATION FAILED for " + benchmarkName + "! Frame output differs from baseline.\n" +
                    "Baseline: " + baselineHash + "\n" +
                    "Current:  " + currentHash + "\n" +
                    "Summary:  " + summary;

            if (mode == VerificationMode.FULL_OUTPUT) {
                message += "\nOutput saved to: " + OUTPUT_ROOT.resolve(benchmarkName);
            }

            throw new IllegalStateException(message);
        }
    }

    private void saveCurrentOutput(byte[] currentJson, String currentHash) {
        try {
            Path outputDir = OUTPUT_ROOT.resolve(benchmarkName);
            Files.createDirectories(outputDir);
            Files.write(outputDir.resolve("current-frame.json"), currentJson);
            Files.writeString(outputDir.resolve("current-frame.sha256"), currentHash);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save verification output", e);
        }
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
