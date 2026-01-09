# Support processing of Heap Dump in Jeffrey using Netbeans OQL analyzer

## Profile Details (ProfileDetails.vue)
- Read the analysis of Netbeans OQL analyzer netbeans-oql-use-cases.md

### Initial Heap Dump Setup Page
- Create a new item in the menu next to the Visualizations tab called "Heap Dump Analysis"
- If you select this tab, it should the first screen related to Heap Dump Analysis
  - this should be the first item in the sidebar menu on the left
  - it should be called "Heap Dump Settings"
  - it should show whether it see the heap dump file or not
    - hardcode the file for this PoC in the application directly (use just no-op input field and design on UI): /Users/petrbouda/heap-dump.hprof.gz
  - if the path is set then enable the button Process Heap Dump
    - when click on this button, it should start processing the heap dump in the background (do we actually need it or Netbeans OQL analyzer can work directly on the heap dump file?)
    - show some progress indicator while processing
    - when finished, show success or failure message
    - store the output first on the local storage to be able to do the analysis
    - use the folder related to $JEFFREY_HOME/profiles/{profileId}/heap-dump-analysis/ (using JeffreyDir)
      - store there any other related files to better analysis

### Heap Dump Analysis Tabs
- Next sidebar menu items should be related to Heap Dump Analysis
- figure out what views to create, there are all possible analysis listed
- Please suggest the best way how to visualize the output
- Create reasonable views, keep them simple and rather do more tabs and dashboards than complex ones
- Create event the tab/dashboard for OQL to run custom queries on the heap dump
  - you can also add the UI elements to generate the OQL queries using Claude assistance (no-op for this PoC, just design the UI)
- If you come up with any other ideas how to visualize the heap dump data, please suggest them and implement them if reasonable

### This is a PoC implementation
- Do the plan and implementation only in /profiles module
- you can create a new module for heap-dump analysis if you find it reasonable
- keep consistency with existing code style and architecture

Use ultrathink!
