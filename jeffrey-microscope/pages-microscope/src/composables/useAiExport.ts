/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import FlamegraphAiExportClient from '@/services/api/FlamegraphAiExportClient';
import ToastService from '@/services/ToastService';

const DOWNLOAD_MIME = 'text/markdown';

export interface AiExportInput {
  profileId: string;
  eventType: string;
  useWeight: boolean | null;
  useThreadMode: boolean;
  search: string | null;
  excludeNonJavaSamples: boolean;
  excludeIdleSamples: boolean;
  onlyUnsafeAllocationSamples: boolean;
}

export function useAiExport() {
  async function fetchMarkdown(input: AiExportInput): Promise<string> {
    const client = new FlamegraphAiExportClient(input.profileId);
    return client.generate({
      eventType: input.eventType,
      useWeight: input.useWeight,
      useThreadMode: input.useThreadMode,
      search: input.search,
      excludeNonJavaSamples: input.excludeNonJavaSamples,
      excludeIdleSamples: input.excludeIdleSamples,
      onlyUnsafeAllocationSamples: input.onlyUnsafeAllocationSamples
    });
  }

  async function copyToClipboard(input: AiExportInput): Promise<void> {
    try {
      const text = await fetchMarkdown(input);
      await navigator.clipboard.writeText(text);
      ToastService.success(
        'Copied for AI',
        'Flamegraph export copied to clipboard. Paste it into Claude Code.'
      );
    } catch (e) {
      console.error('AI export copy failed', e);
      ToastService.error('Copy Failed', 'Could not generate or copy the export.');
    }
  }

  async function downloadAsFile(input: AiExportInput): Promise<void> {
    try {
      const text = await fetchMarkdown(input);
      const blob = new Blob([text], { type: DOWNLOAD_MIME });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = buildFilename(input.profileId, input.eventType);
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      ToastService.success('Downloaded', 'Flamegraph export saved as Markdown.');
    } catch (e) {
      console.error('AI export download failed', e);
      ToastService.error('Download Failed', 'Could not generate the export file.');
    }
  }

  return { copyToClipboard, downloadAsFile };
}

function buildFilename(profileId: string, eventType: string): string {
  const ts = formatTimestamp(new Date());
  const safeProfileId = profileId.replace(/[^A-Za-z0-9_-]/g, '_').slice(0, 32);
  const eventTypeShort = eventType.replace(/^[a-z]+\./, '').toLowerCase();
  return `jeffrey-flamegraph-${safeProfileId}-${eventTypeShort}-${ts}.md`;
}

function formatTimestamp(d: Date): string {
  const pad = (n: number) => n.toString().padStart(2, '0');
  return (
    d.getUTCFullYear().toString() +
    pad(d.getUTCMonth() + 1) +
    pad(d.getUTCDate()) +
    pad(d.getUTCHours()) +
    pad(d.getUTCMinutes())
  );
}
