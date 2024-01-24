<script setup>
import { FilterMatchMode } from 'primevue/api';
import { onBeforeMount, onMounted, ref } from 'vue';
import { useToast } from 'primevue/usetoast';
import ProfileService from '../service/ProfileService';
import SelectedProfileService from '@/service/SelectedProfileService';

const toast = useToast();
const profiles = ref(null);
const dt = ref(null);
const filters = ref({});

const profileService = new ProfileService();

onBeforeMount(() => {
    initFilters();
});
onMounted(() => {
    profileService.listProfiles().then((data) => (profiles.value = data));
});

const selectProfile = (profile) => {
    SelectedProfileService.update(profile);
    toast.add({ severity: 'success', summary: 'Successful', detail: 'Profile Selected: ' + profile.name, life: 3000 });
    profileService.listProfiles().then((data) => (profiles.value = data));
};

const deleteProfile = (profile) => {
    profileService.deleteProfile(profile.id)
        .then(() => {
            profiles.value = profiles.value.filter((val) => val.id !== profile.id);
            toast.add({
                severity: 'success',
                summary: 'Successful',
                detail: 'Profile Deleted: ' + profile.name,
                life: 3000
            });
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
                    id="datatable"
                    ref="dt"
                    :value="profiles"
                    dataKey="Name"
                    :paginator="true"
                    :rows="20"
                    :filters="filters"
                    :rowHover="true"
                    paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
                    currentPageReportTemplate="Showing {first} to {last} of {totalRecords} profiles"
                    responsiveLayout="scroll">
                    <template #header>
                        <div class="flex flex-column md:flex-row md:justify-content-between md:align-items-center">
                            <h5 class="m-0">Select a Generated Profile</h5>
                            <span class="block mt-2 md:mt-0 p-input-icon-left">
                <i class="pi pi-search" />
                <InputText v-model="filters['global'].value" placeholder="Search..." />
              </span>
                        </div>
                    </template>

                    <Column>
                        <template #body="slotProps">
                            <div v-if="SelectedProfileService.get().id === slotProps.data.id">
                                <Button icon="pi pi-circle-fill" class="p-button-rounded p-button-success" />
                            </div>
                            <div v-else>
                                <Button icon="pi pi-play" class="p-button-rounded p-button-primary mt-2"
                                        @click="selectProfile(slotProps.data)" />
                            </div>
                        </template>
                    </Column>
                    <Column field="name" header="Name" :sortable="true" headerStyle="width:60%; min-width:10rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">Name</span>
                            <div v-if="SelectedProfileService.get().id === slotProps.data.id">
                                <span style="color: green; font-weight: bold">{{ slotProps.data.name }}</span>
                            </div>
                            <div v-else>
                                {{ slotProps.data.name }}
                            </div>
                        </template>
                    </Column>
                    <Column field="code" header="ID" headerStyle="width:25%; min-width:10rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">ID</span>
                            {{ slotProps.data.id }}
                        </template>
                    </Column>
                    <Column field="createdAt" header="Date" :sortable="true" headerStyle="width:15%; min-width:10rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">Created</span>
                            {{ slotProps.data.createdAt }}
                        </template>
                    </Column>
                    <Column>
                        <template #body="slotProps">
                            <Button icon="pi pi-trash" class="p-button-rounded p-button-warning mt-2"
                                    @click="deleteProfile(slotProps.data)" />
                        </template>
                    </Column>
                </DataTable>
            </div>
        </div>
    </div>

    <Toast />
</template>

<style scoped lang="scss"></style>
