/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.otlpparser;

import io.opentelemetry.proto.profiles.v1development.ProfilesData;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Reads {@code .otlp} files in Jeffrey's file convention (see {@link OtlpFileFormat}) and hands every
 * decoded {@code ProfilesData} frame to a consumer. Framed files are streamed frame-by-frame; raw
 * files (no header) are parsed as a single message.
 */
public class OtlpStreamReader {

    public void read(Path file, Consumer<ProfilesData> frameConsumer) {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(file))) {
            byte[] header = input.readNBytes(OtlpFileFormat.HEADER_SIZE);
            if (OtlpFileFormat.startsWithMagic(header, header.length)) {
                int version = OtlpFileFormat.readVersion(header);
                if (version > OtlpFileFormat.VERSION) {
                    throw new IllegalArgumentException(
                            "Unsupported OTLP file format version: file=" + file + " version=" + version);
                }
                readDelimitedFrames(file, input, frameConsumer);
            } else {
                readRawMessage(file, header, input, frameConsumer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read OTLP recording: " + file, e);
        }
    }

    private void readDelimitedFrames(Path file, InputStream input, Consumer<ProfilesData> frameConsumer) {
        try {
            ProfilesData frame;
            while ((frame = ProfilesData.parseDelimitedFrom(input)) != null) {
                frameConsumer.accept(frame);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse a frame of the OTLP recording: " + file, e);
        }
    }

    private void readRawMessage(
            Path file,
            byte[] alreadyReadHeader,
            InputStream input,
            Consumer<ProfilesData> frameConsumer) {

        try {
            byte[] rest = input.readAllBytes();
            byte[] content = new byte[alreadyReadHeader.length + rest.length];
            System.arraycopy(alreadyReadHeader, 0, content, 0, alreadyReadHeader.length);
            System.arraycopy(rest, 0, content, alreadyReadHeader.length, rest.length);

            frameConsumer.accept(ProfilesData.parseFrom(content));
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to parse the OTLP recording as a raw ProfilesData message: " + file, e);
        }
    }
}
