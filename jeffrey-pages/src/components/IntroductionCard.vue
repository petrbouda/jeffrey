<script setup>
const props = defineProps({
  title: String,
  content: String,
  file: String,
  icon: String,
  colorClass: String,
  route: String // New property for route path
});
</script>

<template>
  <div class="card h-100 intro-card">
    <div :class="['card-accent', colorClass]"></div>
    <div class="card-body">
      <div class="d-flex align-items-center mb-3">
        <div :class="['card-icon me-3', colorClass]">
          <i :class="['bi', icon]"></i>
        </div>
        <h3 class="card-title mb-0">{{ title }}</h3>
      </div>
      <p class="card-text">{{ content }}</p>
    </div>
    <div class="card-footer border-0 bg-transparent">
      <router-link :to="route" class="btn text-primary">
        <span>Read More</span>
        <i class="bi bi-arrow-right ms-2"></i>
      </router-link>
    </div>
  </div>
</template>

<style scoped>
.intro-card {
  border: none;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 5px 15px rgba(0,0,0,0.08);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
  background-color: white;
}

.intro-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0,0,0,0.12);
}

.card-accent {
  height: 6px;
  width: 100%;
}

.card-icon {
  font-size: 1.5rem;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  flex-shrink: 0;
  color: white;
}

.card-title {
  font-weight: 700;
  margin-bottom: 0.75rem;
  font-size: 1.25rem;
  color: #343a40;
}

.card-text {
  color: #495057;
  font-size: 0.95rem;
}

.card-footer {
  padding: 1rem;
}

.card-footer .btn {
  border-radius: 8px;
  padding: 0.4rem 1rem;
  font-weight: 600;
  font-size: 0.9rem;
  transition: transform 0.2s ease;
  background-color: rgba(52, 152, 219, 0.1);
}

.card-footer .btn:hover {
  background-color: rgba(52, 152, 219, 0.2);
}

/* Softer pastel colors for card accents and icons */
.bg-primary-gradient {
  background: rgba(78, 115, 223, 0.8);
}

.bg-success-gradient {
  background: rgba(87, 182, 132, 0.8);
}

.bg-info-gradient {
  background: rgba(54, 162, 235, 0.8);
}
</style>
