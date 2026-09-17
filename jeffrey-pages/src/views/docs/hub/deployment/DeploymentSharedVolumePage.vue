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
  { id: 'hub-home', text: 'Pointing Jeffrey Hub at the Volume', level: 2 },
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

const onDiskTree = `/mnt/jeffrey/                                # JEFFREY_HOME (from sharedVolume.mountPath)
├── jeffrey-data.db                          # Jeffrey Hub's own database
├── temp/
└── workspaces/
    └── <workspace-ref-id>/
        ├── .pending/                        # provisioner-declared work for the Hub
        ├── .settings/                       # Hub-pushed profiler settings
        └── <project-name>/
            └── <instance-id>/
                └── <session-id>/            # the JFR recordings land here`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Shared Volume"
      icon="bi bi-hdd-stack"
    />

    <div class="docs-content">
      <p>
        Jeffrey Hub and the monitored applications coordinate through a single
        <strong>ReadWriteMany</strong> PersistentVolumeClaim. Every monitored pod writes its
        recordings into <code>${JEFFREY_HOME}/workspaces/</code>; Jeffrey Hub mounts the same PVC
        and reconciles what it finds there.
      </p>

      <h2 id="why-shared">Why a Shared Volume?</h2>
      <p>
        It is the recording handoff, and nothing else. The provisioner never talks to Jeffrey Hub
        directly — it writes files, and the Hub reads them. Binaries do not travel this way: each
        application image carries its own provisioner and async-profiler, baked in at build time by
        the <router-link to="/docs/hub/deployment/jeffrey-jib">JIB extension</router-link>, so a pod
        that starts before Jeffrey Hub still profiles from its first second.
      </p>

      <DocsCallout type="info">
        <strong><code>ReadWriteMany</code> is mandatory.</strong> Every monitored pod writes to the
        volume while Jeffrey Hub reads and compresses what lands there. RWO won't work
        beyond a single application replica on the same node. In production: NFS, EFS,
        Azure Files, or any other RWX-capable <code>StorageClass</code>. In dev: a
        <code>hostPath</code> PV (see below). File sizes are read off the directory listing,
        so on a mount that caches attributes — an SMB share such as Azure Files, NFS with a
        long <code>actimeo</code> — a session that is still recording can list at the size the
        cache last saw; a low <code>actimeo</code> on the hub's mount keeps the figures current.
      </DocsCallout>

      <h2 id="pvc-contract">PVC Contract</h2>
      <p>
        Defaults from
        <a href="https://github.com/petrbouda/jeffrey-testapp/blob/main/helm/jeffrey-hub/values.yaml" target="_blank" rel="noopener">
          <code>helm/jeffrey-hub/values.yaml</code></a>:
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
        <a href="https://github.com/petrbouda/jeffrey-testapp/blob/main/helm/jeffrey-hub/templates/persistent-volume-claim.yaml" target="_blank" rel="noopener">
          <code>helm/jeffrey-hub/templates/persistent-volume-claim.yaml</code></a>:
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
        <a href="https://github.com/petrbouda/jeffrey-testapp/blob/main/helm/jeffrey-hub/templates/persistent-volume.yaml" target="_blank" rel="noopener">
          <code>helm/jeffrey-hub/templates/persistent-volume.yaml</code></a>:
      </p>

      <DocsCodeBlock
        language="yaml"
        :code="pvTemplate"
      />

      <p>Pass the override flags inline at install time:</p>

      <DocsCodeBlock
        language="bash"
        code='helm upgrade --install jeffrey-hub helm/jeffrey-hub \
  --namespace jeffrey-testapp --create-namespace \
  --set sharedVolume.storageClassName="" \
  --set sharedVolume.hostPath.create=true'
      />

      <DocsCallout type="tip">
        <strong>Cleanup gotcha.</strong> Statically-defined hostPath PVs default to
        <code>reclaimPolicy=Retain</code>, so <code>helm uninstall</code> tears down the
        PV resource but leaves the contents on the node (e.g. <code>/tmp/jeffrey-data</code>
        on OrbStack). The next install would inherit stale recordings and session directories — wipe the host
        directory yourself before re-installing on dev clusters.
      </DocsCallout>

      <h2 id="hub-home">Pointing Jeffrey Hub at the Volume</h2>
      <p>The chart's Deployment injects <code>JEFFREY_HOME</code> from
        <code>sharedVolume.mountPath</code>, so Jeffrey Hub and the applications agree on the path
        without either side hard-coding it. One line of <code>application.properties</code> then
        points Jeffrey Hub's home directory at it:</p>

      <DocsCodeBlock
        language="properties"
        code="jeffrey.hub.home.dir=${JEFFREY_HOME}"
      />

      <h2 id="on-disk-layout">On-Disk Layout</h2>
      <p>What the two sides write to the shared volume:</p>

      <DocsCodeBlock
        language="text"
        :code="onDiskTree"
      />

      <p>
        The layout is a contract between the provisioner that writes it and the Hub that reads it;
        both resolve every name from a single shared class, so neither side can drift. See
        <router-link to="/docs/provisioner/directory-structure">Directory Structure</router-link>
        for what each marker file carries.
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
