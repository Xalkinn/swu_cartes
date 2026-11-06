package fr.xalkinn.swu.deck;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface DeckCarteRepository extends JpaRepository<DeckCarte, Long> {
    @EntityGraph(attributePaths = {"carte", "carte.extension"})
    List<DeckCarte> findByDeckIdOrderByCarteCoutAscCarteTitreAscCarteIdAsc(Long deckId);
    Optional<DeckCarte> findByDeckIdAndCarteId(Long deckId, Long carteId);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DeckCarte l where l.deck.id = :id")
    void supprimerLignes(@Param("id") Long deckId);
    @Query("select l.deck.id, sum(l.quantite) from DeckCarte l where l.deck.id in :ids group by l.deck.id")
    List<Object[]> compter(@Param("ids") List<Long> ids);
}
