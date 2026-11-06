(() => {
    'use strict';
    if (document.documentElement.dataset.galerieCartesReady) return;
    document.documentElement.dataset.galerieCartesReady = 'true';
    const dialogs = [...document.querySelectorAll('dialog.carte-dialog')];
    const zoom = document.getElementById('carte-zoom');
    const openers = new WeakMap();
    const opened = [];
    const syncScroll = () => document.documentElement.classList.toggle('cartes-dialog-ouvertes', dialogs.some(dialog => dialog.open));
    const open = (dialog, trigger) => {
        if (!(dialog instanceof HTMLDialogElement) || dialog.open || typeof dialog.showModal !== 'function') return;
        openers.set(dialog, trigger);
        dialog.showModal();
        opened.push(dialog);
        syncScroll();
    };
    document.addEventListener('click', event => {
        const target = event.target instanceof Element ? event.target : null;
        if (!target) return;
        const close = target.closest('[data-fermer-dialog]');
        if (close?.closest('dialog.carte-dialog')) { close.closest('dialog').close(); return; }
        const image = target.closest('[data-carte-zoom]');
        if (image && zoom) {
            const title = image.dataset.carteTitre || 'Carte en grand';
            zoom.querySelector('[data-carte-zoom-titre]').textContent = title;
            zoom.querySelector('[data-carte-zoom-reference]').textContent = image.dataset.carteReference || '';
            const photo = zoom.querySelector('[data-carte-zoom-image]');
            photo.alt = title;
            photo.src = image.dataset.carteZoom;
            open(zoom, image);
            return;
        }
        const trigger = target.closest('[data-dialog-id]');
        if (trigger) { open(document.getElementById(trigger.dataset.dialogId), trigger); return; }
        // Les contrôles restent indépendants du clic sur le reste de la tuile.
        if (target.closest('button, a, form, input, select, textarea, label, details, dialog')) return;
        const tile = target.closest('[data-catalogue-dialog]');
        if (tile) open(document.getElementById(tile.dataset.catalogueDialog), tile.querySelector('[data-dialog-id]'));
    });
    document.addEventListener('keydown', event => {
        if (event.key !== 'Tab' || event.ctrlKey || event.altKey || event.metaKey) return;
        const dialog = opened.filter(candidate => candidate.open).pop();
        if (!dialog) return;
        const focusable = [...dialog.querySelectorAll('button:not(:disabled), a[href], input:not(:disabled):not([type="hidden"]), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])')]
            .filter(element => element.getClientRects().length && getComputedStyle(element).visibility !== 'hidden');
        const first = focusable[0], last = focusable[focusable.length - 1];
        if (!first) return;
        if (!dialog.contains(document.activeElement) || (event.shiftKey ? document.activeElement === first : document.activeElement === last)) {
            event.preventDefault();
            (event.shiftKey ? last : first).focus();
        }
    });
    dialogs.forEach(dialog => {
        let startedOutside = false;
        const outside = event => {
            const rect = dialog.getBoundingClientRect();
            return event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom;
        };
        dialog.addEventListener('pointerdown', event => { startedOutside = event.target === dialog && outside(event); });
        dialog.addEventListener('click', event => {
            if (startedOutside && event.target === dialog && outside(event)) dialog.close();
            startedOutside = false;
        });
        dialog.addEventListener('close', () => {
            const index = opened.indexOf(dialog);
            if (index >= 0) opened.splice(index, 1);
            syncScroll();
            const opener = openers.get(dialog);
            if (opener?.isConnected) opener.focus({ preventScroll: true });
        });
    });
})();
