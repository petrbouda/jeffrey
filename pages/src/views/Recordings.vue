<script setup>
import {FilterMatchMode} from 'primevue/api';
import {onMounted, ref} from 'vue';
import {useToast} from 'primevue/usetoast';
import PrimaryProfileService from '@/service/PrimaryProfileService';
import FormattingService from '@/service/FormattingService';
import RecordingService from '@/service/RecodingService';
import ProfileService from '@/service/ProfileService';
import Utils from "../service/Utils";
import GlobalVars from "@/service/GlobalVars";

const toast = useToast();
const recordings = ref(null);
const deleteRecordingDialog = ref(false);
const recordingToRemove = ref({});
const dt = ref(null);
const filters = ref({
  'file.filename': {value: null, matchMode: FilterMatchMode.CONTAINS}
});
const clearCallback = ref(null)

const recordingService = new RecordingService();
const profileService = new ProfileService();
const uploadUrl = GlobalVars.url + "/recordings/upload"

onMounted(() => {
  recordingService.list()
      .then((data) => (recordings.value = data));
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

const selectProfile = (profile) => {
  profileService.create(profile.filename)
      .then((data) => PrimaryProfileService.update(data))
      .then(() => recordingService.list().then((data) => (recordings.value = data)));
};

const confirmDeleteRecording = (profile) => {
  recordingToRemove.value = profile;
  deleteRecordingDialog.value = true;
};

const deleteProfile = () => {
  recordingService.delete(recordingToRemove.value.filename)
      .then(() => {
        recordings.value = recordings.value.filter((val) => val.file.filename !== recordingToRemove.value.filename);

        toast.add({
          severity: 'success',
          summary: 'Successful',
          detail: 'File Deleted: ' + recordingToRemove.value.filename,
          life: 3000
        });
        deleteRecordingDialog.value = false;
        recordingToRemove.value = {};
      });
};
</script>

<template>
  <div class="grid">
    <div class="col-12">
      <FileUpload name="files[]" :url="uploadUrl" @upload="onTemplatedUpload($event)"
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
          <div class="flex align-items-center justify-content-center flex-column">
            <i class="pi pi-cloud-upload border-2 border-circle p-3 text-6xl text-400 border-400"/>
          </div>
        </template>
      </FileUpload>
    </div>
    <div class="col-12">
      <DataTable
          ref="dt"
          :value="recordings"
          dataKey="name"
          :paginator="true"
          :rows="20"
          v-model:filters="filters"
          filterDisplay="menu">

        <Column>
          <template #body="slotProps">
            <div v-if="slotProps.data.used">
              <Button icon="pi pi-circle-fill" class="p-button-filled p-button-success"/>
            </div>
            <div v-else>
              <Button icon="pi pi-play" class="p-button-primary" @click="selectProfile(slotProps.data.file)"/>
            </div>
          </template>
        </Column>
        <Column field="file.filename" header="Name" :sortable="true" :showFilterMatchModes="false"
                headerStyle="width:70%; min-width:10rem;">
          <template #body="slotProps">
            {{ slotProps.data.file.filename }}
          </template>
          <template #filter="{ filterModel }">
            <InputText v-model="filterModel.value" type="text" class="p-column-filter" placeholder="Search by name"/>
          </template>
        </Column>
        <Column field="file.dateTime" header="Date" :sortable="true"
                headerStyle="width:15%; min-width:10rem;">
          <template #body="slotProps">
            {{ Utils.formatDateTime(slotProps.data.file.dateTime) }}
          </template>
        </Column>
        <Column field="file.sizeInBytes" header="Size" headerStyle="width:15%; min-width:15rem;">
          <template #body="slotProps">
            {{ FormattingService.formatBytes(slotProps.data.file.sizeInBytes) }}
          </template>
        </Column>
        <Column>
          <template #body="slotProps">
            <Button icon="pi pi-trash" class="p-button-filled p-button-warning mt-2"
                    @click="confirmDeleteRecording(slotProps.data.file)"/>
          </template>
        </Column>
      </DataTable>

      <Dialog v-model:visible="deleteRecordingDialog" :style="{ width: '450px' }" header="Confirm"
              :modal="true">
        <div class="flex align-items-center justify-content-center">
          <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem"/>
          <span v-if="recordingToRemove">Are you sure you want to delete: <b>{{
              recordingToRemove.filename
            }}</b>?</span>
        </div>
        <template #footer>
          <Button label="No" icon="pi pi-times" class="p-button-text"
                  @click="deleteRecordingDialog = false"/>
          <Button label="Yes" icon="pi pi-check" class="p-button-text" @click="deleteProfile"/>
        </template>
      </Dialog>
    </div>
  </div>

  <Toast/>
</template>

<style scoped lang="scss">
.ui-datatable table thead tr {
  display: none;
}
</style>
