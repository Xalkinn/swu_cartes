"use strict";

(() => {
    const ouvrir = document.getElementById("ouvrirCamera");
    const fermer = document.getElementById("fermerCamera");
    const photographier = document.getElementById("photographier");

    const panneau = document.getElementById("panneauCamera");
    const video = document.getElementById("cameraVideo");
    const cadre = document.getElementById("cameraCadre");

    const statut = document.getElementById("statutLecture");
    const entreePhoto = document.getElementById("photoCamera");
    const fichier = document.getElementById("photoFichier");
    const orientation = document.getElementById("orientationCarte");

    let flux = null;
    let session = 0;

    function fermerCamera() {
        // Invalide aussi une ouverture ou une capture encore en attente.
        session++;

        if (flux) {
            flux.getTracks().forEach(piste => piste.stop());
            flux = null;
        }

        video.srcObject = null;
        panneau.hidden = true;
        photographier.disabled = true;
        ouvrir.disabled = false;
    }

    function calculerCadre() {
        const largeurVideo = video.videoWidth;
        const hauteurVideo = video.videoHeight;

        if (!largeurVideo || !hauteurVideo) {
            return null;
        }

        const ratio = orientation.value === "paysage"
            ? 88 / 63
            : 63 / 88;

        const hauteur = Math.min(
            hauteurVideo * 0.88,
            largeurVideo * 0.88 / ratio
        );

        const largeur = hauteur * ratio;

        return {
            x: (largeurVideo - largeur) / 2,
            y: (hauteurVideo - hauteur) / 2,
            largeur,
            hauteur
        };
    }

    function actualiserCadre() {
        const zone = calculerCadre();

        if (!zone) {
            return;
        }

        cadre.style.left =
            `${zone.x / video.videoWidth * 100}%`;

        cadre.style.top =
            `${zone.y / video.videoHeight * 100}%`;

        cadre.style.width =
            `${zone.largeur / video.videoWidth * 100}%`;

        cadre.style.height =
            `${zone.hauteur / video.videoHeight * 100}%`;
    }

    ouvrir.addEventListener("click", async () => {
        if (entreePhoto.disabled) {
            statut.textContent =
                "Attendez la fin de la lecture avant d’ouvrir la caméra.";
            return;
        }

        if (!window.isSecureContext
            || !navigator.mediaDevices?.getUserMedia) {

            statut.textContent =
                "L’aperçu caméra nécessite une adresse HTTPS, "
                + "ou localhost sur cet ordinateur. "
                + "Vous pouvez toujours choisir une image existante.";

            return;
        }

        ouvrir.disabled = true;
        panneau.hidden = false;
        photographier.disabled = true;

        statut.textContent =
            "Autorisez l’accès à la caméra dans votre navigateur.";

        const sessionDemandee = ++session;

        try {
            const nouveauFlux =
                await navigator.mediaDevices.getUserMedia({
                    audio: false,
                    video: {
                        // Privilégie la caméra arrière sur téléphone.
                        facingMode: { ideal: "environment" },
                        width: { ideal: 1920 },
                        height: { ideal: 1080 }
                    }
                });

            // Si l'utilisateur a fermé entre-temps,
            // on libère immédiatement les pistes reçues.
            if (sessionDemandee !== session) {
                nouveauFlux.getTracks()
                    .forEach(piste => piste.stop());
                return;
            }

            flux = nouveauFlux;
            video.srcObject = flux;

            await video.play();

            if (sessionDemandee !== session) {
                return;
            }

            actualiserCadre();
            photographier.disabled = false;

            statut.textContent =
                "Placez la carte dans le cadre, "
                + "attendez que l’image soit nette, puis photographiez.";

        } catch (erreur) {
            if (sessionDemandee !== session) {
                return;
            }

            fermerCamera();

            if (erreur.name === "NotAllowedError") {
                statut.textContent =
                    "L’accès à la caméra est refusé. "
                    + "Vérifiez les autorisations du site dans le navigateur.";

            } else if (erreur.name === "NotFoundError") {
                statut.textContent =
                    "Aucune caméra ou webcam disponible.";

            } else if (erreur.name === "NotReadableError") {
                statut.textContent =
                    "La caméra est indisponible. "
                    + "Fermez les autres applications qui l’utilisent.";

            } else {
                statut.textContent =
                    "Impossible d’ouvrir la caméra. "
                    + "Vous pouvez choisir une image existante.";
            }

            console.error("Ouverture caméra :", erreur);
        }
    });

    photographier.addEventListener("click", () => {
        if (entreePhoto.disabled) {
            statut.textContent =
                "Attendez la fin de la lecture en cours.";
            return;
        }

        const zone = calculerCadre();

        if (!flux || !zone) {
            statut.textContent =
                "Attendez que l’aperçu de la caméra soit prêt.";
            return;
        }

        photographier.disabled = true;

        // On conserve uniquement la zone délimitée par le cadre.
        const canvas = document.createElement("canvas");

        canvas.width = Math.round(zone.largeur);
        canvas.height = Math.round(zone.hauteur);

        const contexte = canvas.getContext("2d");

        if (!contexte) {
            photographier.disabled = false;
            statut.textContent = "Impossible de préparer la photo.";
            return;
        }

        contexte.drawImage(
            video,
            zone.x,
            zone.y,
            zone.largeur,
            zone.hauteur,
            0,
            0,
            canvas.width,
            canvas.height
        );

        const sessionCapture = session;

        canvas.toBlob(blob => {
            if (sessionCapture !== session) {
                return;
            }

            if (!blob) {
                photographier.disabled = false;
                statut.textContent =
                    "La capture a échoué. Réessayez.";
                return;
            }

            const photo = new File(
                [blob],
                "carte-swu.jpg",
                { type: "image/jpeg" }
            );

            // Coupe la caméra après la prise de vue.
            fermerCamera();

            document.dispatchEvent(
                new CustomEvent("scan:photo", {
                    detail: photo
                })
            );

        }, "image/jpeg", 0.95);
    });

    fermer.addEventListener("click", () => {
        fermerCamera();

        statut.textContent =
            "Caméra fermée. Vous pouvez la rouvrir ou choisir une image.";
    });

    video.addEventListener("resize", actualiserCadre);
    orientation.addEventListener("change", actualiserCadre);

    // Choisir une image ferme également l'aperçu caméra.
    fichier.addEventListener("change", () => {
        if (fichier.files?.length) {
            fermerCamera();
        }
    });

    // Libère la caméra quand on quitte la page.
    window.addEventListener("pagehide", fermerCamera);
})();