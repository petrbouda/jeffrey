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

The extension installs `/usr/local/bin/jeffrey-entrypoint` into a new image layer, makes it
the `ENTRYPOINT`, and moves JIB's auto-derived `java -cp … <MainClass>` into `CMD`. At
container start the wrapper runs `provisioner init` (resolved from
`$JEFFREY_HOME/libs/current/` — populated by `jeffrey-hub`'s `copy-libs` feature on a
shared volume) and `exec`s the JIB command with the provisioner-produced argfile inserted right
after the `java` binary.

## Zero-config setup

The config file is optional. Without `/jeffrey/jeffrey-base.conf` the provisioner configures
itself from `JEFFREY_*` environment variables, and the extension bakes
`JEFFREY_PROJECT_NAME` into the image (from the `projectName` property, defaulting to the
Maven artifactId / Gradle project name). An image built with this extension is therefore
fully self-identifying — the only Kubernetes YAML an application needs is the shared-volume
mount:

```yaml
containers:
  - name: app                       # image built with jeffrey-jib
    volumeMounts:
      - { name: jeffrey, mountPath: /mnt/jeffrey }   # = JEFFREY_HOME
volumes:
  - { name: jeffrey, persistentVolumeClaim: { claimName: jeffrey-pvc } }
```

Optional pod-level env overrides: `JEFFREY_PROJECT_NAME`, `JEFFREY_WORKSPACE_REF_ID`,
`JEFFREY_PROJECT_LABEL`, `JEFFREY_ATTRIBUTES` (`key=value,key=value`),
`JEFFREY_HEAP_DUMP` (`exit`|`crash`|`off`), `JEFFREY_PERF_COUNTERS`,
`JEFFREY_JVM_LOGGING`, `JEFFREY_ADDITIONAL_JVM_OPTIONS`. A mounted HOCON file still wins
over environment variables wherever it sets a value.

## Fail-open guarantee

Any misconfiguration starts the application **without profiling** instead of preventing it
from starting: a missing provisioner binary, a broken or missing config, or a failed
`provisioner init` all log one `profiling DISABLED: <reason>` line and `exec` the original
command. On success the provisioner logs a single greppable verdict:
`Jeffrey profiling ENABLED: project=… workspace=… instance=… session=… profiler_source=…`.
Set `JEFFREY_WAIT_FOR_LIBS=<seconds>` to wait for the hub's copy-libs to populate the shared
volume on fresh clusters (default `0`).

## Gradle usage

```kotlin
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
      <artifactId>jeffrey-jib-maven</artifactId>
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

| Property | Image ENV set | Default fallback (wrapper) |
|---|---|---|
| `enabled` | — | `true` (build-time gate) |
| `jeffreyHome` | `JEFFREY_HOME` | `/mnt/azure/runtime/shared/jeffrey` |
| `baseConfig` | `JEFFREY_BASE_CONFIG` | `/jeffrey/jeffrey-base.conf` (optional file) |
| `overrideConfig` | `JEFFREY_OVERRIDE_CONFIG` | `/jeffrey/jeffrey-overrides.conf` (optional) |
| `provisionerPath` | `JEFFREY_PROVISIONER_PATH` | `${JEFFREY_HOME}/libs/current/provisioner-<arch>` |
| `argFile` | `JEFFREY_ARG_FILE` | `/tmp/jvm.args` |
| `projectName` | `JEFFREY_PROJECT_NAME` | Maven artifactId / Gradle project name |

All string properties are optional. Non-null values are baked as image-level ENV defaults;
Kubernetes pod-level env vars still override them.

## Limitations

- Requires a POSIX shell in the base image. True distroless images (`gcr.io/distroless/java-*`)
  lack `/bin/sh` and are incompatible — use the status-quo Kubernetes `command:` pattern
  instead.
- Requires a running `jeffrey-hub` elsewhere in the cluster with `copy-libs.enabled=true`
  to populate `${JEFFREY_HOME}/libs/current/` on the shared volume.

## License

GNU Affero General Public License v3.0 — see `LICENSE`.
