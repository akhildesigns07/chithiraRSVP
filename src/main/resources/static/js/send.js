// Send screen: after a Send button opens WhatsApp (in a new tab), mark that row as sent
// and move the highlight and focus to the next guest who has not been sent yet.
(function () {
    var rows = Array.prototype.slice.call(document.querySelectorAll('.send-list > li'));
    var counter = document.getElementById('sent-count');
    var total = Number(document.getElementById('total-count').textContent);
    var bar = document.getElementById('sent-bar');

    function focusRow(row) {
        rows.forEach(function (r) { r.classList.remove('current'); });
        if (!row) {
            document.getElementById('all-done').hidden = false;
            return;
        }
        row.classList.add('current');
        row.scrollIntoView({ behavior: 'smooth', block: 'center' });
        row.querySelector('button').focus({ preventScroll: true });
    }

    rows.forEach(function (row, index) {
        row.querySelector('form').addEventListener('submit', function () {
            if (row.dataset.sent !== 'true') {
                row.dataset.sent = 'true';
                var sent = Number(counter.textContent) + 1;
                counter.textContent = String(sent);
                bar.style.width = (total ? Math.round(sent * 100 / total) : 0) + '%';
            }
            var status = row.querySelector('.status');
            status.textContent = 'Sent';
            status.classList.add('success');
            row.querySelector('.label').textContent = 'Resend';
            var next = rows.slice(index + 1).concat(rows.slice(0, index))
                .find(function (r) { return r.dataset.sent !== 'true'; });
            focusRow(next);
        });
    });

    var current = document.querySelector('.send-list > li.current');
    if (current) {
        current.querySelector('button').focus({ preventScroll: true });
        current.scrollIntoView({ block: 'center' });
    }
})();
