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

const GITHUB_RELEASES_URL = 'https://api.github.com/repos/petrbouda/jeffrey/releases';
const DOCKERHUB_TAGS_URL = 'https://hub.docker.com/v2/repositories/petrbouda/volume-scheduler/tags/';
const MAVEN_SEARCH_URL = 'https://search.maven.org/solrsearch/select';
const JETBRAINS_PLUGIN_ID = '31963';
const JETBRAINS_API_URL = `https://plugins.jetbrains.com/api/plugins/${JETBRAINS_PLUGIN_ID}/updates`;

interface GitHubRelease {
  tag_name: string;
  html_url: string;
  published_at: string;
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

interface DockerHubTag {
  name: string;
}

interface DockerHubResponse {
  results: DockerHubTag[];
}

async function fetchVolumeScheduler(): Promise<ProductVersion | null> {
  try {
    const response = await fetch(`${DOCKERHUB_TAGS_URL}?page_size=10&ordering=last_updated`);
    if (!response.ok) {
      return null;
    }
    const data: DockerHubResponse = await response.json();
    const versionTag = data.results.find(t => t.name !== 'latest' && /^\d+\.\d+/.test(t.name));
    if (!versionTag) {
      return null;
    }
    return {
      version: `v${versionTag.name}`,
      url: 'https://hub.docker.com/repository/docker/petrbouda/volume-scheduler',
      source: 'DockerHub'
    };
  } catch {
    return null;
  }
}

interface MavenDoc {
  latestVersion: string;
}

interface MavenResponse {
  response: { docs: MavenDoc[] };
}

async function fetchJib(): Promise<ProductVersion | null> {
  try {
    const params = new URLSearchParams({
      q: 'g:cafe.jeffrey-analyst AND a:jeffrey-jib-gradle',
      rows: '1',
      wt: 'json'
    });
    const response = await fetch(`${MAVEN_SEARCH_URL}?${params}`);
    if (!response.ok) {
      return null;
    }
    const data: MavenResponse = await response.json();
    const doc = data.response.docs[0];
    if (!doc) {
      return null;
    }
    return {
      version: `v${doc.latestVersion}`,
      url: `https://mvnrepository.com/artifact/cafe.jeffrey-analyst/jeffrey-jib-gradle/${doc.latestVersion}`,
      source: 'Maven'
    };
  } catch {
    return null;
  }
}

interface JetBrainsUpdate {
  version: string;
  id: number;
}

async function fetchPlugin(): Promise<ProductVersion | null> {
  try {
    const params = new URLSearchParams({ channel: 'stable', size: '1' });
    const response = await fetch(`${JETBRAINS_API_URL}?${params}`);
    if (!response.ok) {
      return null;
    }
    const updates: JetBrainsUpdate[] = await response.json();
    const latest = updates[0];
    if (!latest) {
      return null;
    }
    return {
      version: `v${latest.version}`,
      url: `https://plugins.jetbrains.com/plugin/${JETBRAINS_PLUGIN_ID}-jeffrey-microscope/versions/stable/${latest.id}`,
      source: 'JetBrains Marketplace'
    };
  } catch {
    return null;
  }
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
    const [microscope, provisioner, volumeScheduler, jib, plugin] = await Promise.all([
      fetchMicroscope(),
      fetchProvisioner(),
      fetchVolumeScheduler(),
      fetchJib(),
      fetchPlugin()
    ]);

    versions.value = {
      microscope,
      mcp: microscope ? { ...microscope, source: 'GitHub Releases' } : null,
      provisioner,
      jib,
      plugin,
      'volume-scheduler': volumeScheduler
    };

    loading.value = false;
  });

  function versionFor(id: ProductId): ProductVersion | null {
    return versions.value[id];
  }

  return { versions, loading, versionFor, jibVersionLinks };
}
