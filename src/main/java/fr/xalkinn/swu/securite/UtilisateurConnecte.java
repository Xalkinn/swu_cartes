package fr.xalkinn.swu.securite;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.util.List;

// User implémente déjà CredentialsContainer : Spring efface le hash du mot de
// passe après l'authentification. L'identifiant stable reste dans le principal.
public class UtilisateurConnecte extends User {
    private static final long serialVersionUID = 1L;
    private final Long utilisateurId;

    public UtilisateurConnecte(Long utilisateurId, String email,
                              String motDePasseHash, String role, boolean actif) {
        super(email, motDePasseHash, actif, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        this.utilisateurId = utilisateurId;
    }

    public Long getUtilisateurId() {
        return utilisateurId;
    }
}
