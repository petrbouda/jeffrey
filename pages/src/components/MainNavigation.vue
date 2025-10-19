<template>
  <div class="navigation-tabs">
    <div class="container-fluid">
      <div class="nav-container">
        <router-link to="/projects" class="nav-pill" active-class="active">
          <i class="bi bi-folder"></i>
          <span>Projects</span>
        </router-link>
        <router-link to="/global-scheduler" class="nav-pill" active-class="active">
          <i class="bi bi-calendar-check"></i>
          <span>Global Scheduler</span>
        </router-link>
        <router-link to="/event-log" class="nav-pill" active-class="active">
          <i class="bi bi-list-ul"></i>
          <span>Event Log</span>
        </router-link>
        <router-link to="/profiler-settings" class="nav-pill" active-class="active">
          <i class="bi bi-gear"></i>
          <span>Profiler Settings</span>
        </router-link>
      </div>
    </div>
  </div>
  <div class="content-spacer"></div>
</template>

<style scoped>
.navigation-tabs {
  background-color: white;
  padding: 0;
  position: relative;
  box-shadow: 0 4px 12px -2px rgba(0, 0, 0, 0.05);
  z-index: 10;
}

.content-spacer {
  height: 24px;
}

.nav-container {
  display: flex;
  align-items: center;
  padding: 0 1rem;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  
  &::-webkit-scrollbar {
    display: none;
  }
}

.nav-pill {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0.75rem 1.5rem;
  border: none;
  background: transparent;
  position: relative;
  color: #718096;
  font-size: 0.85rem;
  font-weight: 500;
  min-width: 100px;
  border-radius: 0;
  transition: all 0.25s ease;
  text-decoration: none;
  
  i {
    font-size: 1.25rem;
    margin-bottom: 0.25rem;
    transition: all 0.25s ease;
  }
  
  span {
    opacity: 0.7;
    transition: all 0.25s ease;
  }
  
  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 0;
    width: 100%;
    height: 3px;
    background-color: transparent;
    transition: background-color 0.25s ease;
  }
  
  &:hover {
    color: #4a5568;
    
    i {
      transform: translateY(-2px);
    }
    
    span {
      opacity: 1;
    }
  }
  
  &.active {
    color: #5e64ff;
    
    i {
      transform: translateY(-2px);
    }
    
    span {
      opacity: 1;
    }
    
    &::after {
      background-color: #5e64ff;
    }
  }
}
</style>