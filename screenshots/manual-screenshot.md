# Manual Screenshot Capture

Interactive workflow for capturing screenshots while exploring Jeffrey.

---

## Setup

1. Run Claude with `--chrome` parameter
2. Have Jeffrey running at `http://localhost:8080` or http://localhost:5173/
3. Ask Claude to **"enable screenshot hotkey"** - this injects the capture script

---

## How to Use

Once the hotkey is enabled:

1. **Navigate** to any interesting view in Jeffrey
2. **Press 's'** on your keyboard to capture
3. Screenshot **downloads automatically** with timestamp filename

---

## Important Notes

- Press **'s'** only when NOT focused on an input field or search box
- Screenshots save to your browser's default Downloads folder
- Filename format: `screenshot-2026-01-23T10-30-45.png`
- The script needs to be re-injected after page navigation (ask Claude again)

---

## Workflow

1. **Ask Claude**: "enable screenshot hotkey"
2. **Explore Jeffrey** - click through the UI freely
3. **Press 's'** when you see an interesting view
4. **Check Downloads** - screenshot is saved automatically
5. **Move files** to `screenshots/target/manual/` and rename as needed

---

## Re-enabling After Navigation

After clicking links that change the page, the script is cleared. Just say:

```
enable screenshot hotkey
```

And Claude will re-inject the capture script.

---
Cool
## Tips

- Works best with Chrome's download location set to `screenshots/target/manual/`
- Rename downloaded files with descriptive names after capture
- Console shows `[Screenshot] Saved: filename` on successful capture
