<script setup>

</script>

<template>
  <div class="grid grid-nogutter bg-white border-round">
    <div class="col-8 p-6 text-center flex align-items-center">
      <div class="grid">
        <div class="col-12">
          <div class="col-12 font-bold flex align-items-end justify-content-start">
            <img src="/quotation_marks.png" style="width: 75px; height: auto" class="rotate-180" alt=""/>
          </div>

          <div class="font-semibold font-italic p-5 text-5xl">
            Hi, I'm Jeffrey, a passionate guy in profiling in Java and JFR. <br>I like unraveling the mysteries of code performance and diving deep into the intricate layers of Java apps.
          </div>

          <div class="col-12 font-bold flex align-items-center justify-content-end">
            <img src="/quotation_marks.png" style="width: 75px; height: auto" alt=""/>
          </div>
        </div>

        <div class="col-12 font-semibold font-italic p-5 pt-5 text-1xl">
          Let's start learning new stuff together by selecting a profile, or generating a new one from recordings.
        </div>

        <div class="col-12">
          <Button label="Select Profile" type="button" class="mr-3 p-button-raised"></Button>
          <Button label="Recordings" type="button" class="p-button-outlined"></Button>
        </div>
      </div>
    </div>

    <div class="col-4 overflow-hidden">
      <img src="/jeffrey.jpg" alt="Image" class="md:ml-auto block md:h-full border-round-right"
           style="clip-path: polygon(8% 0, 100% 0%, 100% 100%, 0 100%)"/>
    </div>
  </div>


</template>
