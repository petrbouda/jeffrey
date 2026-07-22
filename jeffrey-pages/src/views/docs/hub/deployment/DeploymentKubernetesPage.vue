<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
-->

<script setup lang="ts">
import { onMounted } from 'vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'pod-discovery', text: 'Pod Discovery (Informer)', level: 2 },
  { id: 'admission-webhook', text: 'Admission Webhook (Injection)', level: 2 },
  { id: 'annotation-reference', text: 'Label & Annotation Reference', level: 2 },
  { id: 'manifests', text: 'Kubernetes Manifests', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const hubProperties = `# Informer-based discovery
jeffrey.hub.kubernetes.enabled=true
jeffrey.hub.kubernetes.namespace=                  # empty = all namespaces
jeffrey.hub.kubernetes.auto-create-workspaces=true

# Mutating admission webhook (independent of the informer)
jeffrey.hub.kubernetes.webhook.enabled=true
jeffrey.hub.kubernetes.webhook.init-image=debian:stable-slim
jeffrey.hub.kubernetes.webhook.pvc-claim-name=jeffrey-pvc`;

const annotatedDeployment = `apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-service
spec:
  template:
    metadata:
      labels:
        app: my-service
        jeffrey.cafe/enabled: "true"          # informer tracks this pod
        jeffrey.cafe/workspace: production    # workspace auto-created on first sight
      annotations:
        jeffrey.cafe/enabled: "true"          # webhook injects profiling
        jeffrey.cafe/workspace: production
        jeffrey.cafe/project: my-service      # optional (defaults to the app label)
    spec:
      containers:
        - name: app
          image: my-service:1.0.0             # unmodified image - no jeffrey-jib needed`;

const rbacManifest = `apiVersion: v1
kind: ServiceAccount
metadata:
  name: jeffrey-hub
  namespace: jeffrey
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: jeffrey-hub-pod-reader
rules:
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "list", "watch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: jeffrey-hub-pod-reader
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: jeffrey-hub-pod-reader
subjects:
  - kind: ServiceAccount
    name: jeffrey-hub
    namespace: jeffrey`;

const webhookManifest = `apiVersion: admissionregistration.k8s.io/v1
kind: MutatingWebhookConfiguration
metadata:
  name: jeffrey-injector
  annotations:
    # cert-manager injects the CA bundle of the certificate that terminates
    # TLS in front of the hub (the API server requires HTTPS webhooks)
    cert-manager.io/inject-ca-from: jeffrey/jeffrey-hub-webhook-cert
webhooks:
  - name: inject.jeffrey.cafe
    admissionReviewVersions: ["v1"]
    sideEffects: None
    # Ignore = a webhook outage never blocks pod creation. Combined with the
    # hub's fail-open response, Jeffrey can never break a deployment.
    failurePolicy: Ignore
    clientConfig:
      service:
        name: jeffrey-hub
        namespace: jeffrey
        port: 443
        path: /api/kubernetes/webhook/mutate
    rules:
      - apiGroups: [""]
        apiVersions: ["v1"]
        operations: ["CREATE"]
        resources: ["pods"]
    # Only pods that opted in - keeps the webhook off the hot path of
    # every other pod in the cluster
    objectSelector:
      matchLabels:
        jeffrey.cafe/enabled: "true"`;
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Kubernetes Integration"
        icon="bi bi-boxes"
      />

      <div class="docs-content">
        <h2 id="overview">Overview</h2>
        <p>
          Jeffrey Hub integrates with Kubernetes through two optional, independent features.
          Both keep the shared-filesystem pipeline as the source of truth — they remove manual
          steps and inference, not the architecture:
        </p>
        <ul>
          <li>
            <strong>Pod discovery (informer)</strong> — the hub watches pods labeled
            <code>jeffrey.cafe/enabled=true</code>. A new pod auto-creates its workspace, and a
            terminated pod finishes its sessions immediately instead of waiting for heartbeat
            staleness.
          </li>
          <li>
            <strong>Admission webhook (injection)</strong> — pods annotated
            <code>jeffrey.cafe/enabled=true</code> get profiling injected at admission time:
            an init container runs the provisioner and the application container starts with
            <code>JDK_JAVA_OPTIONS=@&lt;argfile&gt;</code>. Any existing image works — no
            <router-link to="/docs/hub/deployment/jeffrey-jib">jeffrey-jib</router-link> build
            step, no entrypoint rewrite.
          </li>
        </ul>

        <h3>Hub Configuration</h3>
        <DocsCodeBlock language="properties" :code="hubProperties" />

        <h2 id="pod-discovery">Pod Discovery (Informer)</h2>
        <p>
          With <code>jeffrey.hub.kubernetes.enabled=true</code> the hub runs a shared informer
          over pods with the <code>jeffrey.cafe/enabled=true</code> label (all namespaces by
          default, scope with <code>jeffrey.hub.kubernetes.namespace</code>). The client uses the
          in-cluster service account, or the local kubeconfig when the hub runs outside a cluster.
        </p>
        <ul>
          <li>
            <strong>Pod appears:</strong> the workspace named by the
            <code>jeffrey.cafe/workspace</code> label is created if missing — the application's
            first filesystem events are accepted without creating the workspace in the UI first.
          </li>
          <li>
            <strong>Pod is deleted or reaches <code>Succeeded</code>/<code>Failed</code>:</strong>
            every unfinished session of the instance whose id equals the pod name is finished
            deterministically. The finish timestamp still prefers the agent's clean-exit marker
            and last heartbeat; pod deletion time is only the last-resort fallback.
          </li>
        </ul>

        <DocsCallout type="info">
          <strong>Why the pod name?</strong> Inside a pod, <code>HOSTNAME</code> equals the pod
          name, and the provisioner uses <code>HOSTNAME</code> as the default instance name — so
          the informer can map a pod directly to its Jeffrey instance without any extra wiring.
        </DocsCallout>

        <h2 id="admission-webhook">Admission Webhook (Injection)</h2>
        <p>
          With <code>jeffrey.hub.kubernetes.webhook.enabled=true</code> the hub serves a mutating
          admission endpoint at <code>POST /api/kubernetes/webhook/mutate</code>. For pods
          annotated <code>jeffrey.cafe/enabled=true</code> it injects:
        </p>
        <ul>
          <li>
            a volume with the shared Jeffrey PVC (claim from <code>jeffrey.cafe/pvc</code>, default
            <code>jeffrey.hub.kubernetes.webhook.pvc-claim-name</code>) mounted at
            <code>/jeffrey-shared</code>, and an emptyDir at <code>/jeffrey-work</code>,
          </li>
          <li>
            an init container (<code>jeffrey.hub.kubernetes.webhook.init-image</code>, needs only a
            POSIX shell + glibc) that writes a minimal HOCON config and runs the arch-suffixed
            provisioner binary from the shared volume, producing
            <code>/jeffrey-work/jvm.args</code>,
          </li>
          <li>
            <code>JDK_JAVA_OPTIONS=@/jeffrey-work/jvm.args</code> on the target container — the
            <code>java</code> launcher reads argument files from this variable, so the container's
            original entrypoint and image stay untouched. An existing
            <code>JDK_JAVA_OPTIONS</code> value is preserved and the argfile reference appended.
          </li>
        </ul>

        <DocsCallout type="tip">
          <strong>Fail-open by design:</strong> the endpoint always answers
          <code>allowed=true</code> — an unparseable payload or a mutation error simply produces
          no patch. Together with <code>failurePolicy: Ignore</code>, Jeffrey can never block a
          deployment.
        </DocsCallout>

        <h3>Annotated Application</h3>
        <p>A complete onboarding is one set of labels and annotations on the pod template:</p>
        <DocsCodeBlock language="yaml" :code="annotatedDeployment" />

        <h2 id="annotation-reference">Label &amp; Annotation Reference</h2>
        <table>
          <thead>
            <tr>
              <th>Key</th>
              <th>Used as</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code>jeffrey.cafe/enabled</code></td>
              <td>Label + Annotation</td>
              <td>Label: the informer tracks the pod. Annotation: the webhook injects profiling. Set both for the full experience.</td>
            </tr>
            <tr>
              <td><code>jeffrey.cafe/workspace</code></td>
              <td>Label + Annotation</td>
              <td>Workspace reference id. Label drives informer auto-create; annotation is written into the provisioner config. Empty = the hub's default workspace.</td>
            </tr>
            <tr>
              <td><code>jeffrey.cafe/project</code></td>
              <td>Annotation</td>
              <td>Jeffrey project name. Defaults to the pod's <code>app</code> label, then the pod name.</td>
            </tr>
            <tr>
              <td><code>jeffrey.cafe/pvc</code></td>
              <td>Annotation</td>
              <td>PVC claim with the shared Jeffrey home. Defaults to the hub-configured claim name.</td>
            </tr>
            <tr>
              <td><code>jeffrey.cafe/container</code></td>
              <td>Annotation</td>
              <td>Name of the container to instrument. Defaults to the first container.</td>
            </tr>
          </tbody>
        </table>

        <h2 id="manifests">Kubernetes Manifests</h2>

        <h3>RBAC for the Informer</h3>
        <p>The hub's service account needs read/watch access to pods:</p>
        <DocsCodeBlock language="yaml" :code="rbacManifest" />

        <h3>MutatingWebhookConfiguration</h3>
        <p>
          The API server requires webhooks to be served over HTTPS. Terminate TLS in front of the
          hub (for example with cert-manager and a Service on port 443) and register the endpoint:
        </p>
        <DocsCodeBlock language="yaml" :code="webhookManifest" />

        <DocsCallout type="warning">
          <strong>Shared volume still required:</strong> the webhook injects the shared PVC into
          the application pod — recordings continue to flow to the hub through the shared
          filesystem (see <router-link to="/docs/hub/deployment/shared-volume">Shared Volume</router-link>).
          The Kubernetes integration removes the build-time plugin and the manual workspace step;
          it does not change the transport.
        </DocsCallout>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
