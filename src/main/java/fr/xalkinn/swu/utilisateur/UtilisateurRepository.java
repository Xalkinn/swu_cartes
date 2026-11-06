package fr.xalkinn.swu.utilisateur;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UtilisateurRepository
        extends JpaRepository<Utilisateur, Long> {

    // Recherche d'un compte par son adresse e-mail.
    Optional<Utilisateur> findByEmailIgnoreCase(String email);

    // Vérifications utilisées lors de l'inscription.
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPseudoIgnoreCase(String pseudo);
}