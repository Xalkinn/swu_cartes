package fr.xalkinn.swu.securite;

import fr.xalkinn.swu.utilisateur.Utilisateur;
import fr.xalkinn.swu.utilisateur.UtilisateurRepository;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UtilisateurDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    public UtilisateurDetailsService(
            UtilisateurRepository utilisateurRepository
    ) {
        this.utilisateurRepository = utilisateurRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        // La connexion utilisera l'adresse e-mail.
        Utilisateur utilisateur = utilisateurRepository
                .findByEmailIgnoreCase(email.strip())
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Identifiants incorrects"
                        )
                );

        return User.withUsername(utilisateur.getEmail())
                .password(utilisateur.getMotDePasseHash())
                .roles(utilisateur.getRole())
                .disabled(!utilisateur.isActif())
                .build();
    }
}