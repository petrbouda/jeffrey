<script setup>
import { FilterMatchMode } from 'primevue/api';
import { onBeforeMount, onMounted, ref } from 'vue';
import { useToast } from 'primevue/usetoast';
import ProfileService from '../service/ProfileService';
import SelectedProfileService from '@/service/SelectedProfileService';

const toast = useToast();
const profiles = ref(null);
const deleteProfileDialog = ref(false);
const profile = ref({});
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
};

const confirmDeleteProduct = (editProduct) => {
    profile.value = editProduct;
    deleteProfileDialog.value = true;
};

const deleteProfile = () => {
    profileService.deleteProfile(profile.value.id)
        .then(() => {
            profiles.value = profiles.value.filter((val) => val.id !== profile.value.id);
            deleteProfileDialog.value = false;
            profile.value = {};
            toast.add({ severity: 'success', summary: 'Successful', detail: 'Profile Deleted', life: 3000 });
        })
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
                    dataKey="Name"
                    :paginator="true"
                    :rows="10"
                    :filters="filters"
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

                    <!--          <Column selectionMode="multiple" headerStyle="width: 3rem"></Column>-->
                    <Column field="code" header="Name" headerStyle="width:60%; min-width:10rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">Name</span>
                            {{ slotProps.data.name }}
                        </template>
                    </Column>
                    <Column field="code" header="ID" headerStyle="width:25%; min-width:10rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">ID</span>
                            {{ slotProps.data.id }}
                        </template>
                    </Column>
                    <Column field="name" header="Date" headerStyle="width:15%; min-width:10rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">Created</span>
                            {{ slotProps.data.createdAt }}
                        </template>
                    </Column>
                    <Column headerStyle="min-width:10rem;">
                        <template #body="slotProps">
                            <Button icon="pi pi-play" class="p-button-rounded p-button-success mt-2" @click="selectProfile(slotProps.data)" />
                            &nbsp;
                            <Button icon="pi pi-trash" class="p-button-rounded p-button-warning mt-2" @click="confirmDeleteProduct(slotProps.data)" />
                        </template>
                    </Column>
                </DataTable>

                <Dialog v-model:visible="deleteProfileDialog" :style="{ width: '450px' }" header="Confirm"
                        :modal="true">
                    <div class="flex align-items-center justify-content-center">
                        <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem" />
                        <span v-if="profile">Are you sure you want to delete <b>{{ profile.name }}</b>?</span>
                    </div>
                    <template #footer>
                        <Button label="No" icon="pi pi-times" class="p-button-text" @click="deleteProfileDialog = false" />
                        <Button label="Yes" icon="pi pi-check" class="p-button-text" @click="deleteProfile" />
                    </template>
                </Dialog>
            </div>
        </div>
    </div>
</template>

<style scoped lang="scss"></style>
