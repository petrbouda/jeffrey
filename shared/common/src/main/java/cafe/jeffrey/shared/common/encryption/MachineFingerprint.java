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

package cafe.jeffrey.shared.common.encryption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Collects a machine-specific fingerprint used to derive encryption keys for secrets.
 * The fingerprint combines an OS-level machine ID (when available) with user-specific
 * properties to bind encrypted values to both the machine and the current user.
 *
 * <p>If the OS machine ID cannot be obtained (unknown OS, missing files, permission denied),
 * falls back to a user-only fingerprint. Secrets are still encrypted, but with weaker binding.</p>
 */
public class MachineFingerprint {

    private static final Logger LOG = LoggerFactory.getLogger(MachineFingerprint.class);

    private static final Pattern IOREG_UUID_PATTERN = Pattern.compile("\"IOPlatformUUID\"\\s*=\\s*\"([^\"]+)\"");

    public enum BindingMode {
        /** Fingerprint includes OS machine ID — strongest binding */
        MACHINE_BOUND,
        /** OS machine ID unavailable — user properties only */
        USER_BOUND
    }

    public record Result(String fingerprint, BindingMode mode) {
    }

    private volatile Result cachedResult;

    /**
     * Returns the machine fingerprint, computing it on first call and caching thereafter.
     */
    public Result resolve() {
        Result result = cachedResult;
        if (result == null) {
            result = compute();
            cachedResult = result;
        }
        return result;
    }

    private Result compute() {
        String userName = System.getProperty("user.name", "unknown");
        String userHome = System.getProperty("user.home", "unknown");
        String osName = System.getProperty("os.name", "unknown");
        String osArch = System.getProperty("os.arch", "unknown");

        String userPart = userName + ":" + userHome + ":" + osName + ":" + osArch;

        String machineId = resolveMachineId(osName);
        if (machineId != null) {
            LOG.info("Machine fingerprint resolved: mode=MACHINE_BOUND");
            return new Result(machineId + ":" + userPart, BindingMode.MACHINE_BOUND);
        } else {
            LOG.warn("OS machine ID unavailable, using user-only fingerprint: mode=USER_BOUND");
            return new Result(userPart, BindingMode.USER_BOUND);
        }
    }

    private static String resolveMachineId(String osName) {
        String os = osName.toLowerCase();
        try {
            if (os.contains("mac")) {
                return resolveMacOsId();
            } else if (os.contains("linux")) {
                return resolveLinuxId();
            } else if (os.contains("windows")) {
                return resolveWindowsId();
            } else {
                LOG.warn("Unknown operating system, cannot resolve machine ID: os={}", osName);
                return null;
            }
        } catch (Exception e) {
            LOG.warn("Failed to resolve machine ID: os={} error={}", osName, e.getMessage());
            return null;
        }
    }

    /**
     * macOS: reads IOPlatformUUID from IORegistry (hardware UUID, survives OS reinstalls).
     */
    private static String resolveMacOsId() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
                .redirectErrorStream(true)
                .start();

        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            LOG.warn("ioreg command failed: exitCode={}", exitCode);
            return null;
        }

        Matcher matcher = IOREG_UUID_PATTERN.matcher(output);
        if (matcher.find()) {
            return matcher.group(1);
        }

        LOG.warn("IOPlatformUUID not found in ioreg output");
        return null;
    }

    /**
     * Linux: reads /etc/machine-id (systemd, stable across reboots).
     */
    private static String resolveLinuxId() throws IOException {
        Path machineIdPath = Path.of("/etc/machine-id");
        if (Files.isReadable(machineIdPath)) {
            return Files.readString(machineIdPath).trim();
        }

        LOG.warn("Linux machine-id file not readable: path={}", machineIdPath);
        return null;
    }

    /**
     * Windows: reads MachineGuid from the registry.
     */
    private static String resolveWindowsId() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "reg", "query",
                "HKLM\\SOFTWARE\\Microsoft\\Cryptography",
                "/v", "MachineGuid")
                .redirectErrorStream(true)
                .start();

        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            LOG.warn("Windows registry query failed: exitCode={}", exitCode);
            return null;
        }

        // Output format: "    MachineGuid    REG_SZ    <guid>"
        for (String line : output.split("\n")) {
            if (line.contains("MachineGuid")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 3) {
                    return parts[parts.length - 1];
                }
            }
        }

        LOG.warn("MachineGuid not found in registry output");
        return null;
    }
}
