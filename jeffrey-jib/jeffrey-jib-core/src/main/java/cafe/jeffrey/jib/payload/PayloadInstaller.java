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

package cafe.jeffrey.jib.payload;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Takes the payloads a {@link PayloadPlan} asks for out of the payload jar on the class path and
 * turns them into one image layer.
 *
 * <p>Every failure aborts. The fail-open guarantee this extension is known for belongs to container
 * start, where an application must boot even if it cannot be profiled; at build time the opposite
 * rule applies, because an image that silently lacks a profiler looks healthy and profiles nothing
 * until somebody notices months later.
 */
public final class PayloadInstaller {

    private static final String LAYER_NAME = "jeffrey-payload";
    private static final String ENV_PROVISIONER_PATH = "JEFFREY_PROVISIONER_PATH";
    private static final String ENV_PROVISIONER_KIND = "JEFFREY_PROVISIONER_KIND";
    private static final String ENV_PROFILER_PATH = "JEFFREY_PROFILER_PATH";
    private static final String ENV_AGENT_PATH = "JEFFREY_AGENT_PATH";
    private static final String PROVENANCE_OPEN = " (";
    private static final String PROVENANCE_CLOSE = ")";
    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    private final ClassLoader payloads;
    private final Path workDirectory;
    private final ExtensionLogger logger;

    /**
     * @param payloads      the class loader the payload jar is visible through — the extension's own
     * @param workDirectory where payloads are unpacked; stable across builds so JIB can reuse the
     *                      cached layer (see {@link PayloadResources})
     */
    public PayloadInstaller(ClassLoader payloads, Path workDirectory, ExtensionLogger logger) {
        this.payloads = payloads;
        this.workDirectory = workDirectory;
        this.logger = logger;
    }

    public PayloadInstallation install(PayloadPlan plan) throws PayloadResolutionException {
        PayloadDescriptor descriptor = plan.descriptor();
        FileEntriesLayer.Builder layer = FileEntriesLayer.builder().setName(LAYER_NAME);
        List<String> installed = new ArrayList<>();
        long totalBytes = 0;
        Map<String, String> environment = new LinkedHashMap<>();
        Set<String> architectures = plan.architectures();

        PayloadSpec provisioner = descriptor.kind().spec();
        totalBytes += bake(layer, provisioner, architectures, descriptor.provisionerProvenance(), installed);
        environment.put(ENV_PROVISIONER_PATH, provisioner.envPath(architectures));
        environment.put(ENV_PROVISIONER_KIND, descriptor.kind().kind());

        if (plan.bakeProfiler()) {
            totalBytes += bake(
                    layer, Payloads.PROFILER, architectures, descriptor.profilerProvenance(), installed);
            environment.put(ENV_PROFILER_PATH, Payloads.PROFILER.envPath(architectures));
        }

        // Built from the same Jeffrey release as the provisioner, so it shares that provenance.
        if (plan.bakeAgent()) {
            totalBytes += bake(
                    layer, Payloads.AGENT, architectures, descriptor.provisionerProvenance(), installed);
            environment.put(ENV_AGENT_PATH, Payloads.AGENT.envPath(architectures));
        }

        // The provenance in parentheses is the only place a build log says which Jeffrey release
        // (provisioner and agent) and async-profiler an image actually carries: the payload jar's
        // version is a jeffrey-jib release, and the mapping to the binaries inside it lives in the
        // payload descriptor.
        logger.log(LogLevel.LIFECYCLE,
                "jeffrey-jib: baking " + installed.size() + " payload file(s) into the image layer '"
                        + LAYER_NAME + "' (" + totalBytes / BYTES_PER_MEGABYTE + " MB): " + installed);
        return new PayloadInstallation(layer.build(), environment);
    }

    /** Adds every file of one payload the targeted architectures need; returns their total size. */
    private long bake(
            FileEntriesLayer.Builder layer,
            PayloadSpec spec,
            Set<String> architectures,
            String provenance,
            List<String> installed) throws PayloadResolutionException {

        long bytes = 0;
        for (PayloadRequest request : spec.requestsFor(architectures)) {
            bytes += add(layer, request, provenance, installed);
        }
        return bytes;
    }

    private long add(FileEntriesLayer.Builder layer, PayloadRequest request, String provenance, List<String> installed)
            throws PayloadResolutionException {

        Path file = PayloadResources.unpack(payloads, request.resource(), workDirectory);
        layer.addEntry(file, request.installPath(), request.permissions());
        installed.add(describe(request.installPath(), provenance));
        return sizeOf(file);
    }

    /** {@code /opt/jeffrey/provisioner (jeffrey v0.13.22)}, for the build log. */
    private static String describe(AbsoluteUnixPath installPath, String provenance) {
        return installPath + PROVENANCE_OPEN + provenance + PROVENANCE_CLOSE;
    }

    /**
     * Best effort, for the size reported to the build log only. A payload that was unpacked but
     * whose size cannot be read is not worth failing a build over.
     */
    private static long sizeOf(Path payload) {
        try {
            return Files.size(payload);
        } catch (IOException e) {
            return 0;
        }
    }
}
