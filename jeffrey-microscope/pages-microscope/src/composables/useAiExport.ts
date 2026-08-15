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

import ToastService from '@shared/services/ToastService';

const DOWNLOAD_MIME = 'text/markdown';

/**
 * One thing that can be handed to an AI: how to render it, and what to call it.
 *
 * The composable deliberately does not know what is being exported. A flamegraph, a trace and an
 * operation are rendered by different endpoints into the same kind of document, and the copy /
 * download / toast machinery around them is identical — so the part that differs is passed in rather
 * than branched on.
 */
export interface AiExportSource {
  /** Produces the Markdown. Rendered server-side, where the semantics of the data are known. */
  fetch: () => Promise<string>;
  /** What the exported thing is, for the toast: "Flamegraph", "Trace", "Operation". */
  label: string;
  /** Filename stem for a download; the timestamp and extension are added here. */
  filenameStem: string;
}

export function useAiExport() {
  async function copyToClipboard(source: AiExportSource): Promise<void> {
    try {
      const text = await source.fetch();
      await navigator.clipboard.writeText(text);
      ToastService.success(
        'Copied for AI',
        `${source.label} export copied to clipboard. Paste it into Claude Code.`
      );
    } catch (e) {
      console.error('AI export copy failed', e);
      ToastService.error('Copy Failed', 'Could not generate or copy the export.');
    }
  }

  async function downloadAsFile(source: AiExportSource): Promise<void> {
    try {
      const text = await source.fetch();
      const blob = new Blob([text], { type: DOWNLOAD_MIME });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = buildFilename(source.filenameStem);
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      ToastService.success('Downloaded', `${source.label} export saved as Markdown.`);
    } catch (e) {
      console.error('AI export download failed', e);
      ToastService.error('Download Failed', 'Could not generate the export file.');
    }
  }

  return { copyToClipboard, downloadAsFile };
}

/** Sanitised and timestamped, so repeated exports of the same thing do not overwrite each other. */
export function buildFilename(stem: string): string {
  const safeStem = stem.replace(/[^A-Za-z0-9_-]/g, '_').slice(0, 64);
  return `jeffrey-${safeStem}-${formatTimestamp(new Date())}.md`;
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
