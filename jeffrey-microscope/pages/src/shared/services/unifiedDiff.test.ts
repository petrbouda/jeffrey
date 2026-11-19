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
import { parseUnifiedDiff } from './unifiedDiff';

const PATCH = `diff --git a/src/main/java/Order.java b/src/main/java/Order.java
--- a/src/main/java/Order.java
+++ b/src/main/java/Order.java
@@ -124,4 +124,5 @@ class Order {
     private final int id;
-    int slow() { return recompute(); }
+    int fast() { return cached; }
+    int cached;
     void other() {}
`;

describe('parseUnifiedDiff', () => {
  it('counts the added and removed lines', () => {
    const parsed = parseUnifiedDiff(PATCH);

    expect(parsed.added).toBe(2);
    expect(parsed.removed).toBe(1);
  });

  it('names the file the hunk belongs to', () => {
    const parsed = parseUnifiedDiff(PATCH);

    expect(parsed.files).toHaveLength(1);
    expect(parsed.files[0].path).toBe('src/main/java/Order.java');
  });

  it('numbers a removed line from the old side', () => {
    const removed = parseUnifiedDiff(PATCH).lines.find(line => line.type === 'del');

    expect(removed?.oldLine).toBe(125);
    expect(removed?.newLine).toBeNull();
  });

  it('numbers an added line from the new side', () => {
    const added = parseUnifiedDiff(PATCH).lines.filter(line => line.type === 'add');

    expect(added[0].newLine).toBe(125);
    expect(added[0].oldLine).toBeNull();
    expect(added[1].newLine).toBe(126);
  });

  it('advances both sides across a context line', () => {
    const context = parseUnifiedDiff(PATCH).lines.filter(line => line.type === 'ctx');

    expect(context[0].oldLine).toBe(124);
    expect(context[0].newLine).toBe(124);
    // after one removal and two additions the sides have diverged
    expect(context[1].oldLine).toBe(126);
    expect(context[1].newLine).toBe(127);
  });

  it('strips the leading marker from the rendered text', () => {
    const added = parseUnifiedDiff(PATCH).lines.find(line => line.type === 'add');

    expect(added?.text).toBe('    int fast() { return cached; }');
  });

  it('returns nothing for an empty patch', () => {
    const parsed = parseUnifiedDiff('');

    expect(parsed.lines).toHaveLength(0);
    expect(parsed.files).toHaveLength(0);
  });

  it('keeps the old path when a file is deleted', () => {
    const parsed = parseUnifiedDiff(
      ['--- a/src/Gone.java', '+++ /dev/null', '@@ -1,1 +0,0 @@', '-gone'].join('\n')
    );

    expect(parsed.files[0].path).toBe('src/Gone.java');
  });
});
