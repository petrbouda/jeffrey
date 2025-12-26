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
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public class CopyLibsInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(CopyLibsInitializer.class);
    private final Path source;
    private final Path target;

    public CopyLibsInitializer(Path source, Path target) {
        this.source = source;
        this.target = target;
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

        copyDirectory(source, target);
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

            LOG.info("Successfully copied libs from {} to {}", source, target);
        } catch (IOException e) {
            LOG.error("Failed to copy libs from {} to {}", source, target, e);
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
            LOG.error("Failed to copy file from {} to {}", source, target, e);
        }
    }
}
