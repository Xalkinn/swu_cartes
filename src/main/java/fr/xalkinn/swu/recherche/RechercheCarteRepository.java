package fr.xalkinn.swu.recherche;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RechercheCarteRepository extends JpaRepository<RechercheCarte, Long>, JpaSpecificationExecutor<RechercheCarte> {
    Optional<RechercheCarte> findByUtilisateurIdAndCarteId(Long utilisateurId, Long carteId);

    List<RechercheCarte> findByUtilisateurIdAndCarteIdIn(Long utilisateurId, Collection<Long> ids);

    @EntityGraph(attributePaths = {"carte", "carte.extension"})
    @Query("""
            select r from RechercheCarte r where r.utilisateur.id=:utilisateurId
              and (:q='' or locate(lower(:q),lower(r.carte.titre))>0
                   or locate(lower(:q),lower(coalesce(r.carte.sousTitre,'')))>0)
              and (:extensionId is null or r.carte.extension.id=:extensionId)
            order by r.dateAjout desc,r.id desc
            """)
    Page<RechercheCarte> rechercher(@Param("utilisateurId") Long utilisateurId, @Param("q") String q,
                                    @Param("extensionId") Long extensionId, Pageable pageable);

    @Query("select c.id from Carte c left join c.carteStandard s where c.id=:id or s.id=:id")
    List<Long> famille(@Param("id") Long standardId);
}
