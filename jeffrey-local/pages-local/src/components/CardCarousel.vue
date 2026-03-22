<template>
  <div class="card-carousel">
    <!-- Navigation moved to category header -->
    
    <Carousel
      ref="carousel"
      :items-to-show="itemsToShow"
      :wrap-around="false"
      :transition="500"
      :breakpoints="breakpoints"
      :autoplay="autoplay ? 5000 : 0"
      :pause-autoplay-on-hover="true"
      :snap-align="'start'"
      class="carousel"
    >
      <slide v-for="(item, index) in items" :key="index" class="carousel-slide">
        <slot name="item" :item="item"></slot>
      </slide>
    </Carousel>
  </div>
</template>

<script setup lang="ts">
import { Carousel, Slide } from 'vue3-carousel';
import 'vue3-carousel/dist/carousel.css';
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';

const props = defineProps<{
  items: any[];
  autoplay?: boolean;
  prevButtonId?: string;
  nextButtonId?: string;
  maxItems?: number;
}>();

const itemsToShow = ref(4);

// Only show navigation if there are more items than can be shown at once
const showNavigation = computed(() => {
  return props.items && props.items.length > itemsToShow.value;
});

const breakpoints = {
  0: {
    itemsToShow: 1,
  },
  576: {
    itemsToShow: 2,
  },
  768: {
    itemsToShow: 2,
  },
  992: {
    itemsToShow: 3,
  },
  1200: {
    itemsToShow: 4,
  }
};

const updateItemsToShow = () => {
  const width = window.innerWidth;
  if (width < 576) {
    itemsToShow.value = 1;
  } else if (width < 768) {
    itemsToShow.value = 2;
  } else if (width < 992) {
    itemsToShow.value = 2;
  } else if (width < 1200) {
    itemsToShow.value = 3;
  } else {
    itemsToShow.value = 4;
  }
  
  // Apply maximum items limit if specified
  if (props.maxItems && itemsToShow.value > props.maxItems) {
    itemsToShow.value = props.maxItems;
  }
  
  // Set itemsToShow to match the actual number of items, but always at least 1
  if (props.items && props.items.length < itemsToShow.value) {
    itemsToShow.value = Math.max(1, props.items.length);
  }
};

const carousel = ref(null);

const slidePrev = () => {
  if (carousel.value) {
    carousel.value.prev();
  }
};

const slideNext = () => {
  if (carousel.value) {
    carousel.value.next();
  }
};

onMounted(() => {
  updateItemsToShow();
  window.addEventListener('resize', updateItemsToShow);
  
  // Set up external navigation buttons if provided
  if (props.prevButtonId) {
    const prevButton = document.getElementById(props.prevButtonId);
    if (prevButton) {
      prevButton.addEventListener('click', slidePrev);
    }
  }
  
  if (props.nextButtonId) {
    const nextButton = document.getElementById(props.nextButtonId);
    if (nextButton) {
      nextButton.addEventListener('click', slideNext);
    }
  }
});

// Clean up event listeners
onUnmounted(() => {
  window.removeEventListener('resize', updateItemsToShow);
  
  if (props.prevButtonId) {
    const prevButton = document.getElementById(props.prevButtonId);
    if (prevButton) {
      prevButton.removeEventListener('click', slidePrev);
    }
  }
  
  if (props.nextButtonId) {
    const nextButton = document.getElementById(props.nextButtonId);
    if (nextButton) {
      nextButton.removeEventListener('click', slideNext);
    }
  }
});

watch(() => props.items, () => {
  // Force carousel to update after items change
  nextTick(() => {
    updateItemsToShow();
  });
}, { deep: true });
</script>

<style scoped>
.card-carousel {
  position: relative;
}

/* Force left alignment for vue3-carousel */
:deep(.carousel__track) {
  justify-content: flex-start !important;
  margin-left: 0 !important;
}

/* Fix for item multiplication */
:deep(.carousel__slide) {
  flex: 0 0 auto !important;
}

.carousel {
  width: 100%;
  display: flex;
  justify-content: flex-start;
}

.carousel-slide {
  display: flex;
  justify-content: flex-start;
  padding-right: 0.8rem;
  height: auto;
  min-height: 5.5rem;
}

/* Consistent styling for all carousels */
</style>
