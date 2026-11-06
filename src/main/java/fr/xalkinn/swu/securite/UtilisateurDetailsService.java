package fr.xalkinn.swu.securite;

import fr.xalkinn.swu.utilisateur.Utilisateur;
import fr.xalkinn.swu.utilisateur.UtilisateurRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UtilisateurDetailsService implements UserDetailsService {
    private final UtilisateurRepository utilisateurRepository;

    public UtilisateurDetailsService(UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String identifiant) throws UsernameNotFoundException {
        String saisie = identifiant == null ? "" : identifiant.strip();
        if (saisie.isEmpty()) throw new UsernameNotFoundException("Identifiants incorrects");

        Utilisateur utilisateur = utilisateurRepository.findByEmailIgnoreCase(saisie)
                .or(() -> utilisateurRepository.findByPseudoIgnoreCase(saisie))
                .orElseThrow(() -> new UsernameNotFoundException("Identifiants incorrects"));

        // Le nom de session reste l'e-mail : collection et paramètres continuent
        // à fonctionner. L'id permet de vérifier l'identité sans se fier à un e-mail réattribué.
        return new UtilisateurConnecte(utilisateur.getId(), utilisateur.getEmail(),
                utilisateur.getMotDePasseHash(), utilisateur.getRole(), utilisateur.isActif());
    }
}
