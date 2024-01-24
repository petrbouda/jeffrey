<script setup>
import { FilterMatchMode } from 'primevue/api';
import { onBeforeMount, onMounted, ref } from 'vue';
import { useToast } from 'primevue/usetoast';
import ProfileService from '../service/ProfileService';
import SelectedProfileService from '@/service/SelectedProfileService';
import FormattingService from '@/service/FormattingService';

const toast = useToast();
const profiles = ref(null);
const deleteProfileDialog = ref(false);
const profileToRemove = ref({});
const dt = ref(null);
const filters = ref({});

const profileService = new ProfileService();

onBeforeMount(() => {
    initFilters();
});
onMounted(() => {
    profileService.listJfr()
        .then((data) => (profiles.value = data));
});

const selectProfile = (profile) => {
    profileService.createProfile(profile.filename)
        .then((data) => SelectedProfileService.update(data))
        .then(() => profileService.listJfr().then((data) => (profiles.value = data)));
};

const confirmDeleteProduct = (profile) => {
    profileToRemove.value = profile;
    deleteProfileDialog.value = true;
};

const deleteProfile = () => {
    profileService.deleteJfr(profileToRemove.value.filename)
        .then(() => {
            console.log(profileToRemove.value.filename);
            console.log(profiles.value);
            profiles.value = profiles.value.filter((val) => val.file.filename !== profileToRemove.value.filename);

            deleteProfileDialog.value = false;
            profileToRemove.value = {};
            toast.add({ severity: 'success', summary: 'Successful', detail: 'Product Deleted', life: 3000 });
        });
};

const initFilters = () => {
    filters.value = {
        global: { value: null, matchMode: FilterMatchMode.CONTAINS }
    };
};
</script>

<template>
    <div class="grid">
        <div class="col-12">
            <div class="card">
                <DataTable
                    ref="dt"
                    :value="profiles"
                    dataKey="name"
                    :paginator="true"
                    :rows="20"
                    :filters="filters"
                    :rowHover="true"
                    paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
                    currentPageReportTemplate="Showing {first} to {last} of {totalRecords} profiles"
                    responsiveLayout="scroll">

                    <template #header>
                        <div class="flex flex-column md:flex-row md:justify-content-between md:align-items-center">
                            <h5 class="m-0">JFR Files</h5>
                            <span class="block mt-2 md:mt-0 p-input-icon-left">
                                <i class="pi pi-search" />
                                <InputText v-model="filters['global'].value" placeholder="Search..." />
                            </span>
                        </div>
                    </template>

                    <Column>
                        <template #body="slotProps">
                            <div v-if="slotProps.data.used">
                                <Button icon="pi pi-circle-fill" class="p-button-rounded p-button-success"/>
                            </div>
                            <div v-else>
                                <Button icon="pi pi-play" class="p-button-rounded p-button-primary mt-2"
                                        @click="selectProfile(slotProps.data.file)" />
                            </div>
                        </template>
                    </Column>
                    <Column field="file.filename" header="Name" :sortable="true"
                            headerStyle="width:70%; min-width:10rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">Name</span>
                            {{ slotProps.data.file.filename }}
                        </template>
                    </Column>
                    <Column field="file.dateTime" header="Date" :sortable="true"
                            headerStyle="width:15%; min-width:10rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">Date</span>
                            {{ slotProps.data.file.dateTime }}
                        </template>
                    </Column>
                    <Column field="file.sizeInBytes" header="Size" headerStyle="width:15%; min-width:15rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">Size</span>
                            {{ FormattingService.formatBytes(slotProps.data.file.sizeInBytes) }}
                        </template>
                    </Column>
                    <Column>
                        <template #body="slotProps">
                            <Button icon="pi pi-trash" class="p-button-rounded p-button-warning mt-2"
                                    @click="confirmDeleteProduct(slotProps.data.file)" />
                        </template>
                    </Column>
                </DataTable>

                <Dialog v-model:visible="deleteProfileDialog" :style="{ width: '450px' }" header="Confirm"
                        :modal="true">
                    <div class="flex align-items-center justify-content-center">
                        <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem" />
                        <span v-if="profileToRemove">Are you sure you want to delete: <b>{{ profileToRemove.filename
                            }}</b>?</span>
                    </div>
                    <template #footer>
                        <Button label="No" icon="pi pi-times" class="p-button-text"
                                @click="deleteProfileDialog = false" />
                        <Button label="Yes" icon="pi pi-check" class="p-button-text" @click="deleteProfile" />
                    </template>
                </Dialog>
            </div>
        </div>
    </div>

    <Toast />
</template>

<style scoped lang="scss"></style>
