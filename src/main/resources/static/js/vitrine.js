(() => {
    'use strict';
    const input = document.getElementById('vitrine-url');
    const button = document.querySelector('[data-copier-vitrine]');
    if (!input || !button) return;
    input.value = new URL(input.value, window.location.href).href;
    button.addEventListener('click', async () => {
        const message = document.getElementById('vitrine-copie-message');
        try {
            if (!navigator.clipboard?.writeText) throw new Error('Copie manuelle');
            await navigator.clipboard.writeText(input.value);
            message.textContent = 'Lien copié !';
        } catch {
            input.focus();
            input.select();
            message.textContent = 'Lien sélectionné : copiez-le avec votre navigateur.';
        }
    });
})();
