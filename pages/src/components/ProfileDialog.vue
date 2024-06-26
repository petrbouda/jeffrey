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
import PrimaryProfileService from "@/service/PrimaryProfileService";
import SecondaryProfileService from "@/service/SecondaryProfileService";
import {useRouter} from "vue-router";
import ProfileCard from "@/components/ProfileCard.vue";
import {onBeforeUnmount, onMounted, ref} from "vue";
import {FilterMatchMode} from "primevue/api";
import ProfileService from "@/service/ProfileService";
import Utils from "../service/Utils";
import MessageBus from "@/service/MessageBus";
import ProfileType from "@/service/flamegraphs/ProfileType";

const router = useRouter();

const props = defineProps(['activatedFor', 'activated']);
const profileSelector = ref(Utils.parseBoolean(props.activated))

let profileType = props.activatedFor

const profiles = ref(null);
const filters = ref({
  name: {value: null, matchMode: FilterMatchMode.CONTAINS}
});

onMounted(() => {
  ProfileService.list()
      .then((data) => (profiles.value = data));

  MessageBus.on(MessageBus.PROFILE_DIALOG_TOGGLE, (content) => {
    profileType = content
    profileSelector.value = true
  })
})

onBeforeUnmount(() => {
  MessageBus.off(MessageBus.PROFILE_DIALOG_TOGGLE);
});

const selectProfile = (profile) => {
  if (profileType === ProfileType.PRIMARY) {
    PrimaryProfileService.update(profile)
  } else {
    SecondaryProfileService.update(profile);
  }
  router.go()
}

// const toggle = (event) => {
//   MessageBus.emit(MessageBus.PROFILE_CARD_TOGGLE, event)
// };

</script>

<template>
  <Dialog v-model:visible="profileSelector" modal header=" " :style="{ width: '80%' }">
    <DataTable
        id="datatable"
        ref="dt"
        :value="profiles"
        dataKey="Name"
        paginator
        :rows="20"
        v-model:filters="filters"
        filterDisplay="menu">

      <Column header="" headerStyle="width:12%">
        <template #body="slotProps">
          <!--          <Button icon="pi pi-info" outlined severity="secondary" class="mr-2" @click="toggle"/>-->
          <Button icon="pi pi-play" class="p-button-primary" @click="selectProfile(slotProps.data)"/>
        </template>
      </Column>
      <Column field="name" header="Name" :sortable="true" headerStyle="width:63%; min-width:10rem;"
              :showFilterMatchModes="false">
        <template #body="slotProps">
          <span class="font-bold">{{ slotProps.data.name }}</span>
        </template>
        <template #filter="{ filterModel }">
          <InputText v-model="filterModel.value" type="text" class="p-column-filter" placeholder="Search by name"/>
        </template>
      </Column>
      <Column field="createdAt" header="Created at" :sortable="true" headerStyle="width:25%; min-width:10rem;">
        <template #body="slotProps">
          {{ Utils.formatDateTime(slotProps.data.createdAt) }}
        </template>
      </Column>
    </DataTable>

    <ProfileCard></ProfileCard>
  </Dialog>
</template>
