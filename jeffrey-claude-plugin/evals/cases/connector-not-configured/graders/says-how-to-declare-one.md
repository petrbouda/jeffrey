---
type: llm
focus: last_message
---

Jeffrey answers `connector_status` with `state: NOT_CONFIGURED`: this Microscope declares no connector,
so production's recordings cannot be reached from here. Declaring one is the user's step, in
configuration only, followed by a restart: `jeffrey.microscope.connector.azure-files.*` (endpoint,
share, protocol, a SAS token or `auth=entra`) for the volume on an Azure file share, or
`jeffrey.microscope.connector.local.path` for a directory on this machine.

PASS if the reply says no connector is configured, gives the `jeffrey.microscope.connector.*`
properties for at least one of the two kinds — with placeholders where the user gave no names — says
Microscope must be restarted with them, and offers to analyse a local recording meanwhile or to check
again once it has restarted.

FAIL if it claims anything about production's recordings, sessions or slowness, invents an account,
share, endpoint or path as if it were the user's, says a tool or a page of the UI can declare the
connector, or answers from the checkout profiles already in the catalogue as if they were production's.
