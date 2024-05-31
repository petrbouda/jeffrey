<script setup>
import FlamegraphList from "../../components/FlamegraphList.vue";
import PrimaryProfileService from "../../service/PrimaryProfileService";
import SecondaryProfileService from "../../service/SecondaryProfileService";
import BreadcrumbComponent from "@/components/BreadcrumbComponent.vue";

const items = [
  {label: 'Profile'},
  {label: 'Saved Graphs', route: '/common/savedgraphs'}
]
</script>

<template>
  <breadcrumb-component :path="items"></breadcrumb-component>

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
