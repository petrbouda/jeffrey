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

<template>
    <div></div>
</template>

<script>
export default {
    name: "AddScripts",
    props: {
        scripts: {
            type: Array,
            required: true
        }
    },
    data(){
        return{
            addedScripts: []
        }
    },
    methods: {
        scriptIsAdded(src){
            return this.addedScripts.includes(src);
        },
        loadScripts(scripts){
            scripts = scripts.filter(src=>!this.scriptIsAdded(src));
            // ^ removes scripts that have already been added
            Promise.allSettled(scripts.map(src => {
                return new Promise((resolve) => {
                    let newScript = document.createElement('script'); // create a new <script> element
                    let uniqueID = "SCRIPT_"+new Date().getTime(); // create a unique ID
                    newScript.setAttribute('src', src); // set the script's src
                    newScript.setAttribute("type", "text/javascript");
                    newScript.setAttribute("id", uniqueID); // add unique ID
                    document.head.appendChild(newScript); // add the new script to the page
                    this.addedScripts.push(src);
                    resolve(newScript);
                });
            })).then((scripts)=>{
                scripts.forEach(v=> {
                    let element = v.value;
                    if(!element) return;
                    element.onload = () => {
                        this.$emit("success", element.src);
                    }
                    element.onerror = () => {
                        this.$emit("error", element.src);
                    }
                });
            }).catch((err) => {
                console.log("Error loading scripts:", err);
            });
        }
    },
    watch:{
        scripts : {
            handler(srcList){
                this.loadScripts(srcList.slice());
            },
            immediate: true,
            deep: true
        }
    }
}
</script>

<style>
</style>
