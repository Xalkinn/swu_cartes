package fr.xalkinn.swu.collection;

import fr.xalkinn.swu.carte.Carte;
import fr.xalkinn.swu.utilisateur.Utilisateur;
import fr.xalkinn.swu.utilisateur.UtilisateurRepository;
import jakarta.persistence.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CollectionImportService {
    public enum Mode {
        ACTUALISER("Mettre à jour les quantités"), AJOUTER("Ajouter à mes quantités");
        private final String libelle;

        Mode(String libelle) {
            this.libelle = libelle;
        }

        public String getLibelle() {
            return libelle;
        }
    }

    public record Cible(Long carteId, int avant, int avantFoil, int apres, int apresFoil) {
        public boolean modifiee() {
            return avant != apres || avantFoil != apresFoil;
        }
    }

    public record LigneApercu(String source, String reference, String nom, int fichier, int importable, int actuel,
                              int apres,
                              boolean prete, boolean ignoree, String detail, List<Cible> cibles) {
        public LigneApercu {
            cibles = List.copyOf(cibles);
        }

        public boolean modifiee() {
            return cibles.stream().anyMatch(Cible::modifiee);
        }
    }

    public record Preparation(String jeton, Long utilisateurId, Instant expiration,
                              CollectionImportLecture.Releve releve, Mode mode,
                              List<LigneApercu> lignes, int exemplaires, int nonGeres) {
        public Preparation {
            lignes = List.copyOf(lignes);
        }

        public long pretes() {
            return lignes.stream().filter(LigneApercu::prete).count();
        }

        public long bloquees() {
            return lignes.stream().filter(l -> !l.prete() && !l.ignoree()).count();
        }

        public long changements() {
            return lignes.stream().filter(LigneApercu::modifiee).count();
        }

        public boolean partiel() {
            return bloquees() > 0 || nonGeres > 0;
        }

        public List<LigneApercu> visibles() {
            return lignes.stream().filter(l -> l.fichier() > 0 || !l.prete() && !l.ignoree() || l.modifiee()).toList();
        }
    }

    public record Bilan(long references, int exemplaires, Mode mode) {
    }

    private final UtilisateurRepository utilisateurs;
    private final PossessionRepository possessions;
    @PersistenceContext
    private EntityManager entityManager;

    public CollectionImportService(UtilisateurRepository utilisateurs, PossessionRepository possessions) {
        this.utilisateurs = utilisateurs;
        this.possessions = possessions;
    }

    @Transactional(readOnly = true)
    public Preparation preparer(Long utilisateurId, CollectionImportLecture.Releve releve, Mode mode) {
        if (mode == null) throw new IllegalArgumentException("Choisissez un mode d'import.");
        utilisateur(utilisateurId);
        List<Carte> catalogue = entityManager.createQuery("select c from Carte c join fetch c.extension left join fetch c.carteStandard order by c.id", Carte.class).getResultList();
        Map<String, List<Carte>> references = catalogue.stream().filter(c -> !c.isHyperspace() && c.getCarteStandard() == null).collect(Collectors.groupingBy(c -> cle(c.getExtension().getCode(), c.getNumeroAffiche())));
        Map<Long, List<Carte>> variantes = catalogue.stream().filter(c -> c.isHyperspace() && c.getCarteStandard() != null).collect(Collectors.groupingBy(c -> c.getCarteStandard().getId()));
        Map<Long, Possession> stock = chargerStock(utilisateurId);
        Map<String, Long> doublons = releve.lignes().stream().filter(l -> l.erreur().isBlank()).collect(Collectors.groupingBy(l -> cle(l.extension(), l.numero()), Collectors.counting()));
        List<LigneApercu> lignes = new ArrayList<>();
        int exemplaires = 0, nonGeres = 0;
        for (var source : releve.lignes()) {
            String ref = source.extension() + " · " + source.numero();
            String erreur = source.erreur();
            int special = source.speciaux();
            nonGeres += special;
            List<Carte> cartes = references.getOrDefault(cle(source.extension(), source.numero()), List.of());
            int importable = source.total() - special;
            List<Cible> cibles = new ArrayList<>();
            String nom = source.nom(), detail = "";
            int actuel = 0, apres = 0;
            if (erreur.isBlank() && doublons.getOrDefault(cle(source.extension(), source.numero()), 0L) > 1)
                erreur = "Référence présente plusieurs fois : corrigez les doublons dans le fichier.";
            // Une ligne vide inconnue du catalogue n'empêche pas les cartes possédées d'être importées.
            boolean ignoree = erreur.isBlank() && source.total() == 0 && cartes.isEmpty();
            if (erreur.isBlank() && !ignoree) {
                if (cartes.size() != 1)
                    erreur = cartes.isEmpty() ? "Carte standard absente du catalogue. Importez d'abord cette extension depuis l'administration." : "Plusieurs cartes standard correspondent à ce numéro. Vérifiez le catalogue.";
                else {
                    Carte standard = cartes.get(0);
                    nom = standard.getTitre() + (standard.getSousTitre() == null ? "" : " — " + standard.getSousTitre());
                    List<Carte> hypers = variantes.getOrDefault(standard.getId(), List.of());
                    int qHyper = source.hyperspace() + source.hyperspaceFoil(), qStandard = importable - qHyper;
                    if (qHyper > 0 && hypers.size() != 1)
                        erreur = hypers.isEmpty() ? "Hyperspace absente ou non rattachée à la carte standard. Actualisez le catalogue." : "Plusieurs hyperspace sont rattachées à cette carte : répartition manuelle nécessaire.";
                    else if (qStandard < source.foil() || qStandard > 9999 || qHyper > 9999)
                        erreur = "Répartition standard/hyperspace invalide ou quantité supérieure à 9999 pour une version.";
                    else {
                        try {
                            cibles.add(cible(standard.getId(), qStandard, source.foil(), stock, mode));
                            // Un zéro explicite actualise toutes les hyperspace déjà rattachées.
                            for (Carte h : hypers)
                                cibles.add(cible(h.getId(), qHyper, source.hyperspaceFoil(), stock, mode));
                            for (var cible : cibles) {
                                actuel += cible.avant();
                                apres += cible.apres();
                            }
                            detail = "Fichier : " + qStandard + " standard dont " + source.foil() + " foil ; " + qHyper + " hyperspace dont " + source.hyperspaceFoil() + " foil. " + (mode == Mode.ACTUALISER ? "Quantités définies par le fichier." : "Quantités ajoutées à votre collection.");
                            if (special > 0)
                                detail += " " + special + " Showcase / Prestige exclus : ces versions ne sont pas encore gérées.";
                            exemplaires += importable;
                        } catch (IllegalArgumentException e) {
                            erreur = e.getMessage();
                            cibles.clear();
                        }
                    }
                }
            }
            boolean prete = erreur.isBlank() && !ignoree;
            lignes.add(new LigneApercu(source.source(), ref, nom, source.total(), Math.max(0, importable), actuel, apres, prete, ignoree,
                    ignoree ? "Aucune quantité et référence absente du catalogue." : prete ? detail : erreur, cibles));
        }
        return new Preparation(UUID.randomUUID().toString(), utilisateurId, Instant.now().plus(Duration.ofMinutes(30)), releve, mode, lignes, exemplaires, nonGeres);
    }

    private Cible cible(Long carteId, int quantite, int foil, Map<Long, Possession> stock, Mode mode) {
        Possession p = stock.get(carteId);
        int avant = p == null ? 0 : p.getQuantite(), avantFoil = p == null ? 0 : p.getQuantiteFoil();
        int apres = quantite + (mode == Mode.AJOUTER ? avant : 0), apresFoil = foil + (mode == Mode.AJOUTER ? avantFoil : 0);
        if (apres > 9999 || apresFoil > apres || apresFoil < 0)
            throw new IllegalArgumentException("L'import dépasserait la limite de 9999 exemplaires pour une version.");
        return new Cible(carteId, avant, avantFoil, apres, apresFoil);
    }

    @Transactional
    public Bilan confirmer(Long utilisateurId, Preparation plan, boolean accepterPartiel) {
        if (plan == null || !plan.utilisateurId().equals(utilisateurId) || Instant.now().isAfter(plan.expiration()))
            throw new IllegalArgumentException("Cet aperçu a expiré. Sélectionnez à nouveau le fichier.");
        Utilisateur utilisateur = utilisateur(utilisateurId);
        entityManager.lock(utilisateur, LockModeType.PESSIMISTIC_WRITE);
        if (plan.partiel() && !accepterPartiel)
            throw new IllegalArgumentException("Confirmez explicitement l'import partiel des lignes prêtes.");
        if (plan.pretes() == 0) throw new IllegalArgumentException("Aucune ligne prête à importer.");
        Preparation actuel = preparer(utilisateurId, plan.releve(), plan.mode());
        if (!actuel.lignes().equals(plan.lignes()))
            throw new IllegalArgumentException("Votre collection ou le catalogue a changé depuis l'aperçu. Analysez à nouveau le fichier avant de confirmer.");
        Map<Long, Possession> stock = chargerStock(utilisateurId);
        for (var ligne : plan.lignes())
            if (ligne.prete()) for (Cible cible : ligne.cibles()) {
                if (!cible.modifiee()) continue;
                Possession possession = stock.get(cible.carteId());
                if (cible.apres() == 0) {
                    if (possession != null) possessions.delete(possession);
                    continue;
                }
                if (possession == null) {
                    possession = new Possession(utilisateur, entityManager.getReference(Carte.class, cible.carteId()));
                    possessions.save(possession);
                }
                possession.definirQuantites(cible.apres(), cible.apresFoil());
            }
        possessions.flush();
        return new Bilan(plan.changements(), plan.exemplaires(), plan.mode());
    }

    private Utilisateur utilisateur(Long id) {
        var u = utilisateurs.findById(id).orElseThrow(() -> new AccessDeniedException("Compte introuvable."));
        if (!u.isActif()) throw new AccessDeniedException("Compte désactivé.");
        return u;
    }

    private Map<Long, Possession> chargerStock(Long id) {
        return entityManager.createQuery("select p from Possession p join fetch p.carte where p.utilisateur.id=:id", Possession.class).setParameter("id", id).getResultList().stream().collect(Collectors.toMap(p -> p.getCarte().getId(), p -> p));
    }

    private static String cle(String code, String numero) {
        try {
            return code.toUpperCase(Locale.ROOT) + ":" + Integer.parseInt(numero);
        } catch (NumberFormatException e) {
            return code + ":" + numero;
        }
    }
}
