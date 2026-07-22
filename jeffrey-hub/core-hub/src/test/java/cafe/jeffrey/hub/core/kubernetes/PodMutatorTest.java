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

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PodMutatorTest {

    private static final String INIT_IMAGE = "debian:stable-slim";
    private static final String DEFAULT_PVC = "jeffrey-pvc";

    private final PodMutator mutator = new PodMutator(
            new PodMutator.WebhookInjectionSettings(INIT_IMAGE, DEFAULT_PVC));

    private static Pod pod(Map<String, String> annotations, Map<String, String> labels, String... containerNames) {
        List<Container> containers = new ArrayList<>();
        for (String containerName : containerNames) {
            containers.add(new ContainerBuilder()
                    .withName(containerName)
                    .withImage("app:latest")
                    .build());
        }

        return new PodBuilder()
                .withNewMetadata()
                .withName("my-service-abc")
                .withAnnotations(annotations)
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withContainers(containers)
                .endSpec()
                .build();
    }

    private static Map<String, String> enabledAnnotation() {
        return Map.of(KubernetesConventions.ANNOTATION_ENABLED, "true");
    }

    @Nested
    class Applicability {

        @Test
        void podWithoutAnnotation_notMutated() {
            Pod pod = pod(Map.of(), Map.of(), "app");

            assertTrue(mutator.mutate(pod).isEmpty());
        }

        @Test
        void podWithFalseAnnotation_notMutated() {
            Pod pod = pod(Map.of(KubernetesConventions.ANNOTATION_ENABLED, "false"), Map.of(), "app");

            assertTrue(mutator.mutate(pod).isEmpty());
        }

        @Test
        void podWithoutContainers_notMutated() {
            Pod pod = pod(enabledAnnotation(), Map.of());

            assertTrue(mutator.mutate(pod).isEmpty());
        }

        @Test
        void alreadyInjectedPod_notMutatedTwice() {
            Pod pod = pod(enabledAnnotation(), Map.of(), "app");
            Pod mutated = mutator.mutate(pod).orElseThrow();

            assertTrue(mutator.mutate(mutated).isEmpty());
        }
    }

    @Nested
    class Injection {

        @Test
        void addsVolumesInitContainerAndInstrumentsFirstContainer() {
            Pod pod = pod(enabledAnnotation(), Map.of("app", "my-service"), "app");

            Pod mutated = mutator.mutate(pod).orElseThrow();

            List<Volume> volumes = mutated.getSpec().getVolumes();
            assertEquals(2, volumes.size());
            assertEquals(DEFAULT_PVC, volumes.getFirst().getPersistentVolumeClaim().getClaimName());
            assertNotNull(volumes.getLast().getEmptyDir());

            Container init = mutated.getSpec().getInitContainers().getFirst();
            assertEquals(PodMutator.INIT_CONTAINER_NAME, init.getName());
            assertEquals(INIT_IMAGE, init.getImage());
            assertEquals(2, init.getVolumeMounts().size());
            String script = init.getCommand().getLast();
            assertTrue(script.contains("provisioner-$ARCH"), "Init script must run the arch-suffixed provisioner");
            assertFalse(script.contains("__JEFFREY_CONFIG_FILE__"), "Config file token must be substituted");

            // Project name derived from the app label; instance from the downward API
            Map<String, String> initEnv = envByName(init.getEnv());
            assertEquals("my-service", initEnv.get("JEFFREY_PROJECT"));
            EnvVar instanceEnv = init.getEnv().stream()
                    .filter(e -> "JEFFREY_INSTANCE".equals(e.getName())).findFirst().orElseThrow();
            assertEquals("metadata.name", instanceEnv.getValueFrom().getFieldRef().getFieldPath());

            Container app = mutated.getSpec().getContainers().getFirst();
            assertEquals(2, app.getVolumeMounts().size());
            Map<String, String> appEnv = envByName(app.getEnv());
            assertEquals("@" + PodMutator.ARG_FILE_PATH, appEnv.get(PodMutator.JDK_JAVA_OPTIONS_ENV));
        }

        @Test
        void annotations_overrideProjectWorkspacePvcAndContainer() {
            Pod pod = pod(Map.of(
                    KubernetesConventions.ANNOTATION_ENABLED, "true",
                    KubernetesConventions.ANNOTATION_PROJECT, "checkout",
                    KubernetesConventions.ANNOTATION_WORKSPACE, "production",
                    KubernetesConventions.ANNOTATION_PVC, "custom-pvc",
                    KubernetesConventions.ANNOTATION_CONTAINER, "second"),
                    Map.of(), "first", "second");

            Pod mutated = mutator.mutate(pod).orElseThrow();

            assertEquals("custom-pvc",
                    mutated.getSpec().getVolumes().getFirst().getPersistentVolumeClaim().getClaimName());

            Map<String, String> initEnv = envByName(mutated.getSpec().getInitContainers().getFirst().getEnv());
            assertEquals("checkout", initEnv.get("JEFFREY_PROJECT"));
            assertEquals("production", initEnv.get("JEFFREY_WORKSPACE"));

            Container first = mutated.getSpec().getContainers().getFirst();
            Container second = mutated.getSpec().getContainers().getLast();
            assertNull(first.getEnv() == null ? null : envByName(first.getEnv()).get(PodMutator.JDK_JAVA_OPTIONS_ENV));
            assertEquals("@" + PodMutator.ARG_FILE_PATH, envByName(second.getEnv()).get(PodMutator.JDK_JAVA_OPTIONS_ENV));
        }

        @Test
        void existingJdkJavaOptions_argfileAppended() {
            Pod pod = pod(enabledAnnotation(), Map.of(), "app");
            pod.getSpec().getContainers().getFirst().setEnv(new ArrayList<>(List.of(
                    new EnvVar(PodMutator.JDK_JAVA_OPTIONS_ENV, "-Xmx2g", null))));

            Pod mutated = mutator.mutate(pod).orElseThrow();

            Map<String, String> appEnv = envByName(mutated.getSpec().getContainers().getFirst().getEnv());
            assertEquals("-Xmx2g @" + PodMutator.ARG_FILE_PATH, appEnv.get(PodMutator.JDK_JAVA_OPTIONS_ENV));
        }

        @Test
        void projectFallsBackToPodName_whenNoAnnotationAndNoAppLabel() {
            Pod pod = pod(enabledAnnotation(), Map.of(), "app");

            Pod mutated = mutator.mutate(pod).orElseThrow();

            Map<String, String> initEnv = envByName(mutated.getSpec().getInitContainers().getFirst().getEnv());
            assertEquals("my-service-abc", initEnv.get("JEFFREY_PROJECT"));
        }
    }

    private static Map<String, String> envByName(List<EnvVar> env) {
        return env.stream()
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(EnvVar::getName, EnvVar::getValue));
    }
}
