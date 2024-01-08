<script setup>
import { onMounted, ref } from 'vue';
import GenerateFlamegraphService from '@/service/FlamegraphService';
import FormattingService from '@/service/FormattingService';
import router from '@/router';

const flamegraphs = ref(null);

onMounted(() => {
    GenerateFlamegraphService.list()
        .then((json) => (flamegraphs.value = json));
});

const selectFlamegraph = (flamegraph) => {
    router.push({ name: 'flamegraph-show', params: { flamegraphFile: flamegraph.filename } });
};

const flamegraphsGenerated = (data) => {
    flamegraphs.value = data;
};

const nestedRouteItems = ref([
    {
        label: 'Event Types',
        to: '/flamegraph/general'
    },
    {
        label: 'JFR SQL',
        to: '/flamegraph/general/sql'
    }
]);
</script>

<template>
    <div class="card card-w-title">
        <h5>Generate Flamegraph</h5>
        <TabMenu :model="nestedRouteItems" />
        <div class="grid p-fluid mt-3">
            <router-view @flamegraph-change="flamegraphsGenerated" />
        </div>
    </div>

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

                    <Column field="code" header="Name" headerStyle="width:60%; min-width:10rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">Name</span>
                            {{ slotProps.data.filename }}
                        </template>
                    </Column>
                    <Column field="name" header="Date" headerStyle="width:15%; min-width:10rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">Date</span>
                            {{ slotProps.data.dateTime }}
                        </template>
                    </Column>
                    <Column header="Size" headerStyle="width:10%; min-width:15rem;">
                        <template #body="slotProps">
                            <span class="p-column-title">Size</span>
                            {{ FormattingService.formatBytes(slotProps.data.sizeInBytes) }}
                        </template>
                    </Column>
                    <Column headerStyle="min-width:10rem;">
                        <template #body="slotProps">
                            <Button icon="pi pi-play" class="p-button-rounded p-button-success mt-2"
                                    @click="selectFlamegraph(slotProps.data)" />
                            &nbsp;
                            <Button icon="pi pi-trash" class="p-button-rounded p-button-warning mt-2"
                                    @click="confirmDeleteProduct(slotProps.data)" />
                        </template>
                    </Column>
                </DataTable>

<!--                <Dialog v-model:visible="deleteProductDialog" :style="{ width: '450px' }" header="Confirm"-->
<!--                        :modal="true">-->
<!--                    <div class="flex align-items-center justify-content-center">-->
<!--                        <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem" />-->
<!--                        <span v-if="profile">Are you sure you want to delete <b>{{ profile.name }}</b>?</span>-->
<!--                    </div>-->
<!--                    <template #footer>-->
<!--                        <Button label="No" icon="pi pi-times" class="p-button-text"-->
<!--                                @click="deleteProductDialog = false" />-->
<!--                        <Button label="Yes" icon="pi pi-check" class="p-button-text" @click="deleteProduct" />-->
<!--                    </template>-->
<!--                </Dialog>-->
            </div>
        </div>
    </div>
</template>

<style scoped lang="scss"></style>
