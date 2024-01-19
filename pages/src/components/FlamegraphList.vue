<script setup>
import { ref, onMounted } from 'vue';
import FlamegraphService from '@/service/FlamegraphService';
import FormattingService from '@/service/FormattingService';
import router from '@/router';

onMounted(() => {
    updateFlamegraphList();
});

const deleteDialogActive = ref(false);
const flamegraphToDelete = ref(null);
const flamegraphs = ref(null);

const confirmDelete = (data) => {
    deleteDialogActive.value = true;
    flamegraphToDelete.value = data.filename;
};

const deleteFlamegraph = () => {
    FlamegraphService.delete(flamegraphToDelete.value)
        .then(() => {
            deleteDialogActive.value = false;
            flamegraphToDelete.value = null;
            updateFlamegraphList()
        })
};

const updateFlamegraphList = () => {
    FlamegraphService.list()
        .then((json) => (flamegraphs.value = json));
};

const selectFlamegraph = (flamegraph) => {
    router.push({ name: 'flamegraph-show', params: { flamegraphId: flamegraph.id } });
};

defineExpose({
    updateFlamegraphList
});
</script>

<template>
    <div class="grid">
        <div class="col-12">
            <div class="card">
                <DataTable
                    ref="dt"
                    :value="flamegraphs"
                    dataKey="id"
                    :paginator="true"
                    :rows="10"
                    paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
                    currentPageReportTemplate="Showing {first} to {last} of {totalRecords} profiles"
                    responsiveLayout="scroll">

                    <Column field="code" header="Name" headerStyle="width:40%; min-width:10rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">Name</span>
                            {{ slotProps.data.name }}
                        </template>
                    </Column>
                    <Column field="name" header="ID" headerStyle="width:35%; min-width:10rem;">
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
                            <Button icon="pi pi-play" class="p-button-rounded p-button-success mt-2"
                                    @click="selectFlamegraph(slotProps.data)" />
                            &nbsp;
                            <Button icon="pi pi-trash" class="p-button-rounded p-button-warning mt-2"
                                    @click="confirmDelete(slotProps.data)" />
                        </template>
                    </Column>
                </DataTable>

                <Dialog v-model:visible="deleteDialogActive" :style="{ width: '450px' }" header="Confirm"
                        :modal="true">
                    <div class="flex align-items-center justify-content-center">
                        <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem" />
                        <span v-if="flamegraphToDelete">Are you sure you want to delete <b>{{ flamegraphToDelete }}</b>?</span>
                    </div>
                    <template #footer>
                        <Button label="No" icon="pi pi-times" class="p-button-text" @click="(deleteDialogActive = false); (flamegraphToDelete.value = null)" />
                        <Button label="Yes" icon="pi pi-check" class="p-button-text" @click="deleteFlamegraph" />
                    </template>
                </Dialog>
            </div>
        </div>
    </div>
</template>
