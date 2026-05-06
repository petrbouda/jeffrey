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
  { id: 'what-it-does', text: 'What the Extension Does', level: 2 },
  { id: 'parent-pom', text: 'Parent pom.xml', level: 2 },
  { id: 'module-pom', text: 'Per-Module Override', level: 2 },
  { id: 'build-commands', text: 'Build Commands', level: 2 },
  { id: 'no-baked-binaries', text: 'No Agent or Profiler in the Image', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const parentPom = `<plugin>
    <groupId>com.google.cloud.tools</groupId>
    <artifactId>jib-maven-plugin</artifactId>
    <version>\${jib.version}</version>
    <dependencies>
        <dependency>
            <groupId>cafe.jeffrey-analyst</groupId>
            <artifactId>jeffrey-jib-maven</artifactId>
            <version>\${jeffrey-jib.version}</version>
        </dependency>
    </dependencies>
    <configuration>
        <from>
            <image>eclipse-temurin:25-jre</image>
        </from>
        <container>
            <ports>
                <port>8080</port>
            </ports>
        </container>
        <pluginExtensions>
            <pluginExtension>
                <implementation>cafe.jeffrey.jib.maven.JeffreyJibMavenExtension</implementation>
            </pluginExtension>
        </pluginExtensions>
    </configuration>
</plugin>`;

const properties = `<properties>
    <springboot.version>4.0.6</springboot.version>
    <jib.version>3.5.1</jib.version>
    <jeffrey-jib.version>0.0.1-b3</jeffrey-jib.version>
</properties>`;

const moduleServer = `<plugin>
    <groupId>com.google.cloud.tools</groupId>
    <artifactId>jib-maven-plugin</artifactId>
    <configuration>
        <to>
            <image>petrbouda/jeffrey-testapp-server</image>
        </to>
        <container>
            <mainClass>\${mainClass}</mainClass>
        </container>
    </configuration>
</plugin>`;

const moduleClient = `<plugin>
    <groupId>com.google.cloud.tools</groupId>
    <artifactId>jib-maven-plugin</artifactId>
    <configuration>
        <to>
            <image>petrbouda/jeffrey-testapp-client</image>
        </to>
        <container>
            <mainClass>\${mainClass}</mainClass>
        </container>
    </configuration>
</plugin>`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Jeffrey JIB Extension"
      icon="bi bi-box-seam"
    />

    <div class="docs-content">
      <p>
        The <strong>Jeffrey JIB extension</strong> wraps the standard
        <a href="https://github.com/GoogleContainerTools/jib" target="_blank" rel="noopener">jib-maven-plugin</a>
        and modifies the image's entrypoint at build time. The result: a Spring Boot image
        that, when launched in a pod with <code>JEFFREY_ENABLED=true</code> and a populated
        <code>JEFFREY_HOME</code>, automatically runs <code>jeffrey-cli init</code> before
        the JVM starts and boots with the right <code>-javaagent</code> + async-profiler
        flags. There is no Dockerfile, no shell script, and no agent or profiler binary
        baked into the image.
      </p>

      <h2 id="what-it-does">What the Extension Does</h2>
      <p>At image-build time, the extension modifies the JIB <code>ContainerBuildPlan</code>:</p>

      <div class="feature-list">
        <div class="feature-item">
          <i class="bi bi-check-circle-fill"></i>
          <div>Installs a small shell wrapper at <code>/usr/local/bin/jeffrey-entrypoint</code> as a new image layer.</div>
        </div>
        <div class="feature-item">
          <i class="bi bi-check-circle-fill"></i>
          <div>Replaces the image <code>ENTRYPOINT</code> with the wrapper.</div>
        </div>
        <div class="feature-item">
          <i class="bi bi-check-circle-fill"></i>
          <div>Moves JIB's auto-derived <code>java -cp @/app/jib-classpath-file &lt;MainClass&gt;</code> into <code>CMD</code>.</div>
        </div>
        <div class="feature-item">
          <i class="bi bi-check-circle-fill"></i>
          <div>Preserves JIB's main-class detection, classpath-file assembly, <code>jvmFlags</code>, base image, and target architecture.</div>
        </div>
      </div>

      <p>
        At container start, the wrapper runs <code>jeffrey-cli init</code> — resolved from
        <code>${JEFFREY_HOME}/libs/current/jeffrey-cli-&lt;arch&gt;</code> on the shared
        volume populated by Jeffrey Server's
        <router-link to="/docs/server/deployment/shared-volume">copy-libs</router-link>
        feature — and then <code>exec</code>s the original JIB command with the
        profiler-agent flags merged in. If the shared-volume root is not configured at
        runtime (neither <code>JEFFREY_HOME</code> nor <code>JEFFREY_CLI_PATH</code> is
        set), the wrapper logs a warning and skips init entirely — useful for "build once,
        ship to dev/prod with profiling, ship to CI without".
      </p>

      <h2 id="parent-pom">Parent pom.xml</h2>
      <p>
        The testapp's parent <code>pom.xml</code> places the extension inside the
        <code>jib-maven-plugin</code>'s <code>&lt;dependencies&gt;</code> block (so JIB
        loads it on the plugin classpath) and registers it via
        <code>&lt;pluginExtensions&gt;</code>:
      </p>

      <DocsCodeBlock
        language="xml"
        :code="parentPom"
      />

      <p>
        The version properties live in the parent's <code>&lt;properties&gt;</code> block
        (excerpted from
        <a href="https://github.com/petrbouda/jeffrey-testapp/blob/main/pom.xml" target="_blank" rel="noopener">
          jeffrey-testapp/pom.xml
        </a>):
      </p>

      <DocsCodeBlock
        language="xml"
        :code="properties"
      />

      <DocsCallout type="info">
        <strong>Coordinates.</strong> The extension lives at
        <code>cafe.jeffrey-analyst:jeffrey-jib-maven</code>, currently version
        <code>0.0.1-b3</code>. JIB itself stays at the standard
        <code>com.google.cloud.tools:jib-maven-plugin:3.5.1</code> — no fork, no patched
        plugin.
      </DocsCallout>

      <h2 id="module-pom">Per-Module Override</h2>
      <p>
        Each module pins its own target image and main class but inherits everything else
        — base image, port, the extension wiring — from the parent. From
        <a href="https://github.com/petrbouda/jeffrey-testapp/blob/main/server/pom.xml" target="_blank" rel="noopener">
          server/pom.xml
        </a>:
      </p>

      <DocsCodeBlock
        language="xml"
        :code="moduleServer"
      />

      <p>And the matching block in
        <a href="https://github.com/petrbouda/jeffrey-testapp/blob/main/client/pom.xml" target="_blank" rel="noopener">
          client/pom.xml
        </a>:
      </p>

      <DocsCodeBlock
        language="xml"
        :code="moduleClient"
      />

      <p>
        The <code>${mainClass}</code> placeholder is the module's own
        <code>&lt;properties&gt;</code> entry — <code>jeffrey.testapp.server.ServerApplication</code>
        for the server and <code>jeffrey.testapp.client.ClientApplication</code> for the
        client.
      </p>

      <h2 id="build-commands">Build Commands</h2>
      <p>
        Two flavours, depending on where the image needs to land. Both run from the parent
        directory and emit one image per Maven module.
      </p>

      <h3>Local Docker daemon (OrbStack / minikube / Docker Desktop)</h3>
      <DocsCodeBlock
        language="bash"
        code="mvn clean package jib:dockerBuild"
      />
      <p>
        Writes the image directly into the local Docker daemon — no registry round-trip.
        Pair with a cluster that mounts the host daemon (OrbStack does this automatically;
        minikube/kind need <code>minikube image load …</code> or an in-cluster registry).
      </p>

      <h3>Container registry</h3>
      <DocsCodeBlock
        language="bash"
        code="mvn clean package jib:build"
      />
      <p>
        Pushes to the registry referenced by the module's <code>&lt;to&gt;&lt;image&gt;</code>
        coordinate (<code>petrbouda/jeffrey-testapp-server</code> →
        <code>docker.io/petrbouda/jeffrey-testapp-server:latest</code> by default).
        Authentication uses your Docker config (<code>~/.docker/config.json</code>) or the
        <code>JIB_REGISTRY_USER</code> / <code>JIB_REGISTRY_PASS</code> env vars.
      </p>

      <h2 id="no-baked-binaries">No Agent or Profiler in the Image</h2>
      <p>
        A deliberate property of the testapp setup: <strong>the application image contains
        only the entrypoint wrapper</strong>. The CLI binary, agent JAR, and async-profiler
        library are not baked into the image — they are delivered to every monitored pod at
        runtime via the shared <code>jeffrey-pvc</code>, populated by Jeffrey Server's
        <code>copy-libs</code> feature.
      </p>

      <DocsCallout type="tip">
        <strong>Why bother?</strong> One Jeffrey Server upgrade publishes a new CLI bundle
        for every monitored pod in the namespace — you never rebuild your application
        image to pick up an agent fix. The trade-off is a runtime dependency on the
        shared volume (and on Jeffrey Server having finished publishing into it before the
        application starts), which the testapp Helm chart handles with an init container
        that polls Jeffrey Server's <code>/actuator/health/readiness</code>. See
        <router-link to="/docs/server/deployment/helm-chart">Helm Chart</router-link> for
        the wiring.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 1.5rem 0;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  background: #f0fdf4;
  border-radius: 8px;
  border: 1px solid #bbf7d0;
}

.feature-item i {
  color: #10b981;
  font-size: 1rem;
}

.feature-item div {
  font-size: 0.875rem;
  color: #374151;
  line-height: 1.5;
}
</style>
