<template>
  <div>
    <MainCard>
    <template #header>
      <PageHeader icon="bi bi-sliders" title="Settings" />
    </template>

    <!-- Restart Banner -->
    <div v-if="restartRequired" class="restart-banner">
      <i class="bi bi-exclamation-triangle"></i>
      <span>Settings have been modified. Restart the application to apply changes.</span>
    </div>

    <!-- Encryption Warning -->
    <div v-if="encryptionMode === 'USER_BOUND'" class="encryption-warning">
      <i class="bi bi-info-circle"></i>
      <span>Machine-specific encryption unavailable. Secrets are encrypted with user-level binding only.</span>
    </div>

    <!-- Tabs -->
    <div class="settings-tabs">
      <button
        class="settings-tab"
        :class="{ active: activeTab === 'ai' }"
        @click="activeTab = 'ai'">
        <i class="bi bi-robot"></i>
        AI Configuration
      </button>
      <button
        class="settings-tab"
        :class="{ active: activeTab === 'general' }"
        @click="activeTab = 'general'">
        <i class="bi bi-gear"></i>
        General
      </button>
      <button
        class="settings-tab"
        :class="{ active: activeTab === 'visualization' }"
        @click="activeTab = 'visualization'">
        <i class="bi bi-bar-chart"></i>
        Visualization
      </button>
    </div>

    <!-- AI Configuration Tab -->
    <div v-if="activeTab === 'ai'" class="settings-content">
      <div class="content-header-with-toggle">
        <div class="toggle-area">
          <span class="toggle-label">Enable AI</span>
          <label class="toggle-switch">
            <input type="checkbox" v-model="aiToggle" @change="onAiToggleChange" />
            <span class="toggle-slider"></span>
          </label>
        </div>
      </div>

      <div class="settings-form-grid" :class="{ 'settings-form-disabled': !aiEnabled }">
        <div class="settings-form-group">
          <label class="settings-label">Provider</label>
          <select :value="settings.get('jeffrey.local.ai.provider')" @change="setSetting('jeffrey.local.ai.provider', ($event.target as HTMLSelectElement).value)" class="form-control select-with-indicator" :disabled="!aiEnabled">
            <option value="claude">Claude (Anthropic)</option>
            <option value="chatgpt">ChatGPT (OpenAI)</option>
          </select>
        </div>
        <div class="settings-form-group">
          <label class="settings-label">Model</label>
          <input
            type="text"
            :value="settings.get('jeffrey.local.ai.model')"
            @input="setSetting('jeffrey.local.ai.model', ($event.target as HTMLInputElement).value)"
            class="form-control"
            :disabled="!aiEnabled"
            placeholder="Enter model name" />
        </div>
        <div class="settings-form-group">
          <label class="settings-label">API Key</label>
          <div class="password-wrap">
            <input
              :type="showApiKey ? 'text' : 'password'"
              :value="settings.get('jeffrey.local.ai.api-key')"
              @input="setSetting('jeffrey.local.ai.api-key', ($event.target as HTMLInputElement).value)"
              class="form-control"
              :disabled="!aiEnabled"
              placeholder="Enter your API key" />
            <button class="toggle-eye" @click="showApiKey = !showApiKey">
              <i :class="showApiKey ? 'bi bi-eye-slash' : 'bi bi-eye'"></i>
            </button>
          </div>
          <div class="settings-hint">
            <i class="bi bi-lock"></i> Encrypted at rest with machine-bound key
          </div>
        </div>
        <div class="settings-form-group">
          <label class="settings-label">Max Tokens</label>
          <input
            type="number"
            :value="settings.get('jeffrey.local.ai.max-tokens')"
            @input="setSetting('jeffrey.local.ai.max-tokens', ($event.target as HTMLInputElement).value)"
            class="form-control"
            :disabled="!aiEnabled"
            placeholder="128000" />
          <div class="settings-hint">Maximum token limit per AI request</div>
        </div>
      </div>

      <div v-if="currentModels.length > 0" class="models-reference" :class="{ 'settings-form-disabled': !aiEnabled }">
        <h4 class="models-reference-title">Available Models</h4>
        <table class="models-table">
          <thead>
            <tr>
              <th>Model</th>
              <th>Max Output Tokens</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="model in currentModels"
              :key="model.id"
              class="model-row"
              :class="{ 'model-row-selected': settings.get('jeffrey.local.ai.model') === model.id }"
              @click="selectModel(model)">
              <td>{{ model.id }}</td>
              <td>{{ model.maxTokens.toLocaleString() }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="settings-actions">
        <button class="btn-primary" @click="saveAiSettings" :disabled="saving || !aiEnabled">
          {{ saving ? 'Saving...' : 'Save Changes' }}
        </button>
      </div>
    </div>

    <!-- General Tab -->
    <div v-if="activeTab === 'general'" class="settings-content">
      <div class="settings-form-grid settings-form-grid-single">
        <div class="settings-form-group">
          <label class="settings-label">Log Level</label>
          <select :value="settings.get('logging.level.pbouda.jeffrey')" @change="setSetting('logging.level.pbouda.jeffrey', ($event.target as HTMLSelectElement).value)" class="form-control select-with-indicator" style="max-width: 300px;">
            <option value="INFO">INFO</option>
            <option value="DEBUG">DEBUG</option>
            <option value="TRACE">TRACE</option>
          </select>
          <div class="settings-hint">Requires restart to take effect</div>
        </div>
      </div>

      <div class="settings-actions">
        <button class="btn-primary" @click="saveGeneralSettings" :disabled="saving">
          {{ saving ? 'Saving...' : 'Save Changes' }}
        </button>
      </div>
    </div>

    <!-- Visualization Tab -->
    <div v-if="activeTab === 'visualization'" class="settings-content">
      <div class="settings-form-grid settings-form-grid-single">
        <div class="settings-form-group">
          <label class="settings-label">Flamegraph — Minimum Frame Threshold (%)</label>
          <input
            type="number"
            :value="settings.get('jeffrey.local.visualization.flamegraph.min-frame-threshold-pct')"
            @input="setSetting('jeffrey.local.visualization.flamegraph.min-frame-threshold-pct', ($event.target as HTMLInputElement).value)"
            class="form-control"
            style="max-width: 300px;"
            min="0"
            max="100"
            step="0.01"
            placeholder="0.05" />
          <div class="settings-hint">
            Frames representing less than this percentage of total samples will be hidden from flamegraphs.
            Set to 0 to show all frames. Default: 0.05%
          </div>
        </div>
      </div>

      <div class="settings-form-grid settings-form-grid-single" style="margin-top: 20px">
        <div class="settings-form-group">
          <label class="settings-label">Flamegraph — Frame Text Mode</label>
          <div class="settings-hint" style="margin-bottom: 10px">
            Choose the default text rendering for flamegraph frames. Can also be toggled per-flamegraph.
          </div>
          <div class="frame-mode-cards">
            <div
              class="frame-mode-card"
              :class="{ selected: frameTextMode === 'single-line' }"
              @click="frameTextMode = 'single-line'">
              <canvas ref="previewSingleLine" class="frame-mode-preview"></canvas>
              <div class="frame-mode-label">Single-line</div>
            </div>
            <div
              class="frame-mode-card"
              :class="{ selected: frameTextMode === 'two-line' }"
              @click="frameTextMode = 'two-line'">
              <canvas ref="previewTwoLine" class="frame-mode-preview"></canvas>
              <div class="frame-mode-label">Two-line</div>
            </div>
          </div>
        </div>
      </div>

      <div class="settings-actions">
        <button class="btn-primary" @click="saveVisualizationSettings" :disabled="saving">
          {{ saving ? 'Saving...' : 'Save Changes' }}
        </button>
      </div>
    </div>
    </MainCard>
  </div>
</template>

<script setup lang="ts">
import '@/styles/form-utilities.css'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import SettingsClient from '@/services/api/SettingsClient'
import MainCard from '@/components/MainCard.vue'
import PageHeader from '@/components/PageHeader.vue'

interface ModelInfo {
  id: string
  maxTokens: number
}

const claudeModels: ModelInfo[] = [
  { id: 'claude-opus-4-6', maxTokens: 128000 },
  { id: 'claude-sonnet-4-6', maxTokens: 64000 },
  { id: 'claude-haiku-4-5-20251001', maxTokens: 64000 },
  { id: 'claude-sonnet-4-5-20250929', maxTokens: 64000 },
  { id: 'claude-opus-4-5-20251101', maxTokens: 64000 },
  { id: 'claude-sonnet-4-20250514', maxTokens: 64000 },
]

const chatgptModels: ModelInfo[] = [
  { id: 'gpt-4o', maxTokens: 16384 },
  { id: 'gpt-4o-mini', maxTokens: 16384 },
  { id: 'gpt-4.1', maxTokens: 32768 },
  { id: 'gpt-4.1-mini', maxTokens: 32768 },
  { id: 'o3', maxTokens: 100000 },
  { id: 'o3-mini', maxTokens: 100000 },
  { id: 'o4-mini', maxTokens: 100000 },
]

const client = new SettingsClient()

const activeTab = ref('ai')
watch(activeTab, (tab) => {
  if (tab === 'visualization') {
    nextTick(() => drawPreviews())
  }
})
const restartRequired = ref(false)
const showApiKey = ref(false)
const saving = ref(false)
const encryptionMode = ref('')

const settings = reactive(new Map<string, string>())
const frameTextMode = ref('single-line')

const previewSingleLine = ref<HTMLCanvasElement | null>(null)
const previewTwoLine = ref<HTMLCanvasElement | null>(null)

const aiToggle = ref(false)
const aiEnabled = computed(() => aiToggle.value)

const currentModels = computed(() => {
  const provider = settings.get('jeffrey.local.ai.provider')
  if (provider === 'claude') return claudeModels
  if (provider === 'chatgpt') return chatgptModels
  return []
})

function setSetting(name: string, value: string) {
  settings.set(name, value)
}

function selectModel(model: ModelInfo) {
  settings.set('jeffrey.local.ai.model', model.id)
  settings.set('jeffrey.local.ai.max-tokens', String(model.maxTokens))
}

onMounted(async () => {
  try {
    const [fetched, status] = await Promise.all([
      client.fetchAll(),
      client.fetchStatus()
    ])

    restartRequired.value = status.restartRequired
    encryptionMode.value = status.encryptionMode

    for (const setting of fetched) {
      settings.set(setting.name, setting.value)
    }

    aiToggle.value = settings.get('jeffrey.local.ai.provider') !== 'none'
    frameTextMode.value = settings.get('jeffrey.local.visualization.flamegraph.frame-text-mode') || 'single-line'

    nextTick(() => drawPreviews())
  } catch (e) {
    console.error('Failed to load settings', e)
  }
})

function drawPreviews() {
  drawSingleLinePreview()
  drawTwoLinePreview()
}

function setupCanvas(canvas: HTMLCanvasElement, cssWidth: number, cssHeight: number): CanvasRenderingContext2D {
  const dpr = devicePixelRatio || 1
  canvas.style.width = cssWidth + 'px'
  canvas.style.height = cssHeight + 'px'
  canvas.width = cssWidth * dpr
  canvas.height = cssHeight * dpr
  const ctx = canvas.getContext('2d')!
  ctx.scale(dpr, dpr)
  return ctx
}

const PREVIEW_WIDTH = 320
const PREVIEW_HEIGHT = 90
const PREVIEW_COLORS = ['#94f25a', '#94f25a', '#cce880']

function drawSingleLinePreview() {
  const canvas = previewSingleLine.value
  if (!canvas) return
  const ctx = setupCanvas(canvas, PREVIEW_WIDTH, PREVIEW_HEIGHT)
  const fh = 20
  const FONT_N = '11px -apple-system, BlinkMacSystemFont, sans-serif'
  const FONT_B = 'bold 11px -apple-system, BlinkMacSystemFont, sans-serif'
  const FONT_I = 'italic 11px -apple-system, BlinkMacSystemFont, sans-serif'
  const packages = ['org.apache.catalina.core.', 'org.apache.catalina.core.', 'org.apache.catalina.authenticator.']
  const classes = ['StandardEngineValve', 'StandardHostValve', 'AuthenticatorBase']
  const methods = ['.invoke', '.invoke', '.invoke']

  ctx.clearRect(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT)
  for (let i = 0; i < 3; i++) {
    const y = i * fh + (PREVIEW_HEIGHT - 3 * fh) / 2
    ctx.fillStyle = PREVIEW_COLORS[i]
    ctx.fillRect(0, y, PREVIEW_WIDTH, fh)
    ctx.strokeStyle = 'white'
    ctx.lineWidth = 1
    ctx.strokeRect(0, y, PREVIEW_WIDTH, fh)

    let cx = 3
    ctx.fillStyle = 'rgba(0,0,0,0.7)'
    ctx.font = FONT_N
    ctx.fillText(packages[i], cx, y + 14)
    cx += ctx.measureText(packages[i]).width

    ctx.fillStyle = '#000'
    ctx.font = FONT_B
    ctx.fillText(classes[i], cx, y + 14)
    cx += ctx.measureText(classes[i]).width

    ctx.fillStyle = '#000'
    ctx.font = FONT_I
    ctx.fillText(methods[i], cx, y + 14)
  }
}

function drawTwoLinePreview() {
  const canvas = previewTwoLine.value
  if (!canvas) return
  const ctx = setupCanvas(canvas, PREVIEW_WIDTH, PREVIEW_HEIGHT)
  const fh = 30
  const classes = ['StandardEngineValve', 'StandardHostValve', 'AuthenticatorBase']
  const methods = ['.invoke', '.invoke', '.invoke']
  const packages = ['org.apache.catalina.core', 'org.apache.catalina.core', 'org.apache.catalina.authenticator']

  ctx.clearRect(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT)
  for (let i = 0; i < 3; i++) {
    const y = i * fh
    ctx.fillStyle = PREVIEW_COLORS[i]
    ctx.fillRect(0, y, PREVIEW_WIDTH, fh)
    ctx.strokeStyle = 'white'
    ctx.lineWidth = 1
    ctx.strokeRect(0, y, PREVIEW_WIDTH, fh)

    // Line 1: bold Class + italic .method
    let cx = 7
    ctx.fillStyle = '#000'
    ctx.font = 'bold 11px -apple-system, BlinkMacSystemFont, sans-serif'
    ctx.fillText(classes[i], cx, y + 13)
    cx += ctx.measureText(classes[i]).width

    ctx.font = 'italic 11px -apple-system, BlinkMacSystemFont, sans-serif'
    ctx.fillText(methods[i], cx, y + 13)

    // Line 2: package
    ctx.fillStyle = 'rgba(0,0,0,0.7)'
    ctx.font = '10px -apple-system, BlinkMacSystemFont, sans-serif'
    ctx.fillText(packages[i], 7, y + 25, PREVIEW_WIDTH - 12)
  }
}

async function onAiToggleChange() {
  if (!aiToggle.value) {
    saving.value = true
    try {
      await client.upsert('ai', 'jeffrey.local.ai.provider', 'none', false)
      settings.set('jeffrey.local.ai.provider', 'none')
      restartRequired.value = true
    } catch (e) {
      console.error('Failed to disable AI', e)
      aiToggle.value = true
    } finally {
      saving.value = false
    }
  } else {
    if (settings.get('jeffrey.local.ai.provider') === 'none' || !settings.get('jeffrey.local.ai.provider')) {
      settings.set('jeffrey.local.ai.provider', 'claude')
      settings.set('jeffrey.local.ai.model', 'claude-opus-4-6')
    }
  }
}

async function saveAiSettings() {
  saving.value = true
  try {
    const apiKey = settings.get('jeffrey.local.ai.api-key') || ''
    await Promise.all([
      client.upsert('ai', 'jeffrey.local.ai.provider', settings.get('jeffrey.local.ai.provider') || '', false),
      client.upsert('ai', 'jeffrey.local.ai.model', settings.get('jeffrey.local.ai.model') || '', false),
      client.upsert('ai', 'jeffrey.local.ai.max-tokens', settings.get('jeffrey.local.ai.max-tokens') || '', false),
      ...(apiKey && !apiKey.includes('****')
        ? [client.upsert('ai', 'jeffrey.local.ai.api-key', apiKey, true)]
        : [])
    ])
    restartRequired.value = true
  } catch (e) {
    console.error('Failed to save AI settings', e)
  } finally {
    saving.value = false
  }
}

async function saveGeneralSettings() {
  saving.value = true
  try {
    await client.upsert('logging', 'logging.level.pbouda.jeffrey', settings.get('logging.level.pbouda.jeffrey') || '', false)
    restartRequired.value = true
  } catch (e) {
    console.error('Failed to save general settings', e)
  } finally {
    saving.value = false
  }
}

async function saveVisualizationSettings() {
  saving.value = true
  try {
    await Promise.all([
      client.upsert(
        'visualization',
        'jeffrey.local.visualization.flamegraph.min-frame-threshold-pct',
        settings.get('jeffrey.local.visualization.flamegraph.min-frame-threshold-pct') || '',
        false
      ),
      client.upsert(
        'visualization',
        'jeffrey.local.visualization.flamegraph.frame-text-mode',
        frameTextMode.value,
        false
      )
    ])
    restartRequired.value = true
  } catch (e) {
    console.error('Failed to save visualization settings', e)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.restart-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff8e1;
  border: 1px solid #ffe082;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 20px;
  color: #8d6e00;
  font-size: 13px;
  font-weight: 500;
}

.encryption-warning {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #e3f2fd;
  border: 1px solid #90caf9;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 20px;
  color: #1565c0;
  font-size: 13px;
  font-weight: 500;
}

.settings-tabs {
  display: flex;
  gap: 0;
  border-bottom: 2px solid var(--color-border, #e2e8f0);
  margin-bottom: 24px;
}

.settings-tab {
  padding: 12px 24px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted, #718096);
  cursor: pointer;
  border: none;
  background: none;
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 0.2s;
}

.settings-tab i {
  font-size: 16px;
}

.settings-tab::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 100%;
  height: 2px;
  background: transparent;
  transition: background 0.2s;
}

.settings-tab.active {
  color: var(--color-primary, #5e64ff);
}

.settings-tab.active::after {
  background: var(--color-primary, #5e64ff);
}

.settings-tab:hover {
  color: var(--color-text, #4a5568);
}

.settings-content {
  padding-top: 4px;
}

.content-header {
  margin-bottom: 24px;
}

.content-header h3 {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 4px;
}

.content-header p {
  font-size: 12px;
  color: var(--color-text-muted, #718096);
  margin: 0;
}

.settings-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 24px;
}

.settings-form-grid-single {
  grid-template-columns: 1fr;
}

.settings-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-dark, #0b1727);
  margin-bottom: 6px;
  display: block;
}

.settings-hint {
  font-size: 11px;
  color: var(--color-text-muted, #748194);
  margin-top: 4px;
}

.password-wrap {
  position: relative;
}

.password-wrap .form-control {
  padding-right: 40px;
}

.password-wrap .toggle-eye {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: var(--color-text-muted, #748194);
  cursor: pointer;
  font-size: 16px;
  padding: 4px;
}

.settings-actions {
  display: flex;
  justify-content: flex-end;
}

.content-header-with-toggle {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.content-header-with-toggle h3 {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 4px;
}

.content-header-with-toggle p {
  font-size: 12px;
  color: var(--color-text-muted, #718096);
  margin: 0;
}

.toggle-area {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.toggle-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text, #5e6e82);
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: #cbd5e0;
  border-radius: 24px;
  transition: 0.3s;
}

.toggle-slider:before {
  position: absolute;
  content: "";
  height: 18px;
  width: 18px;
  left: 3px;
  bottom: 3px;
  background: white;
  border-radius: 50%;
  transition: 0.3s;
}

.toggle-switch input:checked + .toggle-slider {
  background: var(--color-primary, #5e64ff);
}

.toggle-switch input:checked + .toggle-slider:before {
  transform: translateX(20px);
}

.settings-form-disabled {
  opacity: 0.4;
  pointer-events: none;
}

.btn-primary {
  padding: 9px 24px;
  background: var(--color-primary, #5e64ff);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary:hover {
  background: var(--color-primary-hover, #4c52db);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.form-control:disabled {
  background: var(--color-bg-hover, #f9fafd);
  cursor: not-allowed;
  opacity: 0.7;
}

.models-reference {
  margin-bottom: 24px;
}

.models-reference-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted, #718096);
  margin-bottom: 8px;
}

.models-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.models-table th {
  text-align: left;
  padding: 8px 12px;
  font-weight: 600;
  color: var(--color-text-muted, #718096);
  border-bottom: 1px solid var(--color-border, #e2e8f0);
}

.models-table td {
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-border, #e2e8f0);
}

.model-row {
  cursor: pointer;
  transition: background 0.15s;
}

.model-row:hover {
  background: var(--color-bg-hover, #f0f4ff);
}

.model-row-selected {
  background: var(--color-bg-hover, #f0f4ff);
  font-weight: 600;
}

.frame-mode-cards {
  display: flex;
  gap: 16px;
}

.frame-mode-card {
  border: 2px solid var(--color-border, #e2e8f0);
  border-radius: 6px;
  padding: 10px;
  cursor: pointer;
  transition: all 0.15s;
  background: var(--color-bg-card, #fff);
}

.frame-mode-card:hover {
  border-color: var(--color-text-muted, #748194);
}

.frame-mode-card.selected {
  border-color: var(--color-primary, #5e64ff);
  box-shadow: 0 0 0 1px var(--color-primary, #5e64ff);
}

.frame-mode-preview {
  display: block;
  border-radius: 3px;
}

.frame-mode-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-dark, #0b1727);
  margin-top: 8px;
  text-align: center;
}

</style>
