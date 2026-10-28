// If the RSVP request fails (e.g. 429 from the rate limit), say so instead of doing nothing.
// Lives in a file rather than hx-on so the page needs no inline or eval'd script (CSP).
document.addEventListener('htmx:responseError', function (e) {
    var form = e.target.closest && e.target.closest('.rsvp-form');
    var note = form && form.querySelector('.rsvp-busy');
    if (note) note.hidden = false;
});
