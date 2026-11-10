package fr.xalkinn.swu.tournoi;

import fr.xalkinn.swu.utilisateur.Utilisateur;
import fr.xalkinn.swu.utilisateur.UtilisateurRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class TournoiService {
    @PersistenceContext
    private EntityManager em;
    private final UtilisateurRepository utilisateurs;

    public TournoiService(UtilisateurRepository utilisateurs) {
        this.utilisateurs = utilisateurs;
    }

    private Utilisateur actif(Long id) {
        var u = utilisateurs.findById(id).orElseThrow(() -> new AccessDeniedException("Connexion requise."));
        if (!u.isActif()) throw new AccessDeniedException("Compte désactivé.");
        return u;
    }

    public boolean administrateur(Long id) {
        return id != null && "ADMIN".equals(actif(id).getRole());
    }

    private void admin(Long id) {
        if (!administrateur(id)) throw new AccessDeniedException("Administration réservée aux administrateurs.");
    }

    private Tournoi trouver(Long id, boolean verrou) {
        Tournoi t = verrou ? em.find(Tournoi.class, id, LockModeType.PESSIMISTIC_WRITE) : em.find(Tournoi.class, id);
        if (t == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournoi introuvable.");
        return t;
    }

    private List<InscriptionTournoi> inscrits(Long id) {
        return em.createQuery("select i from InscriptionTournoi i join fetch i.utilisateur where i.tournoi.id=:id order by i.date,i.id", InscriptionTournoi.class).setParameter("id", id).getResultList();
    }

    private int nombreInscrits(Long id) {
        return em.createQuery("select count(i) from InscriptionTournoi i where i.tournoi.id=:id", Long.class).setParameter("id", id).getSingleResult().intValue();
    }

    private List<MatchTournoi> matchs(Long id) {
        return em.createQuery("select m from MatchTournoi m left join fetch m.joueur1 left join fetch m.joueur2 left join fetch m.vainqueur where m.tournoi.id=:id order by m.ronde,m.position", MatchTournoi.class).setParameter("id", id).getResultList();
    }

    public Page<Resume> lister(Long acteur, String filtre, int page) {
        boolean admin = administrateur(acteur);
        if (acteur == null) throw new AccessDeniedException("Connexion requise.");
        if (!List.of("TOUS", "OUVERT", "EN_COURS", "TERMINE").contains(filtre))
            throw new IllegalArgumentException("Filtre de tournois invalide.");
        String clause = " where (:admin=true or t.etat<>:brouillon)" + (filtre.equals("TOUS") ? "" : " and t.etat=:etat");
        var count = em.createQuery("select count(t) from Tournoi t" + clause, Long.class).setParameter("admin", admin).setParameter("brouillon", Tournoi.Etat.BROUILLON);
        if (!filtre.equals("TOUS")) count.setParameter("etat", Tournoi.Etat.valueOf(filtre));
        long total = count.getSingleResult();
        int index = Math.min(Math.max(0, page), total == 0 ? 0 : (int) ((total - 1) / 12));
        var query = em.createQuery("select t from Tournoi t" + clause + " order by t.creation desc,t.id desc", Tournoi.class).setParameter("admin", admin).setParameter("brouillon", Tournoi.Etat.BROUILLON);
        if (!filtre.equals("TOUS")) query.setParameter("etat", Tournoi.Etat.valueOf(filtre));
        List<Resume> lignes = query.setFirstResult(index * 12).setMaxResults(12).getResultList().stream().map(t -> resume(t, nombreInscrits(t.id))).toList();
        return new PageImpl<>(lignes, PageRequest.of(index, 12), total);
    }

    public Vue consulter(Long id, Long acteur) {
        boolean admin = administrateur(acteur);
        actif(acteur);
        Tournoi t = trouver(id, false);
        if (t.etat == Tournoi.Etat.BROUILLON && !admin)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournoi introuvable.");
        var inscrits = inscrits(id);
        var ms = matchs(id);
        int nombreRondes = ms.stream().mapToInt(m -> m.ronde).max().orElse(0);
        List<Ronde> rondes = new ArrayList<>();
        for (int r = 1; r <= nombreRondes; r++) {
            final int numero = r;
            String nom = r == nombreRondes ? "Finale" : r == nombreRondes - 1 ? "Demi-finales" : r == nombreRondes - 2 ? "Quarts de finale" : "Tour " + r;
            rondes.add(new Ronde(r, nom, ms.stream().filter(m -> m.ronde == numero).map(m -> new Rencontre(m.id, m.ronde, m.position, pseudo(m.joueur1), pseudo(m.joueur2), id(m.joueur1), id(m.joueur2), m.score1, m.score2, id(m.vainqueur), m.etat.name())).toList()));
        }
        String champion = nombreRondes == 0 ? null : pseudo(ms.get(ms.size() - 1).vainqueur);
        boolean inscrit = inscrits.stream().anyMatch(i -> i.utilisateur.getId().equals(acteur));
        return new Vue(resume(t, inscrits.size()), t.description, t.recompenses, t.conditions, admin, inscrit, inscrits.stream().map(i -> new Participant(i.utilisateur.getId(), i.utilisateur.getPseudo())).toList(), rondes, champion);
    }

    private static String pseudo(Utilisateur u) {
        return u == null ? null : u.getPseudo();
    }

    private static Long id(Utilisateur u) {
        return u == null ? null : u.getId();
    }

    private static Resume resume(Tournoi t, int inscrits) {
        return new Resume(t.id, t.nom, t.etat.name(), libelle(t.etat), t.capacite, inscrits, t.meilleurDes, t.debut == null ? "À définir" : t.debut.format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm")));
    }

    private static String libelle(Tournoi.Etat e) {
        return switch (e) {
            case BROUILLON -> "Brouillon";
            case OUVERT -> "Inscriptions ouvertes";
            case FERME -> "Inscriptions fermées";
            case EN_COURS -> "En cours";
            case TERMINE -> "Terminé";
            case ANNULE -> "Annulé";
        };
    }

    public Formulaire formulaire(Long acteur, Long id) {
        admin(acteur);
        if (id == null) return new Formulaire("", "", "", "", 16, 3, "");
        Tournoi t = trouver(id, false);
        return new Formulaire(t.nom, t.description, t.recompenses, t.conditions, t.capacite, t.meilleurDes, t.debut == null ? "" : t.debut.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long enregistrer(Long acteur, Long id, Formulaire f) {
        admin(acteur);
        Tournoi t = id == null ? new Tournoi() : trouver(id, true);
        String nom = f.nom().strip();
        if (nom.isBlank() || nom.length() > 100)
            throw new IllegalArgumentException("Le nom doit contenir de 1 à 100 caractères.");
        if (f.description().length() > 5000 || f.recompenses().length() > 3000 || f.conditions().isBlank() || f.conditions().length() > 5000)
            throw new IllegalArgumentException("Renseignez les conditions (5 000 caractères maximum). Description : 5 000, récompenses : 3 000 caractères maximum.");
        if (f.capacite() < 2 || f.capacite() > 128 || !List.of(1, 3, 5).contains(f.meilleurDes()))
            throw new IllegalArgumentException("Choisissez 2 à 128 places et un format BO1, BO3 ou BO5.");
        LocalDateTime debut = null;
        try {
            if (!f.debut().isBlank()) debut = LocalDateTime.parse(f.debut());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Date du tournoi invalide.");
        }
        if (debut != null && (debut.getYear() < 1000 || debut.getYear() > 9999))
            throw new IllegalArgumentException("La date doit être comprise entre les années 1000 et 9999.");
        int inscrits = id == null ? 0 : nombreInscrits(id);
        if (f.capacite() < inscrits)
            throw new IllegalArgumentException("La capacité ne peut pas être inférieure au nombre d'inscrits.");
        if (inscrits > 0 && (!f.conditions().equals(t.conditions) || f.meilleurDes() != t.meilleurDes))
            throw new IllegalArgumentException("Les conditions et le format sont figés dès la première inscription.");
        if (id != null && List.of(Tournoi.Etat.EN_COURS, Tournoi.Etat.TERMINE, Tournoi.Etat.ANNULE).contains(t.etat) && f.capacite() != t.capacite)
            throw new IllegalArgumentException("La capacité est figée après le démarrage ou l'annulation.");
        t.nom = nom;
        t.description = f.description();
        t.recompenses = f.recompenses();
        t.conditions = f.conditions();
        t.capacite = f.capacite();
        t.meilleurDes = f.meilleurDes();
        t.debut = debut;
        if (id == null) {
            t.createurId = acteur;
            em.persist(t);
        }
        em.flush();
        return t.id;
    }

    // Le verrou du tournoi et READ_COMMITTED sérialisent les inscriptions sans snapshot périmé sous MySQL.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void inscrire(Long acteur, Long id, boolean accepte) {
        Utilisateur u = actif(acteur);
        Tournoi t = trouver(id, true);
        if (t.etat != Tournoi.Etat.OUVERT) throw new IllegalArgumentException("Les inscriptions ne sont pas ouvertes.");
        if (!accepte) throw new IllegalArgumentException("Acceptez les conditions du tournoi pour vous inscrire.");
        var liste = inscrits(id);
        if (liste.stream().anyMatch(i -> i.utilisateur.getId().equals(acteur))) return;
        if (liste.size() >= t.capacite) throw new IllegalArgumentException("Ce tournoi est complet.");
        em.persist(new InscriptionTournoi(t, u));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void retirer(Long acteur, Long id, Long cible) {
        actif(acteur);
        if (!acteur.equals(cible)) admin(acteur);
        Tournoi t = trouver(id, true);
        if (!List.of(Tournoi.Etat.BROUILLON, Tournoi.Etat.OUVERT, Tournoi.Etat.FERME).contains(t.etat))
            throw new IllegalArgumentException("Les inscriptions sont figées après le démarrage.");
        inscrits(id).stream().filter(i -> i.utilisateur.getId().equals(cible)).findFirst().ifPresent(em::remove);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void etat(Long acteur, Long id, String action) {
        admin(acteur);
        Tournoi t = trouver(id, true);
        switch (action) {
            case "ouvrir" -> {
                if (!List.of(Tournoi.Etat.BROUILLON, Tournoi.Etat.FERME).contains(t.etat))
                    throw new IllegalArgumentException("Impossible d'ouvrir ces inscriptions.");
                t.etat = Tournoi.Etat.OUVERT;
            }
            case "fermer" -> {
                if (t.etat != Tournoi.Etat.OUVERT)
                    throw new IllegalArgumentException("Les inscriptions ne sont pas ouvertes.");
                t.etat = Tournoi.Etat.FERME;
            }
            case "annuler" -> {
                if (t.etat == Tournoi.Etat.TERMINE || t.etat == Tournoi.Etat.ANNULE)
                    throw new IllegalArgumentException("Ce tournoi est déjà terminé ou annulé.");
                t.etat = Tournoi.Etat.ANNULE;
            }
            default -> throw new IllegalArgumentException("Action inconnue.");
        }
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void demarrer(Long acteur, Long id) {
        admin(acteur);
        Tournoi t = trouver(id, true);
        if (!List.of(Tournoi.Etat.OUVERT, Tournoi.Etat.FERME).contains(t.etat))
            throw new IllegalArgumentException("Ouvrez ou fermez les inscriptions avant de démarrer.");
        var inscrits = inscrits(id);
        if (inscrits.size() < 2) throw new IllegalArgumentException("Il faut au moins deux joueurs inscrits.");
        if (inscrits.stream().anyMatch(i -> !i.utilisateur.isActif()))
            throw new IllegalArgumentException("Retirez les comptes désactivés avant le tirage.");
        var joueurs = new ArrayList<>(inscrits.stream().map(i -> i.utilisateur).toList());
        Collections.shuffle(joueurs, new SecureRandom());
        int taille = 2;
        while (taille < joueurs.size()) taille *= 2;
        List<Integer> ordre = new ArrayList<>(List.of(1, 2));
        for (int n = 4; n <= taille; n *= 2) {
            List<Integer> suivant = new ArrayList<>();
            for (int seed : ordre) {
                suivant.add(seed);
                suivant.add(n + 1 - seed);
            }
            ordre = suivant;
        }
        List<MatchTournoi> ms = new ArrayList<>();
        int ronde = 1;
        for (int nb = taille / 2; nb >= 1; nb /= 2, ronde++)
            for (int p = 0; p < nb; p++) {
                var m = new MatchTournoi(t, ronde, p);
                if (ronde == 1) {
                    int a = ordre.get(p * 2) - 1, b = ordre.get(p * 2 + 1) - 1;
                    m.joueur1 = a < joueurs.size() ? joueurs.get(a) : null;
                    m.joueur2 = b < joueurs.size() ? joueurs.get(b) : null;
                    preparer(m);
                }
                em.persist(m);
                ms.add(m);
            }
        t.etat = Tournoi.Etat.EN_COURS;
        propager(t, ms);
        em.flush();
    }

    private static void preparer(MatchTournoi m) {
        if (m.joueur1 != null && m.joueur2 != null) m.etat = MatchTournoi.Etat.PRET;
        else if (m.joueur1 != null || m.joueur2 != null) {
            m.vainqueur = m.joueur1 != null ? m.joueur1 : m.joueur2;
            m.etat = MatchTournoi.Etat.EXEMPT;
        } else m.etat = MatchTournoi.Etat.ATTENTE;
    }

    private static void propager(Tournoi t, List<MatchTournoi> ms) {
        Map<String, MatchTournoi> carte = new HashMap<>();
        for (var m : ms) carte.put(m.ronde + ":" + m.position, m);
        for (var m : ms)
            if (m.ronde > 1 && !m.resolu()) {
                var a = carte.get((m.ronde - 1) + ":" + (m.position * 2));
                var b = carte.get((m.ronde - 1) + ":" + (m.position * 2 + 1));
                m.joueur1 = a.resolu() ? a.vainqueur : null;
                m.joueur2 = b.resolu() ? b.vainqueur : null;
                if (a.resolu() && b.resolu()) preparer(m);
            }
        var finale = ms.get(ms.size() - 1);
        if (finale.etat == MatchTournoi.Etat.TERMINE) t.etat = Tournoi.Etat.TERMINE;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void resultat(Long acteur, Long tournoiId, Long matchId, int a, int b) {
        admin(acteur);
        Tournoi t = trouver(tournoiId, true);
        if (t.etat != Tournoi.Etat.EN_COURS) throw new IllegalArgumentException("Le tournoi n'est pas en cours.");
        var ms = matchs(tournoiId);
        var m = ms.stream().filter(x -> x.id.equals(matchId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Match introuvable dans ce tournoi."));
        if (m.etat != MatchTournoi.Etat.PRET)
            throw new IllegalArgumentException("Ce match n'est pas prêt ou possède déjà un résultat.");
        int cible = t.meilleurDes / 2 + 1;
        if (a < 0 || b < 0 || !((a == cible && b < cible) || (b == cible && a < cible)))
            throw new IllegalArgumentException("Le score doit désigner un vainqueur au format BO" + t.meilleurDes + ".");
        m.score1 = a;
        m.score2 = b;
        m.vainqueur = a > b ? m.joueur1 : m.joueur2;
        m.etat = MatchTournoi.Etat.TERMINE;
        propager(t, ms);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void effacerResultat(Long acteur, Long tournoiId, Long matchId) {
        admin(acteur);
        Tournoi t = trouver(tournoiId, true);
        if (!List.of(Tournoi.Etat.EN_COURS, Tournoi.Etat.TERMINE).contains(t.etat))
            throw new IllegalArgumentException("Ce tournoi ne permet pas la correction des résultats.");
        var ms = matchs(tournoiId);
        var m = ms.stream().filter(x -> x.id.equals(matchId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Match introuvable."));
        if (m.etat != MatchTournoi.Etat.TERMINE)
            throw new IllegalArgumentException("Ce match n'a pas de résultat à effacer.");
        int position = m.position / 2;
        List<MatchTournoi> descendants = new ArrayList<>();
        for (int r = m.ronde + 1; r <= ms.get(ms.size() - 1).ronde; r++) {
            final int rr = r, pp = position;
            var d = ms.stream().filter(x -> x.ronde == rr && x.position == pp).findFirst().orElseThrow();
            if (d.score1 != null)
                throw new IllegalArgumentException("Un match suivant possède déjà un résultat. Corrigez d'abord les tours suivants.");
            descendants.add(d);
            position /= 2;
        }
        for (var d : descendants) {
            d.joueur1 = null;
            d.joueur2 = null;
            d.vainqueur = null;
            d.score1 = null;
            d.score2 = null;
            d.etat = MatchTournoi.Etat.ATTENTE;
        }
        m.vainqueur = null;
        m.score1 = null;
        m.score2 = null;
        m.etat = MatchTournoi.Etat.PRET;
        t.etat = Tournoi.Etat.EN_COURS;
        propager(t, ms);
    }

    public record Formulaire(String nom, String description, String recompenses, String conditions, int capacite,
                             int meilleurDes, String debut) {
    }

    public record Resume(Long id, String nom, String etat, String etatLibelle, int capacite, int inscrits,
                         int meilleurDes, String debut) {
    }

    public record Participant(Long id, String pseudo) {
    }

    public record Rencontre(Long id, int ronde, int position, String joueur1, String joueur2, Long joueur1Id,
                            Long joueur2Id, Integer score1, Integer score2, Long vainqueurId, String etat) {
    }

    public record Ronde(int numero, String nom, List<Rencontre> matchs) {
    }

    public record Vue(Resume tournoi, String description, String recompenses, String conditions, boolean administrateur,
                      boolean inscrit, List<Participant> participants, List<Ronde> rondes, String champion) {
    }
}
