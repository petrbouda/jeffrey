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

package cafe.jeffrey.microscope.volume.client;

import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.WritableVolume;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A volume that remembers the files that never change once written, so a page read again does not
 * ask an Azure file share for them again: the provisioner's {@code .project-info.json},
 * {@code .instance-info.json} and {@code .session-info.json}. Each is written whole, once, and only
 * ever removed with its directory. Nothing else is remembered — not a listing, a size or the
 * workspace marker Microscope rewrites — and
 * a file that was not there is asked for again next time.
 *
 * <p>What is remembered is forgotten:
 * <ul>
 *   <li>when a listing of a directory above it no longer shows the directory it lies in, or the
 *   file itself — a session the hub's retention removed is gone from the next listing that would
 *   have reached it;</li>
 *   <li>when Microscope itself writes or deletes at or above it;</li>
 *   <li>after {@link #REMEMBERED_FOR}, whatever happened, and the oldest first once
 *   {@link #CAPACITY} files are held.</li>
 * </ul>
 * Microscope's only; the hub reads its own mount and remembers nothing.
 */
public final class RememberingVolume implements WritableVolume {

    /** The markers the provisioner writes once, in a directory they describe. */
    private static final Set<String> MARKERS = Set.of(
            JeffreyLayout.PROJECT_INFO_FILE, JeffreyLayout.INSTANCE_INFO_FILE, JeffreyLayout.SESSION_INFO_FILE);

    /** A safety bound: a file is asked for again at least this often. */
    static final Duration REMEMBERED_FOR = Duration.ofMinutes(10);

    /** At most this many files are held; markers are a few hundred bytes. */
    static final int CAPACITY = 20_000;

    private record Remembered(byte[] content, Instant readAt) {
    }

    private final WritableVolume delegate;
    private final Clock clock;
    private final Map<VolumePath, Remembered> remembered = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<VolumePath, Remembered> eldest) {
            return size() > CAPACITY;
        }
    };

    public RememberingVolume(WritableVolume delegate, Clock clock) {
        if (delegate == null || clock == null) {
            throw new IllegalArgumentException("A remembering volume needs the volume it reads and a clock");
        }
        this.delegate = delegate;
        this.clock = clock;
    }

    /** A provisioner marker. */
    static boolean isWriteOnce(VolumePath file) {
        return !file.segments().isEmpty() && MARKERS.contains(file.name());
    }

    @Override
    public Optional<byte[]> read(VolumePath file) {
        if (!isWriteOnce(file)) {
            return delegate.read(file);
        }
        Optional<Remembered> known = recall(file);
        if (known.isPresent()) {
            return Optional.of(known.get().content().clone());
        }
        Optional<byte[]> content = delegate.read(file);
        content.ifPresent(bytes -> remember(file, bytes));
        return content;
    }

    /** Lists the directory, and forgets what lies under anything the listing no longer shows. */
    @Override
    public List<VolumeEntry> list(VolumePath directory) {
        List<VolumeEntry> entries = delegate.list(directory);
        Set<String> listed = entries.stream().map(VolumeEntry::name).collect(Collectors.toSet());
        int depth = directory.segments().size();
        synchronized (remembered) {
            remembered.keySet().removeIf(file -> isUnder(file, directory)
                    && !listed.contains(file.segments().get(depth)));
        }
        return entries;
    }

    @Override
    public Optional<VolumeEntry> stat(VolumePath path) {
        return delegate.stat(path);
    }

    @Override
    public Optional<OpenedFile> open(VolumePath file) {
        return delegate.open(file);
    }

    @Override
    public void createDirectories(VolumePath directory) {
        delegate.createDirectories(directory);
    }

    @Override
    public void write(VolumePath file, byte[] content) {
        forgetAtOrUnder(file);
        delegate.write(file, content);
    }

    @Override
    public void delete(VolumePath path) {
        forgetAtOrUnder(path);
        delegate.delete(path);
    }

    private Optional<Remembered> recall(VolumePath file) {
        synchronized (remembered) {
            Remembered known = remembered.get(file);
            if (known == null) {
                return Optional.empty();
            }
            if (known.readAt().plus(REMEMBERED_FOR).isBefore(clock.instant())) {
                remembered.remove(file);
                return Optional.empty();
            }
            return Optional.of(known);
        }
    }

    private void remember(VolumePath file, byte[] content) {
        synchronized (remembered) {
            remembered.put(file, new Remembered(content.clone(), clock.instant()));
        }
    }

    private void forgetAtOrUnder(VolumePath path) {
        synchronized (remembered) {
            remembered.keySet().removeIf(file -> file.equals(path) || isUnder(file, path));
        }
    }

    /** Whether {@code file} lies strictly below {@code directory}. */
    private static boolean isUnder(VolumePath file, VolumePath directory) {
        List<String> segments = file.segments();
        List<String> prefix = directory.segments();
        return segments.size() > prefix.size() && segments.subList(0, prefix.size()).equals(prefix);
    }
}
