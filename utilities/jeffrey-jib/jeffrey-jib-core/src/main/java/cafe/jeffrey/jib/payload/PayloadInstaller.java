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

package cafe.jeffrey.jib.payload;

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
import java.util.Optional;

/**
 * Fetches the payloads a {@link PayloadPlan} asks for and turns them into one image layer.
 *
 * <p>Every resolution failure aborts. The fail-open guarantee this extension is known for belongs
 * to container start, where an application must boot even if it cannot be profiled; at build time
 * the opposite rule applies, because an image that silently lacks a profiler looks healthy and
 * profiles nothing until somebody notices months later.
 */
public final class PayloadInstaller {

    private static final String LAYER_NAME = "jeffrey-payload";
    private static final String ENV_PROVISIONER_PATH = "JEFFREY_PROVISIONER_PATH";
    private static final String ENV_PROVISIONER_KIND = "JEFFREY_PROVISIONER_KIND";
    private static final String ENV_PROFILER_PATH = "JEFFREY_PROFILER_PATH";
    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    private final PayloadResolver resolver;
    private final String payloadVersion;
    private final ExtensionLogger logger;

    public PayloadInstaller(PayloadResolver resolver, String payloadVersion, ExtensionLogger logger) {
        this.resolver = resolver;
        this.payloadVersion = payloadVersion;
        this.logger = logger;
    }

    public PayloadInstallation install(PayloadPlan plan) throws PayloadResolutionException {
        if (!plan.bakesAnything()) {
            logger.log(LogLevel.LIFECYCLE,
                    "jeffrey-jib: provisionerPath and profilerPath are both set; no payload is baked"
                            + " and the image must supply both binaries itself");
            return PayloadInstallation.empty();
        }

        List<PayloadRequest> requests = new ArrayList<>();
        Map<String, String> environment = new LinkedHashMap<>();

        if (plan.bakeProvisioner()) {
            PayloadSpec spec = plan.source().spec();
            requests.addAll(spec.requestsFor(plan.architectures(), payloadVersion));
            environment.put(ENV_PROVISIONER_PATH, spec.envPath(plan.architectures()));
            environment.put(ENV_PROVISIONER_KIND, plan.source().kind());
        }
        if (plan.bakeProfiler()) {
            requests.addAll(Payloads.PROFILER.requestsFor(plan.architectures(), payloadVersion));
            environment.put(ENV_PROFILER_PATH, Payloads.PROFILER.envPath(plan.architectures()));
        }

        FileEntriesLayer layer = buildLayer(requests);
        return new PayloadInstallation(Optional.of(layer), environment);
    }

    private FileEntriesLayer buildLayer(List<PayloadRequest> requests) throws PayloadResolutionException {
        FileEntriesLayer.Builder builder = FileEntriesLayer.builder().setName(LAYER_NAME);
        long totalBytes = 0;

        for (PayloadRequest request : requests) {
            Path payloadJar = resolver.resolve(request.coordinates());
            Path payload = PayloadJars.extractSingleEntry(payloadJar, request.coordinates());
            builder.addEntry(payload, request.installPath(), request.permissions());
            totalBytes += sizeOf(payload);
        }

        logger.log(LogLevel.LIFECYCLE,
                "jeffrey-jib: baking " + requests.size() + " payload file(s) into the image layer '"
                        + LAYER_NAME + "' (" + totalBytes / BYTES_PER_MEGABYTE + " MB): "
                        + installPaths(requests));
        return builder.build();
    }

    /**
     * Best effort, for the size reported to the build log only. A payload that was resolved and
     * unpacked but whose size cannot be read is not worth failing a build over.
     */
    private static long sizeOf(Path payload) {
        try {
            return Files.size(payload);
        } catch (IOException e) {
            return 0;
        }
    }

    private static List<String> installPaths(List<PayloadRequest> requests) {
        return requests.stream()
                .map(request -> request.installPath().toString())
                .toList();
    }
}
