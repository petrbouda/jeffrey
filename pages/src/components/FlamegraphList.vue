<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';
import FlamegraphService from '@/service/FlamegraphService';
import router from '@/router';
import MessageBus from '@/service/MessageBus';

const props = defineProps(['profileId', 'profileType']);
const flamegraphs = ref(null);

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
        .then((json) => (flamegraphs.value = json));
};

const selectFlamegraph = (flamegraph) => {
    router.push({ name: 'flamegraph-show', query: { mode: 'custom', profileId: props.profileId, flamegraphId: flamegraph.id } });
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

        <Column>
            <template #body="slotProps">
                <div v-if="props.profileType === 'primary'">
                    <Button icon="pi pi-play" class="p-button-rounded p-button-success mt-2"
                            @click="selectFlamegraph(slotProps.data)" />
                </div>
                <div v-else>
                    <Button icon="pi pi-play" class="p-button-rounded p-button-info mt-2"
                            @click="selectFlamegraph(slotProps.data)" />
                </div>
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
</template>
