package fr.xalkinn.swu.deck;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeckRepository extends JpaRepository<Deck, Long> {
    @EntityGraph(attributePaths = {"leader", "base"})
    Page<Deck> findByUtilisateurIdOrderByDateModificationDescIdDesc(Long utilisateurId, Pageable pageable);

    Optional<Deck> findByIdAndUtilisateurId(Long id, Long utilisateurId);

    // Une seule modification à la fois par deck, avec contrôle du propriétaire dans la requête.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Deck d where d.id = :id and d.utilisateur.id = :utilisateurId")
    Optional<Deck> verrouiller(@Param("id") Long id, @Param("utilisateurId") Long utilisateurId);

    @EntityGraph(attributePaths = {"leader", "base", "utilisateur"})
    @Query(value = """
            select d from Deck d
            left join d.leader l left join d.base b join d.utilisateur u
            where d.publieCommunaute = true and u.actif = true
              and (:q = '' or locate(lower(:q), lower(d.nom)) > 0
                   or locate(lower(:q), lower(u.pseudo)) > 0
                   or locate(lower(:q), lower(coalesce(l.titre, ''))) > 0
                   or locate(lower(:q), lower(coalesce(b.titre, ''))) > 0)
            order by d.datePublicationCommunaute desc, d.id desc
            """, countQuery = """
            select count(d) from Deck d
            left join d.leader l left join d.base b join d.utilisateur u
            where d.publieCommunaute = true and u.actif = true
              and (:q = '' or locate(lower(:q), lower(d.nom)) > 0
                   or locate(lower(:q), lower(u.pseudo)) > 0
                   or locate(lower(:q), lower(coalesce(l.titre, ''))) > 0
                   or locate(lower(:q), lower(coalesce(b.titre, ''))) > 0)
            """)
    Page<Deck> chercherPublies(@Param("q") String q, Pageable pageable);

    @EntityGraph(attributePaths = {"leader", "base", "utilisateur"})
    @Query("select d from Deck d where d.id = :id and d.publieCommunaute = true and d.utilisateur.actif = true")
    Optional<Deck> trouverPublie(@Param("id") Long id);

}
