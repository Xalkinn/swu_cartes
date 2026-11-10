package fr.xalkinn.swu.collection;

import fr.xalkinn.swu.carte.Affinite;
import fr.xalkinn.swu.carte.Carte;
import fr.xalkinn.swu.carte.FiltresCarte;
import fr.xalkinn.swu.carte.VarianteCarte;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Critères du catalogue appliqués aux références d'une collection regroupée.
 */
public record FiltresCollection(Integer coutMin, Integer coutMax, List<String> affinites,
                                String mode, List<String> raretes, List<String> types, String variante,
                                String finition) {
    public static final FiltresCollection VIDE = new FiltresCollection(null, null, List.of(), "TOUTES", List.of(), List.of(), "TOUTES", "TOUTES");

    public FiltresCollection {
        mode = mode == null ? "TOUTES" : mode;
        affinites = "SANS".equals(mode) ? List.of() : FiltresCarte.nettoyer(affinites);
        raretes = FiltresCarte.nettoyer(raretes);
        types = FiltresCarte.nettoyer(types);
        variante = variante == null ? "TOUTES" : variante;
        finition = finition == null ? "TOUTES" : finition;
    }

    public void verifier() {
        FiltresCarte.raretes(raretes);
        FiltresCarte.types(types);
        if ((coutMin != null && coutMin < 0) || (coutMax != null && coutMax < 0))
            throw new IllegalArgumentException("Les coûts doivent être positifs ou nuls.");
        if (coutMin != null && coutMax != null && coutMin > coutMax)
            throw new IllegalArgumentException("Le coût minimum doit être inférieur ou égal au maximum.");
        if (!List.of("TOUTES", "UNE", "SANS").contains(mode))
            throw new IllegalArgumentException("Mode des affinités invalide.");
        for (String code : affinites)
            try {
                Affinite.valueOf(code);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Affinité inconnue.");
            }
        if (!"TOUTES".equals(variante)) try {
            VarianteCarte.valueOf(variante);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Variante inconnue.");
        }
        if (!List.of("TOUTES", "FOIL", "NON_FOIL").contains(finition))
            throw new IllegalArgumentException("Finition inconnue.");
    }

    Predicate condition(Root<Carte> reference, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Predicate> choix = new ArrayList<>();
        if (coutMin != null) choix.add(cb.ge(reference.get("cout"), coutMin));
        if (coutMax != null) choix.add(cb.le(reference.get("cout"), coutMax));
        choix.add(FiltresCarte.conditionRaretes(reference, cb, raretes));
        choix.add(FiltresCarte.conditionTypes(reference, cb, types));
        if ("SANS".equals(mode)) choix.add(cb.not(affinite(reference, query, cb, null)));
        else if (!affinites.isEmpty()) {
            Predicate[] symboles = affinites.stream().map(c -> affinite(reference, query, cb, c)).toArray(Predicate[]::new);
            choix.add("UNE".equals(mode) ? cb.or(symboles) : cb.and(symboles));
        }
        return cb.and(choix.toArray(Predicate[]::new));
    }

    private static Predicate affinite(Root<Carte> reference, CriteriaQuery<?> query, CriteriaBuilder cb, String code) {
        var sq = query.subquery(Long.class);
        var carte = sq.from(Carte.class);
        var symboles = carte.joinMap("affinites");
        Predicate lien = cb.equal(carte.get("id"), reference.get("id"));
        sq.select(carte.get("id")).where(code == null ? lien : cb.and(lien, cb.equal(symboles.key(), code)));
        return cb.exists(sq);
    }

    public void parametres(Map<String, List<String>> params) {
        if (coutMin != null) params.put("coutMin", List.of(coutMin.toString()));
        if (coutMax != null) params.put("coutMax", List.of(coutMax.toString()));
        if (!affinites.isEmpty()) params.put("affinites", affinites);
        if (!"TOUTES".equals(mode)) params.put("mode", List.of(mode));
        if (!raretes.isEmpty()) params.put("raretes", raretes);
        if (!types.isEmpty()) params.put("types", types);
        if (!"TOUTES".equals(variante)) params.put("variante", List.of(variante));
        if (!"TOUTES".equals(finition)) params.put("finition", List.of(finition));
    }

    public int nombre() {
        return (coutMin == null ? 0 : 1) + (coutMax == null ? 0 : 1) + ("SANS".equals(mode) ? 1 : affinites.size())
                + raretes.size() + types.size() + ("TOUTES".equals(variante) ? 0 : 1) + ("TOUTES".equals(finition) ? 0 : 1);
    }
}
