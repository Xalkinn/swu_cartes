package fr.xalkinn.swu.importation;

import fr.xalkinn.swu.carte.FinitionCarte;
import fr.xalkinn.swu.carte.VarianteCarte;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Les codes proviennent de variantTypes.variantId, jamais d'un intervalle de numéros.
 */
public record VarianteSource(VarianteCarte famille, FinitionCarte finition, String code, String libelle) {
    public static VarianteSource lire(Map<?, ?> attributs) {
        Object relation = attributs.get("variantTypes");
        Object data = relation instanceof Map<?, ?> r ? r.get("data") : null;
        List<VarianteSource> types = new ArrayList<>();
        if (data instanceof List<?> liste) for (Object o : liste) {
            if (!(o instanceof Map<?, ?> m) || !(m.get("attributes") instanceof Map<?, ?> a)) continue;
            String code = Objects.toString(a.get("variantId"), "").strip(), nom = Objects.toString(a.get("name"), "").strip();
            if (code.length() > 24 || nom.length() > 120)
                throw new IllegalStateException("Type de variante source invalide.");
            VarianteSource v = switch (code) {
                case "01" -> new VarianteSource(VarianteCarte.STANDARD, FinitionCarte.NON_FOIL, code, "Standard");
                case "31" -> new VarianteSource(VarianteCarte.STANDARD, FinitionCarte.FOIL, code, "Standard");
                case "02" -> new VarianteSource(VarianteCarte.HYPERSPACE, FinitionCarte.NON_FOIL, code, "Hyperspace");
                case "32" -> new VarianteSource(VarianteCarte.HYPERSPACE, FinitionCarte.FOIL, code, "Hyperspace");
                case "03" -> new VarianteSource(VarianteCarte.SHOWCASE, FinitionCarte.FOIL, code, "Showcase");
                case "06" -> new VarianteSource(VarianteCarte.PRESTIGE, FinitionCarte.NON_FOIL, code, "Prestige");
                case "33" -> new VarianteSource(VarianteCarte.PRESTIGE, FinitionCarte.FOIL, code, "Prestige");
                case "34" ->
                        new VarianteSource(VarianteCarte.PRESTIGE_SERIALISEE, FinitionCarte.FOIL, code, "Prestige sérialisée");
                default ->
                        new VarianteSource(VarianteCarte.PROMO, a.get("foil") instanceof Boolean f ? (f ? FinitionCarte.FOIL : FinitionCarte.NON_FOIL) : FinitionCarte.MIXTE, code.isBlank() ? null : code, nom.isBlank() ? "Promotionnelle" : nom);
            };
            types.add(v);
        }
        if (types.isEmpty()) {
            // Quelques fiches anciennes sont dépourvues de variantTypes ; on conserve cette incertitude.
            if (Boolean.TRUE.equals(attributs.get("showcase")))
                return new VarianteSource(VarianteCarte.SHOWCASE, FinitionCarte.FOIL, null, "Showcase");
            if (Boolean.TRUE.equals(attributs.get("hyperspace")))
                return new VarianteSource(VarianteCarte.HYPERSPACE, FinitionCarte.MIXTE, null, "Hyperspace");
            return new VarianteSource(VarianteCarte.AUTRE, FinitionCarte.MIXTE, null, "Traitement non renseigné");
        }
        VarianteSource premier = types.get(0);
        if (types.stream().anyMatch(t -> t.famille() != premier.famille() || t.finition() != premier.finition()))
            throw new IllegalStateException("Plusieurs traitements incompatibles sur une même impression source.");
        return premier;
    }
}
