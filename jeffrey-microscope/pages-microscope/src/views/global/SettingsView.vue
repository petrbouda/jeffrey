<template>
  <div>
    <MainCard>
      <template #header>
        <MainCardHeader icon="bi bi-sliders" title="Settings" />
      </template>

      <!-- Encryption Warning -->
      <div v-if="encryptionMode === 'USER_BOUND'" class="encryption-warning">
        <i class="bi bi-info-circle"></i>
        <span
          >Machine-specific encryption unavailable. Secrets are encrypted with user-level binding
          only.</span
        >
      </div>

      <!-- Tabs -->
      <div class="settings-tabs">
        <button
          class="settings-tab"
          :class="{ active: activeTab === 'ai' }"
          @click="activeTab = 'ai'"
        >
          <i class="bi bi-robot"></i>
          AI Configuration
        </button>
        <button
          class="settings-tab"
          :class="{ active: activeTab === 'general' }"
          @click="activeTab = 'general'"
        >
          <i class="bi bi-gear"></i>
          General
        </button>
        <button
          class="settings-tab"
          :class="{ active: activeTab === 'visualization' }"
          @click="activeTab = 'visualization'"
        >
          <i class="bi bi-bar-chart"></i>
          Visualization
        </button>
        <button
          class="settings-tab"
          :class="{ active: activeTab === 'ai-export' }"
          @click="activeTab = 'ai-export'"
        >
          <i class="bi bi-stars"></i>
          AI Export
        </button>
        <button
          class="settings-tab"
          :class="{ active: activeTab === 'mcp' }"
          @click="activeTab = 'mcp'"
        >
          <i class="bi bi-plug"></i>
          Coding Agents (MCP)
        </button>
      </div>

      <!-- AI Configuration Tab -->
      <div v-if="activeTab === 'ai'" class="settings-content">
        <div class="content-header-with-toggle">
          <div class="toggle-area">
            <span class="toggle-label">Enable AI</span>
            <label class="toggle-switch">
              <input
                type="checkbox"
                class="toggle-input"
                v-model="aiToggle"
                @change="onAiToggleChange"
              />
              <span class="toggle-slider"></span>
            </label>
          </div>
        </div>

        <div v-if="isClaudeCode" class="ai-subscription-panel">
          <i class="bi bi-stars ai-sub-icon"></i>
          <div class="ai-sub-body">
            <div class="ai-sub-title">Claude Code uses your Claude subscription</div>
            <div class="ai-sub-point">
              <i class="bi bi-credit-card-2-front"></i>
              <span>
                Your <strong>Claude (Anthropic) subscription will be used</strong> — no API key
                required.
              </span>
            </div>
            <div class="ai-sub-point">
              <i class="bi bi-shield-check"></i>
              <span>
                By enabling, you
                <strong
                  >agree to comply with Anthropic's
                  <a
                    href="https://www.anthropic.com/legal/consumer-terms"
                    target="_blank"
                    rel="noopener noreferrer"
                    >Terms of Service</a
                  ></strong
                >.
              </span>
            </div>
          </div>
        </div>

        <div class="settings-form-grid" :class="{ 'settings-form-disabled': !aiEnabled }">
          <div class="settings-form-group">
            <label class="settings-label">Provider</label>
            <select
              :value="settings.get('jeffrey.microscope.ai.provider')"
              @change="
                setSetting(
                  'jeffrey.microscope.ai.provider',
                  ($event.target as HTMLSelectElement).value
                )
              "
              class="form-control select-with-indicator"
              :disabled="!aiEnabled"
            >
              <option value="claude">Claude (Anthropic)</option>
              <option value="claude-code">Claude Code (subscription)</option>
              <option value="chatgpt">ChatGPT (OpenAI)</option>
              <option value="ollama">Ollama (self-hosted)</option>
            </select>
          </div>
          <div class="settings-form-group">
            <label class="settings-label">Model</label>
            <input
              type="text"
              :value="settings.get('jeffrey.microscope.ai.model')"
              @input="
                setSetting('jeffrey.microscope.ai.model', ($event.target as HTMLInputElement).value)
              "
              class="form-control"
              :disabled="!aiEnabled"
              placeholder="Enter model name"
            />
          </div>
          <div v-if="isOllama" class="settings-form-group">
            <label class="settings-label">Base URL</label>
            <input
              type="text"
              :value="settings.get('jeffrey.microscope.ai.base-url')"
              @input="
                setSetting(
                  'jeffrey.microscope.ai.base-url',
                  ($event.target as HTMLInputElement).value
                )
              "
              class="form-control"
              :disabled="!aiEnabled"
              placeholder="http://localhost:11434"
            />
            <div class="settings-hint">
              <i class="bi bi-hdd-network"></i> URL of your self-hosted Ollama server
            </div>
          </div>
          <div v-else-if="isClaudeCode" class="settings-form-group">
            <label class="settings-label">Claude CLI Path</label>
            <input
              type="text"
              :value="settings.get('jeffrey.microscope.ai.cli-path')"
              @input="
                setSetting(
                  'jeffrey.microscope.ai.cli-path',
                  ($event.target as HTMLInputElement).value
                )
              "
              class="form-control"
              :disabled="!aiEnabled"
              placeholder="claude"
            />
            <div class="settings-hint">
              <i class="bi bi-terminal"></i> Uses your logged-in Claude subscription via the Claude
              Code CLI — no API key required. The CLI must be installed and authenticated on the
              host running Jeffrey.
            </div>
          </div>
          <div v-else class="settings-form-group">
            <label class="settings-label">API Key</label>
            <div class="password-wrap">
              <input
                :type="showApiKey ? 'text' : 'password'"
                :value="settings.get('jeffrey.microscope.ai.api-key')"
                @input="
                  setSetting(
                    'jeffrey.microscope.ai.api-key',
                    ($event.target as HTMLInputElement).value
                  )
                "
                class="form-control"
                :disabled="!aiEnabled"
                placeholder="Enter your API key"
              />
              <button class="toggle-eye" @click="showApiKey = !showApiKey">
                <i :class="showApiKey ? 'bi bi-eye-slash' : 'bi bi-eye'"></i>
              </button>
            </div>
            <div class="settings-hint">
              <i class="bi bi-lock"></i> Encrypted at rest with machine-bound key
            </div>
          </div>
          <div v-if="!isClaudeCode" class="settings-form-group">
            <label class="settings-label">Max Tokens</label>
            <input
              type="number"
              :value="settings.get('jeffrey.microscope.ai.max-tokens')"
              @input="
                setSetting(
                  'jeffrey.microscope.ai.max-tokens',
                  ($event.target as HTMLInputElement).value
                )
              "
              class="form-control"
              :disabled="!aiEnabled"
              placeholder="128000"
            />
            <div class="settings-hint">Maximum token limit per AI request</div>
          </div>
          <div v-if="isClaudeCode" class="settings-form-group">
            <label class="settings-label">Timeout (seconds)</label>
            <input
              type="number"
              :value="settings.get('jeffrey.microscope.ai.timeout-seconds')"
              @input="
                setSetting(
                  'jeffrey.microscope.ai.timeout-seconds',
                  ($event.target as HTMLInputElement).value
                )
              "
              class="form-control"
              :disabled="!aiEnabled"
              placeholder="600"
            />
            <div class="settings-hint">
              Maximum time to wait for a Claude Code response. Agentic tool loops can take longer
              than a single API call.
            </div>
          </div>
        </div>

        <div
          v-if="currentModels.length > 0"
          class="models-reference"
          :class="{ 'settings-form-disabled': !aiEnabled }"
        >
          <h4 class="models-reference-title">Available Models</h4>
          <DataTable>
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
                :class="{
                  'model-row-selected': settings.get('jeffrey.microscope.ai.model') === model.id
                }"
                @click="selectModel(model)"
              >
                <td>{{ model.id }}</td>
                <td>{{ model.maxTokens.toLocaleString() }}</td>
              </tr>
            </tbody>
          </DataTable>
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
            <select
              :value="settings.get('logging.level.cafe.jeffrey')"
              @change="
                setSetting('logging.level.cafe.jeffrey', ($event.target as HTMLSelectElement).value)
              "
              class="form-control select-with-indicator"
              style="max-width: 300px"
            >
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
              :value="
                settings.get('jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct')
              "
              @input="
                setSetting(
                  'jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct',
                  ($event.target as HTMLInputElement).value
                )
              "
              class="form-control"
              style="max-width: 300px"
              min="0"
              max="100"
              step="0.01"
              placeholder="0.05"
            />
            <div class="settings-hint">
              Frames representing less than this percentage of total samples will be hidden from
              flamegraphs. Set to 0 to show all frames. Default: 0.05%
            </div>
          </div>
        </div>

        <div class="settings-form-grid settings-form-grid-single" style="margin-top: 20px">
          <div class="settings-form-group">
            <label class="settings-label">Flamegraph — Frame Text Mode</label>
            <div class="settings-hint" style="margin-bottom: 10px">
              Choose the default text rendering for flamegraph frames. Can also be toggled
              per-flamegraph.
            </div>
            <div class="frame-mode-cards">
              <div
                class="frame-mode-card"
                :class="{ selected: frameTextMode === 'single-line' }"
                @click="frameTextMode = 'single-line'"
              >
                <canvas ref="previewSingleLine" class="frame-mode-preview"></canvas>
                <div class="frame-mode-label">Single-line</div>
              </div>
              <div
                class="frame-mode-card"
                :class="{ selected: frameTextMode === 'two-line' }"
                @click="frameTextMode = 'two-line'"
              >
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

      <!-- AI Export Tab -->
      <div v-if="activeTab === 'ai-export'" id="ai-export" class="settings-content">
        <div class="settings-form-grid settings-form-grid-single">
          <div class="settings-form-group">
            <label class="settings-label">Flamegraph — Minimum Frame Threshold (%)</label>
            <input
              type="number"
              :value="
                settings.get('jeffrey.microscope.ai-export.flamegraph.min-frame-threshold-pct')
              "
              @input="
                setSetting(
                  'jeffrey.microscope.ai-export.flamegraph.min-frame-threshold-pct',
                  ($event.target as HTMLInputElement).value
                )
              "
              class="form-control"
              style="max-width: 300px"
              min="0"
              max="100"
              step="0.01"
              placeholder="1.0"
            />
            <div class="settings-hint">
              Subtrees representing less than this percentage of total samples are dropped from the
              AI export. Same semantics as the visualization threshold, but tuned coarser to keep
              the LLM payload compact. Default: 1.0%
            </div>
          </div>
        </div>

        <div class="settings-actions">
          <button class="btn-primary" @click="saveAiExportSettings" :disabled="saving">
            {{ saving ? 'Saving...' : 'Save Changes' }}
          </button>
        </div>
      </div>

      <!-- Coding Agents (MCP) Tab -->
      <div v-if="activeTab === 'mcp'" id="mcp" class="settings-content">
        <div class="settings-form-grid settings-form-grid-single">
          <div class="settings-form-group">
            <label class="settings-label">MCP Server</label>
            <div class="mcp-state">
              <Badge
                :value="mcpEnabled ? 'Serving' : 'Disabled'"
                :variant="mcpEnabled ? 'success' : 'secondary'"
                :icon="mcpEnabled ? 'bi bi-check-circle-fill' : 'bi bi-slash-circle'"
                size="s"
              />
            </div>
            <div class="settings-hint">
              Lets an interactive coding agent in your own repository — Claude Code, Codex, or
              anything else that speaks MCP — work with this Jeffrey: list the analysed profiles,
              query their DuckDB tables, and pull flamegraph, trace and heap dump exports. Every
              analysis tool is read-only.
            </div>
            <div v-if="mcpEnabled" class="mcp-state">
              <Badge
                :value="mcpHubsEnabled ? 'Hubs on' : 'Hubs off'"
                :variant="mcpHubsEnabled ? 'success' : 'secondary'"
                :icon="mcpHubsEnabled ? 'bi bi-hdd-network' : 'bi bi-slash-circle'"
                size="s"
              />
              <Badge
                :value="mcpTokenRequired ? 'Token required' : 'No token'"
                :variant="mcpTokenRequired ? 'success' : 'warning'"
                :icon="mcpTokenRequired ? 'bi bi-shield-lock-fill' : 'bi bi-shield-exclamation'"
                size="s"
              />
            </div>
            <div v-if="mcpEnabled" class="settings-hint">
              The agent can also analyse a recording for you: point it at a <code>.jfr</code> or
              <code>.hprof</code> file and it imports the file and builds the profile, without you
              opening this UI. Jeffrey opens the path itself, so the file has to be on this machine
              &mdash; one more reason to keep the endpoint on localhost or behind a token.
            </div>
            <div v-if="mcpEnabled && mcpHubsEnabled" class="settings-hint">
              The agent can also reach the Jeffrey Hubs this Microscope is connected to: it lists
              the recording sessions on them and pulls one in to analyse, so a question like
              <em>&ldquo;what did production record in the last hour&rdquo;</em> never needs a
              download by hand. Set <code>jeffrey.microscope.mcp.hubs.enabled=false</code> and
              restart to keep the agent to what is already here.
            </div>
            <div v-else-if="mcpEnabled" class="settings-hint">
              Hub access is off, so the agent works only with recordings already on this machine.
              Remove <code>jeffrey.microscope.mcp.hubs.enabled=false</code> and restart to let it
              pull recordings from a connected hub.
            </div>
            <div v-if="mcpEnabled" class="settings-hint">
              It can also build what a heap dump needs before it can be read &mdash; the index, the
              dominator tree that retained sizes come from, and the cached reports like Leak Suspects
              &mdash; so a dump nobody has opened here still answers. Those runs are the expensive
              ones: each can hold a core for minutes.
            </div>
            <div v-if="mcpEnabled && !mcpTokenRequired" class="settings-hint">
              The endpoint has no token, so anything that can reach this address can read every
              profile here. That is fine while Jeffrey is on localhost. Before exposing it to another
              machine, set <code>jeffrey.microscope.mcp.token</code> and restart &mdash; the snippets
              below then carry the header your client has to send.
            </div>
            <div v-if="!mcpEnabled" class="settings-hint">
              Serving is on unless the deployment turns it off. This one sets
              <code>jeffrey.microscope.mcp.enabled=false</code>, so the endpoint answers
              <code>404</code>. Remove that property and restart Jeffrey to serve again.
            </div>
          </div>
        </div>

        <div v-if="mcpEnabled && mcpStatus" class="settings-form-grid settings-form-grid-single">
          <div class="settings-form-group">
            <label class="settings-label">Register with the Claude Code CLI</label>
            <div class="mcp-snippet">
              <code>{{ mcpStatus.claudeMcpAddCommand }}</code>
              <button
                class="btn-secondary mcp-copy"
                @click="copyToClipboard(mcpStatus.claudeMcpAddCommand, 'Command')"
              >
                <i class="bi bi-clipboard"></i>
                Copy
              </button>
            </div>
            <div class="settings-hint">
              Run this once in the repository you want to analyse from, then ask Claude to list the
              profiles.
            </div>
          </div>

          <div class="settings-form-group">
            <label class="settings-label">Or check it in as <code>.mcp.json</code></label>
            <div class="mcp-snippet">
              <pre>{{ mcpStatus.mcpJsonSnippet }}</pre>
              <button
                class="btn-secondary mcp-copy"
                @click="copyToClipboard(mcpStatus.mcpJsonSnippet, 'Snippet')"
              >
                <i class="bi bi-clipboard"></i>
                Copy
              </button>
            </div>
            <div class="settings-hint">
              Shares the server with everyone working in that repository.
            </div>
          </div>

          <div class="settings-form-group">
            <label class="settings-label">Register with the Codex CLI</label>
            <div class="mcp-snippet">
              <code>{{ mcpStatus.codexMcpAddCommand }}</code>
              <button
                class="btn-secondary mcp-copy"
                @click="copyToClipboard(mcpStatus.codexMcpAddCommand, 'Command')"
              >
                <i class="bi bi-clipboard"></i>
                Copy
              </button>
            </div>
            <div class="settings-hint">
              Registers the server for every repository you open with Codex.
            </div>
          </div>

          <div class="settings-form-group">
            <label class="settings-label">Or write it into <code>~/.codex/config.toml</code></label>
            <div class="mcp-snippet">
              <pre>{{ mcpStatus.codexConfigTomlSnippet }}</pre>
              <button
                class="btn-secondary mcp-copy"
                @click="copyToClipboard(mcpStatus.codexConfigTomlSnippet, 'Snippet')"
              >
                <i class="bi bi-clipboard"></i>
                Copy
              </button>
            </div>
            <div class="settings-hint">
              Also how a Codex user reaches a Jeffrey that is not on the port the plugin ships with —
              Codex has no per-install setting for the endpoint.
            </div>
          </div>
        </div>

        <div v-if="mcpEnabled" class="settings-hint mcp-security-note">
          <i class="bi bi-shield-exclamation"></i>
          The MCP endpoint has no authentication yet, exactly like the rest of Jeffrey's API: anyone
          who can reach this address can read every profile here — and, while ingestion is on, have
          Jeffrey open a file from this machine. Keep Jeffrey bound to localhost, or put it behind an
          SSH tunnel or an authenticating reverse proxy, before opening this on a shared network.
        </div>
      </div>
    </MainCard>
  </div>
</template>

<script setup lang="ts">
import '@shared/styles/form-utilities.css';
import '@shared/styles/shared-components.css';
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import SettingsClient, { type SettingUpdate } from '@/services/api/SettingsClient';
import MessageBus from '@/services/MessageBus';
import ToastService from '@shared/services/ToastService';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import Badge from '@shared/components/Badge.vue';
import McpAccessClient, { type McpAccessStatus } from '@/services/api/McpAccessClient';

const route = useRoute();
const SUPPORTED_TABS: ReadonlySet<string> = new Set([
  'ai',
  'general',
  'visualization',
  'ai-export',
  'mcp'
]);

interface ModelInfo {
  id: string;
  maxTokens: number;
}

// One entry per model line, always its latest release. Aliases rather than dated snapshot
// IDs — an alias resolves to the current release, so the list does not go stale on the next
// point release. Older versions stay usable: the Model field above takes any ID you type.
const claudeModels: ModelInfo[] = [
  { id: 'claude-fable-5', maxTokens: 128000 },
  { id: 'claude-opus-5', maxTokens: 128000 },
  { id: 'claude-sonnet-5', maxTokens: 128000 },
  { id: 'claude-haiku-4-5', maxTokens: 64000 }
];

const chatgptModels: ModelInfo[] = [
  { id: 'gpt-5.5', maxTokens: 128000 },
  { id: 'gpt-5.4', maxTokens: 128000 },
  { id: 'gpt-4.1', maxTokens: 32768 },
  { id: 'gpt-4o', maxTokens: 16384 }
];

// Tool-capable Ollama models are recommended — the DuckDB/heap-dump analysis
// features rely on tool calling. maxTokens maps to Ollama's num_predict (max output).
const ollamaModels: ModelInfo[] = [
  { id: 'llama4', maxTokens: 8192 },
  { id: 'qwen3', maxTokens: 8192 },
  { id: 'gemma4', maxTokens: 8192 },
  { id: 'mistral-small', maxTokens: 8192 }
];

const client = new SettingsClient();
const mcpAccessClient = new McpAccessClient();

const activeTab = ref('ai');
watch(activeTab, tab => {
  if (tab === 'visualization') {
    nextTick(() => drawPreviews());
  }
});
const showApiKey = ref(false);
const saving = ref(false);
const encryptionMode = ref('');

const settings = reactive(new Map<string, string>());
const frameTextMode = ref('single-line');

const previewSingleLine = ref<HTMLCanvasElement | null>(null);
const previewTwoLine = ref<HTMLCanvasElement | null>(null);

const aiToggle = ref(false);
const mcpStatus = ref<McpAccessStatus | null>(null);
// Whether the endpoint serves is the server's answer, not a stored preference: it comes from an
// application property fixed at startup, so the page reports it rather than offering to change it.
const mcpEnabled = computed(() => mcpStatus.value?.enabled === true);
const mcpHubsEnabled = computed(() => mcpStatus.value?.hubsEnabled === true);
const mcpTokenRequired = computed(() => mcpStatus.value?.tokenRequired === true);
const aiEnabled = computed(() => aiToggle.value);

const isOllama = computed(() => settings.get('jeffrey.microscope.ai.provider') === 'ollama');
const isClaudeCode = computed(
  () => settings.get('jeffrey.microscope.ai.provider') === 'claude-code'
);

const currentModels = computed(() => {
  const provider = settings.get('jeffrey.microscope.ai.provider');
  if (provider === 'claude' || provider === 'claude-code') return claudeModels;
  if (provider === 'chatgpt') return chatgptModels;
  if (provider === 'ollama') return ollamaModels;
  return [];
});

function setSetting(name: string, value: string) {
  settings.set(name, value);
}

function selectModel(model: ModelInfo) {
  settings.set('jeffrey.microscope.ai.model', model.id);
  settings.set('jeffrey.microscope.ai.max-tokens', String(model.maxTokens));
}

onMounted(async () => {
  const hash = route.hash.replace(/^#/, '');
  if (SUPPORTED_TABS.has(hash)) {
    activeTab.value = hash;
  }

  try {
    const [fetched, status] = await Promise.all([client.fetchAll(), client.fetchStatus()]);

    encryptionMode.value = status.encryptionMode;

    for (const setting of fetched) {
      settings.set(setting.name, setting.value);
    }

    aiToggle.value = settings.get('jeffrey.microscope.ai.provider') !== 'none';
    await loadMcpStatus();
    frameTextMode.value =
      settings.get('jeffrey.microscope.visualization.flamegraph.frame-text-mode') || 'single-line';

    nextTick(() => drawPreviews());
  } catch (e) {
    console.error('Failed to load settings', e);
  }
});

function drawPreviews() {
  drawSingleLinePreview();
  drawTwoLinePreview();
}

function setupCanvas(
  canvas: HTMLCanvasElement,
  cssWidth: number,
  cssHeight: number
): CanvasRenderingContext2D {
  const dpr = devicePixelRatio || 1;
  canvas.style.width = cssWidth + 'px';
  canvas.style.height = cssHeight + 'px';
  canvas.width = cssWidth * dpr;
  canvas.height = cssHeight * dpr;
  const ctx = canvas.getContext('2d')!;
  ctx.scale(dpr, dpr);
  return ctx;
}

const PREVIEW_WIDTH = 320;
const PREVIEW_HEIGHT = 90;
const PREVIEW_COLORS = ['#94f25a', '#94f25a', '#cce880'];

function drawSingleLinePreview() {
  const canvas = previewSingleLine.value;
  if (!canvas) return;
  const ctx = setupCanvas(canvas, PREVIEW_WIDTH, PREVIEW_HEIGHT);
  const fh = 20;
  const FONT_N = '11px -apple-system, BlinkMacSystemFont, sans-serif';
  const FONT_B = 'bold 11px -apple-system, BlinkMacSystemFont, sans-serif';
  const FONT_I = 'italic 11px -apple-system, BlinkMacSystemFont, sans-serif';
  const packages = [
    'org.apache.catalina.core.',
    'org.apache.catalina.core.',
    'org.apache.catalina.authenticator.'
  ];
  const classes = ['StandardEngineValve', 'StandardHostValve', 'AuthenticatorBase'];
  const methods = ['.invoke', '.invoke', '.invoke'];

  ctx.clearRect(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT);
  for (let i = 0; i < 3; i++) {
    const y = i * fh + (PREVIEW_HEIGHT - 3 * fh) / 2;
    ctx.fillStyle = PREVIEW_COLORS[i];
    ctx.fillRect(0, y, PREVIEW_WIDTH, fh);
    ctx.strokeStyle = 'white';
    ctx.lineWidth = 1;
    ctx.strokeRect(0, y, PREVIEW_WIDTH, fh);

    let cx = 3;
    ctx.fillStyle = 'rgba(0,0,0,0.7)';
    ctx.font = FONT_N;
    ctx.fillText(packages[i], cx, y + 14);
    cx += ctx.measureText(packages[i]).width;

    ctx.fillStyle = '#000';
    ctx.font = FONT_B;
    ctx.fillText(classes[i], cx, y + 14);
    cx += ctx.measureText(classes[i]).width;

    ctx.fillStyle = '#000';
    ctx.font = FONT_I;
    ctx.fillText(methods[i], cx, y + 14);
  }
}

function drawTwoLinePreview() {
  const canvas = previewTwoLine.value;
  if (!canvas) return;
  const ctx = setupCanvas(canvas, PREVIEW_WIDTH, PREVIEW_HEIGHT);
  const fh = 30;
  const classes = ['StandardEngineValve', 'StandardHostValve', 'AuthenticatorBase'];
  const methods = ['.invoke', '.invoke', '.invoke'];
  const packages = [
    'org.apache.catalina.core',
    'org.apache.catalina.core',
    'org.apache.catalina.authenticator'
  ];

  ctx.clearRect(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT);
  for (let i = 0; i < 3; i++) {
    const y = i * fh;
    ctx.fillStyle = PREVIEW_COLORS[i];
    ctx.fillRect(0, y, PREVIEW_WIDTH, fh);
    ctx.strokeStyle = 'white';
    ctx.lineWidth = 1;
    ctx.strokeRect(0, y, PREVIEW_WIDTH, fh);

    // Line 1: bold Class + italic .method
    let cx = 7;
    ctx.fillStyle = '#000';
    ctx.font = 'bold 11px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillText(classes[i], cx, y + 13);
    cx += ctx.measureText(classes[i]).width;

    ctx.font = 'italic 11px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillText(methods[i], cx, y + 13);

    // Line 2: package
    ctx.fillStyle = 'rgba(0,0,0,0.7)';
    ctx.font = '10px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillText(packages[i], 7, y + 25, PREVIEW_WIDTH - 12);
  }
}

async function onAiToggleChange() {
  if (!aiToggle.value) {
    saving.value = true;
    try {
      await client.upsert('ai', 'jeffrey.microscope.ai.provider', 'none', false);
      settings.set('jeffrey.microscope.ai.provider', 'none');
      announceAiChange('AI disabled');
    } catch (e) {
      console.error('Failed to disable AI', e);
      ToastService.error('Settings', 'Failed to disable AI');
      aiToggle.value = true;
    } finally {
      saving.value = false;
    }
  } else {
    if (
      settings.get('jeffrey.microscope.ai.provider') === 'none' ||
      !settings.get('jeffrey.microscope.ai.provider')
    ) {
      settings.set('jeffrey.microscope.ai.provider', 'claude');
      settings.set('jeffrey.microscope.ai.model', 'claude-opus-5');
    }
  }
}

async function saveAiSettings() {
  const apiKey = settings.get('jeffrey.microscope.ai.api-key') || '';
  const updates: SettingUpdate[] = [
    aiSetting(
      'jeffrey.microscope.ai.provider',
      settings.get('jeffrey.microscope.ai.provider') || ''
    ),
    aiSetting('jeffrey.microscope.ai.model', settings.get('jeffrey.microscope.ai.model') || ''),
    aiSetting(
      'jeffrey.microscope.ai.max-tokens',
      settings.get('jeffrey.microscope.ai.max-tokens') || ''
    ),
    aiSetting(
      'jeffrey.microscope.ai.base-url',
      settings.get('jeffrey.microscope.ai.base-url') || ''
    ),
    aiSetting(
      'jeffrey.microscope.ai.cli-path',
      settings.get('jeffrey.microscope.ai.cli-path') || 'claude'
    ),
    aiSetting(
      'jeffrey.microscope.ai.timeout-seconds',
      settings.get('jeffrey.microscope.ai.timeout-seconds') || '600'
    ),
    // A masked key is what the server sent us, not something the user typed — saving it back would
    // overwrite the real key with its own mask.
    ...(apiKey && !apiKey.includes('****')
      ? [{ category: 'ai', name: 'jeffrey.microscope.ai.api-key', value: apiKey, secret: true }]
      : [])
  ];

  await save(updates, () => announceAiChange('AI settings applied'));
}

function aiSetting(name: string, value: string): SettingUpdate {
  return { category: 'ai', name, value, secret: false };
}

async function saveGeneralSettings() {
  await save(
    [
      {
        category: 'logging',
        name: 'logging.level.cafe.jeffrey',
        value: settings.get('logging.level.cafe.jeffrey') || '',
        secret: false
      }
    ],
    () => ToastService.success('Settings', 'Log level applied')
  );
}

async function saveVisualizationSettings() {
  await save(
    [
      {
        category: 'visualization',
        name: 'jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct',
        value:
          settings.get('jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct') || '',
        secret: false
      },
      {
        category: 'visualization',
        name: 'jeffrey.microscope.visualization.flamegraph.frame-text-mode',
        value: frameTextMode.value,
        secret: false
      }
    ],
    () => ToastService.success('Settings', 'Visualization settings applied')
  );
}

/**
 * The connection details are fetched rather than composed here: the reachable URL depends on the
 * request, and only the server sees that.
 */
async function loadMcpStatus() {
  try {
    mcpStatus.value = await mcpAccessClient.fetchStatus();
  } catch (e) {
    console.error('Failed to load MCP status', e);
    mcpStatus.value = null;
  }
}

async function copyToClipboard(text: string, label: string) {
  // The Clipboard API is undefined on a plain-HTTP origin, which is how a self-hosted Jeffrey is
  // usually served — so say that rather than blaming the copy for failing.
  if (!window.isSecureContext || navigator.clipboard === undefined) {
    ToastService.error(
      'Copy unavailable',
      'The browser only allows copying over HTTPS or on localhost. Select the text instead.'
    );
    return;
  }
  try {
    await navigator.clipboard.writeText(text);
    ToastService.success('Copied', `${label} copied to clipboard.`);
  } catch (e) {
    console.error('Clipboard write failed', e);
    ToastService.error('Copy Failed', 'Could not copy to the clipboard.');
  }
}

async function saveAiExportSettings() {
  await save(
    [
      {
        category: 'ai-export',
        name: 'jeffrey.microscope.ai-export.flamegraph.min-frame-threshold-pct',
        value:
          settings.get('jeffrey.microscope.ai-export.flamegraph.min-frame-threshold-pct') || '',
        secret: false
      }
    ],
    () => ToastService.success('Settings', 'AI export settings applied')
  );
}

/**
 * Saves a tab's settings as one batch. Sending them together lets the server validate and apply them
 * as a unit, so a rejected value leaves nothing half-written and derived state is rebuilt once from
 * the final values rather than once per field.
 */
async function save(updates: SettingUpdate[], onSaved: () => void) {
  saving.value = true;
  try {
    await client.upsertAll(updates);
    onSaved();
  } catch (e) {
    console.error('Failed to save settings', e);
    ToastService.error('Settings', 'Failed to save settings. Check the values and try again.');
  } finally {
    saving.value = false;
  }
}

/**
 * The backend applies an AI change immediately, but rebuilding the backend can take a moment and open
 * profile pages cached their feature list on mount — so tell them to refresh it.
 */
function announceAiChange(message: string) {
  ToastService.success('Settings', message);
  MessageBus.emit(MessageBus.AI_SETTINGS_CHANGED, null);
}
</script>

<style scoped>
.encryption-warning {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--color-info-bg);
  border: 1px solid var(--color-info-border);
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 20px;
  color: var(--color-info-text);
  font-size: 13px;
  font-weight: 500;
}

.settings-tabs {
  display: flex;
  gap: 0;
  border-bottom: 2px solid var(--color-border);
  margin-bottom: 24px;
}

.settings-tab {
  padding: 12px 24px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted);
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
  color: var(--color-primary);
}

.settings-tab.active::after {
  background: var(--color-primary);
}

.settings-tab:hover {
  color: var(--color-text);
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
  color: var(--color-text-muted);
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
  color: var(--color-dark);
  margin-bottom: 6px;
  display: block;
}

.settings-hint {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-top: 4px;
}

/* Claude Code subscription / ToS notice */
.ai-subscription-panel {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 14px 16px;
  margin-bottom: 22px;
  border-radius: 10px;
  border: 1px solid var(--color-primary-border, #9ba8ff);
  background: linear-gradient(120deg, var(--color-primary-light), rgba(139, 92, 246, 0.08));
}

.ai-sub-icon {
  font-size: 1.3rem;
  color: var(--color-primary);
  margin-top: 1px;
}

.ai-sub-body {
  flex: 1;
  min-width: 0;
}

.ai-sub-title {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--color-dark);
  margin-bottom: 7px;
}

.ai-sub-point {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12.5px;
  color: var(--color-text);
  line-height: 1.5;
  margin: 5px 0;
}

.ai-sub-point i {
  color: var(--color-primary);
  margin-top: 2px;
  font-size: 0.9rem;
  flex-shrink: 0;
}

.ai-sub-point strong {
  color: var(--color-dark);
  font-weight: 700;
}

.ai-sub-point a {
  color: var(--color-primary);
  text-decoration: none;
}

.ai-sub-point a:hover {
  text-decoration: underline;
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
  color: var(--color-text-muted);
  cursor: pointer;
  font-size: 16px;
  padding: 4px;
}

.settings-actions {
  display: flex;
  justify-content: flex-end;
}

/* Coding Agents (MCP) tab */
.mcp-state {
  margin-bottom: 8px;
}

.mcp-snippet {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 10px 12px;
}

.mcp-snippet code,
.mcp-snippet pre {
  flex: 1;
  margin: 0;
  font-family: var(--font-family-monospace);
  font-size: 12px;
  color: var(--color-text);
  background: none;
  /* The command is one long line and the JSON is several: both scroll rather than reflow, so the
     text stays copyable as written. */
  overflow-x: auto;
  white-space: pre;
}

.mcp-copy {
  flex-shrink: 0;
  white-space: nowrap;
}

.mcp-security-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 16px;
  padding: 10px 12px;
  background: var(--color-warning-bg);
  border: 1px solid var(--color-warning-border);
  border-radius: var(--radius-md);
  color: var(--color-warning-text);
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
  color: var(--color-text-muted);
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
  color: var(--color-text);
}

.settings-form-disabled {
  opacity: 0.4;
  pointer-events: none;
}

.btn-primary {
  padding: 9px 24px;
  background: var(--color-primary);
  color: var(--color-white);
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary:hover {
  background: var(--color-primary-hover);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.form-control:disabled {
  background: var(--color-bg-hover);
  cursor: not-allowed;
  opacity: 0.7;
}

.models-reference {
  margin-bottom: 24px;
}

.models-reference-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted);
  margin-bottom: 8px;
}

.model-row {
  cursor: pointer;
  transition: background 0.15s;
}

.model-row:hover {
  background: var(--color-bg-hover);
}

.model-row-selected {
  background: var(--color-bg-hover);
  font-weight: 600;
}

.frame-mode-cards {
  display: flex;
  gap: 16px;
}

.frame-mode-card {
  border: 2px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 10px;
  cursor: pointer;
  transition: all var(--transition-fast);
  background: var(--color-bg-card);
}

.frame-mode-card:hover {
  border-color: var(--color-text-muted);
}

.frame-mode-card.selected {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.frame-mode-preview {
  display: block;
  border-radius: 3px;
}

.frame-mode-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-dark);
  margin-top: 8px;
  text-align: center;
}
</style>
