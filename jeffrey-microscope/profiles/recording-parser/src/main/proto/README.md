# Vendored Protos

Two sets of proto files are vendored (copied verbatim) here and compiled with the repository's
pinned `protobuf-maven-plugin` / protoc (`${protobuf.version}`), message-only. The generated classes
(`com.google.perftools.profiles.*`, `io.opentelemetry.proto.*`) stay an implementation detail of
`recording-parser` and never leak past its module boundary: `module-info.java` exports only the
parser packages.

All proto files are licensed under the Apache License 2.0 (see the header in each file).

## pprof

`perftools/profiles/profile.proto` is vendored from
[google/pprof](https://github.com/google/pprof/blob/main/proto/profile.proto) (`main` branch,
2026-07-16). pprof's `profile.proto` has been stable for years (unlike the OpenTelemetry profiles
`v1development` schema), so this rarely needs re-vendoring. It is read by `cafe.jeffrey.microscope.profile.parser.pprof`.

## OpenTelemetry

Vendored from the
[open-telemetry/opentelemetry-proto](https://github.com/open-telemetry/opentelemetry-proto)
repository, `main` branch, as of 2026-07-15:

- `opentelemetry/proto/common/v1/common.proto`
- `opentelemetry/proto/resource/v1/resource.proto`
- `opentelemetry/proto/profiles/v1development/profiles.proto`
- `opentelemetry/proto/collector/profiles/v1development/profiles_service.proto`

### Why vendored instead of the `io.opentelemetry.proto` Maven artifact

The profiles signal is in **Alpha** and its proto package is still
`opentelemetry.proto.profiles.v1development` — the message shapes changed several
times during 2025 and a mechanical rename to a stable `v1` package is expected.
Vendoring a pinned copy means:

- Jeffrey upgrades the format on its own schedule (re-vendor + adjust the mappers),
- the generated classes stay compiled against the repository-wide `protobuf-java`
  version (`${protobuf.version}` in the root POM), avoiding runtime version skew,
- the generated OTel types remain an implementation detail of this module.

### How to re-vendor

Download the four files from the `main` branch (or a tagged release) and overwrite
the copies here, then update the date above and fix any compilation fallout in
`cafe.jeffrey.microscope.profile.parser.otlp`.
