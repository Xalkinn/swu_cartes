(() => {
    'use strict';
    // Évite les écouteurs en double si le script est inclus deux fois par erreur.
    if (document.documentElement.dataset.deckDialogsReady) return;
    document.documentElement.dataset.deckDialogsReady = 'true';

    const composition = document.getElementById('composition');
    const deckId = document.querySelector('[data-deck-id]')?.dataset.deckId;
    if (composition && deckId) {
        const key = `swu:composition:${deckId}`;
        try {
            composition.open = sessionStorage.getItem(key) !== 'fermee';
        } catch (_) { /* Stockage facultatif. */
        }
        composition.addEventListener('toggle', () => {
            try {
                sessionStorage.setItem(key, composition.open ? 'ouverte' : 'fermee');
            } catch (_) { /* Mode privé. */
            }
        });
        // Un lien explicite vers la liste doit toujours pouvoir l’ouvrir.
        document.querySelectorAll('a[href="#composition"]').forEach(link => link.addEventListener('click', () => {
            composition.open = true;
        }));
    }

    const dialogs = [...document.querySelectorAll('dialog.deck-dialog')];
    if (!dialogs.length) return;
    const zoom = document.getElementById('deck-dialog-zoom');
    const openers = new WeakMap();
    const syncScroll = () => document.documentElement.classList.toggle(
        'deck-dialog-ouverte', dialogs.some(dialog => dialog.open)
    );
    const open = (dialog, trigger) => {
        if (!dialog || dialog.open || typeof dialog.showModal !== 'function') return;
        openers.set(dialog, trigger);
        dialog.showModal();
        syncScroll();
    };

    document.addEventListener('click', event => {
        const target = event.target instanceof Element ? event.target : null;
        if (!target) return;
        const opener = target.closest('[data-deck-dialog-open]');
        if (opener) {
            open(document.getElementById(opener.dataset.deckDialogOpen), opener);
            return;
        }
        const close = target.closest('[data-deck-dialog-close]');
        if (close) {
            close.closest('dialog.deck-dialog')?.close();
            return;
        }
        const image = target.closest('[data-deck-zoom-src]');
        if (image && zoom) {
            const title = image.dataset.deckZoomTitle || 'Carte du deck';
            const subtitle = image.dataset.deckZoomSubtitle;
            zoom.querySelector('[data-deck-zoom-titre]').textContent = title;
            zoom.querySelector('[data-deck-zoom-reference]').textContent = [subtitle, image.dataset.deckZoomReference].filter(Boolean).join(' · ');
            const photo = zoom.querySelector('[data-deck-zoom-image]');
            photo.alt = title;
            photo.src = image.dataset.deckZoomSrc;
            open(zoom, image);
        }
    });

    // Boucle de tabulation explicite, y compris pour le zoom qui n'a qu'un bouton.
    document.addEventListener('keydown', event => {
        if (event.key !== 'Tab' || event.altKey || event.ctrlKey || event.metaKey) return;
        const activeDialog = dialogs.filter(dialog => dialog.open).pop();
        if (!activeDialog) return;
        const focusable = [...activeDialog.querySelectorAll(
            'button:not(:disabled), a[href], input:not(:disabled):not([type="hidden"]), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])'
        )].filter(element => element.getClientRects().length > 0 && getComputedStyle(element).visibility !== 'hidden');
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (!first) return;
        const current = document.activeElement;
        if (!activeDialog.contains(current) || (event.shiftKey ? current === first : current === last)) {
            event.preventDefault();
            (event.shiftKey ? last : first).focus();
        }
    });

    dialogs.forEach(dialog => {
        // Le clic sur le fond ferme la fenêtre ; les clics dans son contenu restent actifs.
        // Vérifie le début et la fin du geste pour ne pas fermer lors d'une sélection de texte.
        let startedOutside = false;
        const outside = event => {
            const rect = dialog.getBoundingClientRect();
            return event.clientX < rect.left || event.clientX > rect.right
                || event.clientY < rect.top || event.clientY > rect.bottom;
        };
        dialog.addEventListener('pointerdown', event => {
            startedOutside = event.target === dialog && outside(event);
        });
        dialog.addEventListener('click', event => {
            if (startedOutside && event.target === dialog && outside(event)) dialog.close();
            startedOutside = false;
        });
        // Échap est géré par le <dialog> natif.
        dialog.addEventListener('close', () => {
            syncScroll();
            const opener = openers.get(dialog);
            if (opener?.isConnected) opener.focus({preventScroll: true});
        });
    });
    // Retour d'un formulaire de cœur sans fetch : réouvre les cartes manquantes.
    if (window.location.hash === '#deck-dialog-manquantes') {
        open(document.getElementById('deck-dialog-manquantes'),
            document.querySelector('[data-deck-dialog-open="deck-dialog-manquantes"]'));
    }
})();
