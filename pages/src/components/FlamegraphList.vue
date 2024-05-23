<script setup>
import { useToast } from 'primevue/usetoast';
import { ref, onMounted, onBeforeUnmount } from 'vue';
import FlamegraphService from '@/service/flamegraphs/FlamegraphService';
import router from '@/router';
import MessageBus from '@/service/MessageBus';

const props = defineProps(['profileId', 'profileType']);
const flamegraphs = ref(null);
const toast = useToast();

onMounted(() => {
    updateFlamegraphList();

    MessageBus.on(MessageBus.FLAMEGRAPH_CREATED, function(profileId) {
        if (props.profileId === profileId) {
            updateFlamegraphList();
        }
    });
});

onBeforeUnmount(() => {
    MessageBus.off(MessageBus.FLAMEGRAPH_CREATED);
});

const deleteFlamegraph = (data) => {
    FlamegraphService.delete(props.profileId, data.id)
        .then(() => {
            updateFlamegraphList();
        });
};

const updateFlamegraphList = () => {
    FlamegraphService.list(props.profileId)
        .then((json) => {
          flamegraphs.value = json
        });
};

const selectFlamegraph = (flamegraph) => {
    router.push({
        name: 'flamegraph-simple',
        query: { profileId: props.profileId, flamegraphId: flamegraph.id }
    });
};

const exportFlamegraph = (flamegraph) => {
    FlamegraphService.exportById(props.profileId, flamegraph.id)
        .then(() => {
            toast.add({ severity: 'success', summary: 'Successful', detail: 'Flamegraph exported', life: 3000 });
        });
};
</script>

<template>
    <DataTable
        ref="dt"
        :value="flamegraphs"
        dataKey="id"
        :paginator="true"
        :rows="20"
        paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
        currentPageReportTemplate="Showing {first} to {last} of {totalRecords} profiles"
        responsiveLayout="scroll">

        <Column header="Actions" headerStyle="width:8%;min-width:10rem">
            <template #body="slotProps">
                <Button icon="pi pi-play" class="p-button-filled p-button-success mt-2"
                        @click="selectFlamegraph(slotProps.data)" />&nbsp;
                <Button icon="pi pi-file-export" class="p-button-filled p-button-info mt-2"
                        @click="exportFlamegraph(slotProps.data)" />
            </template>
        </Column>
        <Column field="name" header="Name" :sortable="true" headerStyle="width:45%; min-width:8rem;">
            <template #body="slotProps">
                <span class="p-column-title">Name</span>
                {{ slotProps.data.name }}
            </template>
        </Column>
        <Column field="id" header="Event Type" headerStyle="width:30%; min-width:10rem;">
            <template #body="slotProps">
                <span class="p-column-title">Event Type</span>
                {{ slotProps.data.eventType }}
            </template>
        </Column>
        <Column field="createdAt" header="Date" :sortable="true" headerStyle="width:20%; min-width:10rem;">
            <template #body="slotProps">
                <span class="p-column-title">Created</span>
                {{ slotProps.data.createdAt }}
            </template>
        </Column>
        <Column>
            <template #body="slotProps">
                <Button icon="pi pi-trash" class="p-button-filled p-button-warning mt-2"
                        @click="deleteFlamegraph(slotProps.data)" />
            </template>
        </Column>
    </DataTable>

    <Toast />
</template>
