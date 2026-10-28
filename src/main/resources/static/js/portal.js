// Card preview tabs load into the preview iframe (plain links with target); keep the
// selected state and the "Open full screen" link in step with the chosen design.
document.addEventListener('click', function (e) {
    var tab = e.target.closest('.preview-tab');
    if (!tab) return;
    document.querySelectorAll('.preview-tab').forEach(function (t) {
        t.setAttribute('aria-selected', String(t === tab));
    });
    var open = document.getElementById('preview-open');
    if (open) open.href = tab.href;
});

// Copy buttons: <button data-copy="#input-id">. Works for content swapped in by htmx too.
document.addEventListener('click', function (e) {
    var btn = e.target.closest('[data-copy]');
    if (!btn) return;
    var input = document.querySelector(btn.dataset.copy);
    if (!input) return;
    input.select();
    var done = function () {
        var label = btn.textContent;
        btn.textContent = 'Copied';
        setTimeout(function () { btn.textContent = label; }, 1500);
    };
    if (navigator.clipboard) {
        navigator.clipboard.writeText(input.value).then(done, function () { document.execCommand('copy'); done(); });
    } else {
        document.execCommand('copy');
        done();
    }
});
