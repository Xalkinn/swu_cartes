package fr.xalkinn.swu.collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PossessionRepository
        extends JpaRepository<Possession, Long> {

    Optional<Possession> findByUtilisateurIdAndCarteId(
            Long utilisateurId,
            Long carteId
    );

    @EntityGraph(attributePaths = "carte")
    @Query("""
            select p from Possession p
            where lower(p.utilisateur.email) = lower(:email)
              and p.carte.id in :carteIds
            """)
    List<Possession> findByUtilisateurEmailIgnoreCaseAndCarteIdIn(
            @Param("email") String email,
            @Param("carteIds") List<Long> carteIds
    );

    @EntityGraph(attributePaths = {
            "carte",
            "carte.extension"
    })
    Page<Possession> findByUtilisateurEmailIgnoreCase(
            String email,
            Pageable pageable
    );

    @Query(
            value = """
                    select coalesce(standard.id, c.id)
                    from Possession p
                    join p.carte c
                    join c.extension e
                    left join c.carteStandard standard
                    where lower(p.utilisateur.email) = lower(:email)
                    group by
                        coalesce(standard.id, c.id),
                        e.code,
                        coalesce(standard.numero, c.numero)
                    order by
                        e.code,
                        coalesce(standard.numero, c.numero),
                        coalesce(standard.id, c.id)
                    """,
            countQuery = """
                    select count(distinct coalesce(standard.id, c.id))
                    from Possession p
                    join p.carte c
                    left join c.carteStandard standard
                    where lower(p.utilisateur.email) = lower(:email)
                    """
    )
    Page<Long> trouverGroupes(
            @Param("email") String email,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "carte",
            "carte.extension",
            "carte.carteStandard"
    })
    @Query("""
            select p
            from Possession p
            join p.carte c
            left join c.carteStandard standard
            where lower(p.utilisateur.email) = lower(:email)
              and coalesce(standard.id, c.id) in :ids
            """)
    List<Possession> trouverPossessionsDesGroupes(
            @Param("email") String email,
            @Param("ids") List<Long> ids
    );

    // Filtre les groupes avant la pagination. Les quantités de toutes leurs versions
    // sont ensuite chargées par trouverPossessionsDesGroupes, sans perdre d'exemplaires.
    @Query(value = """
            select coalesce(standard.id,c.id) from Possession p
            join p.carte c join c.extension e left join c.carteStandard standard
            where lower(p.utilisateur.email) = lower(:email)
              and (:q = '' or locate(lower(:q), lower(coalesce(standard.titre,c.titre))) > 0
                   or locate(lower(:q), lower(coalesce(standard.sousTitre,c.sousTitre,''))) > 0
                   or (:numero is not null and (c.numero = :numero or standard.numero = :numero)))
              and (:extensionId is null or e.id = :extensionId)
              and (:version = 'TOUTES'
                   or (:version = 'STANDARD' and c.hyperspace = false)
                   or (:version = 'HYPERSPACE' and c.hyperspace = true)
                   or (:version = 'FOIL' and p.quantiteFoil > 0)
                   or (:version = 'NONFOIL' and p.quantite > p.quantiteFoil))
              and (:recherches = false or exists (
                   select r.id from RechercheCarte r where r.utilisateur.id = p.utilisateur.id
                     and r.carte.id = coalesce(standard.id,c.id)))
            group by coalesce(standard.id,c.id),e.code,coalesce(standard.numero,c.numero)
            order by e.code,coalesce(standard.numero,c.numero),coalesce(standard.id,c.id)
            """, countQuery = """
            select count(distinct coalesce(standard.id,c.id)) from Possession p
            join p.carte c join c.extension e left join c.carteStandard standard
            where lower(p.utilisateur.email) = lower(:email)
              and (:q = '' or locate(lower(:q), lower(coalesce(standard.titre,c.titre))) > 0
                   or locate(lower(:q), lower(coalesce(standard.sousTitre,c.sousTitre,''))) > 0
                   or (:numero is not null and (c.numero = :numero or standard.numero = :numero)))
              and (:extensionId is null or e.id = :extensionId)
              and (:version = 'TOUTES'
                   or (:version = 'STANDARD' and c.hyperspace = false)
                   or (:version = 'HYPERSPACE' and c.hyperspace = true)
                   or (:version = 'FOIL' and p.quantiteFoil > 0)
                   or (:version = 'NONFOIL' and p.quantite > p.quantiteFoil))
              and (:recherches = false or exists (
                   select r.id from RechercheCarte r where r.utilisateur.id = p.utilisateur.id
                     and r.carte.id = coalesce(standard.id,c.id)))
            """)
    Page<Long> trouverGroupesFiltres(@Param("email") String email, @Param("q") String q,
            @Param("numero") Integer numero, @Param("extensionId") Long extensionId,
            @Param("version") String version, @Param("recherches") boolean recherches, Pageable pageable);
}