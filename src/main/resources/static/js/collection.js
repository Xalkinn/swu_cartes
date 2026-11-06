(() => {
    "use strict";

    document.querySelectorAll("[data-dialog-id]").forEach((bouton) => {
        bouton.addEventListener("click", () => {
            const dialogue = document.getElementById(
                bouton.dataset.dialogId
            );

            if (!dialogue || dialogue.open) {
                return;
            }

            dialogue.showModal();
            document.body.classList.add("collection-dialog-ouverte");
        });
    });

    document.querySelectorAll(".versions-dialog").forEach((dialogue) => {
        dialogue.querySelectorAll("[data-fermer-dialog]")
            .forEach((bouton) => {
                bouton.addEventListener("click", () => {
                    dialogue.close();
                });
            });

        dialogue.addEventListener("close", () => {
            if (!document.querySelector(".versions-dialog[open]")) {
                document.body.classList.remove(
                    "collection-dialog-ouverte"
                );
            }
        });

        // Ferme uniquement si le clic se trouve hors de la fenêtre.
        dialogue.addEventListener("click", (evenement) => {
            if (evenement.target !== dialogue) {
                return;
            }

            const limites = dialogue.getBoundingClientRect();

            const clicExterieur =
                evenement.clientX < limites.left
                || evenement.clientX > limites.right
                || evenement.clientY < limites.top
                || evenement.clientY > limites.bottom;

            if (clicExterieur) {
                dialogue.close();
            }
        });
    });
})();