# Update Jeffrey Pages

Build and deploy the jeffrey-pages documentation site to petrbouda.github.io.

## Workflow

Execute these steps in order:

### 1. Build the Frontend

```bash
cd /Users/petrbouda/IdeaProjects/jeffrey-pages && npm run build
```

Verify the build completed successfully by checking that `dist/` contains:
- `index.html`
- `assets/` directory
- `images/` directory

### 2. Clean Destination

Remove old files from the GitHub Pages repository:

```bash
rm -f /Users/petrbouda/IdeaProjects/petrbouda.github.io/index.html
rm -rf /Users/petrbouda/IdeaProjects/petrbouda.github.io/assets
rm -rf /Users/petrbouda/IdeaProjects/petrbouda.github.io/images
```

### 3. Copy New Files

Copy the built files to the destination:

```bash
cp /Users/petrbouda/IdeaProjects/jeffrey-pages/dist/index.html /Users/petrbouda/IdeaProjects/petrbouda.github.io/
cp -r /Users/petrbouda/IdeaProjects/jeffrey-pages/dist/assets /Users/petrbouda/IdeaProjects/petrbouda.github.io/
cp -r /Users/petrbouda/IdeaProjects/jeffrey-pages/dist/images /Users/petrbouda/IdeaProjects/petrbouda.github.io/
```

### 4. Git Commit and Push

Commit and push the changes:

```bash
cd /Users/petrbouda/IdeaProjects/petrbouda.github.io && git add -A && git commit -m "Update Jeffrey documentation site" && git push
```

## Verification

After push completes, the site will be live at the GitHub Pages URL within a few minutes.
