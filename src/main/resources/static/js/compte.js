(() => {
    "use strict";

    document.querySelectorAll(".compte-page [data-password-toggle]").forEach((bouton) => {
        const champ = document.getElementById(bouton.dataset.passwordToggle);
        if (!champ) return;

        // Sans JavaScript, le formulaire reste entièrement utilisable.
        bouton.hidden = false;
        bouton.addEventListener("click", () => {
            const afficher = champ.type === "password";
            champ.type = afficher ? "text" : "password";
            bouton.setAttribute("aria-pressed", String(afficher));
            bouton.setAttribute("aria-label", `${afficher ? "Masquer" : "Afficher"} le ${bouton.dataset.passwordLabel}`);
        });
    });
})();
