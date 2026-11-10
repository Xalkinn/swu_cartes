(() => {
    document.querySelectorAll('[data-import-form]').forEach(form => {
        let enCours = false;
        form.addEventListener('submit', event => {
            if (enCours) { event.preventDefault(); return; }
            if (!form.checkValidity()) return;
            enCours = true;
            const bouton = form.querySelector('button[type=submit]');
            const etat = form.querySelector('[data-import-etat]');
            bouton.disabled = true;
            bouton.textContent = form.dataset.importForm === 'confirmer' ? 'Enregistrement…' : 'Analyse du fichier…';
            form.setAttribute('aria-busy', 'true');
            if (etat) { etat.hidden = false; etat.textContent = 'Veuillez patienter. Cette opération peut prendre quelques instants.'; }
        });
        window.addEventListener('pageshow', event => { if (event.persisted) window.location.reload(); });
    });
})();
