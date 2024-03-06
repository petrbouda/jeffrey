<script setup>
import PrimaryProfileService from '@/service/PrimaryProfileService';
import SecondaryProfileService from '@/service/SecondaryProfileService';
import {useToast} from 'primevue/usetoast';

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
