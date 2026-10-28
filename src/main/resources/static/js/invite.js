// Background particles for the invitation card: falling petals (marigold) or twinkling stars (moonlight).
(function () {
    var body = document.body;
    var sky = document.querySelector('.sky');
    if (!sky || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        body.classList.add('settled');
        return;
    }

    function rand(min, max) {
        return min + Math.random() * (max - min);
    }

    if (body.classList.contains('tpl-marigold')) {
        for (var i = 0; i < 18; i++) {
            var petal = document.createElement('span');
            petal.className = i % 3 === 0 ? 'petal alt' : 'petal';
            petal.style.left = rand(0, 100) + 'vw';
            petal.style.animationDuration = rand(7, 14) + 's';
            petal.style.animationDelay = rand(-14, 0) + 's';
            sky.appendChild(petal);
        }
    } else if (body.classList.contains('tpl-moonlight')) {
        for (var j = 0; j < 45; j++) {
            var star = document.createElement('span');
            star.className = 'star';
            star.style.left = rand(0, 100) + 'vw';
            star.style.top = rand(0, 100) + 'vh';
            star.style.animationDuration = rand(2, 5) + 's';
            star.style.animationDelay = rand(-5, 0) + 's';
            if (Math.random() < 0.2) {
                star.style.width = star.style.height = '3px';
            }
            sky.appendChild(star);
        }
    }

    // Once the entrance sequence has played, stop it replaying on content swapped in by htmx.
    setTimeout(function () { body.classList.add('settled'); }, 2500);
})();
