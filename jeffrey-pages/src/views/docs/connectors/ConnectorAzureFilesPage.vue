<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
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
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'connect', text: 'Connect Step by Step', level: 2 },
  { id: 'step-share', text: '1. Find the Share', level: 3 },
  { id: 'step-access', text: '2. Grant Access', level: 3 },
  { id: 'step-declare', text: '3. Declare It in Microscope', level: 3 },
  { id: 'step-verify', text: '4. Restart and Check', level: 3 },
  { id: 'properties', text: 'Properties', level: 2 },
  { id: 'sign-in', text: 'Signing In', level: 2 },
  { id: 'sas', text: 'SAS Token', level: 3 },
  { id: 'entra', text: 'Microsoft Entra ID', level: 3 },
  { id: 'read-only', text: 'Read-only Mode', level: 2 },
  { id: 'protocol', text: 'SMB or NFS', level: 2 },
  { id: 'secrets', text: 'Keeping the Credential Out of the File', level: 2 },
  { id: 'logging', text: 'Logging', level: 2 },
  { id: 'startup', text: 'Startup Behaviour', level: 2 },
  { id: 'troubleshooting', text: 'Troubleshooting', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const azureFilesPropertiesExample = `jeffrey.microscope.connector.name=Production share
jeffrey.microscope.connector.azure-files.endpoint=https://jeffreyprod.file.core.windows.net
jeffrey.microscope.connector.azure-files.share=recordings
jeffrey.microscope.connector.azure-files.protocol=smb
jeffrey.microscope.connector.azure-files.auth=sas
jeffrey.microscope.connector.azure-files.sas-token=\${AZURE_FILES_SAS}
jeffrey.microscope.connector.azure-files.read-only=false`;

const findShareExample = `# The Azure Files volume behind the Helm chart's claim (jeffrey-pvc by default)
PV=$(kubectl get pvc jeffrey-pvc -o jsonpath='{.spec.volumeName}')
kubectl get pv "$PV" -o jsonpath='{.spec.csi.volumeHandle}{"\\n"}'
# → <resource-group>#<storage-account>#<share>#...`;

const endpointExample = `az storage account show \\
  --resource-group <resource-group> --name <storage-account> \\
  --query primaryEndpoints.file -o tsv
# → https://<storage-account>.file.core.windows.net/`;

const sasExample = `az storage share generate-sas \\
  --account-name <storage-account> --name <share> \\
  --permissions rld \\
  --expiry 2027-06-30T00:00Z --https-only \\
  --account-key "$(az storage account keys list \\
      --resource-group <resource-group> --account-name <storage-account> \\
      --query '[0].value' -o tsv)" \\
  -o tsv`;

const roleExample = `az role assignment create \\
  --assignee <user, group or managed-identity object id> \\
  --role "Storage File Data Privileged Contributor" \\
  --scope "$(az storage account show \\
      --resource-group <resource-group> --name <storage-account> --query id -o tsv)"`;

const declareExample = `# connector.properties
jeffrey.microscope.connector.name=Production recordings
jeffrey.microscope.connector.azure-files.endpoint=https://<storage-account>.file.core.windows.net
jeffrey.microscope.connector.azure-files.share=<share>
jeffrey.microscope.connector.azure-files.root-path=workspaces
jeffrey.microscope.connector.azure-files.sas-token=\${AZURE_FILES_SAS}`;

const runJarExample = `export AZURE_FILES_SAS='<token printed in step 2>'
java -jar microscope.jar --spring.config.additional-location=file:./connector.properties`;

const runDockerExample = `docker run -it --network host \\
  -e JEFFREY_MICROSCOPE_CONNECTOR_NAME="Production recordings" \\
  -e JEFFREY_MICROSCOPE_CONNECTOR_AZUREFILES_ENDPOINT=https://<storage-account>.file.core.windows.net \\
  -e JEFFREY_MICROSCOPE_CONNECTOR_AZUREFILES_SHARE=<share> \\
  -e JEFFREY_MICROSCOPE_CONNECTOR_AZUREFILES_ROOTPATH=workspaces \\
  -e JEFFREY_MICROSCOPE_CONNECTOR_AZUREFILES_SASTOKEN="$AZURE_FILES_SAS" \\
  petrbouda/microscope`;

const entraPropertiesExample = `jeffrey.microscope.connector.azure-files.endpoint=https://jeffreyprod.file.core.windows.net
jeffrey.microscope.connector.azure-files.share=recordings
jeffrey.microscope.connector.azure-files.auth=entra
jeffrey.microscope.connector.azure-files.read-only=true`;

const connectorEnvExample = `JEFFREY_MICROSCOPE_CONNECTOR_NAME="Production share"
JEFFREY_MICROSCOPE_CONNECTOR_AZUREFILES_ENDPOINT=https://jeffreyprod.file.core.windows.net
JEFFREY_MICROSCOPE_CONNECTOR_AZUREFILES_SHARE=recordings
JEFFREY_MICROSCOPE_CONNECTOR_AZUREFILES_SASTOKEN=<sas-token>`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Azure File Share"
      icon="bi bi-cloud"
    />

    <div class="docs-content">
      <h2 id="overview">Overview</h2>
      <p>
        The Azure File Share is one of the <router-link to="/docs/connectors/available">connector kinds</router-link>
        Microscope can read; its block is <code>jeffrey.microscope.connector.azure-files.*</code>. This connector is the recording volume itself: the Azure Files share (SMB or NFS)
        the <router-link to="/docs/provisioner">provisioner</router-link> writes into &mdash; in AKS, the
        volume mounted through the <code>azurefile-csi</code> storage class
        (<router-link to="/docs/hub/deployment/shared-volume">Shared Volume</router-link>). Microscope works
        with it over the <strong>Azure Files REST API</strong>, so it needs no mount of its own and can run
        on a laptop outside the cluster. It lists workspaces, projects, instances and sessions straight off
        the share, downloads what you open, and deletes what you ask it to &mdash; unless it is declared
        <a href="#read-only">read-only</a>.
      </p>
      <p>
        Each directory at the share's root (or under its configured <code>root-path</code>) is a
        <router-link to="/docs/connectors/workspaces">workspace</router-link>. Retention, cleanup and
        compression are not Microscope's: they are the scheduled jobs of a
        <router-link to="/docs/hub">Jeffrey Hub</router-link> running on the same volume.
      </p>

      <DocsCodeBlock language="properties" :code="azureFilesPropertiesExample" />

      <h2 id="connect">Connect Step by Step</h2>
      <p>
        From a share your JVMs already write into to its workspaces on the Connector page. You need the
        Azure CLI (<code>az</code>) signed in to the subscription that holds the storage account, and
        <code>kubectl</code> if the volume came from the Hub's Helm chart.
      </p>

      <h3 id="step-share">1. Find the Share</h3>
      <p>
        Microscope must read the <strong>same share</strong> the provisioner writes into. If you deployed with the
        <router-link to="/docs/hub/deployment/helm-chart">Helm chart</router-link> on an
        <code>azurefile-csi</code> storage class, the share was created for its claim; its
        <code>volumeHandle</code> names the resource group, the storage account and the share:
      </p>
      <DocsCodeBlock language="bash" :code="findShareExample" />
      <p>Then read the storage account's file endpoint. The trailing <code>/</code> it prints is accepted:</p>
      <DocsCodeBlock language="bash" :code="endpointExample" />
      <p>
        Finally, decide the <strong>root path</strong>. The provisioner and the Hub keep workspaces in a
        <code>workspaces/</code> folder under the Jeffrey home directory, beside a <code>temp/</code> folder. When the
        share is mounted as that home directory, which is the Helm chart's layout, set
        <code>root-path=workspaces</code>. Without it, Microscope lists <code>temp</code> and
        <code>workspaces</code> as two workspaces. If the share holds workspace folders at its root, leave
        <code>root-path</code> unset.
      </p>

      <h3 id="step-access">2. Grant Access</h3>
      <p>Pick one way to <a href="#sign-in">sign in</a>.</p>
      <p>
        <strong>SAS token</strong>, the simplest and scoped to this one share. Issue it with read, list and delete
        (<code>rld</code>), or with <code>rl</code> for a <a href="#read-only">read-only</a> connector:
      </p>
      <DocsCodeBlock language="bash" :code="sasExample" />
      <p>
        It prints the token. Keep it as a secret, without a leading <code>?</code> if you copy one from the
        portal instead. When it expires, the connector turns <strong>Unreachable</strong>: issue a new one and
        restart Microscope.
      </p>
      <p>
        <strong>Microsoft Entra ID</strong>, with no token to rotate. Give the identity Microscope runs as the role
        on the storage account; with <code>read-only=true</code>, use
        <strong>Storage File Data Privileged Reader</strong> instead:
      </p>
      <DocsCodeBlock language="bash" :code="roleExample" />
      <p>
        On a laptop that identity is whoever ran <code>az login</code>. In a cluster it is the pod's workload or
        managed identity. Role assignments can take a few minutes to apply.
      </p>

      <h3 id="step-declare">3. Declare It in Microscope</h3>
      <p>Put the declaration in a properties file, and leave the token in an environment variable:</p>
      <DocsCodeBlock language="properties" :code="declareExample" />
      <p>
        For Entra ID, replace the <code>sas-token</code> line with
        <code>jeffrey.microscope.connector.azure-files.auth=entra</code>. Then start Microscope with the file:
      </p>
      <DocsCodeBlock language="bash" :code="runJarExample" />
      <p>Or, in Docker, pass every property as an environment variable:</p>
      <DocsCodeBlock language="bash" :code="runDockerExample" />
      <p>
        Microscope talks to the share over HTTPS (port 443) only, with no SMB mount, so port 445 need not be
        open. If the storage account's firewall restricts networks, allow the address Microscope connects from.
      </p>

      <h3 id="step-verify">4. Restart and Check</h3>
      <ul>
        <li>Open the <strong>Connector</strong> page. The connector's name carries a green <strong>Online</strong> <router-link to="/docs/connectors/workspaces#status">status</router-link>, and the Workspaces column lists your workspace reference ids (<code>$default</code> when the provisioner set none).</li>
        <li>The log shows one <code>AzureFilesTraffic</code> line per request to the share, with its status. A run of <code>200</code>s means you are connected.</li>
        <li>With a SAS token, no warning about its rights should appear at startup.</li>
      </ul>
      <p>Anything else: see <a href="#troubleshooting">Troubleshooting</a>.</p>

      <h2 id="properties">Properties</h2>
      <p>
        The block below, plus the common <code>jeffrey.microscope.connector.name</code> (defaulting to the share's
        name), which every connector shares; see <router-link to="/docs/connectors/declaring#common-properties">Declaring
        a Connector</router-link>.
      </p>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.<wbr>microscope.<wbr>connector.<wbr>azure-files.<wbr>endpoint</code></td>
            <td>—</td>
            <td>
              <strong>Required.</strong> The storage account's file endpoint, e.g.
              <code>https://<wbr>&lt;account&gt;<wbr>.file.<wbr>core.<wbr>windows.<wbr>net</code>: <code>https</code>, a host
              and nothing more. A URL with a path, a query or credentials — such as the portal's
              <em>File service SAS URL</em> — is refused, so a SAS never ends up stored, listed or
              logged as part of the address; put it in <code>sas-token</code>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.<wbr>microscope.<wbr>connector.<wbr>azure-files.<wbr>share</code></td>
            <td>—</td>
            <td>
              <strong>Required.</strong> Name of the file share; also the connector's display name when
              the common <code>jeffrey.microscope.connector.name</code> is not set.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.<wbr>microscope.<wbr>connector.<wbr>azure-files.<wbr>root-path</code></td>
            <td>the share's root</td>
            <td>
              Directory on the share that holds the workspaces — the same directory the Jeffrey Hub
              uses as its volume. Every directory under it is shown as a workspace.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.<wbr>microscope.<wbr>connector.<wbr>azure-files.<wbr>protocol</code></td>
            <td><code>smb</code></td>
            <td>
              <code>smb</code> or <code>nfs</code> — the share's protocol; see <a href="#protocol">SMB or NFS</a>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.<wbr>microscope.<wbr>connector.<wbr>azure-files.<wbr>auth</code></td>
            <td><code>sas</code></td>
            <td>
              <code>sas</code> signs in with <code>sas-token</code>; <code>entra</code> signs in with
              Microsoft Entra ID. See <a href="#sign-in">Signing In</a>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.<wbr>microscope.<wbr>connector.<wbr>azure-files.<wbr>sas-token</code></td>
            <td>—</td>
            <td>
              <strong>Required with <code>auth=sas</code>.</strong> A SAS token granting read, list
              and delete on the share (<code>sp=rld</code>) — read and list alone with
              <code>read-only=true</code>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.<wbr>microscope.<wbr>connector.<wbr>azure-files.<wbr>read-only</code></td>
            <td><code>false</code></td>
            <td>
              Refuse every change to the share — no workspace, project or session deletion — and hide
              those actions in the UI. The credential then needs only read and list rights.
            </td>
          </tr>
        </tbody>
      </table>

      <p>Every property has the usual environment-variable form:</p>

      <DocsCodeBlock language="bash" :code="connectorEnvExample" />

      <h2 id="sign-in">Signing In</h2>
      <p>
        Microscope authenticates at the connector level only &mdash; there is no separate per-workspace
        credential. Pick one of two sign-ins with <code>auth</code>.
      </p>

      <h3 id="sas">SAS Token</h3>
      <p>
        <code>auth=sas</code> (the default) signs every request with <code>sas-token</code>. Grant read,
        list and delete on the share (<code>sp=rld</code>); with <code>read-only=true</code>, read and list
        are enough. Microscope warns at startup, naming only the rights, when a writable token lacks one of
        them, or a read-only one grants create, write or delete. The token stays in the configuration:
        Microscope never stores it in its database and never logs it.
      </p>

      <h3 id="entra">Microsoft Entra ID</h3>
      <p>
        <code>auth=entra</code> signs in through <code>DefaultAzureCredential</code> &mdash; managed
        identity, workload identity, environment variables, the Azure CLI, …, which it may run. That
        identity needs the <strong>Storage File Data Privileged Contributor</strong> role on the share, or
        <strong>Storage File Data Privileged Reader</strong> with <code>read-only=true</code>.
      </p>

      <DocsCodeBlock language="properties" :code="entraPropertiesExample" />

      <DocsCallout type="warning">
        <strong>Give recordings a storage account of their own.</strong> Both privileged roles, which
        the Entra sign-in needs, ignore file-level permissions and reach every share in the storage
        account, not just this one. A SAS can be scoped to one share; a role cannot.
      </DocsCallout>

      <h2 id="read-only">Read-only Mode</h2>
      <p>
        <a href="#properties"><code>read-only=true</code></a> makes this a
        <router-link to="/docs/connectors/declaring#writable-read-only">read-only connector</router-link>. It
        carries a <strong>Read-only</strong> badge on the Connector page and only browses and downloads. There
        is no button to delete a workspace, a project or a session, and a request that reaches the backend
        anyway is refused, saying the connector is read-only. The MCP <code>connector_</code> tools are
        unaffected, since they only list and download. Pair it with a SAS token granting only read and list
        (<code>sp=rl</code>), or an Entra identity holding <strong>Storage File Data Privileged Reader</strong>.
      </p>

      <h2 id="protocol">SMB or NFS</h2>
      <p>
        Set <code>protocol</code> to match the share. Both are read over the same REST API, but an NFS
        share's listings carry no file times, so Microscope reads them one file at a time: expect larger
        projects to load more slowly, and instance and project creation dates to be missing from the
        columns.
      </p>

      <h2 id="secrets">Keeping the Credential Out of the File</h2>
      <p>
        Pass the SAS token from a secret &mdash; an environment variable or a mounted secret file &mdash;
        rather than writing it into <code>application.properties</code>. On a development machine,
        Microscope also reads an optional <code>secrets.properties</code> on its classpath
        (<code>spring.config.import=optional:classpath:secrets.properties</code>), which the repository
        ignores.
      </p>

      <h2 id="logging">Logging</h2>
      <p>
        Microscope logs through stock Spring Boot, so the usual <code>logging.level.*</code> keys apply. Two
        loggers belong to this connector:
      </p>

      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>logging.<wbr>level.<wbr>cafe.<wbr>jeffrey.<wbr>microscope.<wbr>connector.<wbr>azure.<wbr>AzureFilesTraffic</code></td>
            <td><code>INFO</code></td>
            <td>
              Logs every request sent to the Azure file share, one line per request once it ends (a retry is its
              own line): method, operation, path, status, elapsed time and the declared request and response sizes,
              or the error's type when no response came. Payloads, headers and the query string &mdash; which
              carries the SAS signature &mdash; are never logged. Set to <code>WARN</code> to silence it.
            </td>
          </tr>
          <tr>
            <td><code>logging.<wbr>level.<wbr>com.<wbr>azure.<wbr>core.<wbr>http.<wbr>jdk.<wbr>httpclient.<wbr>JdkHttpClient</code></td>
            <td><code>ERROR</code></td>
            <td>
              The Azure SDK's HTTP client warns on every call that <code>java.net.http</code> drops its
              <code>Content-Length</code> header; the JDK client sets that header itself from the request body,
              so nothing is lost. Microscope's default raises the logger to <code>ERROR</code> to hide that
              warning, the only one it carries.
            </td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="tip">
        <strong>Diagnosing a slow or failing share?</strong> Keep <code>AzureFilesTraffic</code> at
        <code>INFO</code>: each line names the operation, its status and how long it took, which tells a
        throttled or unreachable share apart from a missing permission (a <code>403</code>).
      </DocsCallout>

      <h2 id="startup">Startup Behaviour</h2>
      <p>
        The share follows the <router-link to="/docs/connectors/declaring#startup">startup rules every connector
        shares</router-link>: nothing is contacted at startup, and a block missing its <code>endpoint</code> or
        <code>share</code> is logged and ignored. On top of those, an Azure File Share checks one thing when
        Microscope starts: with <code>auth=sas</code> it reads the rights the token grants and warns, naming only
        the rights, when a writable connector's token lacks read, list or delete, or a read-only connector's token
        grants create, write or delete.
      </p>

      <h2 id="troubleshooting">Troubleshooting</h2>
      <table>
        <thead>
          <tr>
            <th>What you see</th>
            <th>Likely cause</th>
            <th>What to do</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>The Connector page says no connector is declared</td>
            <td>The block was ignored: a missing <code>endpoint</code> or <code>share</code>, an endpoint with a path or query, an unknown <code>protocol</code> or <code>auth</code>, or <code>auth=sas</code> without a token.</td>
            <td>Find <code>Ignoring the declared connector: reason=…</code> in the startup log; the reason names the property.</td>
          </tr>
          <tr>
            <td><strong>Unreachable</strong>, requests answer <code>403</code></td>
            <td>The SAS token is expired, mistyped or for another share; or the Entra identity lacks the role, or the assignment has not applied yet; or the account's firewall blocks Microscope's network.</td>
            <td>Issue a new token (step 2), check the role on the storage account, or allow the network. Restart after changing the token.</td>
          </tr>
          <tr>
            <td><strong>Unreachable</strong>, requests answer <code>404</code></td>
            <td>The share or the root path does not exist under that endpoint.</td>
            <td>Re-check the share name and <code>root-path</code> against step 1.</td>
          </tr>
          <tr>
            <td><strong>Unreachable</strong>, no response at all</td>
            <td>DNS, proxy or VPN: Microscope cannot reach <code>&lt;account&gt;<wbr>.file.<wbr>core.<wbr>windows.<wbr>net</code> on port 443.</td>
            <td>Check the network path from the machine Microscope runs on.</td>
          </tr>
          <tr>
            <td>Workspaces named <code>temp</code> and <code>workspaces</code></td>
            <td>The share is mounted as the Jeffrey home directory and <code>root-path</code> is unset.</td>
            <td>Set <code>root-path=workspaces</code> (step 1).</td>
          </tr>
          <tr>
            <td>Online, but no workspaces</td>
            <td>Wrong share or root path, or nothing has been provisioned into it yet.</td>
            <td>Compare the provisioner's <code>jeffrey-home</code> mount with the share and <code>root-path</code> declared here.</td>
          </tr>
          <tr>
            <td>Delete buttons are missing</td>
            <td>The connector is declared <code>read-only=true</code>.</td>
            <td>Intended. Drop the flag and use a credential with delete rights if you want deletions.</td>
          </tr>
          <tr>
            <td>A warning about the SAS token's rights at startup</td>
            <td>A writable connector's token lacks <code>r</code>, <code>l</code> or <code>d</code>, or a read-only one grants <code>c</code>, <code>w</code> or <code>d</code>.</td>
            <td>Issue a token with exactly <code>rld</code>, or <code>rl</code> for read-only.</td>
          </tr>
          <tr>
            <td>Instance and project dates are missing</td>
            <td>An NFS share lists no file times.</td>
            <td>Expected with <code>protocol=nfs</code>; see <a href="#protocol">SMB or NFS</a>.</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="tip">
        <strong>Tuning the MCP side.</strong> How long the <code>connector_</code> tools spend scanning the
        share and waiting on a download is set under <code>jeffrey.microscope.mcp.connector.*</code>; see
        <router-link to="/docs/microscope/configuration/application-properties#mcp-server">MCP Server properties</router-link>.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Property names break after each dot. A share of the width, not a fixed minimum, keeps them
   to a few lines without the long descriptions squeezing them, and never widens the table. */
.docs-content td:first-child {
  width: 32%;
}
</style>
