package fr.xalkinn.swu.meta;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Lecture des données publiquement embarquées dans la page des listes, sans exécuter son JavaScript.
 */
public final class MetaSource {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern SCRIPT = Pattern.compile("<script\\b[^>]*>\\s*self\\.__next_f\\.push\\((\\[.*?)\\)\\s*;?\\s*</script>", Pattern.DOTALL);

    private MetaSource() {
    }

    public static Selection lire(String html, LocalDate aujourdHui) {
        if (html == null || html.length() > 8_000_000)
            throw new IllegalArgumentException("Réponse de la source invalide.");
        StringBuilder donnees = new StringBuilder();
        var scripts = SCRIPT.matcher(html);
        while (scripts.find()) {
            JsonNode fragment = JSON.readTree(scripts.group(1));
            if (fragment.isArray() && fragment.size() == 2 && fragment.get(0).asInt() == 1 && fragment.get(1).isTextual())
                donnees.append(fragment.get(1).asText());
        }
        String texte = donnees.toString();
        if (!"Premier".equals(champ(texte, "ssrFormat").asText()))
            throw new IllegalArgumentException("Format Premier absent du flux.");
        JsonNode periode = champ(texte, "ssrMetaBoundary");
        String meta = texte(periode, "label", 160);
        LocalDate debut = LocalDate.parse(texte(periode, "start", 10));
        LocalDate depuis = aujourdHui.minusDays(28).isAfter(debut) ? aujourdHui.minusDays(28) : debut;
        JsonNode listes = champ(texte, "decklists");
        if (!listes.isArray() || listes.size() > 200) throw new IllegalArgumentException("Listes absentes du flux.");
        List<DeckBrut> resultats = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode d : listes) {
            int rang = d.path("standing").asInt(-1);
            if (rang < 1 || rang > 8) continue;
            String dateSource = texte(d.path("tournament"), "startDate", 60);
            LocalDate date = Instant.parse(dateSource.startsWith("$D") ? dateSource.substring(2) : dateSource).atZone(ZoneOffset.UTC).toLocalDate();
            if (date.isBefore(depuis) || date.isAfter(aujourdHui)) continue;
            String id = UUID.fromString(texte(d, "guid", 36)).toString();
            if (!ids.add(id)) throw new IllegalArgumentException("Liste répétée dans le flux.");
            JsonNode cartes = d.path("cards");
            if (!cartes.isArray() || cartes.size() > 200)
                throw new IllegalArgumentException("Composition absente ou invalide.");
            List<CarteBrute> composition = new ArrayList<>();
            int leaders = 0, bases = 0, total = 0, reserve = 0;
            for (JsonNode ligne : cartes) {
                String section = texte(ligne, "section", 20);
                if (!Set.of("Leader", "Base", "MainDeck", "Sideboard").contains(section))
                    throw new IllegalArgumentException("Section inconnue dans une liste.");
                int quantite = ligne.path("count").asInt(-1);
                if (quantite < 1 || quantite > 100) throw new IllegalArgumentException("Quantité invalide.");
                JsonNode c = ligne.path("card");
                String nom = texte(ligne, "cardName", 300);
                if (section.equals("Leader")) {
                    leaders++;
                    if (quantite != 1) throw new IllegalArgumentException("Leader invalide.");
                }
                if (section.equals("Base")) {
                    bases++;
                    if (quantite != 1) throw new IllegalArgumentException("Base invalide.");
                }
                if (section.equals("MainDeck")) total += quantite;
                if (section.equals("Sideboard")) reserve += quantite;
                Integer cout = c.path("cost").isIntegralNumber() ? c.path("cost").asInt() : null;
                composition.add(new CarteBrute(nom, image(c.path("imageUrl").asText("")), section, quantite, cout));
            }
            if (leaders != 1 || bases != 1 || total < 50 || total > 150 || reserve > 30)
                throw new IllegalArgumentException("Une liste Top 8 est incomplète.");
            resultats.add(new DeckBrut(id, texte(d.path("player"), "name", 160), texte(d.path("tournament"), "name", 300), date.toString(), rang, List.copyOf(composition)));
        }
        if (resultats.isEmpty())
            throw new IllegalArgumentException("Aucune liste Top 8 récente complète n'est disponible.");
        resultats.sort(Comparator.comparing(DeckBrut::date).reversed().thenComparingInt(DeckBrut::rang).thenComparing(DeckBrut::id));
        return new Selection(meta, depuis.toString(), List.copyOf(resultats));
    }

    private static JsonNode champ(String texte, String nom) {
        int position = texte.indexOf("\"" + nom + "\":");
        if (position < 0) throw new IllegalArgumentException("Le format de la source a changé.");
        int debut = position + nom.length() + 3;
        while (debut < texte.length() && Character.isWhitespace(texte.charAt(debut))) debut++;
        boolean chaine = false, echappe = false;
        int profondeur = 0;
        for (int i = debut; i < texte.length(); i++) {
            char c = texte.charAt(i);
            if (chaine) {
                if (echappe) echappe = false;
                else if (c == '\\') echappe = true;
                else if (c == '"') {
                    chaine = false;
                    if (profondeur == 0) return JSON.readTree(texte.substring(debut, i + 1));
                }
            } else if (c == '"') chaine = true;
            else if (c == '[' || c == '{') profondeur++;
            else if (c == ']' || c == '}') {
                profondeur--;
                if (profondeur == 0) return JSON.readTree(texte.substring(debut, i + 1));
            }
        }
        throw new IllegalArgumentException("Champ incomplet dans le flux.");
    }

    private static String texte(JsonNode objet, String champ, int max) {
        JsonNode v = objet.path(champ);
        String s = v.asText("").strip();
        if (!v.isTextual() || s.isBlank() || s.length() > max)
            throw new IllegalArgumentException("Champ source absent ou invalide : " + champ);
        return s;
    }

    private static String image(String valeur) {
        try {
            var u = URI.create(valeur);
            if ("https".equals(u.getScheme()) && "cdn.starwarsunlimited.com".equalsIgnoreCase(u.getHost()) && u.getUserInfo() == null && u.getPort() == -1 && valeur.length() < 2000)
                return valeur;
        } catch (IllegalArgumentException ignore) {
        }
        return "";
    }

    public static String serial(String image) {
        try {
            String chemin = URI.create(image).getPath();
            var m = Pattern.compile("(?:^|/)([A-Za-z0-9]{6,24})_(?:EN|FR|DE|ES|IT)_").matcher(chemin);
            return m.find() ? m.group(1).toUpperCase(Locale.ROOT) : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static String ecrire(Selection s) {
        return JSON.writeValueAsString(s);
    }

    public static Selection relire(String s) {
        return s == null || s.isBlank() ? null : JSON.readValue(s, Selection.class);
    }

    public record CarteBrute(String nom, String image, String section, int quantite, Integer cout) {
    }

    public record DeckBrut(String id, String joueur, String tournoi, String date, int rang, List<CarteBrute> cartes) {
    }

    public record Selection(String meta, String depuis, List<DeckBrut> decks) {
    }
}
