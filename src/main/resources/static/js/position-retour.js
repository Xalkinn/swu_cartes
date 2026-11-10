(() => {
    'use strict';
    if (document.documentElement.dataset.positionRetourReady) return;
    document.documentElement.dataset.positionRetourReady = 'true';
    const key = 'swu:retour-carte';
    const storage = {
        get: () => {
            try {
                return JSON.parse(sessionStorage.getItem(key));
            } catch (_) {
                return null;
            }
        },
        set: value => {
            try {
                sessionStorage.setItem(key, JSON.stringify(value));
            } catch (_) { /* Sans stockage : POST classique. */
            }
        },
        clear: () => {
            try {
                sessionStorage.removeItem(key);
            } catch (_) { /* Mode privé. */
            }
        }
    };
    const signature = form => JSON.stringify([
        new URL(form.action, location.href).pathname,
        form.elements.namedItem('carteId')?.value || '',
        form.elements.namedItem('foil')?.value || '',
        form.elements.namedItem('emplacement')?.value || ''
    ]);
    const anchor = form => form.closest('.collection-carte, .catalogue-carte, .deck-carte, .deck-lignes > li') || form;
    document.addEventListener('submit', event => {
        const form = event.target;
        if (event.defaultPrevented || !(form instanceof HTMLFormElement) || form.method.toLowerCase() !== 'post') return;
        const action = new URL(form.action, location.href);
        if (action.origin !== location.origin || !/\/(?:collection\/(?:ajuster|ajout-rapide)|scan\/ajouter|decks\/\d+\/(?:ajouter|quantite|choisir)|recherches\/cartes)\/?$/.test(action.pathname)) return;
        // On ne mémorise ni identifiants de connexion, ni mots de passe, ni données de compte.
        const dialog = form.closest('dialog[open]');
        const target = anchor(form);
        const button = event.submitter;
        storage.set({
            path: location.pathname, time: Date.now(), y: scrollY,
            signature: signature(form), offset: target.getBoundingClientRect().top,
            dialog: dialog?.id || null, dialogScroll: dialog?.scrollTop || 0,
            dialogContentScroll: dialog?.querySelector('.carte-dialog-corps')?.scrollTop || 0,
            button: button ? {name: button.name, value: button.value, label: button.getAttribute('aria-label')} : null
        });
    });

    const saved = storage.get();
    if (!saved) return;
    storage.clear(); // Un retour unique, jamais appliqué à une navigation ultérieure.
    if (saved.path !== location.pathname || Date.now() - saved.time > 120000) return;
    const form = [...document.forms].find(candidate => signature(candidate) === saved.signature);
    const target = form ? anchor(form) : null;
    const previousRestoration = history.scrollRestoration;
    history.scrollRestoration = 'manual';
    let interrupted = false;
    const stop = () => {
        interrupted = true;
        history.scrollRestoration = previousRestoration;
    };
    for (const type of ['pointerdown', 'wheel', 'touchstart', 'keydown']) window.addEventListener(type, stop, {
        once: true,
        passive: true
    });
    const restoreScroll = () => {
        if (interrupted) return;
        const y = target ? scrollY + target.getBoundingClientRect().top - saved.offset : saved.y;
        window.scrollTo({top: Math.max(0, y), left: 0, behavior: 'instant'});
    };
    const restore = () => {
        restoreScroll();
        const dialog = saved.dialog ? document.getElementById(saved.dialog) : null;
        if (dialog instanceof HTMLDialogElement && !dialog.open) {
            // Réutilise l’ouverture existante pour conserver les styles et les écouteurs de fermeture.
            const opener = [...document.querySelectorAll('[data-dialog-id]')].find(button => button.dataset.dialogId === saved.dialog);
            if (opener) opener.click();
            else if (typeof dialog.showModal === 'function') dialog.showModal();
        }
        if (dialog?.open) {
            dialog.scrollTop = saved.dialogScroll;
            const content = dialog.querySelector('.carte-dialog-corps');
            if (content) content.scrollTop = saved.dialogContentScroll || 0;
        }
        // L’ajout rapide garde la saisie du numéro prête pour la carte suivante.
        const inputAutoFocus = document.querySelector('input[autofocus], textarea[autofocus], select[autofocus]');
        if (form && saved.button && !inputAutoFocus) {
            const button = [...form.querySelectorAll('button')].find(candidate =>
                candidate.name === saved.button.name && candidate.value === saved.button.value
                && candidate.getAttribute('aria-label') === saved.button.label);
            if (button && !button.disabled) button.focus({preventScroll: true});
        }
        restoreScroll();
    };
    // Les scripts différés de l’éditeur et les classes de mise en page sont déjà initialisés.
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', restore, {once: true});
    else restore();
    // Corrige le décalage si les images ou les polices ont terminé après le DOM.
    const finish = () => {
        if (!interrupted && saved.dialog) {
            const dialog = document.getElementById(saved.dialog);
            const content = dialog?.querySelector('.carte-dialog-corps');
            if (dialog?.open && content) content.scrollTop = saved.dialogContentScroll || 0;
        }
        restoreScroll();
        history.scrollRestoration = previousRestoration;
    };
    if (document.readyState === 'complete') requestAnimationFrame(finish);
    else window.addEventListener('load', finish, {once: true});
})();
