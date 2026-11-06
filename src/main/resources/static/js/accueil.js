(() => {
    "use strict";

    const racine = document.querySelector(".accueil-dashboard");
    if (!racine) return;

    const carrousel = racine.querySelector("[data-carrousel]");
    const diapos = racine.querySelector("[data-diapos]");
    const points = racine.querySelector("[data-points]");
    const navigation = racine.querySelector("[data-navigation]");
    const etat = racine.querySelector("[data-news-etat]");
    const boutonPause = racine.querySelector("[data-pause]");
    const mouvementReduit = window.matchMedia("(prefers-reduced-motion: reduce)");

    let index = 0;
    let articles = [];
    let empreinte = "";
    let pauseUtilisateur = mouvementReduit.matches;
    let survol = false;
    let focusInterieur = false;
    let minuteur = null;

    function element(balise, classe, texte) {
        const resultat = document.createElement(balise);
        if (classe) resultat.className = classe;
        if (texte) resultat.textContent = texte;
        return resultat;
    }

    function urlSure(valeur, hote) {
        try {
            const url = new URL(valeur);
            return url.protocol === "https:" && url.hostname === hote ? url.href : null;
        } catch {
            return null;
        }
    }

    function dateLisible(valeur) {
        const date = new Date(valeur);
        return Number.isNaN(date.getTime()) ? "" : new Intl.DateTimeFormat("fr-FR", {
            day: "numeric", month: "long", year: "numeric"
        }).format(date);
    }

    function fleche() {
        const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
        svg.setAttribute("aria-hidden", "true");
        const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
        use.setAttribute("href", "#acc-fleche");
        svg.append(use);
        return svg;
    }

    function actualiserPause() {
        boutonPause.textContent = pauseUtilisateur ? "▶" : "Ⅱ";
        boutonPause.setAttribute("aria-pressed", String(pauseUtilisateur));
        boutonPause.setAttribute("aria-label", pauseUtilisateur
            ? "Reprendre le défilement" : "Mettre le défilement en pause");
    }

    function afficher(numero, manuel = false) {
        if (!articles.length) return;
        index = (numero + articles.length) % articles.length;
        Array.from(diapos.children).forEach((diapo, i) => {
            diapo.hidden = i !== index;
            diapo.setAttribute("aria-hidden", String(i !== index));
        });
        Array.from(points.children).forEach((point, i) => {
            point.setAttribute("aria-current", String(i === index));
        });
        if (manuel) {
            pauseUtilisateur = true;
            actualiserPause();
        }
    }

    function installer(nouveauxArticles) {
        const valides = nouveauxArticles.filter(article => article
            && typeof article.titre === "string"
            && urlSure(article.url, "starwarsunlimited.com"));
        if (!valides.length) return false;

        const nouvelleEmpreinte = JSON.stringify(valides);
        if (nouvelleEmpreinte === empreinte) return true;
        empreinte = nouvelleEmpreinte;
        articles = valides;
        diapos.replaceChildren();
        points.replaceChildren();

        articles.forEach((article, i) => {
            const diapo = element("article", "accueil-diapo");
            diapo.id = `accueil-actualite-${i}`;
            diapo.setAttribute("role", "group");
            diapo.setAttribute("aria-roledescription", "diapositive");
            diapo.setAttribute("aria-label", `${i + 1} sur ${articles.length} : ${article.titre}`);

            const imageUrl = urlSure(article.image, "cdn.starwarsunlimited.com");
            if (imageUrl) {
                const image = element("img", "accueil-hero-image");
                image.src = imageUrl;
                image.alt = "";
                image.decoding = "async";
                image.loading = i === 0 ? "eager" : "lazy";
                if (i === 0) image.fetchPriority = "high";
                image.addEventListener("error", () => image.remove(), { once: true });
                diapo.append(image);
            }

            const contenu = element("div", "accueil-diapo-contenu");
            const meta = element("div", "accueil-hero-meta");
            meta.append(element("span", "accueil-hero-badge", "ACTUALITÉ OFFICIELLE"));
            const date = element("time", "accueil-hero-date", dateLisible(article.date));
            if (!Number.isNaN(Date.parse(article.date))) date.dateTime = article.date;
            meta.append(date);
            contenu.append(meta, element("h2", "", article.titre));
            if (article.description) contenu.append(element("p", "", article.description));

            const lien = element("a", "accueil-hero-lien", "Lire l’article");
            lien.href = urlSure(article.url, "starwarsunlimited.com");
            lien.target = "_blank";
            lien.rel = "noopener noreferrer";
            lien.append(fleche());
            contenu.append(lien);
            diapo.append(contenu);
            diapos.append(diapo);

            const point = element("button", "accueil-point");
            point.type = "button";
            point.setAttribute("aria-label", `Afficher l’actualité ${i + 1} : ${article.titre}`);
            point.setAttribute("aria-controls", diapo.id);
            point.addEventListener("click", () => afficher(i, true));
            points.append(point);
        });

        navigation.hidden = articles.length < 2;
        points.hidden = articles.length < 2;
        afficher(0);
        actualiserPause();
        if (minuteur) clearInterval(minuteur);
        if (articles.length > 1) {
            minuteur = setInterval(() => {
                if (!pauseUtilisateur && !survol && !focusInterieur && !document.hidden) {
                    afficher(index + 1);
                }
            }, 7500);
        }
        return true;
    }

    function indisponible() {
        etat.textContent = articles.length
            ? "Dernières actualités conservées · source momentanément indisponible"
            : "Le fil officiel est momentanément indisponible";
        const texte = racine.querySelector("[data-attente-texte]");
        if (texte) texte.textContent = "Retrouvez les annonces, les sorties et le jeu organisé sur le site officiel.";
    }

    async function charger(tentative = 0) {
        const controleur = new AbortController();
        const delai = setTimeout(() => controleur.abort(), 8000);
        try {
            const reponse = await fetch(racine.dataset.actualitesUrl, {
                headers: { Accept: "application/json" },
                credentials: "same-origin",
                cache: "no-store",
                signal: controleur.signal
            });
            if (!reponse.ok || !reponse.headers.get("content-type")?.includes("application/json")) {
                throw new Error("Actualités indisponibles");
            }
            const donnees = await reponse.json();
            if (Array.isArray(donnees.articles)) installer(donnees.articles);

            if (donnees.indisponible) {
                indisponible();
            } else {
                etat.textContent = articles.length
                    ? "Les dernières nouvelles du site officiel"
                    : "Chargement des actualités officielles…";
            }
            if (donnees.chargement && tentative < 18) {
                setTimeout(() => charger(tentative + 1), 1500);
            } else if (!articles.length) {
                indisponible();
            }
        } catch {
            indisponible();
        } finally {
            clearTimeout(delai);
        }
    }

    racine.querySelector("[data-precedent]").addEventListener("click", () => afficher(index - 1, true));
    racine.querySelector("[data-suivant]").addEventListener("click", () => afficher(index + 1, true));
    boutonPause.addEventListener("click", () => {
        pauseUtilisateur = !pauseUtilisateur;
        actualiserPause();
    });
    carrousel.addEventListener("mouseenter", () => { survol = true; });
    carrousel.addEventListener("mouseleave", () => { survol = false; });
    carrousel.addEventListener("focusin", () => { focusInterieur = true; });
    carrousel.addEventListener("focusout", evenement => {
        focusInterieur = carrousel.contains(evenement.relatedTarget);
    });
    carrousel.addEventListener("keydown", evenement => {
        if (evenement.key === "ArrowRight" || evenement.key === "ArrowLeft") {
            evenement.preventDefault();
            afficher(index + (evenement.key === "ArrowRight" ? 1 : -1), true);
        }
    });
    mouvementReduit.addEventListener("change", evenement => {
        if (evenement.matches) {
            pauseUtilisateur = true;
            actualiserPause();
        }
    });

    const dialogue = racine.querySelector("#accueil-bientot");
    const rubriques = {
        tournois: ["À bientôt autour d’une table.", "L’espace tournois permettra de retrouver vos prochaines rencontres. Cette fonctionnalité n’est pas encore disponible."],
        communaute: ["Les bonnes idées se partagent.", "Les decks de la communauté auront leur place ici. La publication et le partage de decks seront ajoutés dans une prochaine étape."],
        moment: ["Votre prochaine inspiration.", "Une sélection de decks du moment viendra enrichir cet espace. Cette rubrique n’est pas encore alimentée."],
        decks: ["Votre stratégie prend forme.", "La création et la sauvegarde de vos decks seront ajoutées prochainement. Vous pouvez déjà préparer votre collection."]
    };
    racine.querySelectorAll("[data-bientot]").forEach(bouton => {
        bouton.addEventListener("click", () => {
            const contenu = rubriques[bouton.dataset.bientot];
            dialogue.querySelector("h2").textContent = contenu[0];
            dialogue.querySelector("[data-bientot-description]").textContent = contenu[1];
            dialogue.showModal();
        });
    });
    dialogue.querySelectorAll("[data-fermer-bientot]").forEach(bouton => {
        bouton.addEventListener("click", () => dialogue.close());
    });
    dialogue.addEventListener("click", evenement => {
        if (evenement.target !== dialogue) return;
        const r = dialogue.getBoundingClientRect();
        if (evenement.clientX < r.left || evenement.clientX > r.right
                || evenement.clientY < r.top || evenement.clientY > r.bottom) dialogue.close();
    });

    charger();
})();
