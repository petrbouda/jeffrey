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
container start the wrapper runs `jeffrey-cli init` (resolved from
`$JEFFREY_HOME/libs/current/` — populated by `jeffrey-server`'s `copy-libs` feature on a
shared volume) and `exec`s the JIB command with the CLI-produced argfile inserted right
after the `java` binary.

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
| `baseConfig` | `JEFFREY_BASE_CONFIG` | `/jeffrey/jeffrey-base.conf` |
| `overrideConfig` | `JEFFREY_OVERRIDE_CONFIG` | `/jeffrey/jeffrey-overrides.conf` (optional) |
| `cliPath` | `JEFFREY_CLI_PATH` | `${JEFFREY_HOME}/libs/current/jeffrey-cli-<arch>` |
| `argFile` | `JEFFREY_ARG_FILE` | `/tmp/jvm.args` |

All string properties are optional. Non-null values are baked as image-level ENV defaults;
Kubernetes pod-level env vars still override them.

## Limitations

- Requires a POSIX shell in the base image. True distroless images (`gcr.io/distroless/java-*`)
  lack `/bin/sh` and are incompatible — use the status-quo Kubernetes `command:` pattern
  instead.
- Requires a running `jeffrey-server` elsewhere in the cluster with `copy-libs.enabled=true`
  to populate `${JEFFREY_HOME}/libs/current/` on the shared volume.

## License

GNU Affero General Public License v3.0 — see `LICENSE`.
