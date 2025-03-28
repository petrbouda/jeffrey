<template>
  <div class="card w-100">
    <div class="card-header bg-soft-blue text-white">
      <h5 class="card-title mb-0">Jobs</h5>
    </div>
    
    <div class="card-body">
      <div class="alert alert-info">
        <i class="bi bi-info-circle-fill me-2"></i>
        This is a placeholder for the Jobs page. In a real implementation, this would display background job information.
      </div>
      
      <div class="table-responsive">
        <table class="table table-hover">
          <thead>
            <tr>
              <th>Job ID</th>
              <th>Type</th>
              <th>Status</th>
              <th>Progress</th>
              <th>Started</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td class="fw-bold">#JOB-1234</td>
              <td>Flamegraph Generation</td>
              <td><span class="badge bg-success">Completed</span></td>
              <td>
                <div class="progress" style="height: 8px;">
                  <div class="progress-bar bg-success" role="progressbar" style="width: 100%" aria-valuenow="100" aria-valuemin="0" aria-valuemax="100"></div>
                </div>
              </td>
              <td>Mar 26, 2025 10:15</td>
              <td>
                <button class="btn btn-sm btn-outline-danger">
                  <i class="bi bi-trash"></i>
                </button>
              </td>
            </tr>
            <tr>
              <td class="fw-bold">#JOB-1233</td>
              <td>Profile Analysis</td>
              <td><span class="badge bg-warning text-dark">Running</span></td>
              <td>
                <div class="progress" style="height: 8px;">
                  <div class="progress-bar bg-warning" role="progressbar" style="width: 65%" aria-valuenow="65" aria-valuemin="0" aria-valuemax="100"></div>
                </div>
              </td>
              <td>Mar 26, 2025 09:45</td>
              <td>
                <button class="btn btn-sm btn-outline-secondary">
                  <i class="bi bi-x-lg"></i>
                </button>
              </td>
            </tr>
            <tr>
              <td class="fw-bold">#JOB-1232</td>
              <td>Data Export</td>
              <td><span class="badge bg-danger">Failed</span></td>
              <td>
                <div class="progress" style="height: 8px;">
                  <div class="progress-bar bg-danger" role="progressbar" style="width: 25%" aria-valuenow="25" aria-valuemin="0" aria-valuemax="100"></div>
                </div>
              </td>
              <td>Mar 25, 2025 16:30</td>
              <td>
                <div class="d-flex gap-2">
                  <button class="btn btn-sm btn-outline-primary">
                    <i class="bi bi-arrow-clockwise"></i>
                  </button>
                  <button class="btn btn-sm btn-outline-danger">
                    <i class="bi bi-trash"></i>
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>