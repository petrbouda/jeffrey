<template>
    <div class="field col-12 md:col-6">
        <span class="p-float-label">
            <Textarea inputId="textarea" rows="10" cols="30" v-model="value10"></Textarea>
            <label for="textarea">Insert JFR SQL</label>
        </span>
        <span class="p-float-label">
            <InputText type="text" id="profilename" v-model="value1" />
            <label for="profilename">Profile's name</label>
        </span>
    </div>
</template>
