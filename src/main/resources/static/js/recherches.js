(() => {
    'use strict';
    if (document.documentElement.dataset.recherchesReady) return;
    document.documentElement.dataset.recherchesReady = 'true';
    const message = document.getElementById('recherches-message');
    const messageParent = message?.parentElement;
    let timer;
    let pending = false;
    const notice = (text, error = false) => {
        if (!message) return;
        const dialog = [...document.querySelectorAll('dialog[open]')].pop();
        // Un élément en dehors du <dialog> serait caché derrière sa couche modale.
        const destination = dialog?.querySelector('.deck-dialog-entete > div, .carte-dialog-entete > div');
        if (destination) destination.append(message);
        else messageParent?.append(message);
        message.classList.toggle('recherche-message-dialog', Boolean(destination));
        clearTimeout(timer);
        message.textContent = text;
        message.classList.toggle('recherche-message-erreur', error);
        message.hidden = false;
        timer = setTimeout(() => { message.hidden = true; }, 5000);
    };
    document.addEventListener('submit', async event => {
        const form = event.target;
        if (!(form instanceof HTMLFormElement) || !form.matches('.coeur-form')) return;
        if (!window.fetch || !form.dataset.rechercheUrl) return; // Le POST normal reste utilisable.
        event.preventDefault();
        if (pending) return;
        pending = true;
        const buttons = [...document.querySelectorAll('.coeur-form button')];
        buttons.forEach(button => { button.disabled = true; });
        form.setAttribute('aria-busy', 'true');
        try {
            const response = await fetch(form.dataset.rechercheUrl, {
                method: 'POST', credentials: 'same-origin',
                headers: { Accept: 'application/json' }, body: new URLSearchParams(new FormData(form))
            });
            if (response.redirected || !response.headers.get('Content-Type')?.includes('json')) {
                throw new Error('Votre session a peut-être expiré. Rechargez la page puis réessayez.');
            }
            const state = await response.json();
            if (!response.ok) throw new Error(state.message || 'Impossible de modifier vos recherches. Réessayez.');
            const ids = new Set(state.carteIds.map(String));
            document.querySelectorAll('.coeur-form').forEach(other => {
                if (!ids.has(other.dataset.carteId)) return;
                const button = other.querySelector('button');
                other.elements.recherche.value = String(!state.recherche);
                button.classList.toggle('recherche-coeur-actif', state.recherche);
                button.setAttribute('aria-pressed', String(state.recherche));
                button.setAttribute('aria-label', `${state.recherche ? 'Retirer de mes recherches : ' : 'Rechercher : '}${other.dataset.titre}`);
                button.title = state.recherche ? 'Retirer de mes recherches' : 'Ajouter à mes recherches';
            });
            notice(state.recherche ? 'Carte ajoutée à vos recherches.' : 'Carte retirée de vos recherches.');
            // Recalcule aussi la pagination et le compteur de la liste après un retrait.
            if (!state.recherche && (form.closest('[data-liste-recherches]') || form.closest('[data-collection-recherches="true"]'))) window.location.reload();
        } catch (error) {
            notice(error.message || 'Erreur de connexion. Réessayez.', true);
        } finally {
            pending = false;
            buttons.forEach(button => { button.disabled = false; });
            form.removeAttribute('aria-busy');
        }
    });
    // Le bouton Retour peut restaurer une page mise en cache avant la modification d'un cœur.
    window.addEventListener('pageshow', event => { if (event.persisted) window.location.reload(); });
})();
