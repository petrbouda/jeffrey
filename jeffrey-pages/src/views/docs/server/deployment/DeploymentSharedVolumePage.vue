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
  { id: 'why-shared', text: 'Why a Shared Volume?', level: 2 },
  { id: 'pvc-contract', text: 'PVC Contract', level: 2 },
  { id: 'pvc-template', text: 'PVC Template', level: 2 },
  { id: 'hostpath-fallback', text: 'OrbStack / minikube Fallback', level: 2 },
  { id: 'copy-libs', text: 'copy-libs Properties', level: 2 },
  { id: 'on-disk-layout', text: 'On-Disk Layout', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const pvcTemplate = `apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: {{ .Values.sharedVolume.claimName }}     # default: jeffrey-pvc
spec:
  # \`| quote\` is critical: an empty value MUST render as a literal \`""\` in YAML,
  # otherwise the field is null and the K8s default-storage-class admission
  # controller auto-fills it (breaking static binding to the hostPath PV).
  storageClassName: {{ .Values.sharedVolume.storageClassName | quote }}
  accessModes:
    {{- toYaml .Values.sharedVolume.accessModes | nindent 4 }}   # ReadWriteMany
  {{- if .Values.sharedVolume.hostPath.create }}
  volumeName: {{ .Values.sharedVolume.claimName }}
  {{- end }}
  resources:
    requests:
      storage: {{ .Values.sharedVolume.capacity }}                # default: 40Gi`;

const valuesBlock = `sharedVolume:
  claimName: jeffrey-pvc
  mountPath: /mnt/jeffrey
  create: true
  storageClassName: nfs                # production
  accessModes:
    - ReadWriteMany
  capacity: 40Gi
  # Static hostPath PV — only useful on minikube/kind/orbstack where there is
  # no dynamic RWX provisioner. Leave create=false on real clusters and let the
  # StorageClass dynamically provision the underlying volume.
  hostPath:
    create: false
    path: /tmp/jeffrey-data`;

const pvTemplate = `apiVersion: v1
kind: PersistentVolume
metadata:
  name: {{ .Values.sharedVolume.claimName }}     # jeffrey-pvc
  labels:
    type: local
spec:
  storageClassName: {{ .Values.sharedVolume.storageClassName | quote }}   # ""
  capacity:
    storage: {{ .Values.sharedVolume.capacity }}
  accessModes:
    {{- toYaml .Values.sharedVolume.accessModes | nindent 4 }}            # ReadWriteMany
  hostPath:
    path: {{ .Values.sharedVolume.hostPath.path | quote }}                # /tmp/jeffrey-data`;

const copyLibsProperties = `# helm/jeffrey-server/application.properties
jeffrey.server.copy-libs.enabled=true
jeffrey.server.home.dir=\${JEFFREY_HOME}
spring.profiles.include=trace-file-log`;

const onDiskTree = `/mnt/jeffrey/                                # JEFFREY_HOME (from sharedVolume.mountPath)
└── libs/
    └── current/                             # symlink → versioned bundle
        ├── jeffrey-cli-amd64                # per-arch CLI binary
        ├── jeffrey-cli-aarch64
        ├── jeffrey-agent.jar                # JVMTI agent
        └── libasyncProfiler.so              # async-profiler shared library`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Shared Volume"
      icon="bi bi-hdd-stack"
    />

    <div class="docs-content">
      <p>
        Jeffrey Server and the monitored applications coordinate through a single
        <strong>ReadWriteMany</strong> PersistentVolumeClaim. Jeffrey Server writes the
        CLI bundle into <code>${JEFFREY_HOME}/libs/current/</code>; every monitored
        pod mounts the same PVC and reads the bundle when its
        <router-link to="/docs/server/deployment/jeffrey-jib">JIB-wrapped entrypoint</router-link>
        runs <code>jeffrey-cli init</code>.
      </p>

      <h2 id="why-shared">Why a Shared Volume?</h2>
      <p>
        The application image deliberately contains <strong>no</strong> CLI binary, agent
        JAR, or profiler library — those are owned by Jeffrey Server and delivered at
        runtime. Upgrading Jeffrey Server upgrades the agent / profiler for every
        monitored pod in the namespace; no rebuild of your application image is required.
      </p>

      <DocsCallout type="info">
        <strong><code>ReadWriteMany</code> is mandatory.</strong> Multiple pods read the
        bundle concurrently while Jeffrey Server writes new versions to it. RWO won't work
        beyond a single application replica on the same node. In production: NFS, EFS,
        Azure Files, or any other RWX-capable <code>StorageClass</code>. In dev: a
        <code>hostPath</code> PV (see below).
      </DocsCallout>

      <h2 id="pvc-contract">PVC Contract</h2>
      <p>
        Defaults from
        <a href="https://github.com/petrbouda/jeffrey-testapp/blob/main/helm/jeffrey-server/values.yaml" target="_blank" rel="noopener">
          <code>helm/jeffrey-server/values.yaml</code></a>:
      </p>

      <DocsCodeBlock
        language="yaml"
        :code="valuesBlock"
      />

      <p>
        The same <code>claimName</code> + <code>mountPath</code> are referenced by both
        testapp charts (<code>helm/jeffrey-testapp-server/values.yaml</code> and
        <code>helm/jeffrey-testapp-client/values.yaml</code>). All three pods need to see
        the same bytes at the same path.
      </p>

      <table>
        <thead>
          <tr>
            <th>Field</th>
            <th>Default</th>
            <th>Notes</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>claimName</code></td>
            <td><code>jeffrey-pvc</code></td>
            <td>Must match across all three charts.</td>
          </tr>
          <tr>
            <td><code>mountPath</code></td>
            <td><code>/mnt/jeffrey</code></td>
            <td>The pod-side path injected as <code>JEFFREY_HOME</code>.</td>
          </tr>
          <tr>
            <td><code>accessModes</code></td>
            <td><code>[ReadWriteMany]</code></td>
            <td>Required — many pods read concurrently.</td>
          </tr>
          <tr>
            <td><code>capacity</code></td>
            <td><code>40Gi</code></td>
            <td>Stay below the underlying provisioner's backing PVC size — provisioners report <code>insufficient available space</code> when the request equals the backing size.</td>
          </tr>
          <tr>
            <td><code>storageClassName</code></td>
            <td><code>nfs</code></td>
            <td>Any RWX-capable class. Use <code>""</code> when statically binding to a hostPath PV (see fallback section).</td>
          </tr>
          <tr>
            <td><code>create</code></td>
            <td><code>true</code></td>
            <td>Set <code>false</code> to bring your own pre-existing claim.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="pvc-template">PVC Template</h2>
      <p>From
        <a href="https://github.com/petrbouda/jeffrey-testapp/blob/main/helm/jeffrey-server/templates/persistent-volume-claim.yaml" target="_blank" rel="noopener">
          <code>helm/jeffrey-server/templates/persistent-volume-claim.yaml</code></a>:
      </p>

      <DocsCodeBlock
        language="yaml"
        :code="pvcTemplate"
      />

      <DocsCallout type="warning">
        <strong>The <code>| quote</code> filter on <code>storageClassName</code> is not
        cosmetic.</strong> An empty value MUST render as the literal string <code>""</code>
        in the YAML output, not as <code>null</code>. Without the filter, the
        default-storage-class admission controller silently rewrites the field to the
        cluster default, breaks the static PV ↔ PVC binding, and your testapp pods sit in
        <code>Pending</code> indefinitely because the PVC never binds to the hostPath PV.
      </DocsCallout>

      <h2 id="hostpath-fallback">OrbStack / minikube Fallback</h2>
      <p>
        Single-node local clusters typically don't ship an RWX-capable provisioner.
        The chart provides a static <code>hostPath</code> PV that you opt into by setting
        <code>storageClassName=""</code> and <code>hostPath.create=true</code> when
        installing the chart.
      </p>

      <p>From
        <a href="https://github.com/petrbouda/jeffrey-testapp/blob/main/helm/jeffrey-server/templates/persistent-volume.yaml" target="_blank" rel="noopener">
          <code>helm/jeffrey-server/templates/persistent-volume.yaml</code></a>:
      </p>

      <DocsCodeBlock
        language="yaml"
        :code="pvTemplate"
      />

      <p>Pass the override flags inline at install time:</p>

      <DocsCodeBlock
        language="bash"
        code='helm upgrade --install jeffrey-server helm/jeffrey-server \
  --namespace jeffrey-testapp --create-namespace \
  --set sharedVolume.storageClassName="" \
  --set sharedVolume.hostPath.create=true'
      />

      <DocsCallout type="tip">
        <strong>Cleanup gotcha.</strong> Statically-defined hostPath PVs default to
        <code>reclaimPolicy=Retain</code>, so <code>helm uninstall</code> tears down the
        PV resource but leaves the contents on the node (e.g. <code>/tmp/jeffrey-data</code>
        on OrbStack). The next install would inherit a stale CLI bundle — wipe the host
        directory yourself before re-installing on dev clusters.
      </DocsCallout>

      <h2 id="copy-libs">copy-libs Properties</h2>
      <p>
        Jeffrey Server's <code>copy-libs</code> feature publishes the CLI bundle into the
        shared volume after the JVM has started. Activate it with three lines in
        <a href="https://github.com/petrbouda/jeffrey-testapp/blob/main/helm/jeffrey-server/application.properties" target="_blank" rel="noopener">
          <code>helm/jeffrey-server/application.properties</code></a>:
      </p>

      <DocsCodeBlock
        language="properties"
        :code="copyLibsProperties"
      />

      <p>The chart's Deployment injects <code>JEFFREY_HOME</code> from
        <code>sharedVolume.mountPath</code>:</p>

      <DocsCodeBlock
        language="yaml"
        code="env:
  - name: SPRING_CONFIG_LOCATION
    value: /jeffrey/application.properties
  - name: JEFFREY_HOME
    value: {{ .Values.sharedVolume.mountPath | quote }}     # /mnt/jeffrey
  - name: JEFFREY_ENABLED
    value: {{ .Values.selfProfile.enabled | quote }}        # true"
      />

      <p>Source-vs-target conventions (from the in-image <code>jeffrey-libs/</code> bundle
        to the shared volume), with all defaults documented on the
        <router-link to="/docs/server/configuration">Configuration</router-link> page:</p>

      <ul>
        <li><code>jeffrey.copy-libs.source</code> — defaults to <code>/jeffrey-libs</code>; the in-image bundle.</li>
        <li><code>jeffrey.copy-libs.target</code> — defaults to <code>${jeffrey.home.dir}/libs</code>; on the shared volume.</li>
        <li><code>jeffrey.copy-libs.max-kept-versions</code> — defaults to <code>10</code>; older bundles are pruned automatically.</li>
      </ul>

      <h2 id="on-disk-layout">On-Disk Layout</h2>
      <p>What ends up on the shared volume after Jeffrey Server has started successfully:</p>

      <DocsCodeBlock
        language="text"
        :code="onDiskTree"
      />

      <p>
        The wrapped entrypoint resolves <code>jeffrey-cli-&lt;arch&gt;</code> against
        <code>uname -m</code> at container start; the testapp Helm charts assume
        <code>amd64</code> on x86_64 nodes and <code>aarch64</code> on ARM64 (no operator
        intervention needed).
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
