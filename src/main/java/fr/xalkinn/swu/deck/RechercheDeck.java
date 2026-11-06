package fr.xalkinn.swu.deck;

import org.springframework.web.util.UriComponentsBuilder;
import java.util.List;

// Paramètres de recherche conservés après l'ajout d'une carte.
public record RechercheDeck(String q, Long extensionId, Integer coutMin, Integer coutMax,
                            List<String> affinites, String mode, DeckService.Zone zone, Integer page) {
    public RechercheDeck {
        q = q == null ? "" : q.strip();
        affinites = affinites == null ? List.of() : List.copyOf(affinites);
        mode = mode == null ? "TOUTES" : mode;
        zone = zone == null ? DeckService.Zone.PRINCIPAL : zone;
        page = page == null ? 0 : Math.max(0, page);
    }
    public String url(Long id, int numeroPage) {
        var url = UriComponentsBuilder.fromPath("/decks/" + id).queryParam("q", q)
                .queryParam("mode", mode).queryParam("zone", zone.name()).queryParam("page", Math.max(0, numeroPage));
        if (extensionId != null) url.queryParam("extensionId", extensionId);
        if (coutMin != null) url.queryParam("coutMin", coutMin);
        if (coutMax != null) url.queryParam("coutMax", coutMax);
        if (!affinites.isEmpty()) url.queryParam("affinites", affinites);
        return url.build().encode().toUriString() + "#recherche";
    }
}
