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
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Injects Jeffrey profiling into pods annotated {@code jeffrey.cafe/enabled=true}.
 * This replaces the jeffrey-jib build-time entrypoint rewrite: any image can be
 * profiled by annotating its pod — no build plugin, no baked entrypoint.
 *
 * <p>The mutation adds:</p>
 * <ul>
 *   <li>a PVC volume with the shared Jeffrey home (hub-populated {@code libs/current}
 *       binaries + the {@code workspaces/} tree) and an emptyDir for generated files,</li>
 *   <li>an init container that writes a minimal HOCON config and runs the
 *       provisioner binary from the shared volume, producing the JVM argfile,</li>
 *   <li>on the target container: both volume mounts and
 *       {@code JDK_JAVA_OPTIONS=@<argfile>} — the {@code java} launcher reads
 *       argument files from this variable, so the original entrypoint stays
 *       untouched.</li>
 * </ul>
 *
 * <p>Annotations: {@code jeffrey.cafe/project} (defaults to the {@code app}
 * label, then the pod name), {@code jeffrey.cafe/workspace} (defaults to the
 * hub's default workspace), {@code jeffrey.cafe/pvc} (defaults to the configured
 * claim name), {@code jeffrey.cafe/container} (defaults to the first container).</p>
 */
public class PodMutator {

    private static final Logger LOG = LoggerFactory.getLogger(PodMutator.class);

    /**
     * Injection settings resolved from hub configuration.
     *
     * @param initImage     image of the injected init container (needs only a POSIX shell
     *                      and glibc — the provisioner binary comes from the shared volume)
     * @param pvcClaimName  default PVC holding the shared Jeffrey home
     */
    public record WebhookInjectionSettings(String initImage, String pvcClaimName) {

        public WebhookInjectionSettings {
            if (initImage == null || initImage.isBlank()) {
                throw new IllegalArgumentException("initImage must not be blank");
            }
            if (pvcClaimName == null || pvcClaimName.isBlank()) {
                throw new IllegalArgumentException("pvcClaimName must not be blank");
            }
        }
    }

    static final String INIT_CONTAINER_NAME = "jeffrey-provisioner";
    static final String SHARED_VOLUME_NAME = "jeffrey-home";
    static final String WORK_VOLUME_NAME = "jeffrey-work";
    static final String SHARED_MOUNT_PATH = "/jeffrey-shared";
    static final String WORK_MOUNT_PATH = "/jeffrey-work";
    static final String ARG_FILE_PATH = WORK_MOUNT_PATH + "/jvm.args";
    static final String JDK_JAVA_OPTIONS_ENV = "JDK_JAVA_OPTIONS";

    private static final String CONFIG_FILE_PATH = WORK_MOUNT_PATH + "/jeffrey-base.conf";
    private static final String APP_LABEL = "app";

    private static final String ENV_JEFFREY_HOME = "JEFFREY_HOME";
    private static final String ENV_JEFFREY_PROJECT = "JEFFREY_PROJECT";
    private static final String ENV_JEFFREY_WORKSPACE = "JEFFREY_WORKSPACE";
    private static final String ENV_JEFFREY_INSTANCE = "JEFFREY_INSTANCE";
    private static final String ENV_JEFFREY_ARG_FILE = "JEFFREY_ARG_FILE";

    private static final String POD_NAME_FIELD_PATH = "metadata.name";

    /**
     * Writes the HOCON config from the injected env vars, resolves the architecture
     * suffix, and runs the provisioner from the shared volume. {@code set -e} makes
     * any failure fail the init container visibly (failurePolicy on the webhook
     * side stays Ignore, so webhook outages never block pods). The config-file path
     * is spliced via token replacement because the script's own {@code printf}
     * format strings use {@code %s}.
     */
    private static final String CONFIG_FILE_TOKEN = "__JEFFREY_CONFIG_FILE__";

    private static final String INIT_SCRIPT = """
            set -e
            ARCH=$(uname -m)
            case "$ARCH" in
              x86_64) ARCH=amd64 ;;
              aarch64) ARCH=arm64 ;;
            esac
            {
              printf 'jeffrey-home = "%s"\\n' "$JEFFREY_HOME"
              printf 'arg-file = "%s"\\n' "$JEFFREY_ARG_FILE"
              printf 'project {\\n'
              printf '    workspace-ref-id = "%s"\\n' "$JEFFREY_WORKSPACE"
              printf '    name = "%s"\\n' "$JEFFREY_PROJECT"
              printf '    instance-name = "%s"\\n' "$JEFFREY_INSTANCE"
              printf '}\\n'
            } > __JEFFREY_CONFIG_FILE__
            "$JEFFREY_HOME/libs/current/provisioner-$ARCH" init --base-config __JEFFREY_CONFIG_FILE__
            """.replace(CONFIG_FILE_TOKEN, CONFIG_FILE_PATH);

    private final WebhookInjectionSettings settings;

    public PodMutator(WebhookInjectionSettings settings) {
        this.settings = settings;
    }

    /**
     * Returns the mutated pod, or empty when the pod is not annotated for
     * injection or is already injected.
     */
    public Optional<Pod> mutate(Pod pod) {
        if (!injectionRequested(pod)) {
            return Optional.empty();
        }
        if (alreadyInjected(pod)) {
            LOG.debug("Pod already carries the Jeffrey init container, skipping: pod={}", podName(pod));
            return Optional.empty();
        }
        if (pod.getSpec() == null || pod.getSpec().getContainers() == null || pod.getSpec().getContainers().isEmpty()) {
            LOG.warn("Pod has no containers to instrument, skipping: pod={}", podName(pod));
            return Optional.empty();
        }

        String project = resolveProjectName(pod);
        String workspace = annotationOrDefault(pod, KubernetesConventions.ANNOTATION_WORKSPACE, "");
        String pvcClaimName = annotationOrDefault(pod, KubernetesConventions.ANNOTATION_PVC, settings.pvcClaimName());
        String targetContainer = annotationOrDefault(pod, KubernetesConventions.ANNOTATION_CONTAINER, null);

        Pod mutated = new PodBuilder(pod)
                .editSpec()
                .addToVolumes(new VolumeBuilder()
                        .withName(SHARED_VOLUME_NAME)
                        .withNewPersistentVolumeClaim()
                        .withClaimName(pvcClaimName)
                        .endPersistentVolumeClaim()
                        .build())
                .addToVolumes(new VolumeBuilder()
                        .withName(WORK_VOLUME_NAME)
                        .withNewEmptyDir()
                        .endEmptyDir()
                        .build())
                .addToInitContainers(provisionerInitContainer(project, workspace))
                .endSpec()
                .build();

        instrumentTargetContainer(mutated, targetContainer);

        LOG.info("Injected Jeffrey profiling into pod: pod={} project={} workspace={} pvc={}",
                podName(pod), project, workspace.isBlank() ? "<default>" : workspace, pvcClaimName);
        return Optional.of(mutated);
    }

    private Container provisionerInitContainer(String project, String workspace) {
        return new ContainerBuilder()
                .withName(INIT_CONTAINER_NAME)
                .withImage(settings.initImage())
                .withCommand("/bin/sh", "-c", INIT_SCRIPT)
                .withEnv(
                        envVar(ENV_JEFFREY_HOME, SHARED_MOUNT_PATH),
                        envVar(ENV_JEFFREY_PROJECT, project),
                        envVar(ENV_JEFFREY_WORKSPACE, workspace),
                        envVar(ENV_JEFFREY_ARG_FILE, ARG_FILE_PATH),
                        podNameEnvVar(ENV_JEFFREY_INSTANCE))
                .withVolumeMounts(sharedMount(), workMount())
                .build();
    }

    private static void instrumentTargetContainer(Pod mutated, String targetContainerName) {
        List<Container> containers = mutated.getSpec().getContainers();
        Container target = containers.getFirst();
        if (targetContainerName != null && !targetContainerName.isBlank()) {
            target = containers.stream()
                    .filter(c -> targetContainerName.equals(c.getName()))
                    .findFirst()
                    .orElse(target);
        }

        List<VolumeMount> mounts = target.getVolumeMounts() == null
                ? new ArrayList<>()
                : new ArrayList<>(target.getVolumeMounts());
        mounts.add(sharedMount());
        mounts.add(workMount());
        target.setVolumeMounts(mounts);

        List<EnvVar> env = target.getEnv() == null ? new ArrayList<>() : new ArrayList<>(target.getEnv());
        EnvVar existing = env.stream()
                .filter(e -> JDK_JAVA_OPTIONS_ENV.equals(e.getName()))
                .findFirst()
                .orElse(null);
        String argFileReference = "@" + ARG_FILE_PATH;
        if (existing == null) {
            env.add(envVar(JDK_JAVA_OPTIONS_ENV, argFileReference));
        } else if (existing.getValue() != null && !existing.getValue().contains(argFileReference)) {
            existing.setValue(existing.getValue() + " " + argFileReference);
        }
        target.setEnv(env);
    }

    private static boolean injectionRequested(Pod pod) {
        Map<String, String> annotations = pod.getMetadata() != null ? pod.getMetadata().getAnnotations() : null;
        if (annotations == null) {
            return false;
        }
        return KubernetesConventions.VALUE_TRUE.equals(annotations.get(KubernetesConventions.ANNOTATION_ENABLED));
    }

    private static boolean alreadyInjected(Pod pod) {
        if (pod.getSpec() == null || pod.getSpec().getInitContainers() == null) {
            return false;
        }
        return pod.getSpec().getInitContainers().stream()
                .anyMatch(c -> INIT_CONTAINER_NAME.equals(c.getName()));
    }

    private static String resolveProjectName(Pod pod) {
        String annotated = annotationOrDefault(pod, KubernetesConventions.ANNOTATION_PROJECT, null);
        if (annotated != null && !annotated.isBlank()) {
            return annotated;
        }
        Map<String, String> labels = pod.getMetadata().getLabels();
        if (labels != null && labels.get(APP_LABEL) != null && !labels.get(APP_LABEL).isBlank()) {
            return labels.get(APP_LABEL);
        }
        return podName(pod);
    }

    private static String annotationOrDefault(Pod pod, String annotation, String defaultValue) {
        Map<String, String> annotations = pod.getMetadata().getAnnotations();
        if (annotations == null) {
            return defaultValue;
        }
        String value = annotations.get(annotation);
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    private static VolumeMount sharedMount() {
        return new VolumeMountBuilder().withName(SHARED_VOLUME_NAME).withMountPath(SHARED_MOUNT_PATH).build();
    }

    private static VolumeMount workMount() {
        return new VolumeMountBuilder().withName(WORK_VOLUME_NAME).withMountPath(WORK_MOUNT_PATH).build();
    }

    private static EnvVar envVar(String name, String value) {
        return new EnvVarBuilder().withName(name).withValue(value).build();
    }

    private static EnvVar podNameEnvVar(String name) {
        return new EnvVarBuilder()
                .withName(name)
                .withNewValueFrom()
                .withNewFieldRef()
                .withFieldPath(POD_NAME_FIELD_PATH)
                .endFieldRef()
                .endValueFrom()
                .build();
    }

    private static String podName(Pod pod) {
        if (pod.getMetadata() == null) {
            return "<unknown>";
        }
        String name = pod.getMetadata().getName();
        if (name != null) {
            return name;
        }
        // Pods created by controllers often only carry generateName at admission time
        String generateName = pod.getMetadata().getGenerateName();
        return generateName != null ? generateName + "*" : "<unknown>";
    }
}
