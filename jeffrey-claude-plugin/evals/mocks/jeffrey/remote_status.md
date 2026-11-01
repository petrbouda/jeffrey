---
type: fixed
---

{"state":"NOT_CONFIGURED","reason":"No remote is configured for this installation. The remote is the recording volume on an Azure file share, declared in configuration only; declare it and restart Microscope (auth=entra signs in with the machine's Azure identity instead of a SAS token; read-only=true refuses every change):\n\n  jeffrey.microscope.remote.name=Production\n  jeffrey.microscope.remote.azure-files.endpoint=https://<account>.file.core.windows.net\n  jeffrey.microscope.remote.azure-files.share=recordings\n  jeffrey.microscope.remote.azure-files.protocol=smb\n  jeffrey.microscope.remote.azure-files.sas-token=<SAS token>\n\nRecordings can still be analysed from a local file with recordings_analyzeFile.","name":null,"address":null,"readOnly":null,"version":null,"followUp":{"nextTools":[],"guidance":[]},"uiLink":"http://localhost:8586/remote"}
