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
import DocsFeatureItem from '@/components/docs/DocsFeatureItem.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'rename-frames', text: 'Rename Frames', level: 2 },
  { id: 'workflow', text: 'Workflow', level: 3 },
  { id: 'example', text: 'Example', level: 3 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Tools Section"
        icon="bi bi-tools"
      />

      <div class="docs-content">
        <p>The Tools section provides <strong>utility operations</strong> for modifying and managing profile data. These tools operate directly on the profile's stored data to support common workflows like anonymization and data preparation.</p>

        <h2 id="overview">Overview</h2>
        <p>Profile tools are designed for scenarios where you need to transform profile data before sharing or further analysis:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-pencil-square" title="Rename Frames">
            Search and replace class name patterns across all frames in a profile. Useful for anonymizing proprietary package names before sharing profiles externally.
          </DocsFeatureItem>
        </div>

        <h2 id="rename-frames">Rename Frames</h2>
        <p>The Rename Frames tool allows you to search for a pattern in frame class names and replace it with a new value. This is particularly useful when you need to <strong>anonymize proprietary package or class names</strong> before sharing profiles with external parties.</p>

        <p>Common use cases:</p>
        <ul>
          <li>Replacing internal company package names (e.g., <code>com.company.internal</code> to <code>com.example.app</code>)</li>
          <li>Anonymizing service-specific class names before sharing with vendors</li>
          <li>Simplifying complex package hierarchies for presentation</li>
        </ul>

        <h3 id="workflow">Workflow</h3>
        <p>The rename operation follows a safe preview-then-apply workflow:</p>

        <div class="workflow-steps">
          <div class="workflow-step">
            <div class="step-number">1</div>
            <div class="step-content">
              <h4>Enter patterns</h4>
              <p>Type the <strong>search pattern</strong> to find in class names and the <strong>replacement</strong> value. The search matches any class name containing the pattern.</p>
            </div>
          </div>
          <div class="workflow-step">
            <div class="step-number">2</div>
            <div class="step-content">
              <h4>Preview</h4>
              <p>Click <strong>Preview</strong> to see how the rename will affect your data. Jeffrey shows the total number of affected frames and up to <strong>10 sample transformations</strong> with the original and renamed class names displayed side by side.</p>
            </div>
          </div>
          <div class="workflow-step">
            <div class="step-number">3</div>
            <div class="step-content">
              <h4>Confirm</h4>
              <p>Review the preview results. If the transformation looks correct, confirm the operation. A confirmation dialog warns that the operation is permanent.</p>
            </div>
          </div>
          <div class="workflow-step">
            <div class="step-number">4</div>
            <div class="step-content">
              <h4>Apply</h4>
              <p>The rename is executed across all matching frames in the profile. All profile caches are cleared to reflect the updated data.</p>
            </div>
          </div>
        </div>

        <DocsCallout type="warning">
          <strong>Permanent operation:</strong> Renaming frames modifies the profile data directly in the database. This operation cannot be undone. The only way to revert is to delete the profile and recreate it from the original recording.
        </DocsCallout>

        <h3 id="example">Example</h3>
        <p>To anonymize an internal package name before sharing a profile:</p>

        <table>
          <thead>
            <tr>
              <th>Field</th>
              <th>Value</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><strong>Search</strong></td>
              <td><code>com.company.internal</code></td>
            </tr>
            <tr>
              <td><strong>Replacement</strong></td>
              <td><code>com.example.app</code></td>
            </tr>
          </tbody>
        </table>

        <p>This would transform all frames like:</p>
        <ul>
          <li><code>com.company.internal.service.UserService.findById</code> &rarr; <code>com.example.app.service.UserService.findById</code></li>
          <li><code>com.company.internal.repository.OrderRepo.save</code> &rarr; <code>com.example.app.repository.OrderRepo.save</code></li>
        </ul>

        <DocsCallout type="tip">
          <strong>Multiple renames:</strong> You can run the rename tool multiple times to replace different patterns. Each operation builds on the result of the previous one.
        </DocsCallout>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Workflow Steps */
.workflow-steps {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1.5rem 0;
}

.workflow-step {
  display: flex;
  gap: 1rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.step-number {
  width: 32px;
  height: 32px;
  min-width: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #5e64ff 0%, #4338ca 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.85rem;
}

.step-content {
  flex: 1;
}

.step-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
}

.step-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  line-height: 1.4;
}
</style>
