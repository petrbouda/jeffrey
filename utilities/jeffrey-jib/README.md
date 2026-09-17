# jeffrey-jib

JIB plugin extensions (Gradle and Maven) that wrap the container entrypoint so
[Jeffrey](https://github.com/petrbouda/jeffrey) profiling initialises before the app starts,
without forcing operators to override the container `command:` in Kubernetes YAML.

## Modules

| Module | Coordinate | Purpose |
|---|---|---|
| `jeffrey-jib-core` | `cafe.jeffrey-analyst:jeffrey-jib-core` | Shared `ContainerBuildPlan` transformation (pulled transitively). |
| `jeffrey-jib-gradle` | `cafe.jeffrey-analyst:jeffrey-jib-gradle` | `JibGradlePluginExtension` implementation. |
| `jeffrey-jib-maven` | `cafe.jeffrey-analyst:jeffrey-jib-maven` | `JibMavenPluginExtension` implementation. |
| `jeffrey-jib-payload-native` | `cafe.jeffrey-analyst:jeffrey-jib-payload-native` | The GraalVM provisioner, one classified jar per architecture. Resolved by the extension. |
| `jeffrey-jib-payload-jar` | `cafe.jeffrey-analyst:jeffrey-jib-payload-jar` | The provisioner jar, architecture-neutral. Resolved by the extension. |
| `jeffrey-jib-payload-profiler` | `cafe.jeffrey-analyst:jeffrey-jib-payload-profiler` | async-profiler, one classified jar per architecture. Resolved by the extension. |

The extension installs `/usr/local/bin/jeffrey-entrypoint` into a new image layer, makes it
the `ENTRYPOINT`, and moves JIB's auto-derived `java -cp … <MainClass>` into `CMD`. It also
fetches the binaries that run needs — the provisioner and async-profiler — through the build's
own dependency resolution and installs them under `/opt/jeffrey` in a second layer. At container
start the wrapper runs `provisioner init` and `exec`s the JIB command with the
provisioner-produced argfile inserted right after the `java` binary.

**The image is self-contained.** Nothing is fetched, copied or waited for at container start, and
the shared volume is needed only for the recordings the application writes to it.

## Choosing a provisioner build

`provisionerSource` picks which build of the provisioner the image carries:

| | `native` (default) | `jar` |
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

The guarantee is about container start only. **At build time the opposite rule applies**: a payload
that cannot be resolved, an unknown `provisionerSource`, an unsupported target architecture or a
missing `payloadVersion` all fail the build. An image that silently lacks a profiler looks healthy
and profiles nothing, which is the failure this design exists to remove.

## Gradle usage

```kotlin
jib {
  pluginExtensions {
    pluginExtension {
      implementation = "cafe.jeffrey.jib.gradle.JeffreyJibGradleExtension"
      configuration(Action<cafe.jeffrey.jib.JeffreyJibConfig> {
        enabled = project.hasProperty("jeffreyProfiling")
        payloadVersion = "0.14.0"
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
      <artifactId>jeffrey-jib-maven</artifactId>
      <version>${jeffrey-jib.version}</version>
    </dependency>
  </dependencies>
  <configuration>
    <pluginExtensions>
      <pluginExtension>
        <implementation>cafe.jeffrey.jib.maven.JeffreyJibMavenExtension</implementation>
        <configuration implementation="cafe.jeffrey.jib.JeffreyJibConfig">
          <payloadVersion>0.14.0</payloadVersion>
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
| `payloadVersion` | — | **required** — see below |
| `provisionerSource` | `JEFFREY_PROVISIONER_KIND` | `native` |
| `jeffreyHome` | `JEFFREY_HOME` | unset (the provisioner also accepts `JEFFREY_WORKSPACES_DIR`) |
| `baseConfig` | `JEFFREY_BASE_CONFIG` | `/jeffrey/jeffrey-base.conf` (optional file) |
| `overrideConfig` | `JEFFREY_OVERRIDE_CONFIG` | `/jeffrey/jeffrey-overrides.conf` (optional) |
| `provisionerPath` | `JEFFREY_PROVISIONER_PATH` | baked: `/opt/jeffrey/provisioner`, or `provisioner.jar` |
| `profilerPath` | `JEFFREY_PROFILER_PATH` | baked: `/opt/jeffrey/libasyncProfiler.so` |
| `argFile` | `JEFFREY_ARG_FILE` | `/tmp/jvm.args` |
| `projectName` | `JEFFREY_PROJECT_NAME` | Maven artifactId / Gradle project name |

All string properties except `payloadVersion` are optional. Non-null values are baked as
image-level ENV defaults; Kubernetes pod-level env vars still override them.

On a multi-platform build the baked paths carry an `{arch}` placeholder
(`/opt/jeffrey/libasyncProfiler-{arch}.so`) that the wrapper expands from `uname -m` at container
start, because JIB layers are not per-platform and each architecture's file needs a distinct name.

### `payloadVersion` is required

It names the Jeffrey release whose provisioner and async-profiler the image carries. There is no
default: this extension is released on its own cadence, so a guessed version would silently pin an
image to a provisioner nobody chose. The build stops and tells you to set it.

```xml
<payloadVersion>0.14.0</payloadVersion>
```

### Bringing your own binaries

Setting `provisionerPath` or `profilerPath` means *this image already has that binary*. The
matching payload is then not resolved at all, so a base image that already ships async-profiler
pays nothing for a second copy. Setting both skips the payload layer entirely, and
`payloadVersion` is no longer required.

## Limitations

- Requires a POSIX shell in the base image. True distroless images (`gcr.io/distroless/java-*`)
  lack `/bin/sh` and are incompatible — use the status-quo Kubernetes `command:` pattern
  instead.
- Resolves the payload artifacts from Maven Central (or whatever repositories the build is
  configured with) at build time, so the build needs to reach them. An air-gapped build either
  mirrors the three `jeffrey-jib-payload-*` artifacts or sets `provisionerPath` and `profilerPath`
  at binaries the base image already provides.
- `provisionerSource=jar` needs the application's JVM to be able to read the provisioner jar's
  class files. When it cannot, the wrapper fails open and the application starts unprofiled — use
  `native` for applications on older JVMs.

## License

GNU Affero General Public License v3.0 — see `LICENSE`.
