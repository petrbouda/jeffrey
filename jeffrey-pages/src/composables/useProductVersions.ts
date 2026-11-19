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

import { ref, onMounted } from 'vue';

export interface ProductVersion {
  version: string;
  url: string;
  source: string;
}

export interface ProductVersions {
  microscope: ProductVersion | null;
  mcp: ProductVersion | null;
  provisioner: ProductVersion | null;
  jib: ProductVersion | null;
  plugin: ProductVersion | null;
  'volume-scheduler': ProductVersion | null;
}

type ProductId = keyof ProductVersions;

const GITHUB_API_URL = 'https://api.github.com/repos/petrbouda/jeffrey';
const GITHUB_TAGS_URL = `${GITHUB_API_URL}/tags`;
const GITHUB_RELEASES_URL = `${GITHUB_API_URL}/releases`;
const JETBRAINS_PLUGIN_ID = '31963';

// Pinned versions — Maven Central and JetBrains Marketplace do not support CORS,
// so these cannot be fetched at runtime from a static site.
const JIB_VERSION = '0.4.0';
const INTELLIJ_PLUGIN_VERSION = '0.3.11';

interface GitHubRelease {
  tag_name: string;
  html_url: string;
}

interface GitHubTag {
  name: string;
}

function latestTagByVersion(tags: GitHubTag[], prefix: string): string | null {
  const matching = tags
    .map(t => t.name)
    .filter(name => name.startsWith(prefix));

  if (matching.length === 0) {
    return null;
  }

  matching.sort((a, b) => {
    const va = a.slice(prefix.length).split('.').map(Number);
    const vb = b.slice(prefix.length).split('.').map(Number);
    for (let i = 0; i < Math.max(va.length, vb.length); i++) {
      const diff = (vb[i] || 0) - (va[i] || 0);
      if (diff !== 0) {
        return diff;
      }
    }
    return 0;
  });

  return matching[0];
}

async function fetchMicroscope(): Promise<ProductVersion | null> {
  try {
    const response = await fetch(`${GITHUB_RELEASES_URL}/latest`);
    if (!response.ok) {
      return null;
    }
    const release: GitHubRelease = await response.json();
    return {
      version: release.tag_name,
      url: release.html_url,
      source: 'GitHub Releases'
    };
  } catch {
    return null;
  }
}

async function fetchProvisioner(): Promise<ProductVersion | null> {
  try {
    const response = await fetch(`${GITHUB_RELEASES_URL}?per_page=20`);
    if (!response.ok) {
      return null;
    }
    const releases: GitHubRelease[] = await response.json();
    const provisioner = releases.find(r => r.tag_name.startsWith('provisioner-'));
    if (!provisioner) {
      return null;
    }
    return {
      version: provisioner.tag_name.replace('provisioner-', ''),
      url: provisioner.html_url,
      source: 'GitHub Releases'
    };
  } catch {
    return null;
  }
}

async function fetchVolumeScheduler(): Promise<ProductVersion | null> {
  try {
    const response = await fetch(`${GITHUB_TAGS_URL}?per_page=100`);
    if (!response.ok) {
      return null;
    }
    const tags: GitHubTag[] = await response.json();
    const latest = latestTagByVersion(tags, 'volume-scheduler-v');
    if (!latest) {
      return null;
    }
    return {
      version: latest.replace('volume-scheduler-', ''),
      url: 'https://hub.docker.com/repository/docker/petrbouda/volume-scheduler',
      source: 'DockerHub'
    };
  } catch {
    return null;
  }
}

function jibVersion(): ProductVersion {
  return {
    version: `v${JIB_VERSION}`,
    url: `https://mvnrepository.com/artifact/cafe.jeffrey-analyst/jeffrey-jib-gradle/${JIB_VERSION}`,
    source: 'Maven'
  };
}

function pluginVersion(): ProductVersion {
  return {
    version: `v${INTELLIJ_PLUGIN_VERSION}`,
    url: `https://plugins.jetbrains.com/plugin/${JETBRAINS_PLUGIN_ID}-jeffrey-microscope`,
    source: 'JetBrains Marketplace'
  };
}

const JIB_GRADLE_URL = 'https://mvnrepository.com/artifact/cafe.jeffrey-analyst/jeffrey-jib-gradle';
const JIB_MAVEN_URL = 'https://mvnrepository.com/artifact/cafe.jeffrey-analyst/jeffrey-jib-maven';

export interface JibLinks {
  maven: string;
  gradle: string;
}

export function jibVersionLinks(version: string): JibLinks {
  const v = version.startsWith('v') ? version.slice(1) : version;
  return {
    maven: `${JIB_MAVEN_URL}/${v}`,
    gradle: `${JIB_GRADLE_URL}/${v}`
  };
}

export function useProductVersions() {
  const versions = ref<ProductVersions>({
    microscope: null,
    mcp: null,
    provisioner: null,
    jib: null,
    plugin: null,
    'volume-scheduler': null
  });

  const loading = ref(true);

  onMounted(async () => {
    const [microscope, provisioner, volumeScheduler] = await Promise.all([
      fetchMicroscope(),
      fetchProvisioner(),
      fetchVolumeScheduler()
    ]);

    versions.value = {
      microscope,
      mcp: microscope ? { ...microscope, source: 'GitHub Releases' } : null,
      provisioner,
      jib: jibVersion(),
      plugin: pluginVersion(),
      'volume-scheduler': volumeScheduler
    };

    loading.value = false;
  });

  function versionFor(id: ProductId): ProductVersion | null {
    return versions.value[id];
  }

  return { versions, loading, versionFor, jibVersionLinks };
}
