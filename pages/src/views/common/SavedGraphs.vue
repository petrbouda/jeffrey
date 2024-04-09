<script setup>
import {useToast} from 'primevue/usetoast';
import FlamegraphList from "../../components/FlamegraphList.vue";
import PrimaryProfileService from "../../service/PrimaryProfileService";
import SecondaryProfileService from "../../service/SecondaryProfileService";

const toast = useToast();
</script>

<template>
  <div class="card">
    <TabView>
      <TabPanel header="Primary">
        <FlamegraphList :profile-id="PrimaryProfileService.id()" profile-type="primary"/>
      </TabPanel>

      <div v-if="SecondaryProfileService.id() != null">
        <TabPanel header="Secondary">
          <FlamegraphList :profile-id="SecondaryProfileService.id()" profile-type="secondary"/>
        </TabPanel>
      </div>
    </TabView>
  </div>

  <Toast/>
</template>
