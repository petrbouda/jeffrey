# jeffrey-jib

JIB plugin extensions (Gradle and Maven) that wrap the container entrypoint so
[Jeffrey](https://github.com/petrbouda/jeffrey) profiling initialises before the app starts,
without forcing operators to override the container `command:` in Kubernetes YAML.

## Modules

| Module | Coordinate | Purpose |
|---|---|---|
| **`jeffrey-jib-maven-jar`** | `cafe.jeffrey-analyst:jeffrey-jib-maven-jar` | **What a Maven build declares.** The Maven extension plus the `jar` payload. |
| **`jeffrey-jib-maven-native`** | `cafe.jeffrey-analyst:jeffrey-jib-maven-native` | **What a Maven build declares.** The Maven extension plus the `native` payload. |
| **`jeffrey-jib-gradle-jar`** | `cafe.jeffrey-analyst:jeffrey-jib-gradle-jar` | **What a Gradle build declares.** The Gradle extension plus the `jar` payload. |
| **`jeffrey-jib-gradle-native`** | `cafe.jeffrey-analyst:jeffrey-jib-gradle-native` | **What a Gradle build declares.** The Gradle extension plus the `native` payload. |
| `jeffrey-jib-maven` | `cafe.jeffrey-analyst:jeffrey-jib-maven` | `JibMavenPluginExtension` implementation (pulled transitively). Fails the build if declared alone. |
| `jeffrey-jib-gradle` | `cafe.jeffrey-analyst:jeffrey-jib-gradle` | `JibGradlePluginExtension` implementation (pulled transitively). Fails the build if declared alone. |
| `jeffrey-jib-core` | `cafe.jeffrey-analyst:jeffrey-jib-core` | Shared `ContainerBuildPlan` transformation (pulled transitively). |
| `jeffrey-jib-payload-jar` | `cafe.jeffrey-analyst:jeffrey-jib-payload-jar` | The architecture-neutral `provisioner.jar` and async-profiler for both Linux architectures, under `jeffrey-payload/` (pulled transitively). |
| `jeffrey-jib-payload-native` | `cafe.jeffrey-analyst:jeffrey-jib-payload-native` | The GraalVM provisioner and async-profiler, each for both Linux architectures (pulled transitively). |

The four bold *flavours* are the only coordinates a consumer ever writes. Each is the extension for
one build tool plus one payload jar, so declaring it is what chooses the provisioner build; there is
no property for it. The payload modules are built only under the `payload` Maven profile, after CI
has staged the binaries; their `jeffrey-payload/payload.properties` records which Jeffrey release
and async-profiler they bundle.

The extension installs `/usr/local/bin/jeffrey-entrypoint` into a new image layer, makes it
the `ENTRYPOINT`, and moves JIB's auto-derived `java -cp … <MainClass>` into `CMD`. It also
takes the binaries that run needs — the provisioner and async-profiler — out of the payload jar
on its own class path and installs them under `/opt/jeffrey` in a second layer; the image gets
only the architectures it targets. Nothing is downloaded by the extension itself: the payload
arrives like any other plugin dependency. At container start the wrapper runs `provisioner init`
and `exec`s the JIB command with the provisioner-produced argfile inserted right after the `java`
binary.

**The image is self-contained.** Nothing is fetched, copied or waited for at container start, and
the shared volume is needed only for the recordings the application writes to it.

## Choosing a provisioner build

The flavour you declare — `…-native` or `…-jar` — picks which build of the provisioner the image
carries:

| | `native` | `jar` |
|---|---|---|
| What ships | GraalVM binary, one per architecture | One architecture-neutral jar |
| Size | ~44 MB per architecture | ~4 MB total |
| Runs on | itself | the application's own JVM |
| Start-up | milliseconds | one short JVM boot before the app |
| Requires | nothing of the application | the app's JVM must be able to read the jar's class files |

Prefer `native` for a single-architecture image, or when the application's JVM is older than the
one the provisioner was compiled with. Prefer `jar` for a multi-architecture image: JIB layers are
not per-platform, so `native` ships *every* architecture's binary in *every* image of the index —
two platforms means ~86 MB, where the jar stays one ~4 MB file.

## Zero-config setup

The config file is optional. Without `/jeffrey/jeffrey-base.conf` the provisioner configures
itself from `JEFFREY_*` environment variables, and the extension bakes
`JEFFREY_PROJECT_NAME` into the image (from the `projectName` property, defaulting to the
Maven artifactId / Gradle project name). An image built with this extension is therefore
fully self-identifying — the only Kubernetes YAML an application needs is the mount of the volume
the recordings are written to:

```yaml
containers:
  - name: app                       # image built with jeffrey-jib
    volumeMounts:
      - { name: jeffrey, mountPath: /mnt/jeffrey }   # = JEFFREY_HOME, where recordings are written
volumes:
  - { name: jeffrey, persistentVolumeClaim: { claimName: jeffrey-pvc } }
```

Optional pod-level env overrides: `JEFFREY_PROJECT_NAME`, `JEFFREY_WORKSPACE_REF_ID`,
`JEFFREY_PROJECT_LABEL`, `JEFFREY_ATTRIBUTES` (`key=value,key=value`),
`JEFFREY_HEAP_DUMP` (`exit`|`crash`|`off`), `JEFFREY_PERF_COUNTERS`,
`JEFFREY_ADDITIONAL_JVM_OPTIONS` (JVM unified logging goes here — pass `-Xlog:…` commands).

Any of these values may contain `<<ENV:NAME>>` or `<<JEFFREY:NAME>>` placeholders,
with an optional `:-default` fallback. Reach for `<<ENV:…>>` where Kubernetes' own `$(VAR)` cannot
help — it expands only variables declared earlier in the same container's `env:` list, so
`JEFFREY_ATTRIBUTES="cluster=$(SF_CLUSTER)"` arrives as literal text when `SF_CLUSTER` comes from
`envFrom` or a webhook. Write `cluster=<<ENV:SF_CLUSTER>>` instead.

**The project name is a stable identity** — it keys the project directory on the shared
volume and links every session to the same project on Jeffrey Hub. Changing it creates a
new project. If you rename the Maven artifactId / Gradle project, pin `projectName` in the
jib configuration to keep the project's history continuous; only the label
(`JEFFREY_PROJECT_LABEL`) is safe to change freely.

## Fail-open guarantee

Any misconfiguration starts the application **without profiling** instead of preventing it
from starting: a missing provisioner binary, a broken or missing config, or a failed
`provisioner init` all log one `profiling DISABLED: <reason>` line and `exec` the original
command. On success the provisioner logs a single greppable verdict:
`Jeffrey profiling ENABLED: project=… workspace=… instance=… session=… profiler_source=…`.

The guarantee is about container start only. **At build time the opposite rule applies**: no
payload on the extension's class path (the bare `jeffrey-jib-maven` / `jeffrey-jib-gradle` was
declared instead of a flavour), two payloads (both flavours declared), a payload missing a binary,
or an unsupported target architecture all fail the build. An image that silently lacks a profiler
looks healthy and profiles nothing, which is the failure this design exists to remove.

## Gradle usage

```kotlin
buildscript {
  dependencies {
    classpath("cafe.jeffrey-analyst:jeffrey-jib-gradle-jar:0.14.0")   // or jeffrey-jib-gradle-native
  }
}

jib {
  pluginExtensions {
    pluginExtension {
      implementation = "cafe.jeffrey.jib.gradle.JeffreyJibGradleExtension"
      configuration(Action<cafe.jeffrey.jib.JeffreyJibConfig> {
        enabled = project.hasProperty("jeffreyProfiling")
        jeffreyHome = "/mnt/azure/runtime/shared/jeffrey"
        baseConfig = "/jeffrey/jeffrey-base.conf"
        overrideConfig = "/jeffrey/jeffrey-overrides.conf"
      })
    }
  }
}
```

## Maven usage

```xml
<plugin>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-maven-plugin</artifactId>
  <dependencies>
    <dependency>
      <groupId>cafe.jeffrey-analyst</groupId>
      <artifactId>jeffrey-jib-maven-jar</artifactId>   <!-- or jeffrey-jib-maven-native -->
      <version>${jeffrey-jib.version}</version>
    </dependency>
  </dependencies>
  <configuration>
    <pluginExtensions>
      <pluginExtension>
        <implementation>cafe.jeffrey.jib.maven.JeffreyJibMavenExtension</implementation>
        <configuration implementation="cafe.jeffrey.jib.JeffreyJibConfig">
          <jeffreyHome>/mnt/azure/runtime/shared/jeffrey</jeffreyHome>
          <baseConfig>/jeffrey/jeffrey-base.conf</baseConfig>
        </configuration>
      </pluginExtension>
    </pluginExtensions>
  </configuration>
</plugin>
```

## Runtime kill switch

Set `JEFFREY_ENABLED=false` (or `0` / `no` / `off`) in the container env to bypass
profiling entirely — the wrapper `exec`s the JIB-produced command verbatim. Useful for
emergency disablement, per-pod opt-out, dev/local runs without the shared volume, and A/B
comparisons. No rebuild required.

## Configuration properties

| Property | Image ENV set | Default |
|---|---|---|
| `enabled` | — | `true` (build-time gate) |
| `jeffreyHome` | `JEFFREY_HOME` | unset (the provisioner also accepts `JEFFREY_WORKSPACES_DIR`) |
| `baseConfig` | `JEFFREY_BASE_CONFIG` | `/jeffrey/jeffrey-base.conf` (optional file) |
| `overrideConfig` | `JEFFREY_OVERRIDE_CONFIG` | `/jeffrey/jeffrey-overrides.conf` (optional) |
| `profilerPath` | `JEFFREY_PROFILER_PATH` | baked: `/opt/jeffrey/libasyncProfiler.so` |
| `argFile` | `JEFFREY_ARG_FILE` | `/tmp/jvm.args` |
| `projectName` | `JEFFREY_PROJECT_NAME` | Maven artifactId / Gradle project name |

Every property is optional, and so is the whole `<configuration>` block. Non-null values are
baked as image-level ENV defaults; Kubernetes pod-level env vars still override them. The
provisioner's own path and kind (`JEFFREY_PROVISIONER_PATH`, `JEFFREY_PROVISIONER_KIND`) are baked
from what the flavour installed and are not configurable.

On a multi-platform build the baked paths carry an `{arch}` placeholder
(`/opt/jeffrey/libasyncProfiler-{arch}.so`) that the wrapper expands from `uname -m` at container
start, because JIB layers are not per-platform and each architecture's file needs a distinct name.

### Which Jeffrey release an image carries

The version of the flavour you declare is a **jeffrey-jib release**, *not* a Jeffrey release
number: jeffrey-jib releases on its own cadence, and which Jeffrey release's provisioner (and which
async-profiler) a given jeffrey-jib release bundles was decided when it was cut. That choice is
recorded in the payload jar's `jeffrey-payload/payload.properties` and the extension prints it
during the build:

```
jeffrey-jib: baking 2 payload file(s) into the image layer 'jeffrey-payload' (45 MB):
  [/opt/jeffrey/provisioner (jeffrey v0.13.22), /opt/jeffrey/libasyncProfiler.so (async-profiler 4.1)]
```

### Bringing your own async-profiler

Setting `profilerPath` means *this image already has async-profiler*. That payload is then not
baked, so a base image that already ships the library pays nothing for a second copy.
Your library has to accept the agent command the provisioner generates, which uses
`event=ctimer`, `jfrsync=default` and `chunksize`; if yours needs different options, replace the
whole command with the provisioner's `profiler-command`.

**There is no `provisionerPath`.** The provisioner is not a third-party component you can
substitute. It is Jeffrey's own binary, and the session layout and workspace events it writes are
the protocol Jeffrey Hub reads. Neither side version-checks that protocol, so an image carrying
someone else's copy would drift from it silently, with nothing recording which copy it had. The
extension therefore always bakes the provisioner, and the flavour chooses only which build of it.
A build still setting `provisionerPath`, `provisionerSource` or `payloadVersion` through the
string `properties` DSL is warned and the value ignored.

### Where the files go during the build

The extension unpacks the payloads and the wrapper script under the build's own output directory
(`target/jeffrey-jib/` on Maven, `build/jeffrey-jib/` on Gradle) and rewrites them only when their
content changes. JIB keys its layer cache on each source file's path and modification time, so the
payload layer is archived and hashed once and then served from the cache; `mvn clean` /
`gradle clean` removes the files like any other build output.

## The `jar` flavour and the JVM environment

The jar provisioner runs on the application's own `java`, as a short-lived second JVM before the
application starts. Every JVM in the container reads the same environment, and three variables are
honoured by any `java` launcher or HotSpot without being asked:

| Variable | Read by | Typical content |
|---|---|---|
| `JDK_JAVA_OPTIONS` | the `java` launcher (JDK 9+) | `-javaagent:…`, `-Xmx…`, `-XX:…` |
| `JAVA_TOOL_OPTIONS` | every HotSpot JVM (and other tools such as `jar`, `javac`) | truststores, proxies, `-XX:MaxRAMPercentage` |
| `_JAVA_OPTIONS` | every HotSpot JVM, applied last | anything, usually by mistake |

Operators use them precisely because they reach the application JVM without touching the command
line — which is also why they would reach the provisioner JVM. The consequences are not cosmetic:

- **A `-javaagent:` is loaded twice.** An OpenTelemetry or APM agent in `JDK_JAVA_OPTIONS`
  instruments the provisioner too: slower init, and a second, short-lived instance of the service
  registering with the agent's backend on every pod start.
- **Memory sized for the application is claimed by a JVM that needs 64 MB.** `-Xmx` or
  `-XX:MaxRAMPercentage` meant for the application applies to the provisioner as well; with
  `-XX:+AlwaysPreTouch` that memory is touched immediately and can push the pod over its limit
  before the application has even started.
- **Diagnostics run twice.** `-XX:StartFlightRecording`, GC logging or heap-dump settings in those
  variables produce a second set of output files from the provisioner, and `JDK_JAVA_OPTIONS`
  additionally prints a `NOTE: Picked up JDK_JAVA_OPTIONS:` line into the startup log.
- **The application's own `-Xshare` / CDS archive is not the provisioner's**, so an
  `-XX:SharedArchiveFile` pointing at an archive built for the application fails to map and logs a
  warning from the provisioner JVM.

For that reason the wrapper **unsets all three variables for the provisioner JVM only** — in a
subshell, so the application's `exec` still sees them untouched. The provisioner does no network
I/O and reads none of those settings, so nothing is lost. If you do need to pass options to the
provisioner JVM (a `-Duser.timezone`, a debugging flag), use the variable that exists for exactly
that:

```yaml
env:
  - name: JEFFREY_PROVISIONER_JAVA_OPTIONS
    value: "-Xmx96m -Dfile.encoding=UTF-8"
```

It is a whitespace-separated list appended after the wrapper's own `-XX:TieredStopAtLevel=1
-XX:+UseSerialGC -Xmx64m`, so a later `-Xmx` wins. Two things it cannot fix:

- **The class-file version floor.** `provisioner.jar` is compiled for the JDK the Jeffrey release
  targets (currently 25). An older application JVM fails with `UnsupportedClassVersionError`, the
  wrapper prints a hint naming that error and starts the application unprofiled. Use the
  `native` flavour for such applications.
- **The application's own JVM is still shared.** `JEFFREY_ADDITIONAL_JVM_OPTIONS` and the other
  `JEFFREY_*` variables are read by the provisioner as *configuration* and written into the
  argfile for the application; they are not options for the provisioner JVM, and the three
  variables above are not read by the provisioner at all.

The native provisioner has none of these concerns: it is not a JVM and ignores all of them.

## Limitations

- Requires a POSIX shell in the base image. True distroless images (`gcr.io/distroless/java-*`)
  lack `/bin/sh` and are incompatible — use the status-quo Kubernetes `command:` pattern
  instead.
- The payload jar is a plugin dependency, so it is fetched wherever the build fetches plugins
  (`<pluginRepositories>` on Maven, the `buildscript` repositories on Gradle) and an air-gapped
  build mirrors it like any other plugin. A `native` flavour is ~90 MB (both architectures, so
  that one dependency serves single- and multi-platform builds alike); the `jar` flavour ~6 MB.
- The `jar` flavour needs the application's JVM to be able to read the provisioner jar's class
  files. When it cannot, the wrapper fails open and the application starts unprofiled — use
  `native` for applications on older JVMs.

## License

Apache License 2.0 — see `LICENSE`.
