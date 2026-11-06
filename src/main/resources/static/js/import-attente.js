(() => {
    "use strict";

    let importEnCours = false;
    const formulaires = document.querySelectorAll("form[data-import-form]");
    const attente = document.querySelector("[data-import-attente]");
    const titre = document.querySelector("[data-import-titre]");

    formulaires.forEach((formulaire) => {
        formulaire.addEventListener("submit", (evenement) => {
            if (importEnCours) {
                evenement.preventDefault();
                return;
            }

            const bouton = evenement.submitter;
            const toutes = Boolean(bouton && bouton.hasAttribute("data-import-toutes"));

            // Le bouton global possède une autre destination.
            // On la copie avant de désactiver les boutons.
            if (bouton && bouton.hasAttribute("formaction")) {
                formulaire.action = bouton.formAction;
            }

            importEnCours = true;
            formulaire.setAttribute("aria-busy", "true");

            if (titre) {
                titre.textContent = toutes
                    ? "Import de toutes les extensions en cours…"
                    : "Import de l’extension en cours…";
            }
            if (attente) {
                attente.hidden = false;
            }

            // Ne pas désactiver le select : sa valeur doit être envoyée.
            // Les boutons n'ont pas de nom de paramètre à transmettre.
            formulaires.forEach((form) => {
                form.querySelectorAll('button[type="submit"], input[type="submit"]')
                    .forEach((element) => {
                        element.disabled = true;
                    });
            });
        });
    });

    window.addEventListener("pageshow", (evenement) => {
        if (evenement.persisted) {
            window.location.reload();
        }
    });
})();
