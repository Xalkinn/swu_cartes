package fr.xalkinn.swu.utilisateur;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
    Optional<Utilisateur> findByEmailIgnoreCase(String email);

    Optional<Utilisateur> findByPseudoIgnoreCase(String pseudo);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPseudoIgnoreCase(String pseudo);

    long countByActif(boolean actif);

    long countByRoleAndActif(String role, boolean actif);

    @Query("""
            select u from Utilisateur u
            where (:q = ''
                or locate(lower(:q), lower(u.pseudo)) > 0
                or locate(lower(:q), lower(u.email)) > 0)
              and (:role is null or u.role = :role)
              and (:actif is null or u.actif = :actif)
            """)
    Page<Utilisateur> rechercherPourAdministration(
            @Param("q") String q,
            @Param("role") String role,
            @Param("actif") Boolean actif,
            Pageable pageable
    );
}
