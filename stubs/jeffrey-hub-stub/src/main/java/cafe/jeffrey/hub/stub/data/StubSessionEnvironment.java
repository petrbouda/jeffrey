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

package cafe.jeffrey.hub.stub.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the {@code environment_json_fields} payload for a session, mirroring what the
 * real hub extracts from a JFR chunk via {@code InstanceEnvironmentParser}: a JSON object
 * keyed by JFR event-type name, whose values are the raw field maps. The instance-timeline
 * UI renders these dynamically (humanizing the JFR field names into the card labels), so the
 * field keys here must match the real JFR field names.
 *
 * <p>Finished sessions include {@code jdk.Shutdown} (with an injected {@code eventTime});
 * active sessions omit it, matching the real parser's {@code expectShutdown} behaviour.
 */
public final class StubSessionEnvironment {

    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;

    private static final String CPU_DESCRIPTION =
            "AArch64 0x61:0x0:0x000:0, fp, asimd, evtstrm, aes, pmull, sha1, sha256, crc32, lse, "
                    + "fphp, asimdhp, dcpop, sha3, sm3, sm4, asimddp, sha512, sve, asimdfhm, dit, uscat, "
                    + "ilrcpc, flagm, ssbs, sb, paca, pacg, dcpodp, sve2, sveaes, svepmull, svebitperm";
    private static final String OS_VERSION =
            "DISTRIB_ID=Ubuntu\n"
                    + "DISTRIB_RELEASE=24.04\n"
                    + "DISTRIB_CODENAME=noble\n"
                    + "DISTRIB_DESCRIPTION=Ubuntu 24.04 LTS\n"
                    + "uname: Linux 6.8.0-40-generic aarch64";
    private static final String JVM_VERSION =
            "OpenJDK 64-Bit Server VM (25.0.3+9-LTS) for linux-aarch64 JRE (25.0.3+9-LTS), "
                    + "built on 2026-04-21T00:00:00Z with gcc 13.2.0";
    private static final String JVM_ARGUMENTS =
            "-agentpath:jeffrey-libs/libasyncProfiler-arm64.so=start,alloc,lock,event=ctimer,"
                    + "jfrsync=default,loop=10s,file=recording.jfr -XX:+UseG1GC -Xms500m -Xmx500m";
    private static final String JAVA_ARGUMENTS = "cafe.jeffrey.hub.core.HubApplication";
    private static final String SHUTDOWN_REASON = "Shutdown requested from Java";
    private static final String SHUTDOWN_THREAD = "SIGTERM handler";

    private StubSessionEnvironment() {
    }

    public static String forSession(StubDataset.Session session) {
        long jvmStartTime = session.createdAt().toEpochMilli();
        // A session is finished iff it has a finish timestamp; every finished session gets the
        // completed environment (including jdk.Shutdown). Active sessions omit jdk.Shutdown.
        boolean finished = session.finishedAt() != null;
        long shutdownTime = finished ? session.finishedAt().toEpochMilli() : 0L;
        return build(jvmStartTime, finished, shutdownTime);
    }

    private static String build(long jvmStartTime, boolean includeShutdown, long shutdownTime) {
        Map<String, Map<String, Object>> env = new LinkedHashMap<>();

        if (includeShutdown) {
            env.put("jdk.Shutdown", ordered(
                    "eventThread", SHUTDOWN_THREAD,
                    "reason", SHUTDOWN_REASON,
                    "eventTime", shutdownTime));
        }

        env.put("jdk.JVMInformation", ordered(
                "jvmName", "OpenJDK 64-Bit Server VM",
                "jvmVersion", JVM_VERSION,
                "jvmArguments", JVM_ARGUMENTS,
                "javaArguments", JAVA_ARGUMENTS,
                "jvmStartTime", jvmStartTime,
                "pid", 1));

        env.put("jdk.GCConfiguration", ordered(
                "youngCollector", "G1New",
                "oldCollector", "G1Old",
                "parallelGCThreads", 2,
                "concurrentGCThreads", 1,
                "usesDynamicGCThreads", true,
                "isExplicitGCConcurrent", false,
                "isExplicitGCDisabled", false,
                "gcTimeRatio", 12));

        env.put("jdk.GCHeapConfiguration", ordered(
                "minSize", 500 * MIB,
                "maxSize", 500 * MIB,
                "initialSize", 500 * MIB,
                "usesCompressedOops", true,
                "compressedOopsMode", "32-bit",
                "objectAlignment", 8,
                "heapAddressBits", 32));

        env.put("jdk.CPUInformation", ordered(
                "cpu", "AArch64",
                "description", CPU_DESCRIPTION,
                "sockets", 16,
                "cores", 16,
                "hwThreads", 16));

        env.put("jdk.ContainerConfiguration", ordered(
                "containerType", "cgroupv2",
                "cpuSlicePeriod", 100_000_000L,
                "cpuQuota", 200_000_000L,
                "cpuShares", 2048,
                "effectiveCpuCount", 2,
                "memorySoftLimit", 0,
                "memoryLimit", GIB,
                "swapMemoryLimit", GIB,
                "hostTotalMemory", 16_804_059_546L,
                "hostTotalSwapMemory", 17_877_801_370L));

        env.put("jdk.CompilerConfiguration", ordered(
                "threadCount", 2,
                "tieredCompilation", true,
                "dynamicCompilerThreadCount", true));

        env.put("jdk.OSInformation", ordered("osVersion", OS_VERSION));

        env.put("jdk.VirtualizationInformation", ordered("name", "No virtualization detected"));

        return toJson(env);
    }

    /** Builds an insertion-ordered field map from alternating key/value pairs. */
    private static Map<String, Object> ordered(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static String toJson(Map<String, Map<String, Object>> env) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Map<String, Object>> entry : env.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(quote(entry.getKey())).append(":").append(fieldsToJson(entry.getValue()));
        }
        return sb.append("}").toString();
    }

    private static String fieldsToJson(Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(quote(entry.getKey())).append(":").append(valueToJson(entry.getValue()));
        }
        return sb.append("}").toString();
    }

    private static String valueToJson(Object value) {
        if (value instanceof String s) {
            return quote(s);
        }
        // Boolean and numeric types render as their literal JSON form.
        return String.valueOf(value);
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append("\"").toString();
    }
}
