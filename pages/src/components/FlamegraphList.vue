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
import {useToast} from 'primevue/usetoast';
import {onBeforeUnmount, onMounted, ref} from 'vue';
import FlamegraphRepositoryClient from '@/service/flamegraphs/client/FlamegraphRepositoryClient.ts';
import router from '@/router';
import MessageBus from '@/service/MessageBus';

const props = defineProps(['projectId', 'profileId']);
const flamegraphs = ref(null);
const toast = useToast();

const flamegraphService = new FlamegraphRepositoryClient(props.projectId, props.profileId)

onMounted(() => {
  updateFlamegraphList();

  MessageBus.on(MessageBus.FLAMEGRAPH_CREATED, function (profileId) {
    if (props.profileId === profileId) {
      updateFlamegraphList();
    }
  });
});

onBeforeUnmount(() => {
  MessageBus.off(MessageBus.FLAMEGRAPH_CREATED);
});

const deleteFlamegraph = (data) => {
  flamegraphService.delete(data.id)
      .then(() => {
        updateFlamegraphList();
      });
};

const updateFlamegraphList = () => {
  flamegraphService.list()
      .then((json) => {
        flamegraphs.value = json
      });
};

const selectFlamegraph = (flamegraph) => {
  router.push({
    name: 'flamegraph-simple',
    query: {flamegraphId: flamegraph.id}
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

    <Column header="Actions" headerStyle="width:4%;">
      <template #body="slotProps">
        <Button icon="pi pi-play" class="p-button-filled p-button-success mt-2"
                @click="selectFlamegraph(slotProps.data)"/>&nbsp;
      </template>
    </Column>
    <Column field="name" header="Name" :sortable="true" headerStyle="width:45%; min-width:8rem;">
      <template #body="slotProps">
        <span class="p-column-title">Name</span>
        {{ slotProps.data.name }}
      </template>
    </Column>
    <Column field="id" header="Event Type" headerStyle="width:20%; min-width:10rem;">
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
                @click="deleteFlamegraph(slotProps.data)"/>
      </template>
    </Column>
  </DataTable>

  <Toast/>
</template>
