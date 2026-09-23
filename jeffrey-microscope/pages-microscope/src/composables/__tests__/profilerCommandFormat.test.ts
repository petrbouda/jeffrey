/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { describe, expect, it } from 'vitest';
import { formatCommand } from '@/composables/profilerCommandFormat';

const OPTIONS = 'start,loop=15m,file=<<JEFFREY:CURRENT_SESSION>>/profile-%t.jfr';

describe('formatCommand', () => {
  it('quotes the environment variable, so a shell reads neither the heredoc nor the %t', () => {
    expect(formatCommand(OPTIONS, 'env')).toBe(`JEFFREY_PROFILER_COMMAND='${OPTIONS}'`);
  });

  it('escapes a single quote inside the environment variable', () => {
    expect(formatCommand("jfrsync=/it's.jfc", 'env')).toBe(
      "JEFFREY_PROFILER_COMMAND='jfrsync=/it'\\''s.jfc'"
    );
  });

  it('writes the provisioner configuration key as a quoted HOCON string', () => {
    expect(formatCommand(OPTIONS, 'hocon')).toBe(`profiler-command = "${OPTIONS}"`);
  });

  it('escapes quotes and backslashes inside the HOCON string', () => {
    expect(formatCommand('jfrsync=C:\\jfc\\"x".jfc', 'hocon')).toBe(
      'profiler-command = "jfrsync=C:\\\\jfc\\\\\\"x\\".jfc"'
    );
  });

  it('hands the command over unchanged as raw', () => {
    expect(formatCommand(OPTIONS, 'raw')).toBe(OPTIONS);
  });

  it('keeps an empty command empty in every format', () => {
    expect(formatCommand('', 'env')).toBe('');
    expect(formatCommand('', 'hocon')).toBe('');
  });
});
