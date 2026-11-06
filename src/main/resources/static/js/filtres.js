(() => {
    'use strict';
    // Rend visible un champ invalide placé dans un panneau replié avant le message du navigateur.
    document.addEventListener('invalid', event => {
        const details = event.target.closest('details');
        if (details) details.open = true;
    }, true);
})();
