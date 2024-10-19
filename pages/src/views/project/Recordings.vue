<!--
  - Jeffrey
  - Copyright (C) 2024 Petr Bouda
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

<script setup>
import {onMounted, ref} from 'vue';
import {useToast} from 'primevue/usetoast';
import FormattingService from '@/service/FormattingService';
import ProjectRecordingService from '@/service/project/ProjectRecordingService';
import GlobalVars from "@/service/GlobalVars";
import Utils from "../../service/Utils";
import {useRoute} from "vue-router";
import ProjectProfileService from "@/service/project/ProjectProfileService";

const route = useRoute()

const toast = useToast();
const recordings = ref(null);
const deleteRecordingDialog = ref(false);
const recordingToRemove = ref({});
const clearCallback = ref(null)

let recordingService;
let profileService;
const uploadUrl = GlobalVars.url + "/projects/" + route.params.projectId + "/recordings/upload"

const filters = ref({});
const filterMode = ref({label: 'Lenient', value: 'lenient'});

let expandedKeys = ref({})

onMounted(() => {
  profileService = new ProjectProfileService(route.params.projectId)

  recordingService = new ProjectRecordingService(route.params.projectId);
  recordingService.list()
      .then((data) => {
        recordings.value = data
      });
});

function onTemplatedUpload(event) {
  clearCallback.value()

  recordingService.list()
      .then((data) => (recordings.value = data));
}

function onUpload(upload, clear) {
  upload();
  clearCallback.value = clear;
}

function onUploadError(response) {
  toast.add({severity: 'error', summary: 'Upload Failed', detail: response.xhr.responseText, life: 3000});
  clearCallback.value()
}

const selectRecording = (recording) => {
  profileService.create(formatRecordingPath(recording))
      .then(() => {
        recordingService.list()
            .then((data) => {
              recordings.value = data

              toast.add({
                severity: 'success',
                summary: 'Successful',
                detail: 'A new Profile created: ' + recording.name,
                life: 3000
              });
            })
      });
};

const confirmDeleteRecording = (recording) => {
  recordingToRemove.value = recording;
  deleteRecordingDialog.value = true;
};

const deleteRecording = (recording) => {
  recordingService.delete(formatRecordingPath(recording))
      .then(() => {
        recordingService.list()
            .then((data) => {
              recordings.value = data

              toast.add({
                severity: 'success',
                summary: 'Successful',
                detail: 'File Deleted: ' + recordingToRemove.value.name,
                life: 3000
              });
              deleteRecordingDialog.value = false;
              recordingToRemove.value = {};
            });
      });
};

const expandAll = () => {
  function markExpanded(recordings) {
    recordings.forEach((it) => {
      if (it.children.length !== 0) {
        markExpanded(it.children)
      }
      expandedKeys.value[it.key] = true;
    })
  }

  markExpanded(recordings.value)
}

const collapseAll = () => {
  expandedKeys.value = {}
}

const formatRecordingPath = (recording) => {
  let recordingPath = ""
  recording.categories.forEach(
      (category) => recordingPath += `${category}/`
  );
  return recordingPath += `${recording.name}`
}
</script>

<template>
  <div class="surface-card p-3 flex-auto xl:ml-5">
    <div class="grid">
      <div class="col-12">
        <FileUpload name="files[]" :url="uploadUrl" @upload="onTemplatedUpload()" @error="onUploadError"
                    :multiple="true">
          <template #header="{ chooseCallback, uploadCallback, clearCallback, files }">
            <div class="flex flex-wrap justify-content-between align-items-center flex-1 gap-2">
              <div class="flex gap-2">
                <Button @click="chooseCallback()" icon="pi pi-images" rounded outlined></Button>
                <Button
                    @click="onUpload(function() { uploadCallback() }, function() { clearCallback() })"
                    icon="pi pi-cloud-upload" rounded outlined
                    severity="success" :disabled="!files || files.length === 0"></Button>
                <Button @click="clearCallback()" icon="pi pi-times" rounded outlined severity="danger"
                        :disabled="!files || files.length === 0"></Button>
              </div>
            </div>
          </template>

          <template #content="{ files, uploadedFiles, removeUploadedFileCallback, removeFileCallback }">
            <div v-if="files.length > 0">
              <div style="width: 100%;">
                <div v-for="(file, index) of files" :key="file.name + file.type + file.size"
                     class="card flex flex-wrap border-1 surface-border align-items-center gap-3">
                  <Button icon="pi pi-times" @click="removeFileCallback(index)" outlined rounded severity="danger"/>

                  <div style="width: 92%">
                    <div class="font-semibold">{{ file.name }}</div>
                    <div>{{ FormattingService.formatBytes(file.size) }}</div>
                  </div>

                </div>
              </div>
            </div>
          </template>

          <template #empty>
            <div class="flex align-items-center justify-content-center">
              <i class="pi pi-cloud-upload border-none text-2xl pb-1 pr-1"/>
              <i class="ml-2 mt-0 mb-0 text-lg">Drop JFR files to upload.</i>
            </div>
          </template>
        </FileUpload>
      </div>

      <div class="col-12">

        <!--        <Button @click="expandAll" label="Expand All" class="m-2 w-2"/>-->
        <!--        <Button @click="collapseAll" label="Collapse All" class="m-2 w-2"/>-->
        <!-- @dragenter="dragleave" -->
        <TreeTable
            :value="recordings" :filters="filters" :filterMode="filterMode.value"
            v-model:expandedKeys="expandedKeys">
          <Column field="name" header="Name" :expander="true" filter-match-mode="contains" headerStyle="width:60%">
            <!--            <template #filter>-->
            <!--              <InputText v-model="filters['name']" type="text" class="p-column-filter" placeholder="Filter by Name"/>-->
            <!--            </template>-->

            <template #body="slotProps">
              <span class="font-bold" v-if="!slotProps.node.leaf">{{ slotProps.node.data.name }}</span>
              <span class="text-primary" v-else>{{ slotProps.node.data.name }}</span>
            </template>
          </Column>

          <Column field="date" header="Created at" headerStyle="width:20%">
            <template #body="slotProps">
            <span class="text-primary" v-if="slotProps.node.leaf">{{
                Utils.formatDateTime(slotProps.node.data.dateTime)
              }}</span>
            </template>
          </Column>

          <Column field="size" header="Size" headerStyle="width:20%">
            <template #body="slotProps">
            <span class="text-primary"
                  v-if="slotProps.node.leaf">{{ FormattingService.formatBytes(slotProps.node.data.sizeInBytes) }}</span>
            </template>
          </Column>
          <Column headerStyle="width:20%">
            <template #body="slotProps">
              <div v-if="slotProps.node.leaf" class="flex justify-content-end">
                <Button class="p-button-primary justify-content-center mr-2 w-2"
                        @click="selectRecording(slotProps.node.data)">
                  <div class="material-symbols-outlined text-xl">play_arrow</div>
                </Button>
                <Button class="p-button-warning justify-content-center w-2"
                        @click="confirmDeleteRecording(slotProps.node.data)">
                  <div class="material-symbols-outlined text-xl">delete</div>
                </Button>
              </div>
            </template>
          </Column>
        </TreeTable>

        <Dialog v-model:visible="deleteRecordingDialog" :style="{ width: '450px' }" header="Confirm"
                :modal="true">
          <div class="flex align-items-center justify-content-center">
            <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem"/>
            <span v-if="recordingToRemove">Are you sure you want to delete the recording: <br><b>{{
                formatRecordingPath(recordingToRemove)
              }}</b>?</span>
          </div>
          <template #footer>
            <Button label="No" icon="pi pi-times" class="p-button-text"
                    @click="deleteRecordingDialog = false"/>
            <Button label="Yes" icon="pi pi-check" class="p-button-text" @click="deleteRecording(recordingToRemove)"/>
          </template>
        </Dialog>
      </div>
    </div>
  </div>

  <Toast/>
</template>

<style>
.p-treetable tr:hover {
  background: #f4fafe;
}

.p-button.p-button-icon-only {
  width: 2.5rem;
  height: 2.5rem;
  padding: 0.75rem 0;
}
</style>
