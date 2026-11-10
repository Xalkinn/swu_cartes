package fr.xalkinn.swu.carte;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FiltresCarte {
    private FiltresCarte() {
    }

    public static List<String> nettoyer(List<String> valeurs) {
        return valeurs == null ? List.of() : valeurs.stream().filter(Objects::nonNull).map(String::strip).filter(v -> !v.isEmpty()).distinct().toList();
    }

    public static List<String> raretes(List<String> valeurs) {
        List<String> resultat = nettoyer(valeurs);
        if (resultat.size() > Rarete.values().length)
            throw new IllegalArgumentException("Trop de raretés sélectionnées.");
        for (String valeur : resultat)
            try {
                Rarete.valueOf(valeur);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Rareté inconnue.");
            }
        return resultat;
    }

    public static List<String> types(List<String> valeurs) {
        List<String> resultat = nettoyer(valeurs);
        if (resultat.size() > TypeCarteFiltre.values().length)
            throw new IllegalArgumentException("Trop de types sélectionnés.");
        for (String valeur : resultat)
            try {
                TypeCarteFiltre.valueOf(valeur);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Type de carte inconnu.");
            }
        return resultat;
    }

    // OU au sein d'une même famille, ET entre raretés, types et autres critères.
    public static Predicate conditionRaretes(Root<Carte> carte, CriteriaBuilder cb, List<String> codes) {
        if (codes.isEmpty()) return cb.conjunction();
        List<String> connus = codes.stream().filter(c -> !c.equals("INCONNUE")).toList();
        List<Predicate> choix = new ArrayList<>();
        if (!connus.isEmpty()) choix.add(carte.get("rarete").in(connus));
        if (codes.contains("INCONNUE")) choix.add(cb.isNull(carte.get("rarete")));
        return cb.or(choix.toArray(Predicate[]::new));
    }

    public static Predicate conditionTypes(Root<Carte> carte, CriteriaBuilder cb, List<String> codes) {
        if (codes.isEmpty()) return cb.conjunction();
        List<String> valeurs = codes.stream().flatMap(c -> TypeCarteFiltre.valueOf(c).getValeurs().stream()).distinct().toList();
        return carte.get("typeCarte").in(valeurs);
    }
}
