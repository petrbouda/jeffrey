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
  { id: 'self-contained-image', text: 'A Self-Contained Image', level: 2 }
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
                <properties>
                    <payloadVersion>\${jeffrey-jib.version}</payloadVersion>
                </properties>
            </pluginExtension>
        </pluginExtensions>
    </configuration>
</plugin>`;

const properties = `<properties>
    <springboot.version>4.0.6</springboot.version>
    <jib.version>3.5.1</jib.version>
    <jeffrey-jib.version>0.14.0</jeffrey-jib.version>
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
        <code>JEFFREY_HOME</code>, automatically runs <code>provisioner init</code> before
        the JVM starts and boots with the right async-profiler flags and
        <code>-Djeffrey.heartbeat.*</code> properties. There is no Dockerfile and no shell script to
        maintain; the provisioner and async-profiler are installed by the extension under
        <code>/opt/jeffrey</code>.
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
        At container start, the wrapper runs <code>provisioner init</code> from
        <code>/opt/jeffrey</code>, where the extension installed it at build time, and then
        <code>exec</code>s the original JIB command with the profiler-agent flags merged in.
        Nothing is downloaded or waited for. Set <code>JEFFREY_ENABLED=false</code> to skip
        profiling entirely — useful for "build once, ship to dev/prod with profiling, ship to
        CI without".
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
        <code>cafe.jeffrey-analyst:jeffrey-jib-maven</code>, pinned above as
        <code>jeffrey-jib.version</code> and reused as <code>payloadVersion</code> so the image
        carries the payloads of the same release. JIB itself stays at the standard
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
        <code>&lt;properties&gt;</code> entry — <code>jeffrey.testapp.server.HubApplication</code>
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

      <h2 id="self-contained-image">A Self-Contained Image</h2>
      <p>
        The extension bakes everything the image needs to profile itself: the entrypoint wrapper at
        <code>/usr/local/bin/jeffrey-entrypoint</code>, and the provisioner and async-profiler under
        <code>/opt/jeffrey</code>. The payloads are ordinary Maven artifacts, resolved through your
        build's own repositories and cache; <code>payloadVersion</code> names the jeffrey-jib release
        they ship with, and the build log prints which Jeffrey release and async-profiler version
        that release bundles.
      </p>

      <DocsCallout type="tip">
        <strong>Why bother?</strong> The shared volume goes back to being just the recording
        handoff. A pod no longer has to wait for Jeffrey Hub to publish binaries before it can
        start profiling, which removes the startup race that used to leave a pod running
        unprofiled until someone restarted it. The cost is that a provisioner fix now arrives with
        an image rebuild rather than a Hub upgrade. An image whose base already ships async-profiler
        can keep it by setting <code>profilerPath</code>, which skips that payload entirely.
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
