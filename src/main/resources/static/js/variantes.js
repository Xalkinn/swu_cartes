(() => {
    const choix = document.getElementById('finition');
    const serie = document.getElementById('serie');
    const champ = serie?.closest('.rapide-champ');
    if (!choix || !serie || !champ) return;
    const afficher = () => { champ.hidden = choix.value !== 'PRESTIGE_SERIALISEE'; };
    choix.addEventListener('change', () => { afficher(); if (champ.hidden) serie.value = ''; });
    afficher();
})();
