<script setup>
// BlogCard component for Jeffrey website
const props = defineProps({
  title: String,
  date: String,
  summary: String,
  slug: String,
  image: String // Added image prop for the thumbnails
});

// Map slug to route path for specific blog posts that have dedicated pages
const getRouteForSlug = (slug) => {
  const routeMap = {
    'java-profiling-literature': '/blog/java-profiling-literature',
    'jfr-in-depth': '/blog/jfr-in-depth',
    'jeffrey-04-announcement': '/blog/jeffrey-04-announcement'
  };
  
  return routeMap[slug] || '/blog'; // Default to blog page if no specific route
};
</script>

<template>
  <div class="card h-100 blog-card">
    <div class="card-accent"></div>
    <div class="card-body pb-0">
      <h3 class="card-title">{{ title }}</h3>
      <p class="card-text">{{ summary }}</p>
    </div>
    <div class="card-footer border-0 bg-transparent d-flex justify-content-between align-items-center">
      <router-link :to="getRouteForSlug(slug)" class="read-more-btn">
        Read Article
        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="icon" viewBox="0 0 16 16">
          <path fill-rule="evenodd" d="M1 8a.5.5 0 0 1 .5-.5h11.793l-3.147-3.146a.5.5 0 0 1 .708-.708l4 4a.5.5 0 0 1 0 .708l-4 4a.5.5 0 0 1-.708-.708L13.293 8.5H1.5A.5.5 0 0 1 1 8z"/>
        </svg>
      </router-link>
      <span class="blog-date">{{ date }}</span>
    </div>
  </div>
</template>

<style scoped>
.blog-card {
  border: none;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08);
  background-color: white;
  position: relative;
  transition: all 0.3s ease;
}

.blog-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 20px 40px rgba(0,0,0,0.1);
}

.card-accent {
  height: 6px;
  background: linear-gradient(90deg, #4f46e5 0%, #7c3aed 50%, #ec4899 100%);
  width: 100%;
}

.card-body {
  padding-top: 1.5rem;
}

.card-title {
  font-weight: 700;
  font-size: 1.35rem;
  color: #111827;
  margin-bottom: 1rem;
  line-height: 1.3;
}

.card-text {
  color: #6b7280;
  line-height: 1.6;
  margin-bottom: 1.5rem;
}

.card-footer {
  padding: 1rem 1.25rem 1.25rem;
  background: transparent;
}

.read-more-btn {
  text-decoration: none;
  font-weight: 600;
  font-size: 0.95rem;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #4f46e5;
  padding: 8px 12px;
  border-radius: 8px;
  position: relative;
  isolation: isolate;
  overflow: hidden;
  transition: color 0.3s ease;
}

.read-more-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  background-color: currentColor;
  opacity: 0.1;
  z-index: -1;
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.3s ease;
}

.read-more-btn:hover {
  color: #4338ca;
}

.read-more-btn:hover::before {
  transform: scaleX(1);
}

.read-more-btn .icon {
  transition: transform 0.2s ease;
}

.read-more-btn:hover .icon {
  transform: translateX(4px);
}

.blog-date {
  color: #6b7280;
  font-size: 0.85rem;
  font-weight: 500;
}

/* Create card variants with different accent colors */
.blog-card:nth-child(3n+1) .card-accent {
  background: linear-gradient(90deg, #4f46e5 0%, #7c3aed 100%);
}

.blog-card:nth-child(3n+2) .card-accent {
  background: linear-gradient(90deg, #0ea5e9 0%, #6366f1 100%);
}

.blog-card:nth-child(3n+3) .card-accent {
  background: linear-gradient(90deg, #8b5cf6 0%, #ec4899 100%);
}
</style>
