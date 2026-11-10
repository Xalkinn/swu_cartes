"use strict";

const cameraInput = document.getElementById("photoCamera");
const fichierInput = document.getElementById("photoFichier");
const apercu = document.getElementById("photoApercu");
const boutonLecture = document.getElementById("lirePhoto");
const boutonRecherche = document.getElementById("rechercherCarte");
const statut = document.getElementById("statutLecture");
const texteLu = document.getElementById("texteLu");
const extensionSelect = document.getElementById("extensionCode");
const numeroInput = document.getElementById("numero");

let photo = null;
let photoUrl = null;
let worker = null;

function normaliser(texte) {
    return texte
        .normalize("NFKD")
        .replace(/[\u0300-\u036f]/g, "")
        .toUpperCase();
}

function selectionnerPhoto(event) {
    const fichier = event.target.files?.[0];

    if (!fichier) {
        return;
    }

    photo = null;
    boutonLecture.disabled = true;
    apercu.hidden = true;
    texteLu.value = "";

    // Évite de garder la référence détectée sur une ancienne photo.
    extensionSelect.value = "";
    numeroInput.value = "";

    if (photoUrl) {
        URL.revokeObjectURL(photoUrl);
        photoUrl = null;
    }

    if (!fichier.type.startsWith("image/")) {
        statut.textContent = "Choisissez un fichier image.";
        return;
    }

    if (fichier.size > 20 * 1024 * 1024) {
        statut.textContent =
            "Cette image dépasse 20 Mo. Choisissez une photo plus légère.";
        return;
    }

    photo = fichier;
    photoUrl = URL.createObjectURL(fichier);

    apercu.src = photoUrl;
    apercu.hidden = false;
    boutonLecture.disabled = false;

    statut.textContent =
        "Photo prête. Vérifiez que le numéro en bas de la carte est lisible.";
}

cameraInput.addEventListener("change", selectionnerPhoto);
fichierInput.addEventListener("change", selectionnerPhoto);

async function preparerImage(angle = 0) {
    const image = new Image();
    image.src = photoUrl;

    await image.decode();

    // On conserve les 45 % inférieurs au lieu de 35 %,
    // pour éviter de couper le code d'extension.
    const debutY = Math.floor(image.naturalHeight * 0.55);
    const hauteur = image.naturalHeight - debutY;

    // Conserve davantage de détails sur une carte entière.
    // Le côté le plus long est limité à 2 000 pixels.
    const echelle = Math.min(
        3,
        2000 / Math.max(
            image.naturalWidth,
            image.naturalHeight
        )
    );

    const source = document.createElement("canvas");

    source.width = Math.max(
        1,
        Math.round(image.naturalWidth * echelle)
    );

    source.height = Math.max(
        1,
        Math.round(hauteur * echelle)
    );

    const contexte = source.getContext("2d");

    if (!contexte) {
        throw new Error("Impossible de préparer l'image.");
    }

    contexte.fillStyle = "#ffffff";
    contexte.fillRect(0, 0, source.width, source.height);

    contexte.imageSmoothingEnabled = true;
    contexte.imageSmoothingQuality = "high";

    contexte.drawImage(
        image,
        0,
        debutY,
        image.naturalWidth,
        hauteur,
        0,
        0,
        source.width,
        source.height
    );

    const pixels = contexte.getImageData(
        0,
        0,
        source.width,
        source.height
    );

    let minimum = 255;
    let maximum = 0;

    // Conversion en niveaux de gris et mesure du contraste.
    for (let i = 0; i < pixels.data.length; i += 4) {
        const gris = Math.round(
            0.299 * pixels.data[i]
            + 0.587 * pixels.data[i + 1]
            + 0.114 * pixels.data[i + 2]
        );

        pixels.data[i] = gris;
        pixels.data[i + 1] = gris;
        pixels.data[i + 2] = gris;

        minimum = Math.min(minimum, gris);
        maximum = Math.max(maximum, gris);
    }

    const amplitude = Math.max(1, maximum - minimum);

    // Contraste automatique + inversion :
    // le texte blanc du pied de carte devient sombre.
    for (let i = 0; i < pixels.data.length; i += 4) {
        const valeur = 255 - Math.round(
            (pixels.data[i] - minimum) * 255 / amplitude
        );

        pixels.data[i] = valeur;
        pixels.data[i + 1] = valeur;
        pixels.data[i + 2] = valeur;
    }

    contexte.putImageData(pixels, 0, 0);

    // Rotation avec marges pour ne pas couper le texte.
    const radians = angle * Math.PI / 180;
    const cosinus = Math.abs(Math.cos(radians));
    const sinus = Math.abs(Math.sin(radians));
    const marge = 24;

    const resultat = document.createElement("canvas");

    resultat.width = Math.ceil(
        source.width * cosinus + source.height * sinus
    ) + marge * 2;

    resultat.height = Math.ceil(
        source.width * sinus + source.height * cosinus
    ) + marge * 2;

    const sortie = resultat.getContext("2d");

    if (!sortie) {
        throw new Error("Impossible de redresser l'image.");
    }

    sortie.fillStyle = "#ffffff";
    sortie.fillRect(0, 0, resultat.width, resultat.height);

    sortie.translate(
        resultat.width / 2,
        resultat.height / 2
    );

    sortie.rotate(radians);

    sortie.drawImage(
        source,
        -source.width / 2,
        -source.height / 2
    );

    return resultat;
}

async function obtenirLecteur() {
    if (!window.Tesseract) {
        throw new Error("Le lecteur n'a pas été téléchargé");
    }

    if (!worker) {
        worker = await Tesseract.createWorker("eng", 1, {
            logger(message) {
                if (message.status === "recognizing text") {
                    const progression = Math.round(
                        (message.progress || 0) * 100
                    );

                    statut.textContent =
                        `Lecture de la référence… ${progression} %`;
                }
            }
        });

        await worker.setParameters({
            tessedit_pageseg_mode: Tesseract.PSM.SPARSE_TEXT
        });
    }

    return worker;
}

function detecterReference(texte) {
    const contenu = normaliser(texte);

    const codesDisponibles = [...extensionSelect.options]
        .map(option => option.value)
        .filter(Boolean);

    const codesTrouves = codesDisponibles.filter(code => {
        const codeEchappe = normaliser(code).replace(
            /[.*+?^${}()|[\]\\]/g,
            "\\$&"
        );

        return new RegExp(
            `(?:^|[^A-Z0-9])${codeEchappe}(?=[^A-Z0-9]|FR|$)`
        ).test(contenu);
    });

    const correspondances = [
        ...contenu.matchAll(
            /\b([0-9OIL]{1,4})\s*\/\s*[0-9OIL]{2,4}\b/g
        )
    ];

    const classiques = [...new Set(
        correspondances.map(correspondance => Number(
            correspondance[1]
                .replace(/O/g, "0")
                .replace(/[IL]/g, "1")
        ))
    )].filter(numero => numero >= 1 && numero <= 9999);

    const jetons = [...new Set([...contenu.matchAll(/\bT\s*([0-9OIL]{1,4})\b/g)]
        .map(m => Number(m[1].replace(/O/g,"0").replace(/[IL]/g,"1")))
        .filter(n => n >= 1 && n <= 9999)
        .map(n => `T${String(n).padStart(2,"0")}`))];
    const numerosTrouves = jetons.length ? jetons : classiques;

    extensionSelect.value = "";
    numeroInput.value = "";

    if (codesTrouves.length === 1) {
        extensionSelect.value = codesTrouves[0];
    }

    if (numerosTrouves.length === 1) {
        numeroInput.value = numerosTrouves[0];
    }

    const referenceComplete =
        codesTrouves.length === 1
        && numerosTrouves.length === 1;

    statut.textContent = referenceComplete
        ? "Référence proposée. Vérifiez les valeurs, puis recherchez la carte."
        : "Lecture incomplète ou ambiguë. Vérifiez ou complétez la référence.";

    return referenceComplete;
}

boutonLecture.addEventListener("click", async () => {
    if (!photo) {
        return;
    }

    boutonLecture.disabled = true;
    boutonRecherche.disabled = true;
    cameraInput.disabled = true;
    fichierInput.disabled = true;

    extensionSelect.value = "";
    numeroInput.value = "";
    texteLu.value = "";

    statut.textContent = "Préparation de la lecture…";

    try {
        const lecteur = await obtenirLecteur();
        const textes = [];

        // Essai droit, puis légère correction dans les deux sens.
        // Dans Canvas, un angle négatif tourne vers la gauche.
        const angles = [0, -8, 8];

        for (let i = 0; i < angles.length; i++) {
            statut.textContent =
                `Lecture de la référence — essai ${i + 1}/${angles.length}…`;

            const canvas = await preparerImage(angles[i]);
            const resultat = await lecteur.recognize(canvas);

            textes.push(resultat.data.text || "");

            const texteCumule = textes.join("\n\n");
            texteLu.value = texteCumule;

            if (detecterReference(texteCumule)) {
                break;
            }
        }

    } catch (erreur) {
        console.error("Échec de lecture de la photo :", erreur);

        statut.textContent =
            "La lecture a échoué. Vérifiez votre connexion "
            + "ou saisissez la référence manuellement.";

    } finally {
        boutonLecture.disabled = false;
        boutonRecherche.disabled = false;
        cameraInput.disabled = false;
        fichierInput.disabled = false;
    }
});

// Évite plusieurs clics pendant l'envoi d'une confirmation.
document.querySelectorAll('form[action$="/scan/ajouter"]')
    .forEach(formulaire => {
        formulaire.addEventListener("submit", () => {
            formulaire.querySelector('button[type="submit"]').disabled = true;
        });
    });

// Reçoit la photo prise par camera.js et réutilise
// le même traitement que pour une image choisie.
document.addEventListener("scan:photo", event => {
    selectionnerPhoto({
        target: {
            files: [event.detail]
        }
    });
});