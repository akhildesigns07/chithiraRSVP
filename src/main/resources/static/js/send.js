// Send screen. The DOM is the state: each row carries data-sent / data-rsvp, and refresh()
// derives counts, filter visibility, the next guest and the dock from it.
(function () {
    var form = document.getElementById('send-form');
    var list = document.getElementById('send-list');
    if (!form || !list) return;

    var rows = Array.prototype.slice.call(list.querySelectorAll('li[data-id]'));
    var byId = {};
    rows.forEach(function (r) { byId[r.dataset.id] = r; });

    var el = function (id) { return document.getElementById(id); };
    var composer = el('composer');
    var problem = el('composer-problem');
    var preview = el('message-preview');
    var toast = el('dock-toast');
    var toastTimer = null;
    var lastSentId = null;
    var nextRow = null;

    // ---------- Composer ----------
    function language() {
        var checked = form.querySelector('input[name="language"]:checked');
        return checked ? checked.value : 'EN';
    }

    function activeTextarea() {
        return el(language() === 'ML' ? 'template-ml' : 'template-en');
    }

    function templateProblem(text) {
        if (!text.trim()) return 'The message is empty.';
        if (text.indexOf('{link}') < 0) return 'The message must include {link} so the guest gets their invitation link.';
        return null;
    }

    function updatePreview() {
        var name = nextRow ? nextRow.dataset.name : 'Guest';
        var text = activeTextarea().value
            .split('{name}').join(name)
            .split('{link}').join(form.dataset.sampleLink);
        preview.textContent = text;
        var p = templateProblem(activeTextarea().value);
        problem.hidden = !p;
        problem.textContent = p || '';
        form.querySelector('.composer-lang-label').textContent = language() === 'ML' ? 'Malayalam' : 'English';
    }

    form.addEventListener('input', updatePreview);
    form.addEventListener('change', updatePreview);

    form.querySelectorAll('[data-insert]').forEach(function (chip) {
        chip.addEventListener('click', function () {
            var ta = activeTextarea();
            var start = ta.selectionStart, end = ta.selectionEnd;
            ta.setRangeText(chip.dataset.insert, start, end, 'end');
            ta.focus();
            updatePreview();
        });
    });

    // ---------- Sending ----------
    // Runs synchronously before the new tab opens, so a bad message can still stop the send.
    form.addEventListener('submit', function (e) {
        var button = e.submitter;
        if (button && button.dataset.confirm && !confirm(button.dataset.confirm)) {
            e.preventDefault();
            return;
        }
        if (!button || !button.dataset.id) return; // e.g. "Reset to original"
        var p = templateProblem(activeTextarea().value);
        if (p) {
            e.preventDefault();
            composer.open = true;
            updatePreview();
            activeTextarea().focus();
            return;
        }
        var row = byId[button.dataset.id];
        // Let the submission start first; then reflect it on the page.
        setTimeout(function () { markSent(row); }, 0);
    });

    function markSent(row) {
        row.dataset.sent = 'true';
        lastSentId = row.dataset.id;
        showToast(row.dataset.name);
        refresh(row);
    }

    // ---------- Undo ----------
    function undo(row) {
        fetch(row.dataset.undoUrl, { method: 'POST', credentials: 'same-origin' })
            .then(function (res) {
                if (!res.ok) throw new Error(res.status);
                row.dataset.sent = 'false';
                if (lastSentId === row.dataset.id) hideToast();
                refresh(null, row);
            })
            .catch(function () {
                alert('Could not undo. Please check your connection and try again.');
            });
    }

    list.addEventListener('click', function (e) {
        var b = e.target.closest('.act-undo');
        if (b) undo(b.closest('li'));
    });

    el('toast-undo').addEventListener('click', function () {
        if (lastSentId) undo(byId[lastSentId]);
    });

    function showToast(name) {
        el('toast-name').textContent = name;
        toast.hidden = false;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(hideToast, 8000);
    }

    function hideToast() {
        toast.hidden = true;
        clearTimeout(toastTimer);
    }

    // ---------- Filters ----------
    document.querySelectorAll('.filter').forEach(function (f) {
        f.addEventListener('click', function () {
            list.dataset.filter = f.dataset.filter;
            document.querySelectorAll('.filter').forEach(function (o) {
                o.setAttribute('aria-pressed', String(o === f));
            });
            refresh();
        });
    });

    function visible(row) {
        switch (list.dataset.filter) {
            case 'unsent': return row.dataset.sent !== 'true';
            case 'sent': return row.dataset.sent === 'true';
            case 'rsvp': return row.dataset.rsvp !== 'PENDING';
            default: return true;
        }
    }

    // ---------- Derived state ----------
    // after: the row just sent (next is searched after it); prefer: a row that should become next.
    function refresh(after, prefer) {
        var sent = 0, rsvp = 0, shown = 0;
        rows.forEach(function (r) {
            if (r.dataset.sent === 'true') sent++;
            if (r.dataset.rsvp !== 'PENDING') rsvp++;
            var show = visible(r);
            r.hidden = !show;
            if (show) shown++;
        });
        var total = rows.length;
        el('sent-count').textContent = sent;
        el('sent-bar').style.width = (total ? Math.round(sent * 100 / total) : 0) + '%';
        setCount('all', total);
        setCount('unsent', total - sent);
        setCount('sent', sent);
        setCount('rsvp', rsvp);
        el('filter-empty').hidden = shown > 0;

        if (prefer) {
            nextRow = prefer;
        } else if (!nextRow || nextRow.dataset.sent === 'true') {
            var start = after ? rows.indexOf(after) + 1 : 0;
            var ordered = rows.slice(start).concat(rows.slice(0, start));
            nextRow = ordered.find(function (r) { return r.dataset.sent !== 'true'; }) || null;
        }
        rows.forEach(function (r) { r.classList.toggle('current', r === nextRow); });

        var dockSend = el('dock-send');
        el('dock-next').hidden = !nextRow;
        dockSend.hidden = !nextRow;
        el('dock-done').hidden = !!nextRow;
        if (nextRow) {
            el('dock-name').textContent = nextRow.dataset.name;
            dockSend.dataset.id = nextRow.dataset.id;
            dockSend.setAttribute('formaction', nextRow.querySelector('button[data-id]').getAttribute('formaction'));
            dockSend.setAttribute('aria-label', 'Send to ' + nextRow.dataset.name);
        }
        updatePreview();
    }

    function setCount(name, n) {
        var c = document.querySelector('[data-count="' + name + '"]');
        if (c) c.textContent = n;
    }

    refresh();
})();
