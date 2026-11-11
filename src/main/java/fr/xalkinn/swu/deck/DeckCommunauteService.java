package fr.xalkinn.swu.deck;

import fr.xalkinn.swu.carte.Carte;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
public class DeckCommunauteService {
    private final DeckRepository decks;
    private final DeckCarteRepository lignes;
    public DeckCommunauteService(DeckRepository decks, DeckCarteRepository lignes) {
        this.decks = decks; this.lignes = lignes;
    }

    @Transactional(readOnly = true)
    public Page<ResumePublic> lister(String q, int page) {
        String recherche = q == null ? "" : q.strip();
        if (recherche.length() > 100) throw new IllegalArgumentException("Recherche limitée à 100 caractères.");
        Page<Deck> resultat = decks.chercherPublies(recherche, PageRequest.of(Math.max(0, page), 12));
        if (resultat.getTotalPages() > 0 && resultat.getNumber() >= resultat.getTotalPages())
            resultat = decks.chercherPublies(recherche, PageRequest.of(resultat.getTotalPages() - 1, 12));
        Map<Long,Integer> nombres = new HashMap<>();
        if (!resultat.isEmpty()) for (Object[] compte : lignes.compter(resultat.map(Deck::getId).getContent()))
            nombres.put((Long) compte[0], ((Number) compte[1]).intValue());
        return resultat.map(d -> resume(d, nombres.getOrDefault(d.getId(), 0)));
    }

    @Transactional(readOnly = true)
    public VuePublique consulter(Long id) {
        // Cette lecture ne passe jamais par la vue personnelle ni par ses stocks.
        Deck deck = decks.trouverPublie(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck public introuvable."));
        List<LignePublique> contenu = lignes.findByDeckIdOrderByCarteCoutAscCarteTitreAscCarteIdAsc(id).stream()
                .map(l -> new LignePublique(fiche(l.getCarte()), l.getQuantite())).toList();
        int total = contenu.stream().mapToInt(LignePublique::quantite).sum();
        return new VuePublique(resume(deck, total), contenu, deck.getReserve().stream().map(l -> new LignePublique(fiche(l.getCarte()), l.getQuantite())).toList());
    }

    private ResumePublic resume(Deck d, int total) {
        return new ResumePublic(d.getId(), d.getNom(), d.getDescription(), fiche(d.getLeader()), fiche(d.getBase()),
                total, d.getUtilisateur().getPseudo(), d.getDatePublicationCommunaute());
    }
    private DeckService.Fiche fiche(Carte c) {
        return c == null ? null : new DeckService.Fiche(c.getId(), c.getTitre(), c.getSousTitre(),
                c.getExtension().getCode() + " · " + c.getNumeroAffiche(), c.getImageRecto(), c.getCout(), c.getTypeCarte());
    }
    public record ResumePublic(Long id, String nom, String description, DeckService.Fiche leader, DeckService.Fiche base,
                               int total, String auteur, Instant datePublication) {}
    public record LignePublique(DeckService.Fiche carte, int quantite) {}
    public record VuePublique(ResumePublic deck, List<LignePublique> lignes, List<LignePublique> reserve) {}
}
