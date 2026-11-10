(() => {
    'use strict';
    const declencheurs = new WeakMap();
    document.querySelectorAll('[data-dialog]').forEach(button => {
        button.addEventListener('click', () => {
            const dialog = document.getElementById(button.dataset.dialog);
            if (!(dialog instanceof HTMLDialogElement) || dialog.open) return;
            declencheurs.set(dialog, button);
            dialog.showModal();
        });
    });
    document.querySelectorAll('.classeur-dialog').forEach(dialog => {
        dialog.querySelector('[data-fermer-classeur]')?.addEventListener('click', () => dialog.close());
        dialog.addEventListener('click', event => {
            if (event.target !== dialog) return;
            const r = dialog.getBoundingClientRect();
            if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) dialog.close();
        });
        dialog.addEventListener('close', () => declencheurs.get(dialog)?.focus({preventScroll:true}));
    });
})();
