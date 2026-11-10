package fr.xalkinn.swu.importation;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Empreinte exacte des règles d'une carte, indépendante de son traitement et numéro.
 */
final class IdentiteCarteSource {
    private IdentiteCarteSource() {
    }

    // Rappels explicatifs rencontrés et vérifiés dans les fiches TWI officielles.
    // Ne jamais supprimer les parenthèses arbitraires : elles peuvent contenir une règle.
    private static final Set<String> RAPPELS = Set.of(
            "Les unités dans cette arène ne peuvent pas attaquer vos unités non-Sentinelle ou votre base.",
            "Vous pouvez éliminer 1 unité que vous contrôlez. Dans ce cas, cette carte coûte 2 ressources de moins.",
            "Lorsque cette unité attaque une unité ennemie, infligez les dégâts en excès à la base de l’adversaire.",
            "Mettez les autres cartes sous votre deck dans un ordre aléatoire. Gagnez cette capacité tant que vous contrôlez au moins 3 unités.",
            "Quand cette unité attaque, soignez 1 dégât de votre base.",
            "Cela n’inclut pas les cartes piochées pendant la phase de regroupement.",
            "Si le défenseur est éliminé, il n’inflige aucun dégât de combat.",
            "Mettez les autres cartes sous votre deck dans un ordre aléatoire."
    ).stream().map(IdentiteCarteSource::normaliser).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Pattern PARENTHESES = Pattern.compile("\\([^()]*\\)");

    private static String regle(Object valeur) {
        String texte = normaliser(valeur);
        var correspondances = PARENTHESES.matcher(texte);
        StringBuilder resultat = new StringBuilder();
        while (correspondances.find()) {
            String interieur = correspondances.group().substring(1, correspondances.group().length() - 1);
            correspondances.appendReplacement(resultat, RAPPELS.contains(interieur) ? " " : java.util.regex.Matcher.quoteReplacement(correspondances.group()));
        }
        correspondances.appendTail(resultat);
        return normaliser(resultat);
    }

    static List<Object> lire(Map<?, ?> attributs) {
        List<Object> valeurs = new ArrayList<>();
        for (String champ : List.of("title", "subtitle", "cost", "hp", "power", "text",
                "deployBox", "epicAction", "upgradeHp", "upgradePower")) {
            valeurs.add(List.of("text", "deployBox", "epicAction").contains(champ)
                    ? regle(attributs.get(champ)) : normaliser(attributs.get(champ)));
        }
        valeurs.add(normaliser(objet(relation(attributs, "type").get("attributes")).get("value")));
        for (String champ : List.of("aspects", "aspectDuplicates", "traits", "arenas")) {
            Object donnees = objet(attributs.get(champ)).get("data");
            if (!(donnees instanceof List<?> liste)) return List.of();
            List<String> symboles = new ArrayList<>();
            for (Object element : liste) {
                Map<?, ?> infos = objet(objet(element).get("attributes"));
                String valeur = normaliser(infos.get("englishName"));
                if (valeur.isEmpty()) valeur = normaliser(infos.get("name"));
                if (valeur.isEmpty()) return List.of();
                symboles.add(valeur);
            }
            symboles.sort(String::compareTo);
            valeurs.add(List.copyOf(symboles));
        }
        return List.copyOf(valeurs);
    }

    private static String normaliser(Object valeur) {
        return Normalizer.normalize(Objects.toString(valeur, ""), Normalizer.Form.NFKC)
                .replace("<Return>", " ").replace("\'", "’").replaceAll("(?U)\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }

    private static Map<?, ?> relation(Map<?, ?> attributs, String champ) {
        return objet(objet(attributs.get(champ)).get("data"));
    }

    private static Map<?, ?> objet(Object valeur) {
        return valeur instanceof Map<?, ?> map ? map : Map.of();
    }
}
