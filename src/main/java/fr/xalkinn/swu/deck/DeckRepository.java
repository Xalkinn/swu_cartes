package fr.xalkinn.swu.deck;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface DeckRepository extends JpaRepository<Deck, Long> {
    @EntityGraph(attributePaths = {"leader", "base"})
    Page<Deck> findByUtilisateurIdOrderByDateModificationDescIdDesc(Long utilisateurId, Pageable pageable);
    Optional<Deck> findByIdAndUtilisateurId(Long id, Long utilisateurId);
    // Une seule modification à la fois par deck, avec contrôle du propriétaire dans la requête.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Deck d where d.id = :id and d.utilisateur.id = :utilisateurId")
    Optional<Deck> verrouiller(@Param("id") Long id, @Param("utilisateurId") Long utilisateurId);
}
