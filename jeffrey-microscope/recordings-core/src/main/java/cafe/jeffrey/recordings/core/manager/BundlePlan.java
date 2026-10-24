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

package cafe.jeffrey.recordings.core.manager;

import cafe.jeffrey.storage.recording.api.file.ManagedFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * How the files of one download are split into a bundle: which become recordings of their own, and
 * which stay download-only files of the bundle.
 * <p>
 * Every recording file of the session — the JFR chunks — together make one item: they are one run of
 * the profiler, and analysing any one chunk alone would quietly answer for a few minutes of a run that
 * lasted hours. Each artifact that can be initialized on its own — a heap dump — is an item of its own,
 * named after its file. Everything else, the logs and crash files, is a file of the bundle.
 *
 * @param name  the bundle's name, which its recording item is called by as well
 * @param items the initializable items, the recording item first
 * @param files the download-only files
 */
public record BundlePlan(String name, List<Item> items, List<Path> files) {

    /**
     * One initializable item: what becomes one recording and, once initialized, one profile.
     *
     * @param name  what the item is called in the list
     * @param files its files, at least one
     */
    public record Item(String name, List<Path> files) {

        public Item {
            if (files == null || files.isEmpty()) {
                throw new IllegalArgumentException("A bundle item needs at least one file: " + name);
            }
            files = List.copyOf(files);
        }
    }

    public BundlePlan {
        items = List.copyOf(items);
        files = List.copyOf(files);
    }

    /**
     * @param name           the session's name
     * @param recordingFiles the session's recording files, all of one profiler run
     * @param artifactFiles  everything else the download brought
     */
    public static BundlePlan of(String name, List<Path> recordingFiles, List<Path> artifactFiles) {
        if (recordingFiles.isEmpty()) {
            throw new IllegalArgumentException("A downloaded bundle needs at least one recording file");
        }

        List<Item> items = new ArrayList<>();
        items.add(new Item(name, recordingFiles));

        List<Path> files = new ArrayList<>();
        for (Path artifact : artifactFiles) {
            if (ManagedFile.of(artifact).isInitializable()) {
                items.add(new Item(artifact.getFileName().toString(), List.of(artifact)));
            } else {
                files.add(artifact);
            }
        }
        return new BundlePlan(name, items, files);
    }
}
