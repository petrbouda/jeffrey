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
import FlamegraphList from "../../components/FlamegraphList.vue";
import SecondaryProfileService from "../../service/SecondaryProfileService";
import BreadcrumbComponent from "@/components/BreadcrumbComponent.vue";
import {useRoute} from "vue-router";

const route = useRoute()

const items = [
  {label: 'Saved Graphs', route: 'savedgraphs'}
]
</script>

<template>
  <breadcrumb-component :path="items"></breadcrumb-component>

  <div class="card">
    <TabView>
      <TabPanel header="Primary">
        <FlamegraphList :project-id="route.params.projectId" :profile-id="route.params.profileId"/>
      </TabPanel>

      <div v-if="SecondaryProfileService.id() != null">
        <TabPanel header="Secondary">
          <FlamegraphList :project-id="route.params.projectId" :profile-id="SecondaryProfileService.id()"/>
        </TabPanel>
      </div>
    </TabView>
  </div>

  <Toast/>
</template>
