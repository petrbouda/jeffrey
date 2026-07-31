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

import { describe, expect, it } from 'vitest';
import MarkdownRenderer from '@/services/MarkdownRenderer';

describe('MarkdownRenderer', () => {
  describe('rendering', () => {
    it('renders headings and emphasis', () => {
      const html = MarkdownRenderer.render('## Summary\n\nThe **hot** path.');

      expect(html).toContain('<h2>Summary</h2>');
      expect(html).toContain('<strong>hot</strong>');
    });

    it('renders inline code, which recommendations lean on heavily', () => {
      const html = MarkdownRenderer.render('Call `RateTable.lookup` once.');

      expect(html).toContain('<code>RateTable.lookup</code>');
    });

    it('returns an empty string for empty input', () => {
      expect(MarkdownRenderer.render('')).toBe('');
      expect(MarkdownRenderer.render(null)).toBe('');
      expect(MarkdownRenderer.render(undefined)).toBe('');
    });
  });

  describe('sanitizing', () => {
    it('strips script tags', () => {
      const html = MarkdownRenderer.render('Before<script>alert(1)</script>After');

      expect(html).not.toContain('<script');
      expect(html).not.toContain('alert(1)');
    });

    it('strips event handlers smuggled through quoted source', () => {
      // A recommendation quotes source the model read. A repository containing this in a comment or
      // string literal must not be able to execute anything when the report is rendered.
      const html = MarkdownRenderer.render('<img src=x onerror="alert(1)">');

      expect(html).not.toContain('onerror');
    });

    it('strips javascript: URLs from links', () => {
      const html = MarkdownRenderer.render('[click](javascript:alert(1))');

      expect(html).not.toContain('javascript:');
    });

    it('keeps ordinary links intact', () => {
      const html = MarkdownRenderer.render('[docs](https://example.test/docs)');

      expect(html).toContain('href="https://example.test/docs"');
    });
  });
});
