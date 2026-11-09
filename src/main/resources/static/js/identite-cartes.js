(() => {
    const normaliser = texte => texte.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('fr');
    document.querySelectorAll('.identite-traits').forEach(zone => {
        const recherche = zone.querySelector('[data-filtre-traits]');
        const choix = [...zone.querySelectorAll('.identite-liste-traits label')];
        const actualiser = () => {
            const texte = normaliser(recherche.value.trim());
            choix.forEach(label => { label.hidden = !label.querySelector('input').checked && !normaliser(label.textContent).includes(texte); });
            zone.querySelector('.identite-nombre').textContent = choix.filter(label => label.querySelector('input').checked).length;
        };
        recherche.addEventListener('input', actualiser);
        zone.addEventListener('change', actualiser);
        // Entrée dans cette aide locale ne soumet pas accidentellement le formulaire.
        recherche.addEventListener('keydown', e => { if (e.key === 'Enter') e.preventDefault(); });
        actualiser();
    });
})();
