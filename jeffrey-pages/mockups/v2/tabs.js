// Tab switching for the mockups: a [data-tabs] container holds .tab-btn[data-tab] buttons
// and .tab-panel[data-panel] panels with matching names.
document.querySelectorAll('[data-tabs]').forEach(function (root) {
  var buttons = root.querySelectorAll('.tab-btn[data-tab]');
  var panels = root.querySelectorAll('.tab-panel[data-panel]');
  buttons.forEach(function (btn) {
    btn.addEventListener('click', function () {
      buttons.forEach(function (b) { b.classList.toggle('active', b === btn); });
      panels.forEach(function (p) { p.classList.toggle('active', p.dataset.panel === btn.dataset.tab); });
    });
  });
});
