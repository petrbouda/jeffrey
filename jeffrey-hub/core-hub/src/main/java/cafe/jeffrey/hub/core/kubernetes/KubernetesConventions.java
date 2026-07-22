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

/**
 * The label and annotation vocabulary of Jeffrey's Kubernetes integration.
 *
 * <p>Labels drive informer-based discovery ({@code KubernetesDiscovery}): a pod
 * labeled {@code jeffrey.cafe/enabled=true} is tracked, its optional
 * {@code jeffrey.cafe/workspace} label names the target workspace, and the pod
 * name doubles as the Jeffrey instance id (the provisioner falls back to
 * {@code HOSTNAME}, which equals the pod name inside a pod).</p>
 *
 * <p>Annotations drive the mutating admission webhook ({@code PodMutator}) —
 * they configure how profiling is injected into the pod.</p>
 */
public abstract class KubernetesConventions {

    /** Label marking a pod as a Jeffrey-profiled application ({@code "true"} enables tracking) */
    public static final String LABEL_ENABLED = "jeffrey.cafe/enabled";

    /** Label naming the workspace reference id the pod's recordings belong to */
    public static final String LABEL_WORKSPACE = "jeffrey.cafe/workspace";

    /** Annotation enabling webhook injection ({@code "true"} injects profiling) */
    public static final String ANNOTATION_ENABLED = "jeffrey.cafe/enabled";

    /** Annotation naming the workspace reference id (webhook injection) */
    public static final String ANNOTATION_WORKSPACE = "jeffrey.cafe/workspace";

    /** Annotation naming the Jeffrey project (webhook injection; defaults to the app label) */
    public static final String ANNOTATION_PROJECT = "jeffrey.cafe/project";

    /** Annotation naming the PVC with the shared Jeffrey volume (webhook injection) */
    public static final String ANNOTATION_PVC = "jeffrey.cafe/pvc";

    /** Annotation naming the container to instrument (webhook injection; defaults to the first container) */
    public static final String ANNOTATION_CONTAINER = "jeffrey.cafe/container";

    /** The value that switches a label/annotation on */
    public static final String VALUE_TRUE = "true";
}
