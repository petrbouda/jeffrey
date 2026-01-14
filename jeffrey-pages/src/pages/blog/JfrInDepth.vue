<script setup>
// JFR in Depth blog post page
</script>

<template>
  <div class="container-wide py-5">
    <div class="row">
      <div class="col-12">
        <div class="d-flex align-items-center justify-content-between mb-4">
          <div class="d-flex align-items-center">
            <div class="page-header-icon me-3 bg-jfr-gradient">
              <i class="bi bi-speedometer2"></i>
            </div>
            <div>
              <h1 class="page-title mb-0">JFR in Depth (Not only Native Image)</h1>
              <p class="text-muted mb-0">Published on June 14, 2025</p>
            </div>
          </div>
          <router-link to="/blog" class="btn btn-outline-secondary">
            <i class="bi bi-arrow-left me-2"></i>Back to Blog
          </router-link>
        </div>
        
        <div class="page-content bg-white rounded-3 shadow-sm p-5">
          <div class="blog-content">
            <div class="lead mb-4">
              Robert Toyonaga - An In-Depth Look at JFR in GraalVM Native Image (FOSFDAM Conference)
            </div>


            <div class="resource-section">
              <h2>Presentation Materials</h2>
              <div class="resource-item">
                <i class="bi bi-file-earmark-ppt me-2"></i>
                <strong>PowerPoint Presentation:</strong> 
                <a href="/assets/FOSDEM_Jan_31_A10m5Q9.pptx" target="_blank" class="ms-2">
                  Robert Toyonaga - An In-Depth Look at JFR in GraalVM Native Image
                </a>
              </div>
            </div>

            <div class="content-section mt-4">
              <h2>About this Presentation</h2>
              <p>
                This presentation by Robert Toyonaga provides a comprehensive deep dive into Java Flight Recorder (JFR) 
                functionality within GraalVM Native Image environments. Originally presented at the FOSDEM conference, 
                this talk explores the intricacies of profiling native images and the unique challenges and opportunities 
                that JFR presents in this context.
              </p>

              <h3>Key Topics Covered</h3>
              <ul class="topic-list">
                <li>JFR implementation details in GraalVM Native Image</li>
                <li>Performance profiling strategies for native applications</li>
                <li>Differences between JFR in traditional JVM vs. Native Image</li>
                <li>Practical examples and use cases</li>
                <li>Best practices for native image profiling</li>
              </ul>

              <h3>Why This Matters</h3>
              <p>
                As GraalVM Native Image adoption continues to grow, especially in cloud-native and microservices 
                environments, understanding how to effectively profile and monitor these applications becomes crucial. 
                JFR provides low-overhead profiling capabilities that are essential for production environments, 
                and this presentation offers insights into leveraging these capabilities in native image contexts.
              </p>

              <div class="conference-info mt-4 p-3 bg-light rounded">
                <h4>About FOSDEM</h4>
                <p class="mb-0">
                  FOSDEM is one of the largest gatherings of free and open-source software developers, 
                  bringing together thousands of developers from around the world. This presentation was 
                  part of the Java DevRoom track, focusing on cutting-edge developments in the Java ecosystem.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-header-icon {
  font-size: 1.5rem;
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  color: white;
  flex-shrink: 0;
}

.page-title {
  font-weight: 700;
  font-size: 2rem;
  color: #343a40;
  line-height: 1.2;
}

.page-content {
  min-height: 60vh;
}

.blog-content {
  line-height: 1.7;
  font-size: 1.1rem;
}

.blog-content h2 {
  color: #2c3e50;
  font-weight: 700;
  margin-top: 3rem;
  margin-bottom: 1.5rem;
  padding-bottom: 0.5rem;
  border-bottom: 3px solid #3498db;
}

.blog-content h3 {
  color: #34495e;
  font-weight: 600;
  margin-top: 2rem;
  margin-bottom: 1rem;
}

.blog-content h4 {
  color: #34495e;
  font-weight: 600;
  margin-top: 1.5rem;
  margin-bottom: 1rem;
}

.featured-image img {
  max-width: 100%;
  height: auto;
  box-shadow: 0 8px 25px rgba(0,0,0,0.15);
}

.resource-section {
  background-color: #f8f9fa;
  padding: 2rem;
  border-radius: 8px;
  border-left: 4px solid #28a745;
  margin: 2rem 0;
}

.resource-section h2 {
  margin-top: 0;
  color: #155724;
  border-bottom: 3px solid #28a745;
}

.resource-item {
  display: flex;
  align-items: center;
  padding: 1rem 0;
  line-height: 1.6;
}

.resource-item i {
  color: #28a745;
  font-size: 1.2rem;
}

.resource-item strong {
  color: #2c3e50;
  font-weight: 600;
}

.resource-item a {
  color: #3498db;
  text-decoration: none;
}

.resource-item a:hover {
  color: #2980b9;
  text-decoration: underline;
}

.topic-list {
  list-style: none;
  padding-left: 0;
}

.topic-list li {
  padding: 0.5rem 0;
  padding-left: 2rem;
  position: relative;
}

.topic-list li::before {
  content: "▶";
  color: #3498db;
  font-weight: bold;
  position: absolute;
  left: 0;
}

.conference-info {
  border-left: 4px solid #6c757d;
}

.conference-info h4 {
  color: #495057;
  margin-top: 0;
}

.lead {
  font-size: 1.2rem;
  color: #495057;
  font-weight: 400;
  font-style: italic;
}

.bg-jfr-gradient {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
</style>
