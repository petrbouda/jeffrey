# jfr-otlp-converter

A standalone CLI that converts a **JDK Flight Recording (JFR)** into the **OpenTelemetry profiles
signal** (`opentelemetry.proto.profiles.v1development`) — a single serialized `ProfilesData`
message written to a `.otlp` file.

## Why not async-profiler's `jfr-converter`?

async-profiler's converter can already emit OTLP, but it does **not** populate the
`profile.frame.type` semantic-convention attribute, so every frame is indistinguishable and
downstream tools render Java, native, and kernel frames identically (all "native").

This converter derives `profile.frame.type` from the JFR frame type on every location:

| JFR frame type | `profile.frame.type` |
|---|---|
| `Interpreted`, `JIT compiled`, `Inlined`, `C1 compiled` | `jvm` |
| `Native`, `C++` | `native` |
| `Kernel` | `kernel` |

> OTLP's `profile.frame.type` has a single `jvm` value, so the JVM sub-types (interpreted / JIT /
> inlined / C1) collapse to `jvm`. The Java-vs-native-vs-kernel distinction is preserved; the
> JIT-vs-interpreted distinction is not representable in OTLP.

## Usage

```bash
java -jar jfr-otlp-converter.jar <input.jfr> <output.otlp> [options]

Options:
  --event cpu|alloc|lock   Which JFR events to convert (default: cpu)
  --service-name NAME      Value of the resource service.name attribute
                           (default: the input file name without extension)
```

### Event categories

| `--event` | JFR event types | OTLP `sample_type` (type/unit) | value |
|---|---|---|---|
| `cpu` (default) | `jdk.ExecutionSample`, `jdk.NativeMethodSample` | `cpu` / `count` | 1 per sample |
| `alloc` | `jdk.ObjectAllocationSample`, `jdk.ObjectAllocationInNewTLAB`, `jdk.ObjectAllocationOutsideTLAB` | `alloc` / `bytes` | allocated bytes |
| `lock` | `jdk.JavaMonitorEnter`, `jdk.JavaMonitorWait`, `jdk.ThreadPark` | `lock` / `nanoseconds` | blocked duration |

Each sample carries its event timestamp (`timestamps_unix_nano`) and the sampled thread
(`thread.name` attribute); the resource carries `service.name`.

## Build

```bash
mvn -pl utilities/jfr-otlp-converter package
# -> utilities/jfr-otlp-converter/target/jfr-otlp-converter.jar (runnable, dependencies shaded in)
```

## Notes

- The OTLP profiles signal is in **Alpha** (`v1development`); the proto shape may still change.
- Reads JFR with the JDK's built-in `jdk.jfr.consumer` — no dependency on Jeffrey's runtime.
- JFR stacks are already leaf-first, matching the OTLP `Stack` convention, so no frame reordering
  is needed.
