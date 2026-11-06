package fr.xalkinn.swu.compte;

import fr.xalkinn.swu.utilisateur.Utilisateur;
import fr.xalkinn.swu.utilisateur.UtilisateurRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@Service
public class CompteService {

    private static final DateTimeFormatter DATE_CREATION = DateTimeFormatter
            .ofPattern("d MMMM uuuu", Locale.FRANCE)
            .withZone(ZoneId.of("Europe/Paris"));

    private final UtilisateurRepository repository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    public CompteService(UtilisateurRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public CompteVue consulter(String emailSession) {
        Utilisateur utilisateur = trouverUtilisateur(emailSession);
        return new CompteVue(
                utilisateur.getPseudo(), utilisateur.getEmail(), utilisateur.getRole(),
                utilisateur.getDateCreation() == null ? null
                        : DATE_CREATION.format(utilisateur.getDateCreation())
        );
    }

    @Transactional
    public ResultatModification modifier(String emailSession, CompteForm formulaire) {
        // L'identité vient exclusivement de la session de connexion.
        // Aucun identifiant utilisateur n'est accepté dans le formulaire.
        Utilisateur utilisateur = trouverUtilisateur(emailSession);
        entityManager.refresh(utilisateur, LockModeType.PESSIMISTIC_WRITE);

        if (!utilisateur.isActif()
                || !utilisateur.getEmail().equalsIgnoreCase(emailSession)) {
            throw new UsernameNotFoundException("Veuillez vous reconnecter.");
        }

        if (!passwordEncoder.matches(formulaire.getMotDePasseActuel(),
                utilisateur.getMotDePasseHash())) {
            throw new ChampCompteException("motDePasseActuel", "Le mot de passe actuel est incorrect.");
        }

        String pseudo = formulaire.getPseudo();
        String email = formulaire.getEmail();
        String nouveauMotDePasse = formulaire.getNouveauMotDePasse();
        String confirmation = formulaire.getConfirmationMotDePasse();

        verifierDisponibilite(pseudo, "pseudo", utilisateur.getId());
        verifierDisponibilite(email, "email", utilisateur.getId());

        boolean changerMotDePasse = !nouveauMotDePasse.isEmpty();
        if (changerMotDePasse || !confirmation.isEmpty()) {
            if (nouveauMotDePasse.length() < 12) {
                throw new ChampCompteException("nouveauMotDePasse",
                        "Choisissez un mot de passe d'au moins 12 caractères.");
            }
            // Compatible avec BCrypt, notamment pour les caractères accentués.
            if (nouveauMotDePasse.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new ChampCompteException("nouveauMotDePasse",
                        "Le mot de passe est trop long. Les caractères accentués et emojis occupent plusieurs octets.");
            }
            if (!nouveauMotDePasse.equals(confirmation)) {
                throw new ChampCompteException("confirmationMotDePasse",
                        "Les deux nouveaux mots de passe ne correspondent pas.");
            }
            if (passwordEncoder.matches(nouveauMotDePasse, utilisateur.getMotDePasseHash())) {
                throw new ChampCompteException("nouveauMotDePasse",
                        "Choisissez un mot de passe différent de l'actuel.");
            }
        }

        boolean emailModifie = !email.equals(utilisateur.getEmail());
        boolean pseudoModifie = !pseudo.equals(utilisateur.getPseudo());
        if (!emailModifie && !pseudoModifie && !changerMotDePasse) {
            return new ResultatModification(false, false);
        }

        // Tout est validé avant de modifier l'entité.
        String nouveauHash = changerMotDePasse ? passwordEncoder.encode(nouveauMotDePasse) : null;
        utilisateur.modifierProfil(pseudo, email);
        if (changerMotDePasse) {
            utilisateur.modifierMotDePasseHash(nouveauHash);
        }
        repository.flush();

        return new ResultatModification(true, emailModifie || changerMotDePasse);
    }

    private Utilisateur trouverUtilisateur(String emailSession) {
        return repository.findByEmailIgnoreCase(emailSession)
                .filter(Utilisateur::isActif)
                .orElseThrow(() -> new UsernameNotFoundException("Veuillez vous reconnecter."));
    }

    private void verifierDisponibilite(String valeur, String champ, Long utilisateurId) {
        // Vérifie aussi les collisions pseudo/e-mail entre comptes différents,
        // puisque les deux valeurs peuvent servir à se connecter.
        Optional<Utilisateur> parEmail = repository.findByEmailIgnoreCase(valeur);
        Optional<Utilisateur> parPseudo = repository.findByPseudoIgnoreCase(valeur);
        if (appartientAUnAutreCompte(parEmail, utilisateurId)
                || appartientAUnAutreCompte(parPseudo, utilisateurId)) {
            throw new ChampCompteException(champ,
                    "pseudo".equals(champ)
                            ? "Ce pseudo est déjà utilisé comme identifiant par un autre compte."
                            : "Cette adresse est déjà utilisée comme identifiant par un autre compte.");
        }
    }

    private static boolean appartientAUnAutreCompte(Optional<Utilisateur> resultat, Long id) {
        return resultat.isPresent() && !resultat.get().getId().equals(id);
    }

    public record CompteVue(String pseudo, String email, String role, String dateCreation) {}
    public record ResultatModification(boolean modifie, boolean reconnexionNecessaire) {}

    public static class ChampCompteException extends IllegalArgumentException {
        private final String champ;
        public ChampCompteException(String champ, String message) {
            super(message);
            this.champ = champ;
        }
        public String getChamp() { return champ; }
    }
}
