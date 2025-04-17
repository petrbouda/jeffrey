<template>
  <div class="card w-100">
    <div class="card-header bg-soft-blue text-white">
      <div class="d-flex align-items-center">
        <i class="bi bi-gear fs-5 me-2"></i>
        <h5 class="card-title mb-0">Settings</h5>
      </div>
    </div>
    
    <div class="card-body">
      <div class="alert alert-info">
        <i class="bi bi-info-circle-fill me-2"></i>
        This is a placeholder for the Settings page. In a real implementation, this would display project settings.
      </div>
      
      <form>
        <div class="row mb-4">
          <div class="col-md-6">
            <h6 class="mb-3">Project Settings</h6>
            
            <div class="mb-3">
              <label for="projectName" class="form-label">Project Name</label>
              <input type="text" class="form-control" id="projectName" value="My Project">
            </div>
            
            <div class="mb-3">
              <label for="description" class="form-label">Description</label>
              <textarea class="form-control" id="description" rows="3"></textarea>
            </div>
            
            <div class="mb-3">
              <label for="storageLimit" class="form-label">Storage Limit (MB)</label>
              <input type="number" class="form-control" id="storageLimit" value="1000">
            </div>
          </div>
          
          <div class="col-md-6">
            <h6 class="mb-3">Analysis Settings</h6>
            
            <div class="mb-3">
              <label class="form-label">Default Flamegraph View</label>
              <div class="form-check">
                <input class="form-check-input" type="radio" name="flamegraphView" id="viewCpu" checked>
                <label class="form-check-label" for="viewCpu">
                  CPU Time
                </label>
              </div>
              <div class="form-check">
                <input class="form-check-input" type="radio" name="flamegraphView" id="viewWall">
                <label class="form-check-label" for="viewWall">
                  Wall Time
                </label>
              </div>
            </div>
            
            <div class="mb-3">
              <label for="stackDepth" class="form-label">Maximum Stack Depth</label>
              <select class="form-select" id="stackDepth">
                <option value="25">25</option>
                <option value="50" selected>50</option>
                <option value="100">100</option>
                <option value="0">Unlimited</option>
              </select>
            </div>
            
            <div class="form-check form-switch mb-3">
              <input class="form-check-input" type="checkbox" id="autoAnalysis" checked>
              <label class="form-check-label" for="autoAnalysis">Enable Auto Analysis</label>
            </div>
          </div>
        </div>
        
        <div class="row">
          <div class="col-12">
            <h6 class="mb-3">Danger Zone</h6>
            
            <div class="card border-danger">
              <div class="card-body">
                <h6 class="card-title text-danger">Delete Project</h6>
                <p class="card-text">Once you delete a project, there is no going back. This action cannot be undone.</p>
                <button type="button" class="btn btn-danger">
                  <i class="bi bi-trash me-2"></i>Delete Project
                </button>
              </div>
            </div>
          </div>
        </div>
        
        <div class="d-flex justify-content-end mt-4">
          <button type="button" class="btn btn-secondary me-2">Cancel</button>
          <button type="button" class="btn btn-primary">Save Changes</button>
        </div>
      </form>
    </div>
  </div>
</template>