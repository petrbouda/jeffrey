<template>
  <div class="card w-100">
    <div class="card-header bg-soft-blue text-white">
      <h5 class="card-title mb-0">Repository</h5>
    </div>
    
    <div class="card-body">
      <div class="alert alert-info">
        <i class="bi bi-info-circle-fill me-2"></i>
        This is a placeholder for the Repository page. In a real implementation, this would display repository information.
      </div>
      
      <div class="row mb-4">
        <div class="col-md-6">
          <div class="card">
            <div class="card-header">
              <h6 class="mb-0">Storage Statistics</h6>
            </div>
            <div class="card-body">
              <div class="d-flex justify-content-between mb-3">
                <span>Total Size:</span>
                <span class="fw-bold">256.5 MB</span>
              </div>
              <div class="d-flex justify-content-between mb-3">
                <span>Profiles:</span>
                <span class="fw-bold">15</span>
              </div>
              <div class="d-flex justify-content-between mb-3">
                <span>Recordings:</span>
                <span class="fw-bold">23</span>
              </div>
              <div class="progress">
                <div class="progress-bar bg-success" role="progressbar" style="width: 35%" aria-valuenow="35" aria-valuemin="0" aria-valuemax="100"></div>
              </div>
              <div class="text-muted small mt-2">
                35% of allocated storage used
              </div>
            </div>
          </div>
        </div>
        <div class="col-md-6">
          <div class="card">
            <div class="card-header">
              <h6 class="mb-0">Recent Activity</h6>
            </div>
            <div class="card-body">
              <ul class="list-group list-group-flush">
                <li class="list-group-item d-flex justify-content-between align-items-center">
                  <div>
                    <i class="bi bi-file-earmark-plus text-success me-2"></i>
                    <span>New recording added</span>
                  </div>
                  <span class="text-muted small">5 mins ago</span>
                </li>
                <li class="list-group-item d-flex justify-content-between align-items-center">
                  <div>
                    <i class="bi bi-person-plus text-primary me-2"></i>
                    <span>Profile created</span>
                  </div>
                  <span class="text-muted small">2 hours ago</span>
                </li>
                <li class="list-group-item d-flex justify-content-between align-items-center">
                  <div>
                    <i class="bi bi-trash text-danger me-2"></i>
                    <span>Recording deleted</span>
                  </div>
                  <span class="text-muted small">1 day ago</span>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>