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

package pbouda.jeffrey.provider.reader.jfr.chunk;

import pbouda.jeffrey.common.model.EventSource;
import pbouda.jeffrey.provider.api.model.recording.RecordingInformation;
import pbouda.jeffrey.tools.impl.jdk.JdkJfrTool;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.nio.file.StandardOpenOption.*;

public abstract class Recordings {

    private static final ChunkBasedRecordingDisassembler DISASSEMBLER =
            new ChunkBasedRecordingDisassembler(new JdkJfrTool());

    public static void main(String[] args) {
        Path input = Path.of("/Users/petrbouda/Desktop/RECORDINGS/persons/jeffrey-persons-dom-serde-cpu.jfr");
        List<JfrChunk> chunks = chunkHeaders(input);
        for (JfrChunk chunk : chunks) {
            System.out.println(chunk.duration() + " - " + chunk.sizeInBytes());
        }

        List<Path> recordings = DISASSEMBLER.disassemble(input, Path.of(""));

        System.out.println("Recording size: " + recordings.size());
        for (Path path : recordings) {
            System.out.println("Recording: " + path);
        }

        Path output = Path.of("/tmp/jeffrey-merged.jfr");
        mergeRecordings(recordings, output);

        chunks = chunkHeaders(output);
        for (JfrChunk chunk : chunks) {
            System.out.println(chunk.duration() + " - " + chunk.sizeInBytes());
        }
    }

    /**
     * Splits the given recording into chunks and saves them as separate files.
     *
     * @param recording the path to the recording file
     * @return a list of paths to the created chunk files
     */
    public static List<Path> splitRecording(Path recording, Path outputDir) {
//        List<Path> chunkFiles = new ArrayList<>();
//        ChunkIterator.iterate(recording, (channel, jfrChunk) -> {
//            Path newPath = outputDir.resolve("chunk_" + chunkFiles.size() + ".jfr");
//            try (var output = FileChannel.open(newPath, CREATE, WRITE)) {
//                channel.transferTo(channel.position(), jfrChunk.sizeInBytes(), output);
//            } catch (IOException e) {
//                throw new RuntimeException("Cannot create recording from chunk: " + recording, e);
//            }
//            chunkFiles.add(newPath);
//        });
//
//        return chunkFiles;
        return DISASSEMBLER.disassemble(recording, outputDir);
    }

    /**
     * Reads the chunk headers from the given recording file.
     *
     * @param recording the path to the recording file
     * @return a list of chunk headers
     */
    public static List<JfrChunk> chunkHeaders(Path recording) {
        List<JfrChunk> jfrChunks = new ArrayList<>();
        ChunkIterator.iterate(recording, (_, jfrChunk) -> jfrChunks.add(jfrChunk));
        return jfrChunks;
    }

    /**
     * Aggregates the recording information from the header chunk of the given recording file.
     *
     * @param recording the path to the recording file
     * @return recording info from the header chunks
     */
    public static RecordingInformation aggregatedRecordingInfo(Path recording) {
        List<JfrChunk> jfrChunks = chunkHeaders(recording);
        long bytes = jfrChunks.stream().mapToLong(JfrChunk::sizeInBytes).sum();
        Instant startTime = jfrChunks.stream()
                .map(JfrChunk::startTime)
                .min(Instant::compareTo)
                .orElse(null);
        Instant endTime = jfrChunks.stream()
                .map(header -> header.startTime().plus(header.duration()))
                .max(Instant::compareTo)
                .orElse(null);

        boolean anyProfilerEvent = eventTypes(recording).stream()
                .anyMatch(eventType -> eventType.startsWith("profiler."));

        EventSource source = anyProfilerEvent ? EventSource.ASYNC_PROFILER : EventSource.JDK;
        return new RecordingInformation(bytes, source, startTime, endTime);
    }

    /**
     * Reads the event types from the given recording file.
     *
     * @param recording the path to the recording file
     * @return a set of event type names
     */
    public static Set<String> eventTypes(Path recording) {
        Set<String> eventTypes = new HashSet<>();
        ChunkIterator.iterate(recording, (_, jfrChunk) -> eventTypes.addAll(jfrChunk.eventTypes()));
        return eventTypes;
    }

    /**
     * Merges a list of JDK Flight Recorder files into a single output file.
     * This method concatenates all recording files in the order they appear in the list.
     *
     * @param recordings List of paths to JFR recording files to be merged
     * @param outputPath Path where the merged recording file will be written
     * @throws RuntimeException if there's an error during the merge operation
     */
    public static void mergeRecordings(List<Path> recordings, Path outputPath) {
        try (FileChannel output = FileChannel.open(outputPath, CREATE, WRITE, TRUNCATE_EXISTING)) {
            for (Path recording : recordings) {
                try (FileChannel input = FileChannel.open(recording, READ)) {
                    long size = input.size();
                    long position = 0;
                    while (position < size) {
                        position += input.transferTo(position, size - position, output);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to merge recordings: " + recordings, e);
        }
    }
}
