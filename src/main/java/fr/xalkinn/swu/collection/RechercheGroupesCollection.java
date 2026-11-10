package fr.xalkinn.swu.collection;

import fr.xalkinn.swu.carte.Carte;
import fr.xalkinn.swu.carte.FiltresIdentite;
import fr.xalkinn.swu.carte.ReferenceCarte;
import fr.xalkinn.swu.carte.VarianteCarte;
import fr.xalkinn.swu.recherche.RechercheCarte;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pagination en base sur les références possédées, puis chargement de toutes leurs versions.
 */
final class RechercheGroupesCollection {
    private RechercheGroupesCollection() {
    }

    static Page<Long> rechercher(EntityManager em, String email, String q, Integer numero, Long extensionId,
                                 String version, boolean recherches, FiltresIdentite identite, Long recherchesUtilisateurId,
                                 FiltresCollection filtres, int page) {
        var cb = em.getCriteriaBuilder();
        var count = cb.createQuery(Long.class);
        var referenceCount = count.from(Carte.class);
        count.select(cb.count(referenceCount)).where(condition(referenceCount, count, cb, email, q, numero, extensionId, version, recherches, identite, recherchesUtilisateurId, filtres));
        long total = em.createQuery(count).getSingleResult();
        int derniere = total == 0 ? 0 : (int) ((total - 1) / 24);
        int selection = Math.min(Math.max(0, page), derniere);
        var pageable = PageRequest.of(selection, 24);
        if (total == 0) return new PageImpl<>(List.of(), pageable, 0);
        var query = cb.createQuery(Long.class);
        var reference = query.from(Carte.class);
        query.select(reference.get("id")).where(condition(reference, query, cb, email, q, numero, extensionId, version, recherches, identite, recherchesUtilisateurId, filtres))
                .orderBy(cb.asc(reference.get("extension").get("code")), cb.asc(reference.get("numero")), cb.asc(reference.get("id")));
        return new PageImpl<>(em.createQuery(query).setFirstResult(selection * 24).setMaxResults(24).getResultList(), pageable, total);
    }

    private static Predicate condition(Root<Carte> reference, CriteriaQuery<?> query, CriteriaBuilder cb,
                                       String email, String q, Integer numero, Long extensionId, String version, boolean recherches,
                                       FiltresIdentite identite, Long recherchesUtilisateurId, FiltresCollection filtres) {
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(filtres.condition(reference, query, cb));
        conditions.add(identite.condition(reference, query, cb));
        if (extensionId != null) conditions.add(cb.equal(reference.get("extension").get("id"), extensionId));
        if (numero != null) {
            Predicate jeton = cb.coalesce(reference.<String>get("typeCarte"), "").in(ReferenceCarte.TYPES_JETON);
            conditions.add(ReferenceCarte.jeton(q) ? jeton : cb.not(jeton));
        }
        var stock = query.subquery(Long.class);
        var possession = stock.from(Possession.class);
        var impression = possession.<Possession, Carte>join("carte");
        var standard = impression.<Carte, Carte>join("carteStandard", JoinType.LEFT);
        List<Predicate> possede = new ArrayList<>();
        possede.add(cb.equal(cb.lower(possession.get("utilisateur").get("email")), email.toLowerCase(Locale.ROOT)));
        possede.add(cb.gt(possession.get("quantite"), 0));
        possede.add(cb.equal(cb.coalesce(standard.<Long>get("id"), impression.<Long>get("id")), reference.get("id")));
        if (!q.isBlank()) {
            Predicate texte = cb.or(cb.gt(cb.locate(cb.lower(reference.get("titre")), q.toLowerCase(Locale.ROOT)), 0),
                    cb.gt(cb.locate(cb.lower(cb.coalesce(reference.<String>get("sousTitre"), "")), q.toLowerCase(Locale.ROOT)), 0));
            if (numero != null)
                texte = cb.or(texte, cb.equal(reference.get("numero"), numero), cb.equal(impression.get("numero"), numero));
            possede.add(texte);
        }
        if ("FOIL".equals(version)) possede.add(cb.gt(possession.get("quantiteFoil"), 0));
        else if ("NONFOIL".equals(version))
            possede.add(cb.gt(possession.get("quantite"), possession.get("quantiteFoil")));
        else if (!"TOUTES".equals(version))
            possede.add(cb.equal(impression.get("variante"), VarianteCarte.valueOf(version)));
        if (!"TOUTES".equals(filtres.variante()))
            possede.add(cb.equal(impression.get("variante"), VarianteCarte.valueOf(filtres.variante())));
        // La finition porte sur le stock réel : une ancienne foil peut être comptée sur une fiche MIXTE/non-foil.
        if ("FOIL".equals(filtres.finition())) possede.add(cb.gt(possession.get("quantiteFoil"), 0));
        if ("NON_FOIL".equals(filtres.finition()))
            possede.add(cb.gt(possession.get("quantite"), possession.get("quantiteFoil")));
        if (recherches) {
            var coeurs = stock.subquery(Long.class);
            var coeur = coeurs.from(RechercheCarte.class);
            coeurs.select(coeur.get("id")).where(cb.equal(coeur.get("carte").get("id"), reference.get("id")),
                    recherchesUtilisateurId == null ? cb.equal(coeur.get("utilisateur").get("id"), possession.get("utilisateur").get("id"))
                            : cb.equal(coeur.get("utilisateur").get("id"), recherchesUtilisateurId));
            possede.add(cb.exists(coeurs));
        }
        stock.select(possession.get("id")).where(possede.toArray(Predicate[]::new));
        conditions.add(cb.exists(stock));
        return cb.and(conditions.toArray(Predicate[]::new));
    }
}
