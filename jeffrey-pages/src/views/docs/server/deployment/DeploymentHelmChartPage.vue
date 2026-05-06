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
  { id: 'three-charts', text: 'Three Charts at a Glance', level: 2 },
  { id: 'chart-structure', text: 'Chart Structure', level: 2 },
  { id: 'configure-server', text: 'Configuring Jeffrey Server', level: 2 },
  { id: 'configure-app', text: 'Configuring the Monitored Application', level: 2 },
  { id: 'init-container', text: 'Init Container Ordering', level: 2 },
  { id: 'side-by-side', text: 'Side-by-Side: direct vs dom', level: 2 },
  { id: 'installing', text: 'Installing the Stack', level: 2 },
  { id: 'tearing-down', text: 'Tearing Down', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const chartStructure = `helm/
├── jeffrey-server/
│   ├── Chart.yaml
│   ├── values.yaml                   # image tag, sharedVolume, ports, ingress, probes
│   ├── application.properties        # Spring Boot config (copy-libs, home.dir)
│   ├── jeffrey-base.conf             # CLI init config for self-profiling
│   └── templates/
│       ├── deployment.yaml
│       ├── service.yaml              # HTTP 8080 + gRPC 9090
│       ├── ingress.yaml              # optional HTTP / gRPC ingress
│       ├── persistent-volume-claim.yaml
│       ├── persistent-volume.yaml    # hostPath PV (orbstack/minikube)
│       ├── configmap.yaml            # mounts application.properties + jeffrey-base.conf
│       ├── serviceaccount.yaml
│       └── _helpers.tpl
│
├── jeffrey-testapp-server/
│   ├── Chart.yaml
│   ├── values.yaml                   # mode toggle, sharedVolume, jeffrey env, probes
│   ├── jeffrey-base.conf             # CLI init config for the monitored service
│   └── templates/
│       ├── deployment.yaml           # JIB image + wait-for-jeffrey-server init container
│       ├── service.yaml              # HTTP 8080
│       ├── configmap.yaml            # application.properties
│       ├── jeffrey-base-configmap.yaml
│       ├── serviceaccount.yaml
│       └── _helpers.tpl
│
└── jeffrey-testapp-client/
    ├── Chart.yaml
    ├── values.yaml                   # load config, sharedVolume, jeffrey env, TCP probes
    ├── jeffrey-base.conf             # CLI init config for the load generator
    └── templates/                    # same shape as testapp-server (no DB mount)
        ├── deployment.yaml
        ├── service.yaml
        ├── configmap.yaml
        ├── jeffrey-base-configmap.yaml
        ├── serviceaccount.yaml
        └── _helpers.tpl`;

const serverImage = `image:
  repository: petrbouda/jeffrey-server
  # Pin to a specific upstream build. Bump when a newer 0.7.x is pushed
  # (https://hub.docker.com/r/petrbouda/jeffrey-server/tags).
  tag: 0.7.1-b68
  pullPolicy: IfNotPresent

containerPort: 8080
grpcPort: 9090

selfProfile:
  enabled: true   # jeffrey-server self-profiles via the JIB entrypoint`;

const serverService = `apiVersion: v1
kind: Service
metadata:
  name: {{ include "jeffrey-server.fullname" . }}
spec:
  type: ClusterIP
  ports:
    - port: 8080
      targetPort: http
      name: http
    - port: 9090
      targetPort: grpc
      name: grpc
  selector:
    {{- include "jeffrey-server.selectorLabels" . | nindent 4 }}`;

const testappValues = `# Service-mode toggle: "direct" (efficient.mode=true) or "dom" (efficient.mode=false).
mode: dom

applicationProperties: |-
  server.port=8080
  database.file=/var/lib/jeffrey/testapp.db
  database.cleanup-on-shutdown=false
  management.endpoints.web.exposure.include=health
  management.endpoint.health.probes.enabled=true

# Must match jeffrey-server's sharedVolume block exactly.
sharedVolume:
  claimName: jeffrey-pvc
  mountPath: /mnt/jeffrey

jeffrey:
  baseConfigPath: /jeffrey/jeffrey-base.conf
  enabled: true
  serverHost: jeffrey-server:8080`;

const testappEnv = `env:
  # Additional-location MERGES with the image-bundled application.properties
  # (preserving spring.sql.init.* and other defaults). SPRING_CONFIG_LOCATION
  # would REPLACE them, which silently drops \`spring.sql.init.mode=always\`
  # and the init.sql schema bootstrap.
  - name: SPRING_CONFIG_ADDITIONAL_LOCATION
    value: /app/config/application.properties
  - name: JEFFREY_HOME
    value: {{ .Values.sharedVolume.mountPath | quote }}
  - name: JEFFREY_BASE_CONFIG
    value: {{ .Values.jeffrey.baseConfigPath | quote }}
  - name: JEFFREY_ENABLED
    value: {{ .Values.jeffrey.enabled | quote }}
  - name: JEFFREY_TESTAPP_MODE
    value: {{ .Values.mode | quote }}`;

const initContainer = `# Block the main container until jeffrey-server reports ready via its actuator
# readiness endpoint. By the time the readiness probe passes, jeffrey-server's
# copy-libs has published the CLI bundle to the shared volume, so the JIB
# entrypoint finds jeffrey-cli + agent + libasyncProfiler on first start
# instead of fail-opening (no profiling) for the first minute or two.
initContainers:
  - name: wait-for-jeffrey-server
    image: busybox:1.37
    command:
      - sh
      - -c
      - |
        until wget -q --spider "http://\${JEFFREY_SERVER_HOST}/actuator/health/readiness"; do
          echo "waiting for http://\${JEFFREY_SERVER_HOST}/actuator/health/readiness ..."
          sleep 2
        done
        echo "jeffrey-server is ready, starting application."
    env:
      - name: JEFFREY_SERVER_HOST
        value: {{ .Values.jeffrey.serverHost | quote }}
    # requests == limits keeps the pod in QoS class Guaranteed.
    resources:
      limits:   { cpu: 50m, memory: 32Mi }
      requests: { cpu: 50m, memory: 32Mi }`;

const sideBySide = `# Two flavours from one chart — same image, different values.mode:
helm upgrade --install direct helm/jeffrey-testapp-server --set mode=direct
helm upgrade --install dom    helm/jeffrey-testapp-server --set mode=dom

# In Jeffrey Server, the two projects appear as:
#   direct-jeffrey-testapp-server   (efficient PersonService)
#   dom-jeffrey-testapp-server      (inefficient PersonService)`;

const installCommands = `# 1) Jeffrey Server — creates the shared PVC and runs copy-libs.
helm upgrade --install jeffrey-server helm/jeffrey-server \\
  --namespace jeffrey-testapp --create-namespace

# 2) Two flavours of the testapp service from the same chart.
helm upgrade --install direct helm/jeffrey-testapp-server \\
  --namespace jeffrey-testapp --set mode=direct
helm upgrade --install dom    helm/jeffrey-testapp-server \\
  --namespace jeffrey-testapp --set mode=dom

# 3) Load generator that drives both servers.
helm upgrade --install jeffrey-testapp-client helm/jeffrey-testapp-client \\
  --namespace jeffrey-testapp`;

const orbstackOverride = `# When the cluster has no RWX provisioner (orbstack/minikube/kind), tell the
# jeffrey-server chart to bind to a static hostPath PV instead of a dynamic one:
helm upgrade --install jeffrey-server helm/jeffrey-server \\
  --namespace jeffrey-testapp --create-namespace \\
  --set sharedVolume.storageClassName="" \\
  --set sharedVolume.hostPath.create=true`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Helm Chart"
      icon="bi bi-file-earmark-code"
    />

    <div class="docs-content">
      <p>
        The full Kubernetes example for the
        <a href="https://github.com/petrbouda/jeffrey-testapp" target="_blank" rel="noopener">jeffrey-testapp</a>
        repo. Three charts under <code>helm/</code> coordinate a Jeffrey Server, two
        flavours of the testapp service, and one load-generating client — all in a single
        namespace, all sharing one PVC. Every snippet on this page is taken verbatim from
        the repo with minor formatting trims; follow the file links for the un-edited source.
      </p>

      <h2 id="three-charts">Three Charts at a Glance</h2>
      <table>
        <thead>
          <tr>
            <th>Chart</th>
            <th>Role</th>
            <th>Releases installed</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>helm/jeffrey-server/</code></td>
            <td>Jeffrey Server itself. Owns the shared PVC, runs <code>copy-libs</code>, exposes HTTP <code>8080</code> + gRPC <code>9090</code>.</td>
            <td><code>jeffrey-server</code></td>
          </tr>
          <tr>
            <td><code>helm/jeffrey-testapp-server/</code></td>
            <td>SQLite-backed Spring Boot REST app, profiled by the JIB entrypoint. Toggles between efficient and inefficient PersonService via <code>mode</code>.</td>
            <td><code>direct</code> (<code>--set mode=direct</code>), <code>dom</code> (<code>--set mode=dom</code>)</td>
          </tr>
          <tr>
            <td><code>helm/jeffrey-testapp-client/</code></td>
            <td>Load generator — drives both server flavours concurrently.</td>
            <td><code>jeffrey-testapp-client</code></td>
          </tr>
        </tbody>
      </table>

      <h2 id="chart-structure">Chart Structure</h2>
      <p>
        Each chart follows the standard Helm v3 layout (<code>Chart.yaml</code> +
        <code>values.yaml</code> + <code>templates/</code>). The two project-config files
        unique to this stack are <code>application.properties</code> (Spring Boot) and
        <code>jeffrey-base.conf</code> (HOCON for the CLI) — both live next to
        <code>values.yaml</code> and are pulled into ConfigMaps by the templates via
        <code>.Files.Get</code>.
      </p>

      <DocsCodeBlock
        language="text"
        :code="chartStructure"
      />

      <DocsCallout type="info">
        <strong>One chart per concern.</strong> <code>jeffrey-server</code> owns the
        cluster-wide objects — the shared PVC, the optional hostPath PV, the Service that
        both protocols share, the optional Ingress. The two testapp charts deliberately
        carry no PV/PVC/Ingress — they only bind to <code>jeffrey-server</code>'s claim
        and stay cluster-internal.
      </DocsCallout>

      <h2 id="configure-server">Configuring Jeffrey Server</h2>
      <p>
        From
        <a href="https://github.com/petrbouda/jeffrey-testapp/blob/main/helm/jeffrey-server/values.yaml" target="_blank" rel="noopener">
          <code>helm/jeffrey-server/values.yaml</code></a>:
      </p>

      <DocsCodeBlock
        language="yaml"
        :code="serverImage"
      />

      <p>Three lines of <code>application.properties</code> activate <code>copy-libs</code>:</p>

      <DocsCodeBlock
        language="properties"
        code="jeffrey.server.copy-libs.enabled=true
jeffrey.server.home.dir=\${JEFFREY_HOME}
spring.profiles.include=trace-file-log"
      />

      <p>The Service exposes both protocols on a single ClusterIP:</p>

      <DocsCodeBlock
        language="yaml"
        :code="serverService"
      />

      <p>
        OrbStack publishes in-cluster Service DNS to the host automatically — open
        <code>http://jeffrey-server.jeffrey-testapp.svc.cluster.local:8080</code> directly
        from the laptop. For other clusters, expose Jeffrey Server via the chart's
        <code>ingress.enabled=true</code> (HTTP) and <code>grpcIngress.enabled=true</code>
        (gRPC, requires <code>backend-protocol: GRPC</code> Nginx annotation).
      </p>

      <h2 id="configure-app">Configuring the Monitored Application</h2>
      <p>
        The testapp's
        <a href="https://github.com/petrbouda/jeffrey-testapp/blob/main/helm/jeffrey-testapp-server/values.yaml" target="_blank" rel="noopener">
          <code>values.yaml</code></a>:
      </p>

      <DocsCodeBlock
        language="yaml"
        :code="testappValues"
      />

      <p>
        The Deployment wires <code>JEFFREY_HOME</code>, <code>JEFFREY_BASE_CONFIG</code>,
        and <code>JEFFREY_ENABLED</code> into the application container — see the
        <router-link to="/docs/server/deployment/jeffrey-cli">Jeffrey CLI</router-link>
        page for the full env-var contract:
      </p>

      <DocsCodeBlock
        language="yaml"
        :code="testappEnv"
      />

      <DocsCallout type="warning">
        <strong>Spring Boot config-mount gotcha.</strong> Use
        <code>SPRING_CONFIG_ADDITIONAL_LOCATION</code>, not
        <code>SPRING_CONFIG_LOCATION</code>. Additional-location <em>merges</em> with the
        image-bundled <code>application.properties</code>; the regular
        <code>SPRING_CONFIG_LOCATION</code> <em>replaces</em> it, silently dropping
        defaults like <code>spring.sql.init.mode=always</code> that the testapp uses for
        its bootstrap SQL.
      </DocsCallout>

      <h2 id="init-container">Init Container Ordering</h2>
      <p>
        Because the application image relies on the shared volume, every monitored pod
        carries a tiny init container that blocks startup until Jeffrey Server is ready
        and <code>copy-libs</code> has populated the volume:
      </p>

      <DocsCodeBlock
        language="yaml"
        :code="initContainer"
      />

      <p>
        Without this gate, the JIB entrypoint runs <code>jeffrey-cli init</code> against
        a half-populated <code>libs/current/</code> the first time the pod schedules,
        fail-opens to plain <code>java</code> (no profiling), and stays that way until
        you restart the pod manually.
      </p>

      <h2 id="side-by-side">Side-by-Side: direct vs dom</h2>
      <p>
        The headline value of this layout: one chart, two distinct projects in Jeffrey
        Server, ready for differential analysis in Microscope.
      </p>

      <DocsCodeBlock
        language="bash"
        :code="sideBySide"
      />

      <p>
        Inside the application container, <code>jeffrey-base.conf</code> reads
        <code>JEFFREY_TESTAPP_MODE</code> via HOCON variable substitution to set the
        project name and label — see the
        <router-link to="/docs/server/deployment/jeffrey-cli#project-block">Project Block</router-link>
        section for the substitution syntax. In Microscope, opening both projects
        side-by-side gives a one-click differential flame graph between the efficient
        and inefficient code paths.
      </p>

      <h2 id="installing">Installing the Stack</h2>
      <p>
        Four <code>helm upgrade --install</code> calls — one per release. Order doesn't
        matter; the testapp pods carry an init container that polls Jeffrey Server's
        <code>/actuator/health/readiness</code> so nothing starts until
        <code>copy-libs</code> has populated the shared volume.
      </p>

      <DocsCodeBlock
        language="bash"
        :code="installCommands"
      />

      <p>
        On dev clusters without an RWX provisioner (OrbStack, minikube, kind), pass two
        extra flags to <code>jeffrey-server</code> so it binds the PVC to a static
        hostPath PV instead:
      </p>

      <DocsCodeBlock
        language="bash"
        :code="orbstackOverride"
      />

      <DocsCallout type="tip">
        Anything you'd put in <code>values.yaml</code> can also be passed inline with
        <code>--set key=value</code> or <code>--values overrides.yaml</code> — the chart
        is a vanilla Helm v3 chart with no opinions on how you supply values.
      </DocsCallout>

      <h2 id="tearing-down">Tearing Down</h2>
      <p>
        Reverse-order <code>helm uninstall</code> — the testapp releases first, then
        <code>jeffrey-server</code> last (so its PVC outlives the consumers):
      </p>

      <DocsCodeBlock
        language="bash"
        code="helm uninstall jeffrey-testapp-client --namespace jeffrey-testapp
helm uninstall dom                    --namespace jeffrey-testapp
helm uninstall direct                 --namespace jeffrey-testapp
helm uninstall jeffrey-server         --namespace jeffrey-testapp"
      />

      <DocsCallout type="warning">
        <strong>Statically-defined hostPath PVs survive <code>helm uninstall</code>.</strong>
        The default <code>reclaimPolicy</code> for a manually-created PV is
        <code>Retain</code>, so the directory on the cluster node (e.g.
        <code>/tmp/jeffrey-data</code> on OrbStack) keeps the contents Jeffrey Server
        wrote into it. The next install would inherit a stale CLI bundle. On dev
        clusters, wipe the directory yourself before re-installing. On real clusters
        with a dynamic RWX provisioner, the <code>StorageClass</code> owns the reclaim
        policy and the cleanup is automatic.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
