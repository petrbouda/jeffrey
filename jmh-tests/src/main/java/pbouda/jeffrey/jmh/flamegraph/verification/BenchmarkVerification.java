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

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.jmh.flamegraph.utils.FrameJsonSerializer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Verifies Frame output against a baseline stored in the repository.
 * Uses murmur3 hashing directly on the Frame tree structure for fast verification.
 * Baselines are located at: jmh-tests/data/baseline/{benchmarkName}/baseline-frame.murmur3
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
        Path baselineHashFile = BASELINE_ROOT.resolve(benchmarkName).resolve("baseline-frame.murmur3");
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
     * Verifies that the given Frame matches the baseline.
     *
     * @param frame the frame to verify
     */
    public void verify(Frame frame) {
        if (frame == null) {
            return;
        }

        String currentHash = hashFrame(frame);
        if (!baselineHash.equals(currentHash)) {
            byte[] currentJson = FrameJsonSerializer.toJsonBytes(frame);
            saveCurrentOutput(currentJson, currentHash);

            FrameJsonSerializer.FrameSummary summary = FrameJsonSerializer.summarize(frame);
            String message = "VERIFICATION FAILED for " + benchmarkName + "! Frame output differs from baseline.\n" +
                    "Baseline: " + baselineHash + "\n" +
                    "Current:  " + currentHash + "\n" +
                    "Summary:  " + summary;

            message += "\nOutput saved to: " + OUTPUT_ROOT.resolve(benchmarkName);
            throw new IllegalStateException(message);
        }
    }

    /**
     * Computes a murmur3 hash of the entire Frame tree structure.
     */
    @SuppressWarnings("UnstableApiUsage")
    private static String hashFrame(Frame frame) {
        Hasher hasher = Hashing.murmur3_32_fixed().newHasher();
        hashFrameRecursive(hasher, frame);
        return hasher.hash().toString();
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void hashFrameRecursive(Hasher hasher, Frame frame) {
        // Hash frame fields
        if (frame.methodName() != null) {
            hasher.putString(frame.methodName(), StandardCharsets.UTF_8);
        }
        hasher.putInt(frame.lineNumber());
        hasher.putInt(frame.bci());
        hasher.putLong(frame.totalSamples());
        hasher.putLong(frame.totalWeight());
        hasher.putLong(frame.selfSamples());
        hasher.putLong(frame.selfWeight());
        hasher.putLong(frame.c1Samples());
        hasher.putLong(frame.interpretedSamples());
        hasher.putLong(frame.jitCompiledSamples());
        hasher.putLong(frame.inlinedSamples());

        // Hash children count to detect structural differences
        hasher.putInt(frame.size());

        // Recursively hash children (TreeMap ensures consistent ordering)
        for (Frame child : frame.values()) {
            hashFrameRecursive(hasher, child);
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
}
