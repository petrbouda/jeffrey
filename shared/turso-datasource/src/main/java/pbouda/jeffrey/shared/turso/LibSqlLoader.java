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

package pbouda.jeffrey.shared.turso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads the native libsql library from bundled resources or system path.
 *
 * <p>The library is extracted from the classpath resource matching the current OS and architecture
 * to a temporary directory, then loaded via {@link SymbolLookup#libraryLookup(Path, Arena)}.
 */
public final class LibSqlLoader {

    private static final Logger LOG = LoggerFactory.getLogger(LibSqlLoader.class);

    private static volatile SymbolLookup lookup;

    private LibSqlLoader() {
    }

    /**
     * Returns the {@link SymbolLookup} for the loaded native libsql library.
     * Thread-safe, initializes on first call.
     */
    public static SymbolLookup symbolLookup() {
        if (lookup == null) {
            synchronized (LibSqlLoader.class) {
                if (lookup == null) {
                    lookup = loadLibrary();
                }
            }
        }
        return lookup;
    }

    private static SymbolLookup loadLibrary() {
        String os = detectOs();
        String arch = detectArch();
        String libName = libraryFileName(os);
        String resourcePath = "native/" + os + "-" + arch + "/" + libName;

        LOG.info("Loading libsql native library: os={} arch={} resource={}", os, arch, resourcePath);

        // Try bundled resource first
        try (InputStream is = LibSqlLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                Path tempDir = Files.createTempDirectory("libsql-native");
                Path tempLib = tempDir.resolve(libName);
                Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);
                tempLib.toFile().deleteOnExit();
                tempDir.toFile().deleteOnExit();

                LOG.info("Extracted native library to: {}", tempLib);
                return SymbolLookup.libraryLookup(tempLib, Arena.global());
            }
        } catch (IOException e) {
            LOG.warn("Failed to extract bundled native library: {}", e.getMessage());
        }

        // Fallback: try system library path
        LOG.info("Bundled library not found, trying system library path for: libsql");
        try {
            System.loadLibrary("libsql");
            return SymbolLookup.loaderLookup();
        } catch (UnsatisfiedLinkError e) {
            throw new LibSqlException(
                    "Cannot load libsql native library. Looked for bundled resource '" + resourcePath
                            + "' and system library 'libsql'. Ensure the native library is available for "
                            + os + "-" + arch, e);
        }
    }

    private static String detectOs() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("linux")) {
            return "linux";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            return "macos";
        } else if (osName.contains("windows")) {
            return "windows";
        }
        throw new LibSqlException("Unsupported OS: " + osName);
    }

    private static String detectArch() {
        String archName = System.getProperty("os.arch", "").toLowerCase();
        if (archName.equals("amd64") || archName.equals("x86_64")) {
            return "x86_64";
        } else if (archName.equals("aarch64") || archName.equals("arm64")) {
            return "aarch64";
        }
        throw new LibSqlException("Unsupported architecture: " + archName);
    }

    private static String libraryFileName(String os) {
        return switch (os) {
            case "linux" -> "liblibsql.so";
            case "macos" -> "liblibsql.dylib";
            case "windows" -> "libsql.dll";
            default -> throw new LibSqlException("Unsupported OS: " + os);
        };
    }
}
