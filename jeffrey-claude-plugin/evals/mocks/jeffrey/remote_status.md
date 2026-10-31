---
type: fixed
---

No remote is configured for this installation. The remote is the recording volume on an Azure file share, declared in configuration only; declare it and restart Microscope (auth=entra signs in with the machine's Azure identity instead of a SAS token; read-only=true refuses every change):

  jeffrey.microscope.remote.name=Production
  jeffrey.microscope.remote.azure-files.endpoint=https://<account>.file.core.windows.net
  jeffrey.microscope.remote.azure-files.share=recordings
  jeffrey.microscope.remote.azure-files.protocol=smb
  jeffrey.microscope.remote.azure-files.sas-token=<SAS token>

Recordings can still be analysed from a local file with recordings_analyzeFile.

---
Open in Microscope: http://localhost:8590/remote
