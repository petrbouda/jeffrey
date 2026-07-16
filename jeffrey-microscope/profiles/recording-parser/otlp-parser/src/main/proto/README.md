# Vendored OpenTelemetry Protos

These proto files are vendored (copied verbatim) from the
[open-telemetry/opentelemetry-proto](https://github.com/open-telemetry/opentelemetry-proto)
repository, `main` branch, as of 2026-07-15.

Vendored files:

- `opentelemetry/proto/common/v1/common.proto`
- `opentelemetry/proto/resource/v1/resource.proto`
- `opentelemetry/proto/profiles/v1development/profiles.proto`
- `opentelemetry/proto/collector/profiles/v1development/profiles_service.proto`

## Why vendored instead of the `io.opentelemetry.proto` Maven artifact

The profiles signal is in **Alpha** and its proto package is still
`opentelemetry.proto.profiles.v1development` — the message shapes changed several
times during 2025 and a mechanical rename to a stable `v1` package is expected.
Vendoring a pinned copy means:

- Jeffrey upgrades the format on its own schedule (re-vendor + adjust the mappers),
- the generated classes stay compiled against the repository-wide `protobuf-java`
  version (`${protobuf.version}` in the root POM), avoiding runtime version skew,
- the generated OTel types remain an implementation detail of the `otlp-parser`
  module and never leak past its boundary.

## How to re-vendor

Download the four files from the `main` branch (or a tagged release) and overwrite
the copies here, then update the date above and fix any compilation fallout in
`cafe.jeffrey.otlpparser`.

The proto files are licensed under the Apache License 2.0 (see the header in each file).
