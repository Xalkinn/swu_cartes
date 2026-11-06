package fr.xalkinn.swu.administration;

import fr.xalkinn.swu.utilisateur.Utilisateur;
import fr.xalkinn.swu.utilisateur.UtilisateurRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class AdministrationUtilisateursService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.FRANCE)
            .withZone(ZoneId.of("Europe/Paris"));
    private final UtilisateurRepository repository;
    @PersistenceContext
    private EntityManager entityManager;

    public AdministrationUtilisateursService(UtilisateurRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ListeUtilisateurs consulter(Long acteurId, String recherche, String role, String etat, int page) {
        verifierAdministrateur(acteurId);
        String q = recherche == null ? "" : recherche.strip();
        if (q.length() > 100) throw new IllegalArgumentException("Recherche trop longue.");
        if (!role.isEmpty() && !"ADMIN".equals(role) && !"USER".equals(role)) {
            throw new IllegalArgumentException("Filtre de rôle invalide.");
        }
        if (!etat.isEmpty() && !"actif".equals(etat) && !"inactif".equals(etat)) {
            throw new IllegalArgumentException("Filtre d'état invalide.");
        }
        Boolean actif = etat.isEmpty() ? null : "actif".equals(etat);
        Sort tri = Sort.by(Sort.Order.asc("pseudo").ignoreCase(), Sort.Order.asc("id"));
        Page<Utilisateur> utilisateurs = repository.rechercherPourAdministration(q,
                role.isEmpty() ? null : role, actif, PageRequest.of(Math.max(0, page), 20, tri));
        if (utilisateurs.getTotalPages() > 0 && utilisateurs.getNumber() >= utilisateurs.getTotalPages()) {
            utilisateurs = repository.rechercherPourAdministration(q, role.isEmpty() ? null : role, actif,
                    PageRequest.of(utilisateurs.getTotalPages() - 1, 20, tri));
        }
        long adminsActifs = repository.countByRoleAndActif("ADMIN", true);
        Page<UtilisateurAdmin> resultat = utilisateurs.map(utilisateur -> new UtilisateurAdmin(
                utilisateur.getId(), utilisateur.getPseudo(), utilisateur.getEmail(), utilisateur.getRole(),
                utilisateur.isActif(), utilisateur.getDateCreation() == null ? null : DATE.format(utilisateur.getDateCreation()),
                utilisateur.getId().equals(acteurId), adminsActifs == 1 && utilisateur.isActif()
                    && "ADMIN".equals(utilisateur.getRole())));
        return new ListeUtilisateurs(resultat, repository.count(), repository.countByActif(true), adminsActifs);
    }

    @Transactional
    public ModificationAcces modifier(Long acteurId, Long cibleId, String role, boolean actif) {
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            throw new IllegalArgumentException("Choisissez un rôle USER ou ADMIN.");
        }

        // Premier accès à la base de cette transaction : toutes les modifications
        // de droits passent par le même verrou, même avec plusieurs instances de l'application.
        entityManager.createNativeQuery(
                "SELECT id FROM verrous_administration WHERE id = 1 FOR UPDATE").getSingleResult();

        // Recontrôle en base : le filtre n'est pas le seul contrôle d'autorisation.
        verifierAdministrateur(acteurId);
        Utilisateur cible = repository.findById(cibleId)
                .orElseThrow(() -> new IllegalArgumentException("Compte introuvable."));
        // Coordonne aussi cette écriture avec les modifications du profil personnel.
        entityManager.refresh(cible, LockModeType.PESSIMISTIC_WRITE);

        boolean retirerUnAdminActif = cible.isActif() && "ADMIN".equals(cible.getRole())
                && (!actif || !"ADMIN".equals(role));
        if (retirerUnAdminActif && repository.countByRoleAndActif("ADMIN", true) <= 1) {
            throw new IllegalArgumentException(
                    "Le dernier administrateur actif doit conserver son rôle et son accès. "
                            + "Nommez d'abord un autre administrateur actif.");
        }

        boolean modifie = !cible.getRole().equals(role) || cible.isActif() != actif;
        cible.modifierAcces(role, actif);
        repository.flush();
        return new ModificationAcces(cible.getPseudo(), modifie, cibleId.equals(acteurId), role, actif);
    }

    private void verifierAdministrateur(Long id) {
        Utilisateur acteur = repository.findById(id)
                .orElseThrow(() -> new AccessDeniedException("Accès administrateur requis."));
        if (!acteur.isActif() || !"ADMIN".equals(acteur.getRole())) {
            throw new AccessDeniedException("Accès administrateur requis.");
        }
    }

    public record UtilisateurAdmin(Long id, String pseudo, String email, String role, boolean actif,
                                   String dateCreation, boolean moi, boolean dernierAdministrateurActif) {}
    public record ListeUtilisateurs(Page<UtilisateurAdmin> utilisateurs, long total, long actifs, long adminsActifs) {}
    public record ModificationAcces(String pseudo, boolean modifie, boolean moi, String role, boolean actif) {}
}
