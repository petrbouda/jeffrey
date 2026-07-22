# Kubernetes Integration — Remaining Work

Follow-up items from the provisioner/jeffrey-hub analysis and the Kubernetes
integration work (branch `claude/provisioner-jeffrey-hub-analysis-2pvexl`).
Each item is written to be executable later without the original session
context.

## Background — what is already done

The shared-filesystem pipeline (provisioner → shared RWX volume → hub polling)
was hardened and extended with two optional Kubernetes features. Key landmarks
in the code:

| Area | Where |
|---|---|
| Shared on-disk layout contract | `shared/common/.../JeffreyLayout.java` (single source for `workspaces/`, `.events/`, `streaming-repo/`, marker filenames, `.settings` contract) |
| Clean-exit marker | Agent writes `<session>/.heartbeat/finished` on shutdown (`jeffrey-agent/.../HeartbeatProducer.java`); `SessionFinisher.tryFinishFromHeartbeat` checks it before heartbeat staleness |
| Scheduler | `PeriodicalScheduler` — fixed-delay, split executors (`Job.ExecutorGroup`: GLOBAL single thread vs PROJECT_FAN_OUT pool, `jeffrey.hub.scheduler.fan-out-pool-size`) |
| Workspace auto-create (filesystem path) | `WorkspaceEventsReplicatorJob`, opt-in `jeffrey.hub.workspaces.auto-create` |
| Settings provenance + id keying | `ProfilerSettingsResolver.ResolvedProfilerSettings` (source stamped into `.session-info.json`); `ProfilerSettings.projectSettingsById` keyed by origin project id, name map kept for back-compat |
| K8s pod informer | `jeffrey-hub/core-hub/.../kubernetes/{KubernetesDiscovery,PodLifecycleHandler,KubernetesConventions}.java`, enabled by `jeffrey.hub.kubernetes.enabled`; workspace auto-create from `jeffrey.cafe/workspace` label; pod termination → `SessionFinisher.forceFinish` for instance id = pod name |
| Admission webhook | `.../kubernetes/{PodMutator,AdmissionWebhook}.java` + `web/controllers/KubernetesWebhookController.java`, enabled by `jeffrey.hub.kubernetes.webhook.enabled`; injects PVC + emptyDir volumes, provisioner init container, `JDK_JAVA_OPTIONS=@/jeffrey-work/jvm.args`; strictly fail-open |
| Docs | `jeffrey-pages/src/views/docs/hub/deployment/DeploymentKubernetesPage.vue` (properties, annotated Deployment example, label/annotation reference, RBAC + MutatingWebhookConfiguration manifests) |

Conventions established (do not change without updating both sides):
- Labels (informer): `jeffrey.cafe/enabled=true`, `jeffrey.cafe/workspace`.
- Annotations (webhook): `jeffrey.cafe/enabled`, `jeffrey.cafe/workspace`,
  `jeffrey.cafe/project` (default: `app` label → pod name), `jeffrey.cafe/pvc`,
  `jeffrey.cafe/container` (default: first container).
- Webhook mount paths: shared PVC at `/jeffrey-shared` (= `JEFFREY_HOME`),
  emptyDir at `/jeffrey-work`, argfile `/jeffrey-work/jvm.args`.
- Instance id = pod name (provisioner falls back to `HOSTNAME`).

---

## 1. Helm chart wiring (external repo) — REQUIRED to use the new features

The Helm charts live outside this repository (`helm/jeffrey-hub`, referenced by
`jeffrey-pages` deployment docs). The manifests are already documented on the
Kubernetes Integration docs page; they need to become chart templates:

1. **RBAC for the informer** — ServiceAccount for the hub + ClusterRole with
   `pods: get/list/watch` + ClusterRoleBinding (namespace-scoped Role variant
   when `jeffrey.hub.kubernetes.namespace` is set).
2. **Webhook TLS + registration** — the API server requires HTTPS:
   - cert-manager `Certificate` + a Service exposing the hub on 443
     (TLS termination in front of the hub, or native TLS on the hub port),
   - `MutatingWebhookConfiguration` with
     `path: /api/kubernetes/webhook/mutate`, `failurePolicy: Ignore`,
     `sideEffects: None`, `admissionReviewVersions: ["v1"]`, and
     `objectSelector: matchLabels: {jeffrey.cafe/enabled: "true"}` so only
     opted-in pods hit the webhook. CA bundle via
     `cert-manager.io/inject-ca-from`.
3. **values.yaml switches** — `kubernetes.discovery.enabled`,
   `kubernetes.webhook.enabled`, `kubernetes.webhook.initImage`,
   `kubernetes.webhook.pvcClaimName`, mapped to the Spring properties
   (`jeffrey.hub.kubernetes.*`) via the hub Deployment env.

Reference: the exact YAML is in `DeploymentKubernetesPage.vue` (`rbacManifest`,
`webhookManifest` constants).

## 1b. Container-restart gap in webhook mode — REQUIRED, known defect

A container restarted **inside** a running pod (OOM-kill, liveness-probe kill,
graceful restart) does not delete the pod: the pod keeps its name, its phase
stays `Running`, and only `status.containerStatuses[].restartCount` increments.

- **Entrypoint (jeffrey-jib) mode is fine by design**: the provisioner is the
  container entrypoint, so every container restart re-runs `provisioner init`
  and creates a NEW session; the old one finishes via clean-exit marker or
  heartbeat staleness, and the new-session event force-finishes any prior
  unfinished session of the instance.
- **Webhook mode is NOT fine**: Kubernetes re-runs init containers only when
  the pod sandbox is recreated — an app-container restart reuses the same
  argfile and the restarted JVM writes into the SAME session directory:
  1. the session spans multiple JVM runs (`heap-dump.hprof.gz` /
     `hs-jvm-err.log` have fixed names and get overwritten by a second crash);
  2. during CrashLoopBackOff (up to 5 min) the heartbeat goes stale → the hub
     finishes the session, then new chunks land in a finished session;
  3. **worst**: after a graceful container restart the previous run's
     `.heartbeat/finished` marker persists → `SessionFinisher` Case 0 finishes
     the LIVE session immediately → `RepositoryCompressionProjectJob` may
     compress `.jfr` files async-profiler is still writing.

Fix plan (smallest first):
1. **Agent deletes a leftover `finished` marker in premain/start** (before the
   first heartbeat) — `jeffrey-agent/.../HeartbeatProducer.java`. Few lines,
   zero-dependency, eliminates hazard (3); harmless in entrypoint mode.
2. **Informer observes `restartCount`** in `PodLifecycleHandler.onUpdate` — it
   cannot rotate the session (paths are baked into the running JVM's args) but
   should log/emit a JFR message so multi-run sessions are visible.
3. **Pod-liveness-aware finishing**: when K8s discovery is enabled, skip
   heartbeat-staleness finishing for instances whose pod the informer knows is
   alive (informer store lookup from `SessionFinishedDetectorProjectJob` or a
   pluggable `InstanceLivenessProbe`); sessions then finish only on pod
   termination or clean-exit marker. Makes "session = pod lifetime" the
   coherent, documented semantic for webhook mode; document that
   session-per-JVM-run workloads should prefer jib/entrypoint mode.

## 2. Live-cluster smoke test — REQUIRED before calling the integration done

Everything is unit/integration tested, but neither feature has been exercised
against a real kube-apiserver. Suggested kind-based verification:

1. `kind create cluster`; install cert-manager; deploy the hub with both
   features enabled and a `hostPath`-backed RWX PVC (see
   `DeploymentSharedVolumePage.vue` for the minikube/kind fallback pattern).
2. Apply a Deployment whose pod template carries the labels + annotations from
   the docs example (`DeploymentKubernetesPage.vue`, `annotatedDeployment`).
3. Assert:
   - webhook injected init container + volumes + `JDK_JAVA_OPTIONS`
     (`kubectl get pod -o yaml`),
   - provisioner init container succeeded and `/jeffrey-work/jvm.args` exists,
   - workspace auto-created in the hub (from the label, before any events),
   - session appears (JFR chunks in the session dir on the PVC),
   - `kubectl delete pod` → session flips to FINISHED immediately (informer
     path), with `finishedAt` = clean-exit marker timestamp.
4. Negative paths: pod without annotation untouched; webhook down +
   `failurePolicy: Ignore` → pod still schedules; non-Java container with
   `JDK_JAVA_OPTIONS` injected is harmless (variable ignored by non-java
   processes).

Edge cases to watch (known, deliberate trade-offs):
- The init container image must have a POSIX shell + glibc (the provisioner is
  a glibc native binary run from the shared volume). `debian:stable-slim` is
  the default; musl images (alpine) will not work.
- Pods created by controllers have only `generateName` at admission time; the
  project-name fallback chain is annotation → `app` label → pod name, so
  recommend the annotation or `app` label in docs/examples (pod-name fallback
  would create one project per pod).

## 3. Phase 4 — HTTP chunk shipping (OPTIONAL, demand-driven)

Goal: drop the RWX shared-volume requirement — the single biggest operational
constraint (NFS/EFS/CephFS needed on every application node; the webhook still
mounts the PVC into every profiled pod today).

Sketch (from the original analysis):
- Sessions write to a node-local `emptyDir` instead of the shared PVC.
- The agent (or a small shipper) POSTs finished JFR chunks + heartbeat to the
  hub using the JDK built-in `java.net.http.HttpClient` — keeps jeffrey-agent
  zero-dependency (hard constraint; see the duplicated-constants comments in
  `AgentArgs.java`).
- Hub side: an upload endpoint that lands chunks in the same
  `workspaces/<ws>/<project>/<instance>/<session>/` layout (`JeffreyLayout`),
  so everything downstream (detector, compression, streaming, gRPC to
  microscope) is unchanged. Heartbeat over HTTP can eventually replace the
  file heartbeat; stream close = deterministic finish.
- The provisioner would need a `transport = filesystem|http` config plus a hub
  base URL; the webhook variant injects emptyDir-only volumes.

Only worth building if RWX availability actually blocks adoption. Decide
before starting: chunk upload auth story (the hub currently has no
producer-facing authn).

## 4. Housekeeping

- **Fix `HeapDumpControllerTest` on master** — broken by commit `7995cbb`
  (added a `HeapDumpInitService` constructor parameter to `HeapDumpController`
  without updating the test). Unrelated to this branch; fails on any tree.
- **`GrpcDocsControllerTest` needs `protoc-gen-doc`** — the
  `generate-grpc-docs` exec in `shared/hub-api/pom.xml` requires `protoc` +
  `protoc-gen-doc` on PATH; in constrained environments build with
  `-Dexec.skip=true` and expect this one test to fail. Consider a Maven
  profile that skips the exec + test together when the tools are absent.
- **Deploy jeffrey-pages** — the provisioner-docs fixes and the new Kubernetes
  Integration page are committed here but the site (petrbouda.github.io) has
  not been redeployed; run the `update-jeffrey-pages` flow from a machine with
  access to that repo.
- **Future idea noted in code** — hub-side heartbeat freshness tracker
  (remember `(value, firstObservedAt)` per session, compute staleness from hub
  clock deltas) to remove clock-skew sensitivity from the crash-detection
  path; see the Javadoc on `SessionFinisher.tryFinishFromHeartbeat`.
