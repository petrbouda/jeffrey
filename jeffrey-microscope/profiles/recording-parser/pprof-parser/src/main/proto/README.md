# Vendored pprof Proto

`perftools/profiles/profile.proto` is vendored verbatim from
[google/pprof](https://github.com/google/pprof/blob/main/proto/profile.proto) (`main` branch,
2026-07-16). pprof's `profile.proto` has been stable for years (unlike the OpenTelemetry profiles
`v1development` schema), so this rarely needs re-vendoring.

The proto is compiled with the repository's pinned `protobuf-maven-plugin` / protoc
(`${protobuf.version}`), message-only. The generated `com.google.perftools.profiles.*` classes stay
an implementation detail of `pprof-parser` and never leak past its module boundary.

Licensed under the Apache License 2.0 (see the header in `profile.proto`).
