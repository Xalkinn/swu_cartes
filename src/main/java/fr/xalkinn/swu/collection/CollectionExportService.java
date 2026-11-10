package fr.xalkinn.swu.collection;

import fr.xalkinn.swu.carte.*;
import fr.xalkinn.swu.utilisateur.UtilisateurRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CollectionExportService {
    private static final int MAX_REFERENCES = 100_000;
    private final CollectionService collection;
    private final CarteRepository cartes;
    private final UtilisateurRepository utilisateurs;

    public CollectionExportService(CollectionService collection, CarteRepository cartes, UtilisateurRepository utilisateurs) {
        this.collection = collection;
        this.cartes = cartes;
        this.utilisateurs = utilisateurs;
    }

    public record Ligne(String extension, String nomExtension, String nom, String type, String numero,
                        String rarete, Map<String, Integer> affinites, int total, int foil, int hyperspace,
                        int hyperspaceFoil, int showcase, int prestige, int prestigeFoil, int serialisees, int promos,
                        int autres) {
        public Ligne {
            affinites = Map.copyOf(affinites);
        }

        public Ligne(String extension, String nomExtension, String nom, String type, String numero, String rarete, Map<String, Integer> affinites, int total, int foil, int hyperspace, int hyperspaceFoil) {
            this(extension, nomExtension, nom, type, numero, rarete, affinites, total, foil, hyperspace, hyperspaceFoil, 0, 0, 0, 0, 0, 0);
        }
    }

    public record Detail(String extension, String nom, String numero, String variante, String codeVariante,
                         String finition, int quantite, String identifiantSource, String series,
                         String extensionReference, String numeroReference, String rarete, String type) {
    }

    static String[] entetesDetail() {
        return new String[]{"Extension", "Nom de la carte", "Numéro", "Variante", "Code variante", "Finition", "Quantité", "Identifiant source", "Numéros de série", "Extension de référence", "Numéro de référence", "Rareté", "Type"};
    }

    static List<String> cellules(Detail d) {
        return List.of(d.extension(), d.nom(), d.numero(), d.variante(), d.codeVariante(), d.finition(), Integer.toString(d.quantite()), d.identifiantSource(), d.series(), d.extensionReference(), d.numeroReference(), d.rarete(), d.type());
    }

    public record Fichier(byte[] contenu, String nom, String type) {
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Fichier exporter(Long utilisateurId, String format, String portee, String q, Long extensionId,
                            String version, boolean recherches, FiltresIdentite identite) throws IOException {
        return exporter(utilisateurId, format, portee, q, extensionId, version, recherches, identite, FiltresCollection.VIDE);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Fichier exporter(Long utilisateurId, String format, String portee, String q, Long extensionId,
                            String version, boolean recherches, FiltresIdentite identite, FiltresCollection filtres) throws IOException {
        var utilisateur = utilisateurs.findById(utilisateurId).orElseThrow(() -> new AccessDeniedException("Compte introuvable."));
        if (!utilisateur.isActif()) throw new AccessDeniedException("Compte désactivé.");
        if (!List.of("xlsx", "csv").contains(format))
            throw new IllegalArgumentException("Choisissez le format Excel ou CSV.");
        if (!List.of("TOUT", "FILTRES").contains(portee))
            throw new IllegalArgumentException("Contenu de l’export invalide.");
        boolean filtrer = "FILTRES".equals(portee);
        List<Ligne> lignes = new ArrayList<>();
        List<Detail> details = new ArrayList<>();
        for (int page = 0; ; page++) {
            var resultat = collection.consulter(utilisateur.getEmail(), filtrer ? q : "", filtrer ? extensionId : null,
                    filtrer ? version : "TOUTES", filtrer && recherches, page, filtrer ? identite : FiltresIdentite.VIDE, null, filtrer ? filtres : FiltresCollection.VIDE);
            if (resultat.getTotalElements() > MAX_REFERENCES)
                throw new IllegalArgumentException("L’export est limité à 100 000 références. Affinez les filtres.");
            Set<Long> ids = new HashSet<>();
            for (var c : resultat) {
                ids.add(c.idCarte());
                for (var v : c.versions()) ids.add(v.idCarte());
            }
            Map<Long, Carte> fiches = cartes.findAllById(ids).stream().collect(Collectors.toMap(Carte::getId, c -> c));
            List<Carte> impressionsConnues = resultat.isEmpty() ? List.of() : cartes.trouverImpressions(resultat.map(CollectionService.CartePossedee::idCarte).getContent());
            for (var groupe : resultat) {
                Carte c = fiches.get(groupe.idCarte());
                if (c == null) throw new IllegalStateException("Une carte de l’export est introuvable.");
                int foil = 0, hyper = 0, hf = 0, total = 0, showcase = 0, prestige = 0, pf = 0, serial = 0, promos = 0, autres = 0;
                for (var v : groupe.versions()) {
                    Carte impression = fiches.get(v.idCarte());
                    if (impression == null) throw new IllegalStateException("Une version de l’export est introuvable.");
                    if (v.foil() && !impression.isFoilAutorise()) {
                        VarianteCarte famille = impression.getVariante();
                        List<Carte> correspondantes = impressionsConnues.stream().filter(candidate -> candidate.getCarteStandard().getId().equals(groupe.idCarte()) && candidate.getVariante() == famille && candidate.getFinition() == FinitionCarte.FOIL).toList();
                        if (correspondantes.size() == 1) impression = correspondantes.get(0);
                    }
                    total += v.quantite();
                    switch (impression.getVariante()) {
                        case STANDARD -> {
                            if (v.foil()) foil += v.quantite();
                        }
                        case HYPERSPACE -> {
                            if (v.foil()) hf += v.quantite();
                            else hyper += v.quantite();
                        }
                        case SHOWCASE -> showcase += v.quantite();
                        case PRESTIGE -> {
                            if (v.foil()) pf += v.quantite();
                            else prestige += v.quantite();
                        }
                        case PRESTIGE_SERIALISEE -> serial += v.quantite();
                        case PROMO -> promos += v.quantite();
                        case AUTRE -> autres += v.quantite();
                    }
                    details.add(new Detail(impression.getExtension().getCode(), impression.getTitre() + (impression.getSousTitre() == null ? "" : " — " + impression.getSousTitre()), impression.getNumeroAffiche(), impression.getVariante().name(), java.util.Objects.toString(impression.getCodeVarianteSource(), ""), v.foil() ? "FOIL" : "NON_FOIL", v.quantite(), impression.getIdentifiantSource(), String.join(" ; ", v.numerosSerie()), c.getExtension().getCode(), c.getNumeroAffiche(), impression.getRarete() == null ? "?" : impression.getRarete(), TypeCarteFiltre.libelle(impression.getTypeCarte())));
                }
                if (total != groupe.quantite() || foil + hyper + hf + showcase + prestige + pf + serial + promos + autres > total)
                    throw new IllegalStateException("Les quantités de l’export sont incohérentes.");
                String nom = c.getTitre() + (c.getSousTitre() == null || c.getSousTitre().isBlank() ? "" : " — " + c.getSousTitre());
                lignes.add(new Ligne(c.getExtension().getCode(), c.getExtension().getNom(), nom,
                        TypeCarteFiltre.libelle(c.getTypeCarte()), c.getNumeroAffiche(), c.getRarete(), c.getAffinites(), total, foil, hyper, hf, showcase, prestige, pf, serial, promos, autres));
            }
            if (!resultat.hasNext()) break;
        }
        Instant date = Instant.now();
        String nom = "collection-swu-" + LocalDate.ofInstant(date, ZoneId.of("Europe/Paris")) + (filtrer ? "-selection" : "") + "." + format;
        if (format.equals("csv")) return new Fichier(csv(details), nom, "text/csv;charset=UTF-8");
        return new Fichier(CollectionXlsx.ecrire(lignes, details, date), nom, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    public static String affinites(Map<String, Integer> valeurs) {
        List<String> labels = new ArrayList<>();
        for (Affinite a : Affinite.values())
            if (valeurs.getOrDefault(a.name(), 0) > 0)
                labels.add(a.getLibelle() + (valeurs.get(a.name()) > 1 ? " ×" + valeurs.get(a.name()) : ""));
        valeurs.keySet().stream().filter(c -> {
            try {
                Affinite.valueOf(c);
                return false;
            } catch (IllegalArgumentException e) {
                return true;
            }
        }).sorted().forEach(labels::add);
        return labels.isEmpty() ? "Neutre" : String.join(" / ", labels);
    }

    /**
     * Deux filtres indépendants, sans perdre une troisième affinité ou un doublon.
     */
    static Map<String, Integer> affinitesColonne(Map<String, Integer> valeurs, boolean alignement) {
        Map<String, Integer> resultat = new LinkedHashMap<>();
        valeurs.forEach((code, nombre) -> {
            boolean estAlignement = code.equals("HEROISM") || code.equals("VILLAINY");
            if (estAlignement == alignement) resultat.put(code, nombre);
        });
        return resultat;
    }

    static String libelleAffinites(Map<String, Integer> valeurs, boolean alignement) {
        var codes = affinitesColonne(valeurs, alignement);
        return codes.isEmpty() ? (alignement ? "Sans alignement" : "Neutre") : affinites(codes);
    }

    private static byte[] csv(List<Detail> details) {
        StringBuilder s = new StringBuilder("\uFEFF");
        ligneCsv(s, List.of(entetesDetail()));
        for (Detail detail : details) ligneCsv(s, cellules(detail));
        return s.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void ligneCsv(StringBuilder s, List<String> valeurs) {
        for (int i = 0; i < valeurs.size(); i++) {
            if (i > 0) s.append(';');
            String v = valeurs.get(i);
            String debut = v.stripLeading();
            // Les textes du catalogue doivent rester du texte dans un tableur.
            if (!debut.isEmpty() && "=+-@".indexOf(debut.charAt(0)) >= 0 || v.startsWith("\t") || v.startsWith("\r") || v.startsWith("\n"))
                v = "'" + v;
            s.append('"').append(v.replace("\"", "\"\"")).append('"');
        }
        s.append("\r\n");
    }
}
