(() => {
    'use strict';
    const attente = document.querySelector('[data-meta-attente="true"]');
    if (!attente) return;
    let tentatives = 0;
    const verifier = async () => {
        if (document.hidden) { window.setTimeout(verifier, 5000); return; }
        try {
            const reponse = await fetch(attente.dataset.metaEtat, {headers: {'Accept': 'application/json'}, cache: 'no-store'});
            if (!reponse.ok) return;
            const etat = await reponse.json();
            if (etat.disponible && etat.actualisation !== attente.dataset.metaVersion) { window.location.reload(); return; }
            if (!etat.chargement) { const message = attente.querySelector('.meta-vide p, .meta-chargement'); if (message) message.textContent = 'La source est indisponible pour le moment. Une prochaine actualisation réessaiera automatiquement.'; return; }
        } catch (_) { /* Une interruption réseau ne vide pas la sélection. */ }
        if (++tentatives < 24) window.setTimeout(verifier, 5000);
    };
    window.setTimeout(verifier, 3000);
})();
