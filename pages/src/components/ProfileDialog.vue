<script setup>
import PrimaryProfileService from "@/service/PrimaryProfileService";
import SecondaryProfileService from "@/service/SecondaryProfileService";
import {useRouter} from "vue-router";
import ProfileCard from "@/components/ProfileCard.vue";
import {onMounted, ref} from "vue";
import {FilterMatchMode} from "primevue/api";
import ProfileService from "@/service/ProfileService";
import MessageBus from "@/service/MessageBus";
import Utils from "../service/Utils";

const router = useRouter();

const props = defineProps(['activatedFor']);
const profileSelector = ref(false)

const profiles = ref(null);
const filters = ref({
  name: {value: null, matchMode: FilterMatchMode.CONTAINS}
});

onMounted(() => {
  profileSelector.value = true

  ProfileService.list().then((data) => (profiles.value = data));
})

const selectProfile = (profile) => {
  if (props.activatedFor === 'primary') {
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
