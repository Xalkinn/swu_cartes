package fr.xalkinn.swu.deck;

import fr.xalkinn.swu.carte.*;
import fr.xalkinn.swu.web.FiltreActif;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RechercheDeck(String q, Long extensionId, Integer coutMin, Integer coutMax,
                            List<String> affinites, String mode, DeckService.Zone zone, Integer page,
                            List<String> raretes, List<String> types, List<String> arenes, List<String> traits,
                            String modeTraits) {
    public RechercheDeck {
        q = q == null ? "" : q.strip();
        affinites = FiltresCarte.nettoyer(affinites);
        mode = mode == null ? "TOUTES" : mode;
        zone = zone == null ? DeckService.Zone.PRINCIPAL : zone;
        page = page == null ? 0 : Math.max(0, page);
        raretes = FiltresCarte.nettoyer(raretes);
        types = FiltresCarte.nettoyer(types);
        FiltresIdentite i = new FiltresIdentite(arenes, traits, modeTraits);
        arenes = i.arenes();
        traits = i.traits();
        modeTraits = i.modeTraits();
    }

    public RechercheDeck(String q, Long extensionId, Integer coutMin, Integer coutMax, List<String> affinites, String mode, DeckService.Zone zone, Integer page) {
        this(q, extensionId, coutMin, coutMax, affinites, mode, zone, page, List.of(), List.of(), List.of(), List.of(), "TOUS");
    }

    public RechercheDeck(String q, Long extensionId, Integer coutMin, Integer coutMax, List<String> affinites, String mode, DeckService.Zone zone, Integer page, List<String> raretes, List<String> types) {
        this(q, extensionId, coutMin, coutMax, affinites, mode, zone, page, raretes, types, List.of(), List.of(), "TOUS");
    }

    public FiltresIdentite identite() {
        return new FiltresIdentite(arenes, traits, modeTraits);
    }

    public String url(Long id, int numeroPage) {
        var url = UriComponentsBuilder.fromPath("/decks/" + id).queryParam("q", q)
                .queryParam("mode", mode).queryParam("zone", zone.name()).queryParam("page", Math.max(0, numeroPage));
        if (extensionId != null) url.queryParam("extensionId", extensionId);
        if (coutMin != null) url.queryParam("coutMin", coutMin);
        if (coutMax != null) url.queryParam("coutMax", coutMax);
        if (!affinites.isEmpty()) url.queryParam("affinites", affinites);
        if (!raretes.isEmpty()) url.queryParam("raretes", raretes);
        if (!types.isEmpty()) url.queryParam("types", types);
        if (!arenes.isEmpty()) url.queryParam("arenes", arenes);
        if (!traits.isEmpty()) url.queryParam("traits", traits).queryParam("modeTraits", modeTraits);
        return url.build().encode().toUriString() + "#recherche";
    }

    public String urlZone(Long id, String nouvelleZone) {
        return new RechercheDeck(q, extensionId, coutMin, coutMax, affinites, mode, DeckService.Zone.valueOf(nouvelleZone), 0, raretes, List.of(), List.of(), List.of(), "TOUS").url(id, 0);
    }

    public List<FiltreActif> filtresActifs(Long id) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("zone", List.of(zone.name()));
        if (!q.isBlank()) params.put("q", List.of(q));
        if (extensionId != null) params.put("extensionId", List.of(extensionId.toString()));
        if (coutMin != null) params.put("coutMin", List.of(coutMin.toString()));
        if (coutMax != null) params.put("coutMax", List.of(coutMax.toString()));
        if (!affinites.isEmpty()) params.put("affinites", affinites);
        if (!"TOUTES".equals(mode)) params.put("mode", List.of(mode));
        if (!raretes.isEmpty()) params.put("raretes", raretes);
        if (!types.isEmpty()) params.put("types", types);
        identite().parametres(params);
        List<FiltreActif> resultat = new ArrayList<>();
        List<FiltreActif> nouveaux = new ArrayList<>();
        identite().filtresActifs("/decks/" + id, params, nouveaux);
        for (FiltreActif f : nouveaux) resultat.add(new FiltreActif(f.libelle(), f.url() + "#recherche"));
        if (!q.isBlank()) ajouter(resultat, params, id, "Recherche : " + q, "q", null);
        if (extensionId != null) ajouter(resultat, params, id, "Extension", "extensionId", null);
        if (coutMin != null) ajouter(resultat, params, id, "Coût ≥ " + coutMin, "coutMin", null);
        if (coutMax != null) ajouter(resultat, params, id, "Coût ≤ " + coutMax, "coutMax", null);
        if ("SANS".equals(mode)) ajouter(resultat, params, id, "Sans affinité", "mode", null);
        else for (String a : affinites) {
            String label;
            try {
                label = Affinite.valueOf(a).getLibelle();
            } catch (IllegalArgumentException e) {
                label = a;
            }
            ajouter(resultat, params, id, label, "affinites", a);
        }
        for (String r : raretes) ajouter(resultat, params, id, Rarete.libelle(r), "raretes", r);
        for (String t : types) {
            String label;
            try {
                label = TypeCarteFiltre.valueOf(t).getLibelle();
            } catch (IllegalArgumentException e) {
                label = t;
            }
            ajouter(resultat, params, id, label, "types", t);
        }
        return List.copyOf(resultat);
    }

    private static void ajouter(List<FiltreActif> resultat, Map<String, List<String>> params, Long id, String label, String key, String value) {
        FiltreActif f = FiltreActif.retirer(label, "/decks/" + id, params, key, value);
        resultat.add(new FiltreActif(f.libelle(), f.url() + "#recherche"));
    }
}
