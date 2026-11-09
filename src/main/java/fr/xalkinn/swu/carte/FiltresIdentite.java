package fr.xalkinn.swu.carte;

import fr.xalkinn.swu.web.FiltreActif;
import jakarta.persistence.criteria.*;
import java.text.Normalizer;
import java.util.*;

/** Arènes en OU, traits en ET (ou OU sur demande), combinés avec les autres filtres. */
public record FiltresIdentite(List<String> arenes, List<String> traits, String modeTraits) {
    public static final FiltresIdentite VIDE = new FiltresIdentite(List.of(), List.of(), "TOUS");
    public FiltresIdentite {
        arenes = FiltresCarte.nettoyer(arenes);
        traits = traits == null ? List.of() : traits.stream().filter(Objects::nonNull)
                .map(FiltresIdentite::normaliserTrait).filter(t -> !t.isBlank()).distinct().toList();
        modeTraits = modeTraits == null ? "TOUS" : modeTraits;
    }
    public static String normaliserTrait(String t) { return Normalizer.normalize(t.strip(), Normalizer.Form.NFC); }
    public void verifier() {
        if (arenes.size() > 2 || !List.of("TERRESTRE", "SPATIALE").containsAll(arenes))
            throw new IllegalArgumentException("Arène inconnue.");
        if (!List.of("TOUS", "UN").contains(modeTraits)) throw new IllegalArgumentException("Mode de traits invalide.");
        if (traits.size() > 20 || traits.stream().anyMatch(t -> t.length() > 80 || t.codePoints().anyMatch(Character::isISOControl)))
            throw new IllegalArgumentException("Choisissez au maximum 20 traits valides.");
    }
    // Sous-requêtes corrélées : aucune multiplication des cartes ni des quantités.
    public Predicate condition(From<?, Carte> carte, CommonAbstractCriteria requete, CriteriaBuilder cb) {
        List<Predicate> conditions = new ArrayList<>();
        if (!arenes.isEmpty()) {
            Subquery<Long> sq = requete.subquery(Long.class); Root<Carte> ref = sq.from(Carte.class);
            SetJoin<Carte,String> a = ref.joinSet("arenes");
            sq.select(ref.get("id")).where(cb.equal(ref.get("id"),carte.get("id")),a.in(arenes));
            conditions.add(cb.exists(sq));
        }
        if (!traits.isEmpty()) {
            Subquery<Long> sq = requete.subquery(Long.class); Root<Carte> ref = sq.from(Carte.class);
            SetJoin<Carte,String> t = ref.joinSet("traits");
            sq.select(cb.countDistinct(t)).where(cb.equal(ref.get("id"),carte.get("id")),t.in(traits));
            conditions.add("TOUS".equals(modeTraits) ? cb.equal(sq, (long)traits.size()) : cb.gt(sq,0L));
        }
        return cb.and(conditions.toArray(Predicate[]::new));
    }
    public void parametres(Map<String,List<String>> params) {
        if (!arenes.isEmpty()) params.put("arenes",arenes);
        if (!traits.isEmpty()) { params.put("traits",traits); if (!"TOUS".equals(modeTraits)) params.put("modeTraits",List.of(modeTraits)); }
    }
    public void filtresActifs(String chemin, Map<String,List<String>> params, List<FiltreActif> resultat) {
        for (String a : arenes) resultat.add(FiltreActif.retirer(libelleArene(a),chemin,params,"arenes",a));
        for (String t : traits) resultat.add(FiltreActif.retirer("Trait : " + t,chemin,params,"traits",t));
    }
    public static String libelleArene(String a) { return switch(a) { case "TERRESTRE" -> "Terrestre"; case "SPATIALE" -> "Spatiale"; default -> a; }; }
}
