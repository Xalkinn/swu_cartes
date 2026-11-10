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
                    left join c.carteStandard standard left join standard.extension se
                    where lower(p.utilisateur.email) = lower(:email)
                    group by
                        coalesce(standard.id, c.id),
                        coalesce(se.code,e.code),
                        coalesce(standard.numero, c.numero)
                    order by
                        coalesce(se.code,e.code),
                        coalesce(standard.numero, c.numero),
                        coalesce(standard.id, c.id)
                    """,
            countQuery = """
                    select count(distinct coalesce(standard.id, c.id))
                    from Possession p
                    join p.carte c
                    left join c.carteStandard standard left join standard.extension se
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
            left join c.carteStandard standard left join standard.extension se
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
            join p.carte c join c.extension e left join c.carteStandard standard left join standard.extension se
            where lower(p.utilisateur.email) = lower(:email)
              and (:q = '' or locate(lower(:q), lower(coalesce(standard.titre,c.titre))) > 0
                   or locate(lower(:q), lower(coalesce(standard.sousTitre,c.sousTitre,''))) > 0
                   or (:numero is not null and (c.numero = :numero or standard.numero = :numero)))
              and (:numero is null
                   or (lower(:q) like 't%' and coalesce(standard.typeCarte,c.typeCarte,'') in ('Token','Token Unit','Token Upgrade'))
                   or (lower(:q) not like 't%' and coalesce(standard.typeCarte,c.typeCarte,'') not in ('Token','Token Unit','Token Upgrade')))
              and (:extensionId is null or coalesce(se.id,e.id) = :extensionId)
              and (:version = 'TOUTES'
                   or (:version = 'STANDARD' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.STANDARD)
                   or (:version = 'HYPERSPACE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.HYPERSPACE)
                   or (:version = 'FOIL' and p.quantiteFoil > 0)
                   or (:version = 'NONFOIL' and p.quantite > p.quantiteFoil)
                   or (:version = 'SHOWCASE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.SHOWCASE)
                   or (:version = 'PRESTIGE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.PRESTIGE)
                   or (:version = 'PRESTIGE_SERIALISEE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.PRESTIGE_SERIALISEE)
                   or (:version = 'PROMO' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.PROMO)
                   or (:version = 'AUTRE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.AUTRE))
              and (:recherches = false or exists (
                   select r.id from RechercheCarte r where r.utilisateur.id = p.utilisateur.id
                     and r.carte.id = coalesce(standard.id,c.id)))
            group by coalesce(standard.id,c.id),coalesce(se.code,e.code),coalesce(standard.numero,c.numero)
            order by coalesce(se.code,e.code),coalesce(standard.numero,c.numero),coalesce(standard.id,c.id)
            """, countQuery = """
            select count(distinct coalesce(standard.id,c.id)) from Possession p
            join p.carte c join c.extension e left join c.carteStandard standard left join standard.extension se
            where lower(p.utilisateur.email) = lower(:email)
              and (:q = '' or locate(lower(:q), lower(coalesce(standard.titre,c.titre))) > 0
                   or locate(lower(:q), lower(coalesce(standard.sousTitre,c.sousTitre,''))) > 0
                   or (:numero is not null and (c.numero = :numero or standard.numero = :numero)))
              and (:numero is null
                   or (lower(:q) like 't%' and coalesce(standard.typeCarte,c.typeCarte,'') in ('Token','Token Unit','Token Upgrade'))
                   or (lower(:q) not like 't%' and coalesce(standard.typeCarte,c.typeCarte,'') not in ('Token','Token Unit','Token Upgrade')))
              and (:extensionId is null or coalesce(se.id,e.id) = :extensionId)
              and (:version = 'TOUTES'
                   or (:version = 'STANDARD' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.STANDARD)
                   or (:version = 'HYPERSPACE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.HYPERSPACE)
                   or (:version = 'FOIL' and p.quantiteFoil > 0)
                   or (:version = 'NONFOIL' and p.quantite > p.quantiteFoil)
                   or (:version = 'SHOWCASE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.SHOWCASE)
                   or (:version = 'PRESTIGE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.PRESTIGE)
                   or (:version = 'PRESTIGE_SERIALISEE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.PRESTIGE_SERIALISEE)
                   or (:version = 'PROMO' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.PROMO)
                   or (:version = 'AUTRE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.AUTRE))
              and (:recherches = false or exists (
                   select r.id from RechercheCarte r where r.utilisateur.id = p.utilisateur.id
                     and r.carte.id = coalesce(standard.id,c.id)))
            """)
    Page<Long> trouverGroupesFiltres(@Param("email") String email, @Param("q") String q,
            @Param("numero") Integer numero, @Param("extensionId") Long extensionId,
            @Param("version") String version, @Param("recherches") boolean recherches, Pageable pageable);
    // Filtre les groupes avant la pagination. Les quantités de toutes leurs versions
    // sont ensuite chargées par trouverPossessionsDesGroupes, sans perdre d'exemplaires.
    @Query(value = """
            select coalesce(standard.id,c.id) from Possession p
            join p.carte c join c.extension e left join c.carteStandard standard left join standard.extension se
            where lower(p.utilisateur.email) = lower(:email)
              and (:q = '' or locate(lower(:q), lower(coalesce(standard.titre,c.titre))) > 0
                   or locate(lower(:q), lower(coalesce(standard.sousTitre,c.sousTitre,''))) > 0
                   or (:numero is not null and (c.numero = :numero or standard.numero = :numero)))
              and (:numero is null
                   or (lower(:q) like 't%' and coalesce(standard.typeCarte,c.typeCarte,'') in ('Token','Token Unit','Token Upgrade'))
                   or (lower(:q) not like 't%' and coalesce(standard.typeCarte,c.typeCarte,'') not in ('Token','Token Unit','Token Upgrade')))
              and (:extensionId is null or coalesce(se.id,e.id) = :extensionId)

              and (:filtreArenes = false or exists (select a from Carte ref join ref.arenes a
                   where ref.id = coalesce(standard.id,c.id) and a in :arenes))
              and (:filtreTraits = false
                   or (:tousTraits = false and exists (select t from Carte ref join ref.traits t
                       where ref.id = coalesce(standard.id,c.id) and t in :traits))
                   or (:tousTraits = true and (select count(distinct t) from Carte ref join ref.traits t
                       where ref.id = coalesce(standard.id,c.id) and t in :traits) = :nombreTraits))
              and (:version = 'TOUTES'
                   or (:version = 'STANDARD' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.STANDARD)
                   or (:version = 'HYPERSPACE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.HYPERSPACE)
                   or (:version = 'FOIL' and p.quantiteFoil > 0)
                   or (:version = 'NONFOIL' and p.quantite > p.quantiteFoil)
                   or (:version = 'SHOWCASE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.SHOWCASE)
                   or (:version = 'PRESTIGE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.PRESTIGE)
                   or (:version = 'PRESTIGE_SERIALISEE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.PRESTIGE_SERIALISEE)
                   or (:version = 'PROMO' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.PROMO)
                   or (:version = 'AUTRE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.AUTRE))
              and (:recherches = false or exists (
                   select r.id from RechercheCarte r where r.utilisateur.id = coalesce(:recherchesUtilisateurId,p.utilisateur.id)
                     and r.carte.id = coalesce(standard.id,c.id)))
            group by coalesce(standard.id,c.id),coalesce(se.code,e.code),coalesce(standard.numero,c.numero)
            order by coalesce(se.code,e.code),coalesce(standard.numero,c.numero),coalesce(standard.id,c.id)
            """, countQuery = """
            select count(distinct coalesce(standard.id,c.id)) from Possession p
            join p.carte c join c.extension e left join c.carteStandard standard left join standard.extension se
            where lower(p.utilisateur.email) = lower(:email)
              and (:q = '' or locate(lower(:q), lower(coalesce(standard.titre,c.titre))) > 0
                   or locate(lower(:q), lower(coalesce(standard.sousTitre,c.sousTitre,''))) > 0
                   or (:numero is not null and (c.numero = :numero or standard.numero = :numero)))
              and (:numero is null
                   or (lower(:q) like 't%' and coalesce(standard.typeCarte,c.typeCarte,'') in ('Token','Token Unit','Token Upgrade'))
                   or (lower(:q) not like 't%' and coalesce(standard.typeCarte,c.typeCarte,'') not in ('Token','Token Unit','Token Upgrade')))
              and (:extensionId is null or coalesce(se.id,e.id) = :extensionId)

              and (:filtreArenes = false or exists (select a from Carte ref join ref.arenes a
                   where ref.id = coalesce(standard.id,c.id) and a in :arenes))
              and (:filtreTraits = false
                   or (:tousTraits = false and exists (select t from Carte ref join ref.traits t
                       where ref.id = coalesce(standard.id,c.id) and t in :traits))
                   or (:tousTraits = true and (select count(distinct t) from Carte ref join ref.traits t
                       where ref.id = coalesce(standard.id,c.id) and t in :traits) = :nombreTraits))
              and (:version = 'TOUTES'
                   or (:version = 'STANDARD' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.STANDARD)
                   or (:version = 'HYPERSPACE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.HYPERSPACE)
                   or (:version = 'FOIL' and p.quantiteFoil > 0)
                   or (:version = 'NONFOIL' and p.quantite > p.quantiteFoil)
                   or (:version = 'SHOWCASE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.SHOWCASE)
                   or (:version = 'PRESTIGE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.PRESTIGE)
                   or (:version = 'PRESTIGE_SERIALISEE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.PRESTIGE_SERIALISEE)
                   or (:version = 'PROMO' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.PROMO)
                   or (:version = 'AUTRE' and c.variante = fr.xalkinn.swu.carte.VarianteCarte.AUTRE))
              and (:recherches = false or exists (
                   select r.id from RechercheCarte r where r.utilisateur.id = coalesce(:recherchesUtilisateurId,p.utilisateur.id)
                     and r.carte.id = coalesce(standard.id,c.id)))
            """)
    Page<Long> trouverGroupesFiltresIdentitePour(@Param("email") String email, @Param("q") String q,
            @Param("numero") Integer numero, @Param("extensionId") Long extensionId,
            @Param("version") String version, @Param("recherches") boolean recherches,
            @Param("filtreArenes") boolean filtreArenes, @Param("arenes") List<String> arenes,
            @Param("filtreTraits") boolean filtreTraits, @Param("traits") List<String> traits,
            @Param("tousTraits") boolean tousTraits, @Param("nombreTraits") long nombreTraits, @Param("recherchesUtilisateurId") Long recherchesUtilisateurId, Pageable pageable);
    default Page<Long> trouverGroupesFiltresIdentite(String email, String q, Integer numero, Long extensionId,
            String version, boolean recherches, boolean filtreArenes, List<String> arenes,
            boolean filtreTraits, List<String> traits, boolean tousTraits, long nombreTraits, Pageable pageable) {
        return trouverGroupesFiltresIdentitePour(email,q,numero,extensionId,version,recherches,filtreArenes,arenes,
                filtreTraits,traits,tousTraits,nombreTraits,null,pageable);
    }
}
