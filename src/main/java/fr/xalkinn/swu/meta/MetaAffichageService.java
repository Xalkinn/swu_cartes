package fr.xalkinn.swu.meta;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MetaAffichageService {
    private final MetaStockage stockage;
    @PersistenceContext
    private EntityManager em;
    private volatile Catalogue cache = new Catalogue(Instant.EPOCH, Map.of());
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    public MetaAffichageService(MetaStockage stockage) {
        this.stockage = stockage;
    }

    @Transactional(readOnly = true)
    public Vue consulter() {
        MetaStockage.Etat etat = stockage.lire();
        MetaSource.Selection selection = MetaSource.relire(etat.payload());
        if (selection == null) return new Vue("", "", List.of(), etat);
        Map<String, LocaleCarte> catalogue = catalogue();
        List<Deck> decks = new ArrayList<>();
        for (var source : selection.decks()) {
            List<Carte> cartes = source.cartes().stream().map(c -> traduire(c, catalogue)).toList();
            Carte leader = cartes.stream().filter(c -> c.section().equals("Leader")).findFirst().orElseThrow();
            Carte base = cartes.stream().filter(c -> c.section().equals("Base")).findFirst().orElseThrow();
            List<Carte> principale = cartes.stream().filter(c -> c.section().equals("MainDeck")).toList();
            List<Carte> reserve = cartes.stream().filter(c -> c.section().equals("Sideboard")).toList();
            decks.add(new Deck(source.id(), leader.titre() + " · " + base.titre(), leader, base,
                    principale, reserve, principale.stream().mapToInt(Carte::quantite).sum(),
                    reserve.stream().mapToInt(Carte::quantite).sum(), source.joueur(), source.tournoi(),
                    DATE.format(LocalDate.parse(source.date())), source.rang(),
                    (int) cartes.stream().filter(c -> c.id() == null).count()));
        }
        return new Vue(selection.meta(), DATE.format(LocalDate.parse(selection.depuis())), List.copyOf(decks), etat);
    }

    private Carte traduire(MetaSource.CarteBrute c, Map<String, LocaleCarte> catalogue) {
        LocaleCarte locale = catalogue.get(MetaSource.serial(c.image()));
        if (locale == null)
            return new Carte(null, c.nom(), null, c.image(), "Liste source", c.section(), c.quantite(), c.cout());
        return new Carte(locale.id(), locale.titre(), locale.sousTitre(), locale.image(), locale.reference(), c.section(), c.quantite(), locale.cout());
    }

    private synchronized Map<String, LocaleCarte> catalogue() {
        if (cache.date().isAfter(Instant.now().minusSeconds(300))) return cache.cartes();
        // Le préfixe de l'illustration officielle identifie la même impression dans chaque langue.
        // Aucun rapprochement approximatif par titre traduit ou numéro de carte.
        List<Object[]> lignes = em.createQuery("""
                select c.imageRecto, coalesce(s.id,c.id), coalesce(s.titre,c.titre),
                       coalesce(s.sousTitre,c.sousTitre), coalesce(s.imageRecto,c.imageRecto),
                       coalesce(se.code,e.code), coalesce(s.numero,c.numero), coalesce(s.cout,c.cout)
                from Carte c join c.extension e left join c.carteStandard s left join s.extension se
                where c.imageRecto is not null
                """, Object[].class).getResultList();
        Map<String, LocaleCarte> resultat = new HashMap<>();
        Set<String> ambigus = new HashSet<>();
        for (Object[] ligne : lignes) {
            String cle = MetaSource.serial((String) ligne[0]);
            if (cle.isEmpty() || ambigus.contains(cle)) continue;
            var carte = new LocaleCarte((Long) ligne[1], (String) ligne[2], (String) ligne[3], (String) ligne[4],
                    ligne[5] + " · " + String.format(Locale.ROOT, "%03d", ligne[6]), (Integer) ligne[7]);
            LocaleCarte precedente = resultat.putIfAbsent(cle, carte);
            if (precedente != null && !precedente.id().equals(carte.id())) {
                resultat.remove(cle);
                ambigus.add(cle);
            }
        }
        cache = new Catalogue(Instant.now(), Map.copyOf(resultat));
        return cache.cartes();
    }

    private record Catalogue(Instant date, Map<String, LocaleCarte> cartes) {
    }

    private record LocaleCarte(Long id, String titre, String sousTitre, String image, String reference, Integer cout) {
    }

    public record Carte(Long id, String titre, String sousTitre, String image, String reference, String section,
                        int quantite, Integer cout) {
    }

    public record Deck(String id, String nom, Carte leader, Carte base, List<Carte> principale, List<Carte> reserve,
                       int total, int totalReserve, String joueur, String tournoi, String date, int rang,
                       int nonTraduites) {
        public List<Carte> toutes() {
            var resultat = new ArrayList<Carte>();
            resultat.add(leader);
            resultat.add(base);
            resultat.addAll(principale);
            resultat.addAll(reserve);
            return List.copyOf(resultat);
        }
    }

    public record Vue(String meta, String depuis, List<Deck> decks, MetaStockage.Etat etat) {
        public String actualisation() {
            return etat.reussite() == null ? "" : DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE).withZone(ZoneId.of("Europe/Paris")).format(etat.reussite());
        }
    }
}
