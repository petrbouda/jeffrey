<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
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
  { id: 'choose-flavour', text: 'Choose a Flavour', level: 2 },
  { id: 'maven-setup', text: 'Maven', level: 2 },
  { id: 'maven-native', text: 'Minimal: native', level: 3 },
  { id: 'maven-jar', text: 'Minimal: jar', level: 3 },
  { id: 'maven-full', text: 'Complete example', level: 3 },
  { id: 'gradle-setup', text: 'Gradle', level: 2 },
  { id: 'gradle-native', text: 'Minimal: native', level: 3 },
  { id: 'gradle-jar', text: 'Minimal: jar', level: 3 },
  { id: 'gradle-full', text: 'Complete example', level: 3 },
  { id: 'migrating', text: 'Migrating from payloadVersion', level: 2 }
];

const mavenNative = `<plugin>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-maven-plugin</artifactId>
  <version>3.5.2</version>
  <dependencies>
    <dependency>
      <groupId>cafe.jeffrey-analyst</groupId>
      <artifactId>jeffrey-jib-maven-native</artifactId>
      <version>\${jeffrey-jib.version}</version>
    </dependency>
  </dependencies>
  <configuration>
    <to>
      <image>registry.example.com/team/my-service</image>
    </to>
    <pluginExtensions>
      <pluginExtension>
        <implementation>cafe.jeffrey.jib.maven.JeffreyJibMavenExtension</implementation>
      </pluginExtension>
    </pluginExtensions>
  </configuration>
</plugin>`;

const mavenJar = `<dependencies>
  <dependency>
    <groupId>cafe.jeffrey-analyst</groupId>
    <artifactId>jeffrey-jib-maven-jar</artifactId>
    <version>\${jeffrey-jib.version}</version>
  </dependency>
</dependencies>`;

const mavenFull = `<plugin>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-maven-plugin</artifactId>
  <version>3.5.2</version>
  <dependencies>
    <!-- jar flavour: this image is multi-platform, and Jib layers are shared by every
         platform in the manifest list, so the native binary would ship twice. -->
    <dependency>
      <groupId>cafe.jeffrey-analyst</groupId>
      <artifactId>jeffrey-jib-maven-jar</artifactId>
      <version>\${jeffrey-jib.version}</version>
    </dependency>
  </dependencies>
  <configuration>
    <from>
      <image>eclipse-temurin:25-jre</image>
      <platforms>
        <platform><architecture>amd64</architecture><os>linux</os></platform>
        <platform><architecture>arm64</architecture><os>linux</os></platform>
      </platforms>
    </from>
    <to>
      <image>registry.example.com/team/my-service:\${project.version}</image>
    </to>
    <container>
      <mainClass>com.example.Application</mainClass>
      <!-- Let the provisioner own the JVM flags. Anything here is moved into CMD and applied
           AFTER the argfile, so a -Xmx or -XX: option would silently override profiling. -->
      <jvmFlags/>
      <environment>
        <!-- Off by default; a pod sets JEFFREY_ENABLED=true to opt in without a rebuild. -->
        <JEFFREY_ENABLED>false</JEFFREY_ENABLED>
      </environment>
    </container>
    <pluginExtensions>
      <pluginExtension>
        <implementation>cafe.jeffrey.jib.maven.JeffreyJibMavenExtension</implementation>
        <configuration implementation="cafe.jeffrey.jib.JeffreyJibConfig">
          <!-- Build-time gate: -Djeffrey.profiling=false produces a plain Jib image. -->
          <enabled>\${jeffrey.profiling}</enabled>
          <!-- Baked as JEFFREY_HOME: the shared volume every pod mounts at the same path. -->
          <jeffreyHome>/mnt/jeffrey</jeffreyHome>
          <!-- Pinned so a later artifactId rename does not start a new project on the Hub. -->
          <projectName>my-service</projectName>
          <!-- Optional per-deployment override file, mounted from a ConfigMap. -->
          <overrideConfig>/etc/jeffrey/overrides.conf</overrideConfig>
        </configuration>
      </pluginExtension>
    </pluginExtensions>
  </configuration>
</plugin>`;

const mavenProperties = `<properties>
  <jeffrey-jib.version>0.14.0</jeffrey-jib.version>
  <jeffrey.profiling>true</jeffrey.profiling>
</properties>`;

const gradleNative = `buildscript {
  dependencies {
    classpath("cafe.jeffrey-analyst:jeffrey-jib-gradle-native:0.14.0")
  }
}

plugins {
  id("com.google.cloud.tools.jib") version "3.5.2"
}

jib {
  to.image = "registry.example.com/team/my-service"
  pluginExtensions {
    pluginExtension {
      implementation = "cafe.jeffrey.jib.gradle.JeffreyJibGradleExtension"
    }
  }
}`;

const gradleJar = `buildscript {
  dependencies {
    classpath("cafe.jeffrey-analyst:jeffrey-jib-gradle-jar:0.14.0")
  }
}`;

const gradleFull = `buildscript {
  dependencies {
    // jar flavour: this image is multi-platform, and Jib layers are shared by every
    // platform in the manifest list, so the native binary would ship twice.
    classpath("cafe.jeffrey-analyst:jeffrey-jib-gradle-jar:0.14.0")
  }
}

plugins {
  id("com.google.cloud.tools.jib") version "3.5.2"
}

jib {
  from {
    image = "eclipse-temurin:25-jre"
    platforms {
      platform { architecture = "amd64"; os = "linux" }
      platform { architecture = "arm64"; os = "linux" }
    }
  }
  to.image = "registry.example.com/team/my-service:\${project.version}"
  container {
    mainClass = "com.example.Application"
    // Let the provisioner own the JVM flags: anything here is moved into CMD and applied
    // AFTER the argfile, so a -Xmx or -XX: option would silently override profiling.
    jvmFlags = emptyList()
    // Off by default; a pod sets JEFFREY_ENABLED=true to opt in without a rebuild.
    environment = mapOf("JEFFREY_ENABLED" to "false")
  }
  pluginExtensions {
    pluginExtension {
      implementation = "cafe.jeffrey.jib.gradle.JeffreyJibGradleExtension"
      properties = mapOf(
        // Build-time gate: -PjeffreyProfiling=false produces a plain Jib image.
        "enabled" to (findProperty("jeffreyProfiling") ?: "true").toString(),
        // Baked as JEFFREY_HOME: the shared volume every pod mounts at the same path.
        "jeffreyHome" to "/mnt/jeffrey",
        // Pinned so a later project rename does not start a new project on the Hub.
        "projectName" to "my-service",
        // Optional per-deployment override file, mounted from a ConfigMap.
        "overrideConfig" to "/etc/jeffrey/overrides.conf",
      )
    }
  }
}`;

const migrationBefore = `<dependency>
  <artifactId>jeffrey-jib-maven</artifactId>            <!-- bare extension -->
</dependency>
…
<configuration implementation="cafe.jeffrey.jib.JeffreyJibConfig">
  <payloadVersion>0.13.22</payloadVersion>
  <provisionerSource>jar</provisionerSource>
</configuration>`;

const migrationAfter = `<dependency>
  <artifactId>jeffrey-jib-maven-jar</artifactId>        <!-- flavour = build + version -->
</dependency>`;

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="JIB Build Setup"
        icon="bi bi-hammer"
      />

      <div class="docs-content">
        <p>Wiring the extension into a JIB build is one plugin dependency and one
          <code>pluginExtension</code> line. The dependency is a <em>flavour</em>: the extension for
          your build tool plus a payload jar carrying one provisioner build and async-profiler, so
          declaring it is the whole configuration. Everything else on
          <router-link to="/docs/jib/configuration">Configuration</router-link> is optional.</p>

        <h2 id="choose-flavour">Choose a Flavour</h2>

        <table>
            <thead>
              <tr>
                <th>Flavour</th>
                <th>Provisioner</th>
                <th>Pick it when</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><code>jeffrey-jib-maven-native</code><br><code>jeffrey-jib-gradle-native</code></td>
                <td>GraalVM binary, ~44&nbsp;MB per architecture, starts in milliseconds, needs nothing of the application's JVM</td>
                <td>Single-architecture images; applications on a JVM older than the one Jeffrey targets (currently 25)</td>
              </tr>
              <tr>
                <td><code>jeffrey-jib-maven-jar</code><br><code>jeffrey-jib-gradle-jar</code></td>
                <td>One architecture-neutral jar, ~4&nbsp;MB, runs a short second JVM on the application's own <code>java</code></td>
                <td>Multi-architecture images (Jib layers are not per-platform, so <code>native</code> would ship every architecture's binary in every image); any application already on a current JVM</td>
              </tr>
            </tbody>
        </table>

        <p>Both flavours carry async-profiler for <code>linux/amd64</code> and
          <code>linux/arm64</code>; the image gets only the architectures it targets. Declaring the
          bare <code>jeffrey-jib-maven</code> / <code>jeffrey-jib-gradle</code>, or both flavours at
          once, fails the build with a message naming the fix.</p>

        <h2 id="maven-setup">Maven</h2>

        <h3 id="maven-native">Minimal: native</h3>
        <p>A single-architecture image that carries the GraalVM provisioner. Nothing is configured on
          the extension; <code>JEFFREY_HOME</code> arrives from the pod.</p>

        <DocsCodeBlock
          language="xml"
          :code="mavenNative"
        />

        <h3 id="maven-jar">Minimal: jar</h3>
        <p>Identical, with the other flavour in the plugin's <code>&lt;dependencies&gt;</code>. The
          rest of the plugin block does not change.</p>

        <DocsCodeBlock
          language="xml"
          :code="mavenJar"
        />

        <h3 id="maven-full">Complete example</h3>
        <p>A multi-platform image pushed to a registry, with the profiling decisions made in the
          build and explained inline: the jar flavour because of the two platforms, an empty
          <code>jvmFlags</code> so the provisioner's argfile is not overridden, profiling off by
          default at the image level with a pod-level opt-in, a build-time gate on a Maven
          property, and the three values worth baking as image <code>ENV</code> defaults.</p>

        <DocsCodeBlock
          language="xml"
          :code="mavenFull"
        />

        <DocsCodeBlock
          language="xml"
          :code="mavenProperties"
        />

        <DocsCallout type="warning">
          <strong><code>jeffreyHome</code> must point at a shared volume / disk.</strong>
          It is not where the binaries come from &mdash; those are in the image &mdash; but it is
          where the application writes its recordings, under
          <code>${JEFFREY_HOME}/workspaces/</code>, and where Jeffrey Hub reads them from. Every
          monitored pod and the Hub must see the same bytes, so a host-local directory or a per-pod
          ephemeral volume will not work. Leave it out of the build and set <code>JEFFREY_HOME</code>
          on the pod instead when the mount path differs per cluster.
        </DocsCallout>

        <h2 id="gradle-setup">Gradle</h2>

        <h3 id="gradle-native">Minimal: native</h3>
        <p>The flavour goes on the build-script classpath &mdash; <code>buildscript.dependencies</code>
          as shown, or the <code>jib</code> plugin's <code>dependencies</code> block, depending on how
          you apply the plugin.</p>

        <DocsCodeBlock
          language="kotlin"
          :code="gradleNative"
        />

        <h3 id="gradle-jar">Minimal: jar</h3>

        <DocsCodeBlock
          language="kotlin"
          :code="gradleJar"
        />

        <h3 id="gradle-full">Complete example</h3>
        <p>The same multi-platform build as the Maven one. The string <code>properties</code> DSL is
          used on purpose: it works on every Gradle version JIB supports and keeps
          <code>JeffreyJibConfig</code> off the build script's compile classpath. The typed
          <code>configuration(Action&lt;JeffreyJibConfig&gt;) { … }</code> form is also accepted
          but is fragile across Gradle versions.</p>

        <DocsCodeBlock
          language="kotlin"
          :code="gradleFull"
        />

        <p>Property names are the setters on <code>JeffreyJibConfig</code>; the constants on that
          class (<code>JeffreyJibConfig.JEFFREY_HOME</code>, …) can replace the strings if you prefer
          an IDE-checked build file at the cost of the import.</p>

        <h2 id="migrating">Migrating from <code>payloadVersion</code></h2>
        <p>Releases before the flavours resolved the payload at build time and needed two
          properties. Both are gone: the flavour's own version is the payload version, and the
          artifactId is the provisioner build. A build that still sets them is warned and the values
          are ignored.</p>

        <DocsCodeBlock
          language="xml"
          :code="migrationBefore"
        />

        <DocsCodeBlock
          language="xml"
          :code="migrationAfter"
        />
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
