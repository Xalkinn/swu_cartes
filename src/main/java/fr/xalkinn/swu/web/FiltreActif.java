package fr.xalkinn.swu.web;

import org.springframework.web.util.UriComponentsBuilder;
import java.util.*;

/** Une étiquette de filtre et son lien de retrait, sans conserver l'ancienne page. */
public record FiltreActif(String libelle, String url) {
    public static FiltreActif retirer(String libelle, String chemin, Map<String,List<String>> parametres,
                                      String cle, String valeur) {
        Map<String,List<String>> copie = new LinkedHashMap<>();
        parametres.forEach((nom, valeurs) -> copie.put(nom, new ArrayList<>(valeurs)));
        if (valeur == null) copie.remove(cle);
        else if (copie.containsKey(cle)) {
            copie.get(cle).remove(valeur);
            if (copie.get(cle).isEmpty()) copie.remove(cle);
        }
        return new FiltreActif(libelle, url(chemin, copie));
    }
    public static String url(String chemin, Map<String,List<String>> parametres) {
        var builder = UriComponentsBuilder.fromPath(chemin);
        parametres.forEach((nom,valeurs) -> builder.queryParam(nom,valeurs));
        return builder.build().encode().toUriString();
    }
}
