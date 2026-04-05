<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
-->

<script setup lang="ts">
import { onMounted } from 'vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'how-to-access', text: 'How to Access', level: 2 },
  { id: 'how-it-works', text: 'How It Works', level: 2 },
  { id: 'example-prompts', text: 'Example Prompts', level: 2 },
  { id: 'oql-syntax-reference', text: 'OQL Syntax Reference', level: 2 },
  { id: 'tips', text: 'Tips', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const exampleLongStrings = `SELECT s FROM java.lang.String s WHERE s.value.length > 1000`;

const exampleLargeArrays = `SELECT {array: x, size: sizeof(x)}
FROM byte[] x
WHERE sizeof(x) > 1024
ORDER BY sizeof(x) DESC`;

const exampleLargeHashMaps = `SELECT m FROM java.util.HashMap m WHERE m.size > 1000`;

const exampleThreadNames = `SELECT {thread: t, name: t.name} FROM java.lang.Thread t`;

const syntaxBasicSelect = `SELECT x FROM java.lang.String x`;

const syntaxFiltering = `SELECT x FROM java.lang.String x WHERE x.value.length > 100`;

const syntaxStructured = `SELECT {name: x.name, size: sizeof(x)} FROM java.lang.Thread x`;

const syntaxSizeFunctions = `-- Shallow size (object's own memory)
SELECT x, sizeof(x) FROM java.lang.String x

-- Retained size (memory freed if object is collected)
SELECT x, rsizeof(x) FROM java.util.HashMap x`;

const syntaxReferences = `-- Objects that reference x
SELECT referrers(x) FROM java.lang.String x

-- Objects that x references
SELECT referees(x) FROM java.util.HashMap x

-- All objects reachable from x
SELECT reachables(x) FROM java.lang.ClassLoader x`;

const syntaxClassLookup = `-- Get the class of an object
SELECT classof(x) FROM java.lang.Object x

-- Find a class by name
SELECT heap.findClass("java.util.HashMap")`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="OQL Assistant"
      icon="bi bi-chat-square-text"
    />

    <div class="docs-content">
      <p>The OQL (Object Query Language) Assistant helps you write heap dump queries without memorizing the OQL syntax. Describe what you want to find in natural language, and the AI generates the corresponding OQL query.</p>

      <h2 id="how-to-access">How to Access</h2>
      <p>Navigate to any heap dump profile, open the <strong>OQL</strong> tab, and click the AI assistant button.</p>

      <h2 id="how-it-works">How It Works</h2>
      <ol>
        <li>Describe what you want to find (e.g., "find all strings longer than 1000 characters")</li>
        <li>The AI generates an OQL query based on your description</li>
        <li>Review the generated query and execute it</li>
        <li>Ask follow-up questions to refine the query</li>
      </ol>

      <DocsCallout type="tip">
        You can always edit the generated OQL before executing it. The assistant is a starting point, not a final answer.
      </DocsCallout>

      <h2 id="example-prompts">Example Prompts</h2>

      <h3>"Find all strings longer than 1000 characters"</h3>
      <DocsCodeBlock :code="exampleLongStrings" language="sql" />

      <h3>"Show the 10 largest byte arrays"</h3>
      <DocsCodeBlock :code="exampleLargeArrays" language="sql" />

      <h3>"Find HashMap instances with more than 1000 entries"</h3>
      <DocsCodeBlock :code="exampleLargeHashMaps" language="sql" />

      <h3>"List all Thread objects and their names"</h3>
      <DocsCodeBlock :code="exampleThreadNames" language="sql" />

      <h2 id="oql-syntax-reference">OQL Syntax Reference</h2>
      <p>A brief reference for the most commonly used OQL constructs:</p>

      <h3>Basic Selection</h3>
      <DocsCodeBlock :code="syntaxBasicSelect" language="sql" />

      <h3>Filtering</h3>
      <DocsCodeBlock :code="syntaxFiltering" language="sql" />

      <h3>Structured Results</h3>
      <DocsCodeBlock :code="syntaxStructured" language="sql" />

      <h3>Size Functions</h3>
      <DocsCodeBlock :code="syntaxSizeFunctions" language="sql" />

      <h3>Reference Traversal</h3>
      <DocsCodeBlock :code="syntaxReferences" language="sql" />

      <h3>Class Lookup</h3>
      <DocsCodeBlock :code="syntaxClassLookup" language="sql" />

      <h2 id="tips">Tips</h2>
      <ul>
        <li><strong>Be specific about types</strong> &mdash; mention the class or type you are looking for to get more precise queries</li>
        <li><strong>Mention sorting or limits</strong> &mdash; if you want results ordered or limited, include that in your description</li>
        <li><strong>Follow suggestions</strong> &mdash; the assistant suggests follow-up queries based on results</li>
        <li><strong>Edit before executing</strong> &mdash; you can modify the generated OQL to fine-tune the query before running it</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
