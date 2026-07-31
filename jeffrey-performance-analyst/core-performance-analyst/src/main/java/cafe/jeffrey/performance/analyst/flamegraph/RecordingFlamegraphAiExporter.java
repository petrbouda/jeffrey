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

package cafe.jeffrey.performance.analyst.flamegraph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.flamegraph.ai.AiExportConfig;
import cafe.jeffrey.flamegraph.ai.FlamegraphAiMarkdownBuilder;
import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.jfrparser.jdk.EventProcessor;
import cafe.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import cafe.jeffrey.jfrparser.jdk.ProcessableEvents;
import cafe.jeffrey.profile.parser.JfrEventReader;
import cafe.jeffrey.jfrparser.raw.JfrParser;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.common.model.Type;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Parses recording JFR file(s) in memory — restricted to the two sample event types — folds them into
 * {@link Frame} trees (no DuckDB), and renders one AI flamegraph prompt per present event type, the same
 * markdown the microscope Flamegraph "Copy for AI" export produces. The prompts are printed to STDOUT and
 * returned to the caller (caching is handled by {@link RecordingAiPromptManager}).
 */
public class RecordingFlamegraphAiExporter {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingFlamegraphAiExporter.class);

    private static final Set<Type> SAMPLE_EVENT_TYPES = AiPromptType.allEventTypes();
    private static final String CHUNKS_DIR = "chunks";

    private final TempDirFactory tempDirFactory;
    private final Lz4Compressor lz4Compressor;

    public RecordingFlamegraphAiExporter(TempDirFactory tempDirFactory, Lz4Compressor lz4Compressor) {
        this.tempDirFactory = tempDirFactory;
        this.lz4Compressor = lz4Compressor;
    }

    /**
     * Parses the given JFR files and builds one AI prompt per profile that produced samples, at the
     * given prune threshold. The threshold is a parameter rather than a constant because it is a
     * per-project setting: a service with one obvious hotspot wants a coarse tree, one with diffuse cost
     * wants a fine one.
     */
    public List<FlamegraphAiPrompt> export(List<Path> jfrFiles, double pruneThresholdPct) {
        Map<Type, Frame> framesByType = parse(jfrFiles);

        AiExportConfig config = new AiExportConfig(pruneThresholdPct);
        List<FlamegraphAiPrompt> prompts = new ArrayList<>();
        for (AiPromptType promptType : AiPromptType.values()) {
            resolveRoot(framesByType, promptType).ifPresent(resolved -> {
                String markdown = new FlamegraphAiMarkdownBuilder(resolved.type(), config)
                        .withThreadMode(false)
                        .build(resolved.root());
                ProfileFrameIndex frameIndex =
                        ProfileFrameIndexBuilder.build(resolved.root(), pruneThresholdPct);

                prompts.add(new FlamegraphAiPrompt(
                        promptType.primaryEventType().code(),
                        promptType.label(),
                        resolved.root().totalSamples(),
                        markdown,
                        frameIndex));

                LOG.info("Generated AI flamegraph prompt: prompt_type={} event_type={} total_samples={} indexed_frames={}",
                        promptType.name(), resolved.type().code(), resolved.root().totalSamples(),
                        frameIndex.frames().size());
            });
        }
        return prompts;
    }

    /**
     * Picks the first event type of the group that actually produced samples. A recording carries either
     * {@code ObjectAllocationSample} or the older TLAB pair depending on how it was recorded, and the
     * person reading the result does not care which.
     */
    private static Optional<ResolvedProfile> resolveRoot(Map<Type, Frame> framesByType, AiPromptType promptType) {
        for (Type type : promptType.eventTypes()) {
            Frame root = framesByType.get(type);
            if (root != null && root.totalSamples() > 0) {
                return Optional.of(new ResolvedProfile(type, root));
            }
        }
        return Optional.empty();
    }

    private record ResolvedProfile(Type type, Frame root) {
    }

    private Map<Type, Frame> parse(List<Path> jfrFiles) {
        FrameBuildingEventWriter writer = new FrameBuildingEventWriter();
        Supplier<EventProcessor<Void>> processorSupplier =
                () -> new JfrEventReader(writer.newSingleThreadedWriter(), ProcessableEvents.of(SAMPLE_EVENT_TYPES));

        for (Path jfrFile : jfrFiles) {
            try (TempDirectory tempDir = tempDirFactory.newTempDir()) {
                Path recording = decompressIfNeeded(jfrFile, tempDir);
                List<Path> chunks = JfrParser.disassemble(recording, tempDir.resolve(CHUNKS_DIR));
                JdkRecordingIterators.parallelAndWait(chunks, processorSupplier);
            }
        }

        writer.onComplete();
        return writer.result();
    }

    private Path decompressIfNeeded(Path jfrFile, TempDirectory tempDir) {
        if (Lz4Compressor.isLz4Compressed(jfrFile)) {
            return lz4Compressor.decompressToDir(jfrFile, tempDir.path());
        }
        return jfrFile;
    }
}
