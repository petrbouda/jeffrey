<script setup>
import { ref, onMounted } from 'vue';
import FlamegraphService from '@/service/FlamegraphService';
import FormattingService from '@/service/FormattingService';
import router from '@/router';

onMounted(() => {
    updateFlamegraphList();
});

const flamegraphToDelete = ref(null);
const flamegraphs = ref(null);

const deleteFlamegraph = (data) => {
    FlamegraphService.delete(data.id)
        .then(() => {
            updateFlamegraphList()
        })
};

const updateFlamegraphList = () => {
    FlamegraphService.list()
        .then((json) => (flamegraphs.value = json));
};

const selectFlamegraph = (flamegraph) => {
    router.push({ name: 'flamegraph-show', query: { mode: "custom", flamegraphId: flamegraph.id } });
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
                    :rows="20"
                    paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
                    currentPageReportTemplate="Showing {first} to {last} of {totalRecords} profiles"
                    responsiveLayout="scroll">

                    <Column>
                        <template #body="slotProps">
                            <Button icon="pi pi-play" class="p-button-rounded p-button-success mt-2"
                                    @click="selectFlamegraph(slotProps.data)" />
                        </template>
                    </Column>
                    <Column field="name" header="Name" :sortable="true" headerStyle="width:60%; min-width:10rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">Name</span>
                            {{ slotProps.data.name }}
                        </template>
                    </Column>
                    <Column field="id" header="ID" headerStyle="width:25%; min-width:10rem;">
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
                                    @click="deleteFlamegraph(slotProps.data)" />
                        </template>
                    </Column>
                </DataTable>
            </div>
        </div>
    </div>
</template>
