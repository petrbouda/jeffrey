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
            console.log("New scripts: %o", scripts);
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