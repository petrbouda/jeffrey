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

package pbouda.jeffrey.platform.appinitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class CopyLibsInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(CopyLibsInitializer.class);
    private static final String CURRENT_SYMLINK = "current";

    private final Path source;
    private final Path target;
    private final String version;
    private final int maxKeptVersions;

    public CopyLibsInitializer(Path source, Path target, String version, int maxKeptVersions) {
        if (maxKeptVersions < 1) {
            throw new IllegalArgumentException("maxKeptVersions must be at least 1, got: " + maxKeptVersions);
        }
        this.source = source;
        this.target = target;
        this.version = version;
        this.maxKeptVersions = maxKeptVersions;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!Files.exists(source)) {
            LOG.debug("Copy libs source directory does not exist: {}", source);
            return;
        }

        if (!Files.isDirectory(source)) {
            LOG.warn("Copy libs source is not a directory: {}", source);
            return;
        }

        if (version == null) {
            copyDirectory(source, target);
            return;
        }

        Path versionedTarget = target.resolve(version);

        if (Files.isDirectory(versionedTarget)) {
            LOG.info("Libs already up to date: version={} path={}", version, versionedTarget);
        } else {
            copyDirectory(source, versionedTarget);
        }

        updateCurrentSymlink();
        cleanupOldVersions();
    }

    private void updateCurrentSymlink() {
        Path symlinkPath = target.resolve(CURRENT_SYMLINK);
        Path tempSymlink = target.resolve("." + CURRENT_SYMLINK + "-tmp");

        try {
            Files.deleteIfExists(tempSymlink);
            Files.createSymbolicLink(tempSymlink, Path.of(version));
            Files.move(tempSymlink, symlinkPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOG.info("Updated current symlink: path={} version={}", symlinkPath, version);
        } catch (IOException e) {
            LOG.error("Failed to update current symlink: path={}", symlinkPath, e);
        }
    }

    private void cleanupOldVersions() {
        List<Path> versionDirs = new ArrayList<>();

        try (DirectoryStream<Path> entries = Files.newDirectoryStream(target)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (Files.isSymbolicLink(entry) || name.startsWith(".")) {
                    continue;
                }
                if (Files.isDirectory(entry)) {
                    versionDirs.add(entry);
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to list target directory for cleanup: path={}", target, e);
            return;
        }

        if (versionDirs.size() <= maxKeptVersions) {
            return;
        }

        versionDirs.sort(Comparator.comparing(this::getLastModifiedTime).reversed());

        for (Path dir : versionDirs.subList(maxKeptVersions, versionDirs.size())) {
            try {
                FileSystemUtils.removeDirectory(dir);
                LOG.info("Cleaned up old version directory: path={}", dir);
            } catch (Exception e) {
                LOG.warn("Failed to clean up old version directory, will retry on next restart: path={}", dir, e);
            }
        }
    }

    private FileTime getLastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    private void copyDirectory(Path source, Path target) {
        try {
            Files.createDirectories(target);

            try (Stream<Path> stream = Files.walk(source)) {
                stream.forEach(sourcePath -> {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    copyFile(sourcePath, targetPath);
                });
            }

            LOG.info("Successfully copied libs: source={} target={}", source, target);
        } catch (IOException e) {
            LOG.error("Failed to copy libs: source={} target={}", source, target, e);
        }
    }

    private void copyFile(Path source, Path target) {
        try {
            if (Files.isDirectory(source)) {
                Files.createDirectories(target);
            } else {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.error("Failed to copy file: source={} target={}", source, target, e);
        }
    }
}
