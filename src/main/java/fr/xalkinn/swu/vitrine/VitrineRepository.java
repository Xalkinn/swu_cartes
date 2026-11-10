package fr.xalkinn.swu.vitrine;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VitrineRepository extends JpaRepository<Vitrine, Long> {
    @EntityGraph(attributePaths = "utilisateur")
    @Query("""
            select v from Vitrine v where v.publiee=true and v.utilisateur.actif=true
            and (:q='' or locate(lower(:q),lower(v.utilisateur.pseudo))>0)
            order by lower(v.utilisateur.pseudo),v.id
            """)
    Page<Vitrine> publiques(@Param("q") String q, Pageable pageable);
}
