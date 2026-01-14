<script setup>
// Java Profiling Literature blog post page
</script>

<template>
  <div class="container-wide py-5">
    <div class="row">
      <div class="col-12">
        <div class="d-flex align-items-center justify-content-between mb-4">
          <div class="d-flex align-items-center">
            <div class="page-header-icon me-3 bg-literature-gradient">
              <i class="bi bi-book"></i>
            </div>
            <div>
              <h1 class="page-title mb-0">Mandatory Literature</h1>
              <p class="text-muted mb-0">Published on June 15, 2025</p>
            </div>
          </div>
          <router-link to="/blog" class="btn btn-outline-secondary">
            <i class="bi bi-arrow-left me-2"></i>Back to Blog
          </router-link>
        </div>
        
        <div class="page-content bg-white rounded-3 shadow-sm p-5">
          <div class="blog-content">
            <div class="lead mb-4">
              All you need to efficiently profile and analyze your profiles
            </div>

            <ul class="resource-list">
              <li class="resource-item">
                <strong>ASPROF Official</strong> - <a href="https://github.com/async-profiler/async-profiler" target="_blank">https://github.com/async-profiler/async-profiler</a>
              </li>
              <li class="resource-item">
                <strong>ASPROF Manual</strong> - <a href="https://krzysztofslusarski.github.io/2022/12/12/async-manual.html" target="_blank">https://krzysztofslusarski.github.io/2022/12/12/async-manual.html</a>
              </li>
              <li class="resource-item">
                <strong>PERF vs. JFR</strong> - <a href="https://bell-sw.com/announcements/2022/04/07/how-to-use-perf-to-monitor-java-performance" target="_blank">https://bell-sw.com/announcements/2022/04/07/how-to-use-perf-to-monitor-java-performance</a>
              </li>
              <li class="resource-item">
                <strong>Counting CPU samples</strong> - async-profiler/async-profiler#466
              </li>
              <li class="resource-item">
                <strong>CPU vs. itimer samples</strong> - async-profiler/async-profiler#272
              </li>
              <li class="resource-item">
                <strong>Info about ctimer in golang</strong> - <a href="https://felixge.de/2022/02/11/profiling-improvements-in-go-1.18/" target="_blank">https://felixge.de/2022/02/11/profiling-improvements-in-go-1.18/</a>
              </li>
              <li class="resource-item">
                <strong>DebugNonSafepoints</strong> - <a href="https://jpbempel.github.io/2022/06/22/debug-non-safepoints.html" target="_blank">https://jpbempel.github.io/2022/06/22/debug-non-safepoints.html</a>
                <ul class="sub-note">
                  <li><code>DebugNonSafepoints</code> may affect accuracy of the topmost virtual frames, but not a frame at the bottom of the stack</li>
                </ul>
              </li>
              <li class="resource-item">
                <strong>Context-switches in ASPROF</strong> - async-profiler/async-profiler#639
              </li>
              <li class="resource-item">
                <strong>Timing Differences between LOGS and JFR</strong> - <a href="https://mail.openjdk.org/pipermail/hotspot-gc-dev/2020-August/030581.html" target="_blank">https://mail.openjdk.org/pipermail/hotspot-gc-dev/2020-August/030581.html</a>
              </li>
              <li class="resource-item">
                <strong>Frame pointers vs. DWARF</strong> - <a href="https://rwmj.wordpress.com/2023/02/14/frame-pointers-vs-dwarf-my-verdict" target="_blank">https://rwmj.wordpress.com/2023/02/14/frame-pointers-vs-dwarf-my-verdict</a>
              </li>
              <li class="resource-item">
                <strong>DWARF in eBPF</strong> - <a href="https://www.polarsignals.com/blog/posts/2022/11/29/dwarf-based-stack-walking-us" target="_blank">https://www.polarsignals.com/blog/posts/2022/11/29/dwarf-based-stack-walking-us</a>
              </li>
            </ul>

            <h2>Tuning</h2>

            <ul class="resource-list">
              <li class="resource-item">
                <a href="https://inside.java/" target="_blank">https://inside.java/</a>
              </li>
            </ul>

            <h3>TimerSlack</h3>
            <ul class="resource-list">
              <li class="resource-item">
                <a href="https://bugs.openjdk.org/browse/JDK-8307766" target="_blank">https://bugs.openjdk.org/browse/JDK-8307766</a>
              </li>
              <li class="resource-item">
                <a href="https://hazelcast.com/blog" target="_blank">https://hazelcast.com/blog</a>
              </li>
            </ul>
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
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
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

.resource-list {
  list-style: none;
  padding-left: 0;
}

.resource-item {
  padding: 1rem 0;
  border-bottom: 1px solid #e9ecef;
  line-height: 1.6;
}

.resource-item:last-child {
  border-bottom: none;
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

.sub-note {
  margin-top: 0.5rem;
  padding-left: 1rem;
  list-style: disc;
}

.sub-note li {
  color: #6c757d;
  font-style: italic;
}

.sub-note code {
  background-color: #f8f9fa;
  padding: 0.2rem 0.4rem;
  border-radius: 3px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.9em;
}

.lead {
  font-size: 1.2rem;
  color: #495057;
  font-weight: 400;
}

.bg-literature-gradient {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}
</style>