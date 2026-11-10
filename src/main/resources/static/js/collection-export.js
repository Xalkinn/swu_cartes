(() => {
    const form = document.querySelector('.collection-export-form');
    if (!form || !window.fetch || !window.URL?.createObjectURL) return;
    let pending = false;
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (pending) return;
        pending = true;
        const bouton = form.querySelector('button');
        const libelle = form.querySelector('[data-export-libelle]');
        const spinner = form.querySelector('.collection-export-spinner');
        const etat = form.querySelector('.collection-export-etat');
        const params = new URLSearchParams(new FormData(form));
        bouton.disabled = true;
        form.setAttribute('aria-busy', 'true');
        spinner.hidden = false;
        libelle.textContent = 'Préparation…';
        etat.hidden = false;
        etat.classList.remove('collection-export-erreur');
        etat.textContent = 'Préparation de votre fichier…';
        try {
            const response = await fetch(`${form.action}?${params}`, {credentials: 'same-origin'});
            if (response.redirected) throw new Error('Reconnectez-vous puis réessayez.');
            if (!response.ok) {
                const texte = response.headers.get('content-type')?.includes('text/plain') ? await response.text() : '';
                throw new Error(texte || 'Impossible de préparer le fichier. Réessayez.');
            }
            const type = response.headers.get('content-type') || '';
            if (!type.includes('spreadsheetml.sheet') && !type.includes('text/csv')) throw new Error('Votre session a peut-être expiré. Rechargez la page.');
            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            const nom = response.headers.get('content-disposition')?.match(/filename="?([^";]+)"?/i)?.[1];
            a.download = nom || `collection-swu.${params.get('format') === 'csv' ? 'csv' : 'xlsx'}`;
            document.body.append(a);
            a.click();
            a.remove();
            setTimeout(() => URL.revokeObjectURL(url), 30000);
            etat.textContent = 'Votre fichier est prêt. Le téléchargement a été lancé.';
        } catch (error) {
            etat.classList.add('collection-export-erreur');
            etat.textContent = error.message || 'Erreur de connexion. Réessayez.';
        } finally {
            pending = false;
            bouton.disabled = false;
            spinner.hidden = true;
            libelle.textContent = 'Télécharger le fichier';
            form.removeAttribute('aria-busy');
        }
    });
})();
