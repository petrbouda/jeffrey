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

package cafe.jeffrey.hub.core.kubernetes;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import java.io.Closeable;
import java.time.Duration;

/**
 * Watches Jeffrey-labeled pods ({@code jeffrey.cafe/enabled=true}) via a shared
 * informer and forwards lifecycle events to {@link PodLifecycleHandler}.
 *
 * <p>This is an additive discovery channel: the filesystem event queue written
 * by the provisioner remains the source of truth for project/instance/session
 * creation, while the informer removes the manual workspace prerequisite and
 * turns pod termination into an immediate, deterministic session finish. Any
 * failure to reach the Kubernetes API only logs — the hub keeps working with
 * filesystem discovery alone.</p>
 */
public class KubernetesDiscovery implements ApplicationListener<ApplicationReadyEvent>, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesDiscovery.class);

    private static final Duration INFORMER_RESYNC_PERIOD = Duration.ofMinutes(10);

    private final KubernetesClient client;
    private final PodLifecycleHandler podLifecycleHandler;
    private final String namespace;

    private SharedIndexInformer<Pod> podInformer;

    public KubernetesDiscovery(
            KubernetesClient client,
            PodLifecycleHandler podLifecycleHandler,
            String namespace) {

        this.client = client;
        this.podLifecycleHandler = podLifecycleHandler;
        this.namespace = namespace;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        start();
    }

    public void start() {
        if (podInformer != null) {
            return;
        }

        try {
            var pods = namespace == null || namespace.isBlank()
                    ? client.pods().inAnyNamespace()
                    : client.pods().inNamespace(namespace);

            podInformer = pods
                    .withLabel(KubernetesConventions.LABEL_ENABLED, KubernetesConventions.VALUE_TRUE)
                    .inform(podLifecycleHandler, INFORMER_RESYNC_PERIOD.toMillis());

            LOG.info("Kubernetes pod discovery started: namespace={} label_selector={}={}",
                    namespace == null || namespace.isBlank() ? "<all>" : namespace,
                    KubernetesConventions.LABEL_ENABLED, KubernetesConventions.VALUE_TRUE);
        } catch (Exception e) {
            // Discovery is additive — the hub keeps running on filesystem discovery alone
            LOG.error("Failed to start Kubernetes pod discovery, continuing without it", e);
        }
    }

    @Override
    public void close() {
        if (podInformer != null) {
            podInformer.close();
        }
        client.close();
    }
}
