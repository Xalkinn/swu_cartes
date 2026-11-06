package fr.xalkinn.swu.deck;

import fr.xalkinn.swu.carte.*;
import fr.xalkinn.swu.collection.Possession;
import fr.xalkinn.swu.collection.PossessionRepository;
import fr.xalkinn.swu.utilisateur.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.criteria.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeckService {
    public enum Zone { PRINCIPAL, LEADER, BASE }
    private final DeckRepository decks;
    private final DeckCarteRepository lignes;
    private final CarteRepository cartes;
    private final UtilisateurRepository utilisateurs;
    private final PossessionRepository possessions;

    public DeckService(DeckRepository decks, DeckCarteRepository lignes, CarteRepository cartes,
                       UtilisateurRepository utilisateurs, PossessionRepository possessions) {
        this.decks = decks; this.lignes = lignes; this.cartes = cartes;
        this.utilisateurs = utilisateurs; this.possessions = possessions;
    }

    @Transactional(readOnly = true)
    public Page<Resume> lister(Long utilisateurId, int page) {
        utilisateur(utilisateurId);
        Page<Deck> resultat = decks.findByUtilisateurIdOrderByDateModificationDescIdDesc(
                utilisateurId, PageRequest.of(Math.max(0, page), 12));
        if (resultat.getTotalPages() > 0 && resultat.getNumber() >= resultat.getTotalPages()) {
            resultat = decks.findByUtilisateurIdOrderByDateModificationDescIdDesc(
                    utilisateurId, PageRequest.of(resultat.getTotalPages() - 1, 12));
        }
        Map<Long, Integer> nombres = new HashMap<>();
        if (!resultat.isEmpty()) {
            for (Object[] compte : lignes.compter(resultat.map(Deck::getId).getContent())) {
                nombres.put((Long) compte[0], ((Number) compte[1]).intValue());
            }
        }
        return resultat.map(d -> new Resume(d.getId(), d.getNom(), d.getDescription(),
                fiche(d.getLeader()), fiche(d.getBase()), nombres.getOrDefault(d.getId(), 0)));
    }

    @Transactional
    public Long creer(Long utilisateurId, String nom, String description) {
        return decks.save(new Deck(utilisateur(utilisateurId), nom, description)).getId();
    }

    @Transactional
    public void renommer(Long utilisateurId, Long id, String nom, String description) {
        verrouiller(utilisateurId, id).renommer(nom, description);
    }

    @Transactional
    public void supprimer(Long utilisateurId, Long id) {
        Deck deck = verrouiller(utilisateurId, id);
        lignes.supprimerLignes(id);
        decks.delete(deck);
    }

    @Transactional
    public void choisir(Long utilisateurId, Long id, Long carteId, Zone zone) {
        if (zone != Zone.LEADER && zone != Zone.BASE) throw new IllegalArgumentException("Emplacement invalide.");
        Deck deck = verrouiller(utilisateurId, id);
        Carte carte = carteId == null ? null : standard(carteId);
        if (carte != null) {
            String typeAttendu = zone == Zone.LEADER ? "Leader" : "Base";
            if (!typeAttendu.equals(carte.getTypeCarte())) throw new IllegalArgumentException("Choisissez une carte de type " + typeAttendu + ".");
        }
        if (zone == Zone.LEADER) deck.choisirLeader(carte); else deck.choisirBase(carte);
    }

    @Transactional
    public void ajouter(Long utilisateurId, Long id, Long carteId, int quantite) {
        if (quantite < 1 || quantite > 99) throw new IllegalArgumentException("Choisissez une quantité comprise entre 1 et 99.");
        Deck deck = verrouiller(utilisateurId, id);
        Carte carte = standard(carteId);
        if (carte.getTypeCarte() == null) throw new IllegalArgumentException("Actualisez cette extension pour renseigner le type des cartes.");
        if ("Leader".equals(carte.getTypeCarte()) || "Base".equals(carte.getTypeCarte())) {
            throw new IllegalArgumentException("Utilisez l'emplacement leader ou base pour cette carte.");
        }
        DeckCarte ligne = lignes.findByDeckIdAndCarteId(id, carte.getId()).orElse(null);
        if (ligne == null) {
            if (lignes.findByDeckIdOrderByCarteCoutAscCarteTitreAscCarteIdAsc(id).size() >= 300) {
                throw new IllegalArgumentException("Un deck peut contenir au maximum 300 références différentes.");
            }
            lignes.save(new DeckCarte(deck, carte, quantite));
        } else {
            ligne.modifierQuantite(ligne.getQuantite() + quantite);
        }
        deck.toucher();
    }

    @Transactional
    public void quantite(Long utilisateurId, Long id, Long carteId, int quantite) {
        if (quantite < 0 || quantite > 99) throw new IllegalArgumentException("La quantité doit être comprise entre 0 et 99 (0 pour retirer).");
        Deck deck = verrouiller(utilisateurId, id);
        DeckCarte ligne = lignes.findByDeckIdAndCarteId(id, carteId)
                .orElseThrow(() -> new IllegalArgumentException("Cette carte n'est pas dans votre deck."));
        if (quantite == 0) lignes.delete(ligne); else ligne.modifierQuantite(quantite);
        deck.toucher();
    }

    @Transactional(readOnly = true)
    public Vue consulter(Long utilisateurId, Long id) {
        Utilisateur utilisateur = utilisateur(utilisateurId);
        Deck deck = trouver(utilisateurId, id);
        List<DeckCarte> contenu = lignes.findByDeckIdOrderByCarteCoutAscCarteTitreAscCarteIdAsc(id);
        Set<Long> ids = contenu.stream().map(l -> l.getCarte().getId()).collect(Collectors.toCollection(LinkedHashSet::new));
        if (deck.getLeader() != null) ids.add(deck.getLeader().getId());
        if (deck.getBase() != null) ids.add(deck.getBase().getId());
        Map<Long, Integer> stocks = stocks(utilisateur.getEmail(), ids);
        List<Ligne> liste = contenu.stream().map(l -> ligne(l.getCarte(), l.getQuantite(), stocks)).toList();
        Ligne leader = deck.getLeader() == null ? null : ligne(deck.getLeader(), 1, stocks);
        Ligne base = deck.getBase() == null ? null : ligne(deck.getBase(), 1, stocks);
        int total = liste.stream().mapToInt(Ligne::quantite).sum();
        int manquantes = liste.stream().mapToInt(Ligne::manquantes).sum()
                + (leader == null ? 0 : leader.manquantes()) + (base == null ? 0 : base.manquantes());
        int necessaires = total + (leader == null ? 0 : 1) + (base == null ? 0 : 1);
        List<Ligne> aTrouver = new ArrayList<>();
        if (leader != null && leader.manquantes() > 0) aTrouver.add(leader);
        if (base != null && base.manquantes() > 0) aTrouver.add(base);
        liste.stream().filter(l -> l.manquantes() > 0).forEach(aTrouver::add);
        return new Vue(deck.getId(), deck.getNom(), deck.getDescription(), leader, base, liste,
                total, manquantes, necessaires - manquantes, necessaires,
                necessaires == 0 ? 0 : (necessaires - manquantes) * 100 / necessaires,
                List.copyOf(aTrouver), cartes.countByTypeCarteIsNull() > 0);
    }

    @Transactional(readOnly = true)
    public Page<ResultatCarte> rechercher(Long utilisateurId, Long id, String q, Long extensionId,
                                          Integer coutMin, Integer coutMax, List<String> affinites,
                                          String mode, Zone zone, int page) {
        Utilisateur utilisateur = utilisateur(utilisateurId);
        trouver(utilisateurId, id);
        String recherche = q == null ? "" : q.strip();
        if (recherche.length() > 100) throw new IllegalArgumentException("Recherche limitée à 100 caractères.");
        if ((coutMin != null && (coutMin < 0 || coutMin > 9999)) || (coutMax != null && (coutMax < 0 || coutMax > 9999))
                || (coutMin != null && coutMax != null && coutMin > coutMax)) throw new IllegalArgumentException("Vérifiez les coûts minimum et maximum.");
        if (affinites.size() > 6 || !List.of("TOUTES", "UNE", "SANS").contains(mode)) throw new IllegalArgumentException("Filtres d'affinités invalides.");
        for (String code : affinites) {
            try { Affinite.valueOf(code); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Affinité inconnue."); }
        }
        Specification<Carte> specification = (c, requete, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.isFalse(c.get("hyperspace")));
            conditions.add(cb.isNull(c.get("carteStandard")));
            conditions.add(cb.isNotNull(c.get("typeCarte")));
            if (zone == Zone.LEADER || zone == Zone.BASE) {
                conditions.add(cb.equal(c.get("typeCarte"), zone == Zone.LEADER ? "Leader" : "Base"));
            } else {
                conditions.add(cb.not(c.get("typeCarte").in("Leader", "Base")));
            }
            if (!recherche.isEmpty()) {
                String motif = "%" + recherche.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                Predicate texte = cb.or(cb.like(cb.lower(c.get("titre")), motif, '\\'), cb.like(cb.lower(c.get("sousTitre")), motif, '\\'));
                if (recherche.matches("[0-9]{1,6}")) texte = cb.or(texte, cb.equal(c.get("numero"), Integer.parseInt(recherche)));
                conditions.add(texte);
            }
            if (extensionId != null) conditions.add(cb.equal(c.get("extension").get("id"), extensionId));
            if (coutMin != null) conditions.add(cb.ge(c.get("cout"), coutMin));
            if (coutMax != null) conditions.add(cb.le(c.get("cout"), coutMax));
            if ("SANS".equals(mode)) conditions.add(cb.isEmpty(c.get("affinites")));
            else if (!affinites.isEmpty()) {
                List<Predicate> symboles = new ArrayList<>();
                for (String code : affinites) {
                    Subquery<Integer> sq = requete.subquery(Integer.class);
                    Root<Carte> racine = sq.correlate(c);
                    MapJoin<Carte, String, Integer> affinite = racine.joinMap("affinites");
                    sq.select(cb.literal(1)).where(cb.equal(affinite.key(), code));
                    symboles.add(cb.exists(sq));
                }
                conditions.add("UNE".equals(mode) ? cb.or(symboles.toArray(Predicate[]::new)) : cb.and(symboles.toArray(Predicate[]::new)));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
        Sort tri = Sort.by("extension.code", "numero", "id");
        Page<Carte> resultat = cartes.findAll(specification, PageRequest.of(Math.max(0, page), 12, tri));
        if (resultat.getTotalPages() > 0 && resultat.getNumber() >= resultat.getTotalPages()) {
            resultat = cartes.findAll(specification, PageRequest.of(resultat.getTotalPages() - 1, 12, tri));
        }
        Map<Long, Integer> stocks = stocks(utilisateur.getEmail(), resultat.stream().map(Carte::getId).toList());
        Map<Long, Integer> presentes = lignes.findByDeckIdOrderByCarteCoutAscCarteTitreAscCarteIdAsc(id).stream()
                .collect(Collectors.toMap(l -> l.getCarte().getId(), DeckCarte::getQuantite));
        return resultat.map(c -> new ResultatCarte(fiche(c), stocks.getOrDefault(c.getId(), 0), presentes.getOrDefault(c.getId(), 0)));
    }

    private Carte standard(Long id) {
        if (id == null) throw new IllegalArgumentException("Choisissez une carte.");
        Carte carte = cartes.findById(id).orElseThrow(() -> new IllegalArgumentException("Carte introuvable."));
        if (carte.getCarteStandard() != null) carte = carte.getCarteStandard();
        if (carte.isHyperspace() || carte.getCarteStandard() != null) throw new IllegalArgumentException("La variante n'est pas reliée à une carte standard. Actualisez l'extension.");
        return carte;
    }
    private Utilisateur utilisateur(Long id) {
        Utilisateur u = utilisateurs.findById(id).orElseThrow(() -> new AccessDeniedException("Compte introuvable."));
        if (!u.isActif()) throw new AccessDeniedException("Compte désactivé.");
        return u;
    }
    private Deck trouver(Long utilisateurId, Long id) {
        return decks.findByIdAndUtilisateurId(id, utilisateurId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck introuvable."));
    }
    private Deck verrouiller(Long utilisateurId, Long id) {
        utilisateur(utilisateurId);
        return decks.verrouiller(id, utilisateurId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck introuvable."));
    }
    private Map<Long, Integer> stocks(String email, Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, Integer> resultat = new HashMap<>();
        for (Possession p : possessions.trouverPossessionsDesGroupes(email, List.copyOf(ids))) {
            Carte c = p.getCarte();
            Long reference = c.getCarteStandard() == null ? c.getId() : c.getCarteStandard().getId();
            // La quantité totale contient déjà les foils : ne pas les additionner une seconde fois.
            resultat.merge(reference, p.getQuantite(), Integer::sum);
        }
        return resultat;
    }
    private Ligne ligne(Carte c, int quantite, Map<Long, Integer> stocks) {
        int stock = stocks.getOrDefault(c.getId(), 0);
        return new Ligne(fiche(c), quantite, stock, Math.max(0, quantite - stock));
    }
    private Fiche fiche(Carte c) {
        return c == null ? null : new Fiche(c.getId(), c.getTitre(), c.getSousTitre(),
                c.getExtension().getCode() + " · " + c.getNumeroAffiche(), c.getImageRecto(), c.getCout(), c.getTypeCarte());
    }
    public record Fiche(Long id, String titre, String sousTitre, String reference, String image, Integer cout, String type) {}
    public record Resume(Long id, String nom, String description, Fiche leader, Fiche base, int total) {}
    public record Ligne(Fiche carte, int quantite, int possedees, int manquantes) {}
    public record ResultatCarte(Fiche carte, int possedees, int dansDeck) {}
    public record Vue(Long id, String nom, String description, Ligne leader, Ligne base, List<Ligne> lignes,
                      int total, int manquantes, int disponibles, int necessaires, int progression,
                      List<Ligne> aTrouver, boolean typesManquants) {}
}
