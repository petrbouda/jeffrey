<script setup>
import { FilterMatchMode } from 'primevue/api';
import { onBeforeMount, onMounted, ref } from 'vue';
import { useToast } from 'primevue/usetoast';
import ProfileService from '../../service/ProfileService';
import SelectedProfileService from '@/service/SelectedProfileService';

const toast = useToast();

const profiles = ref(null);
const productDialog = ref(false);
const deleteProductDialog = ref(false);
const deleteProductsDialog = ref(false);
const profile = ref({});
const selectedProducts = ref(null);
const dt = ref(null);
const filters = ref({});
const submitted = ref(false);
const statuses = ref([
    { label: 'INSTOCK', value: 'instock' },
    { label: 'LOWSTOCK', value: 'lowstock' },
    { label: 'OUTOFSTOCK', value: 'outofstock' }
]);

const profileService = new ProfileService();

onBeforeMount(() => {
    initFilters();
});
onMounted(() => {
    profileService.list().then((data) => (profiles.value = data));
});
const formatCurrency = (value) => {
    return value.toLocaleString('en-US', { style: 'currency', currency: 'USD' });
};

const openNew = () => {
    profile.value = {};
    submitted.value = false;
    productDialog.value = true;
};

const hideDialog = () => {
    productDialog.value = false;
    submitted.value = false;
};

function formatBytes(bytes, decimals = 2) {
    if (!+bytes) return '0 Bytes'

    const k = 1024
    const dm = decimals < 0 ? 0 : decimals
    const sizes = ['Bytes', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB']

    const i = Math.floor(Math.log(bytes) / Math.log(k))

    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${sizes[i]}`
}

// const saveProduct = () => {
//   submitted.value = true;
//   if (product.value.name && product.value.name.trim() && product.value.price) {
//     if (product.value.id) {
//       product.value.inventoryStatus = product.value.inventoryStatus.value ? product.value.inventoryStatus.value : product.value.inventoryStatus;
//       products.value[findIndexById(product.value.id)] = product.value;
//       toast.add({severity: 'success', summary: 'Successful', detail: 'Product Updated', life: 3000});
//     } else {
//       product.value.id = createId();
//       product.value.code = createId();
//       product.value.image = 'product-placeholder.svg';
//       product.value.inventoryStatus = product.value.inventoryStatus ? product.value.inventoryStatus.value : 'INSTOCK';
//       products.value.push(product.value);
//       toast.add({severity: 'success', summary: 'Successful', detail: 'Product Created', life: 3000});
//     }
//     productDialog.value = false;
//     product.value = {};
//   }
// };

const editProduct = (editProduct) => {
    profile.value = { ...editProduct };
    console.log(profile);
    productDialog.value = true;
};

const selectProfile = (profile) => {
    SelectedProfileService.update(profile)
};

const confirmDeleteProduct = (editProduct) => {
    profile.value = editProduct;
    deleteProductDialog.value = true;
};

const deleteProduct = () => {
    profiles.value = profiles.value.filter((val) => val.id !== product.value.id);
    deleteProductDialog.value = false;
    profile.value = {};
    toast.add({ severity: 'success', summary: 'Successful', detail: 'Product Deleted', life: 3000 });
};

const findIndexById = (id) => {
    let index = -1;
    for (let i = 0; i < profiles.value.length; i++) {
        if (profiles.value[i].id === id) {
            index = i;
            break;
        }
    }
    return index;
};

const createId = () => {
    let id = '';
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    for (let i = 0; i < 5; i++) {
        id += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return id;
};

const confirmDeleteSelected = () => {
    deleteProductsDialog.value = true;
};
const deleteSelectedProducts = () => {
    profiles.value = profiles.value.filter((val) => !selectedProducts.value.includes(val));
    deleteProductsDialog.value = false;
    selectedProducts.value = null;
    toast.add({ severity: 'success', summary: 'Successful', detail: 'Products Deleted', life: 3000 });
};

const initFilters = () => {
    filters.value = {
        global: { value: null, matchMode: FilterMatchMode.CONTAINS }
    };
};
</script>

<template>
    <div class="grid">
        <div class="col-12">
            <div class="card">
                <DataTable
                    ref="dt"
                    :value="profiles"
                    dataKey="id"
                    :paginator="true"
                    :rows="10"
                    :filters="filters"
                    paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
                    currentPageReportTemplate="Showing {first} to {last} of {totalRecords} profiles"
                    responsiveLayout="scroll">
                    <template #header>
                        <div class="flex flex-column md:flex-row md:justify-content-between md:align-items-center">
                            <h5 class="m-0">Manage Profiles</h5>
                            <span class="block mt-2 md:mt-0 p-input-icon-left">
                <i class="pi pi-search" />
                <InputText v-model="filters['global'].value" placeholder="Search..." />
              </span>
                        </div>
                    </template>

                    <!--          <Column selectionMode="multiple" headerStyle="width: 3rem"></Column>-->
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
                            {{ formatBytes(slotProps.data.sizeInBytes) }}
                        </template>
                    </Column>
                    <!--          <Column field="price" header="Price" :sortable="true" headerStyle="width:14%; min-width:8rem;">-->
                    <!--            <template #body="slotProps">-->
                    <!--              <span class="p-column-title">Price</span>-->
                    <!--              {{ formatCurrency(slotProps.data.price) }}-->
                    <!--            </template>-->
                    <!--          </Column>-->
                    <!--          <Column field="category" header="Category" :sortable="true" headerStyle="width:14%; min-width:10rem;">-->
                    <!--            <template #body="slotProps">-->
                    <!--              <span class="p-column-title">Category</span>-->
                    <!--              {{ slotProps.data.category }}-->
                    <!--            </template>-->
                    <!--          </Column>-->
                    <!--          <Column field="rating" header="Reviews" :sortable="true" headerStyle="width:14%; min-width:10rem;">-->
                    <!--            <template #body="slotProps">-->
                    <!--              <span class="p-column-title">Rating</span>-->
                    <!--              <Rating :modelValue="slotProps.data.rating" :readonly="true" :cancel="false"/>-->
                    <!--            </template>-->
                    <!--          </Column>-->
                    <!--          <Column field="inventoryStatus" header="Status" :sortable="true" headerStyle="width:14%; min-width:10rem;">-->
                    <!--            <template #body="slotProps">-->
                    <!--              <span class="p-column-title">Status</span>-->
                    <!--              <span-->
                    <!--                  :class="'product-badge status-' + (slotProps.data.inventoryStatus ? slotProps.data.inventoryStatus.toLowerCase() : '')">{{-->
                    <!--                  slotProps.data.inventoryStatus-->
                    <!--                }}</span>-->
                    <!--            </template>-->
                    <!--          </Column>-->
                    <Column headerStyle="min-width:10rem;">
                        <template #body="slotProps">
                            <!--              <Button icon="pi pi-pencil" class="p-button-rounded p-button-success mr-2"-->
                            <!--                      @click="editProduct(slotProps.data)"/>-->
                            <Button icon="pi pi-play" class="p-button-rounded p-button-success mt-2" @click="selectProfile(slotProps.data)" />
                            &nbsp;
                            <Button icon="pi pi-trash" class="p-button-rounded p-button-warning mt-2" @click="confirmDeleteProduct(slotProps.data)" />
                        </template>
                    </Column>
                </DataTable>

                <Dialog v-model:visible="productDialog" :style="{ width: '450px' }" header="Product Details"
                        :modal="true"
                        class="p-fluid">
                    <img :src="'demo/images/product/' + profile.image" :alt="profile.image" v-if="profile.image"
                         width="150"
                         class="mt-0 mx-auto mb-5 block shadow-2" />
                    <div class="field">
                        <label for="name">Name</label>
                        <InputText id="name" v-model.trim="profile.name" required="true" autofocus
                                   :class="{ 'p-invalid': submitted && !profile.name }" />
                        <small class="p-invalid" v-if="submitted && !profile.name">Name is required.</small>
                    </div>
                    <div class="field">
                        <label for="description">Description</label>
                        <Textarea id="description" v-model="product.description" required="true" rows="3" cols="20" />
                    </div>

                    <div class="field">
                        <label for="inventoryStatus" class="mb-3">Inventory Status</label>
                        <Dropdown id="inventoryStatus" v-model="profile.inventoryStatus" :options="statuses"
                                  optionLabel="label"
                                  placeholder="Select a Status">
                            <template #value="slotProps">
                                <div v-if="slotProps.value && slotProps.value.value">
                                    <span :class="'product-badge status-' + slotProps.value.value">{{ slotProps.value.label }}</span>
                                </div>
                                <div v-else-if="slotProps.value && !slotProps.value.value">
                                    <span :class="'product-badge status-' + slotProps.value.toLowerCase()">{{ slotProps.value }}</span>
                                </div>
                                <span v-else>
                                    {{ slotProps.placeholder }}
                                </span>
                            </template>
                        </Dropdown>
                    </div>

                    <div class="field">
                        <label class="mb-3">Category</label>
                        <div class="formgrid grid">
                            <div class="field-radiobutton col-6">
                                <RadioButton id="category1" name="category" value="Accessories"
                                             v-model="profile.category" />
                                <label for="category1">Accessories</label>
                            </div>
                            <div class="field-radiobutton col-6">
                                <RadioButton id="category2" name="category" value="Clothing"
                                             v-model="profile.category" />
                                <label for="category2">Clothing</label>
                            </div>
                            <div class="field-radiobutton col-6">
                                <RadioButton id="category3" name="category" value="Electronics"
                                             v-model="profile.category" />
                                <label for="category3">Electronics</label>
                            </div>
                            <div class="field-radiobutton col-6">
                                <RadioButton id="category4" name="category" value="Fitness"
                                             v-model="profile.category" />
                                <label for="category4">Fitness</label>
                            </div>
                        </div>
                    </div>

                    <div class="formgrid grid">
                        <div class="field col">
                            <label for="price">Price</label>
                            <InputNumber id="price" v-model="profile.price" mode="currency" currency="USD"
                                         locale="en-US"
                                         :class="{ 'p-invalid': submitted && !profile.price }" :required="true" />
                            <small class="p-invalid" v-if="submitted && !profile.price">Price is required.</small>
                        </div>
                        <div class="field col">
                            <label for="quantity">Quantity</label>
                            <InputNumber id="quantity" v-model="profile.quantity" integeronly />
                        </div>
                    </div>
                    <template #footer>
                        <Button label="Cancel" icon="pi pi-times" class="p-button-text" @click="hideDialog" />
                        <Button label="Save" icon="pi pi-check" class="p-button-text" @click="saveProduct" />
                    </template>
                </Dialog>

                <Dialog v-model:visible="deleteProductDialog" :style="{ width: '450px' }" header="Confirm"
                        :modal="true">
                    <div class="flex align-items-center justify-content-center">
                        <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem" />
                        <span v-if="profile"
                        >Are you sure you want to delete <b>{{ profile.name }}</b
                        >?</span
                        >
                    </div>
                    <template #footer>
                        <Button label="No" icon="pi pi-times" class="p-button-text"
                                @click="deleteProductDialog = false" />
                        <Button label="Yes" icon="pi pi-check" class="p-button-text" @click="deleteProduct" />
                    </template>
                </Dialog>

                <Dialog v-model:visible="deleteProductsDialog" :style="{ width: '450px' }" header="Confirm"
                        :modal="true">
                    <div class="flex align-items-center justify-content-center">
                        <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem" />
                        <span v-if="product">Are you sure you want to delete the selected products?</span>
                    </div>
                    <template #footer>
                        <Button label="No" icon="pi pi-times" class="p-button-text"
                                @click="deleteProductsDialog = false" />
                        <Button label="Yes" icon="pi pi-check" class="p-button-text" @click="deleteSelectedProducts" />
                    </template>
                </Dialog>
            </div>
        </div>
    </div>
</template>

<style scoped lang="scss"></style>
