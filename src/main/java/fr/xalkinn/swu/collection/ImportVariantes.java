package fr.xalkinn.swu.collection;

import fr.xalkinn.swu.carte.Carte;
import fr.xalkinn.swu.carte.FinitionCarte;
import fr.xalkinn.swu.carte.ReferenceCarte;
import fr.xalkinn.swu.carte.VarianteCarte;

import java.util.*;
import java.util.stream.Collectors;

import static fr.xalkinn.swu.collection.CollectionImportService.*;

/**
 * Calcul sans écriture des quantités de chaque impression.
 */
final class ImportVariantes {
    private record Demande(VarianteCarte famille, boolean foil, int quantite) {
    }

    private record LigneResolue(CollectionImportLecture.Ligne source, Carte carte, String erreur) {
    }

    private ImportVariantes() {
    }

    static List<LigneApercu> preparer(CollectionImportLecture.Releve releve, List<Carte> catalogue, Map<Long, Possession> stock, Mode mode) {
        if (releve.lignes().stream().anyMatch(CollectionImportLecture.Ligne::detail))
            return details(releve, catalogue, stock, mode);
        Map<String, List<Carte>> references = catalogue.stream().filter(Carte::isReference).collect(Collectors.groupingBy(c -> cle(c.getExtension().getCode(), c.getNumeroAffiche())));
        Map<Long, List<Carte>> enfants = catalogue.stream().filter(c -> c.getCarteStandard() != null).collect(Collectors.groupingBy(c -> c.getCarteStandard().getId()));
        Map<String, Long> doublons = releve.lignes().stream().filter(l -> !l.erreur().startsWith("Référence du bloc SPECIAL")).collect(Collectors.groupingBy(l -> cle(l.extension(), l.numero()), Collectors.counting()));
        List<LigneApercu> resultats = new ArrayList<>();
        for (var l : releve.lignes()) {
            List<Carte> refs = references.getOrDefault(cle(l.extension(), l.numero()), List.of());
            String erreur = l.erreur();
            if (erreur.isBlank() && doublons.getOrDefault(cle(l.extension(), l.numero()), 0L) > 1)
                erreur = "Référence présente plusieurs fois dans le fichier.";
            if (refs.isEmpty() && l.total() == 0 && erreur.isBlank()) {
                resultats.add(new LigneApercu(l.source(), l.extension() + " · " + l.numero(), l.nom(), 0, 0, 0, 0, false, true, "Aucune quantité et référence absente.", List.of()));
                continue;
            }
            if (erreur.isBlank() && refs.size() != 1)
                erreur = refs.isEmpty() ? "Carte de référence absente. Actualisez le catalogue." : "Référence standard ambiguë.";
            if (!erreur.isBlank()) {
                resultats.add(bloquee(l, erreur));
                continue;
            }
            Carte reference = refs.get(0);
            List<Carte> impressions = new ArrayList<>(enfants.getOrDefault(reference.getId(), List.of()));
            impressions.add(reference);
            try {
                if (l.promos() > 0 || l.autres() > 0)
                    throw new IllegalArgumentException("Pour les promos et autres traitements, utilisez l'onglet Détail versions ou un CSV détaillé afin d'identifier l'édition exacte.");
                int normales = l.total() - l.foil() - l.hyperspace() - l.hyperspaceFoil() - l.speciaux();
                List<Demande> demandes = List.of(new Demande(VarianteCarte.STANDARD, false, normales), new Demande(VarianteCarte.STANDARD, true, l.foil()), new Demande(VarianteCarte.HYPERSPACE, false, l.hyperspace()), new Demande(VarianteCarte.HYPERSPACE, true, l.hyperspaceFoil()), new Demande(VarianteCarte.SHOWCASE, true, l.showcase()), new Demande(VarianteCarte.PRESTIGE, false, l.prestige()), new Demande(VarianteCarte.PRESTIGE, true, l.prestigeFoil()), new Demande(VarianteCarte.PRESTIGE_SERIALISEE, true, l.serialisees()));
                Map<Long, int[]> souhaits = new LinkedHashMap<>();
                // Les anciens fichiers ne décrivent pas les promos et numéros de série : ces stocks restent préservés.
                for (Carte c : impressions)
                    if (l.complet() || c.getVariante() != VarianteCarte.PROMO && c.getVariante() != VarianteCarte.AUTRE && c.getVariante() != VarianteCarte.PRESTIGE_SERIALISEE || c.getVariante() == VarianteCarte.PRESTIGE_SERIALISEE && l.serialisees() > 0)
                        souhaits.put(c.getId(), new int[2]);
                for (Demande d : demandes) {
                    if (d.quantite() == 0) continue;
                    List<Carte> choix = impressions.stream().filter(c -> c.getVariante() == d.famille() && c.getFinition().autorise(d.foil())).toList();
                    List<Carte> explicites = choix.stream().filter(c -> c.getFinition() != FinitionCarte.MIXTE).toList();
                    if (!explicites.isEmpty()) choix = explicites;
                    if (choix.size() != 1)
                        throw new IllegalArgumentException((choix.isEmpty() ? "Version absente du catalogue : " : "Version ambiguë : ") + d.famille().getLibelle() + (d.foil() ? " foil" : "") + ". Actualisez le catalogue ou utilisez un import détaillé.");
                    int[] q = souhaits.computeIfAbsent(choix.get(0).getId(), id -> new int[2]);
                    q[0] += d.quantite();
                    if (d.foil()) q[1] += d.quantite();
                }
                List<Cible> cibles = new ArrayList<>();
                for (var e : souhaits.entrySet())
                    cibles.add(cible(e.getKey(), e.getValue()[0], e.getValue()[1], null, stock, mode));
                resultats.add(prete(l, reference.getTitre(), cibles, "Répartition standard, foil, hyperspace, Showcase et Prestige vérifiée."));
            } catch (IllegalArgumentException ex) {
                resultats.add(bloquee(l, ex.getMessage()));
            }
        }
        return resultats;
    }

    private static List<LigneApercu> details(CollectionImportLecture.Releve releve, List<Carte> catalogue, Map<Long, Possession> stock, Mode mode) {
        List<LigneApercu> resultats = new ArrayList<>();
        Map<Long, List<LigneResolue>> groupes = new LinkedHashMap<>();
        Set<Long> idsBloques = new HashSet<>();
        Map<String, Carte> parSource = catalogue.stream().collect(Collectors.toMap(Carte::getIdentifiantSource, c -> c));
        for (var l : releve.lignes()) {
            if (!l.erreur().isBlank()) {
                catalogue.stream().filter(c -> !l.identifiantSource().isBlank() ? c.getIdentifiantSource().equals(l.identifiantSource()) : cle(c.getExtension().getCode(), c.getNumeroAffiche()).equals(cle(l.extension(), l.numero()))).forEach(c -> idsBloques.add(c.getId()));
                resultats.add(bloquee(l, l.erreur()));
                continue;
            }
            List<Carte> choix = l.identifiantSource().isBlank() ? catalogue.stream().filter(c -> cle(c.getExtension().getCode(), c.getNumeroAffiche()).equals(cle(l.extension(), l.numero()))).toList() : Optional.ofNullable(parSource.get(l.identifiantSource())).map(List::of).orElse(List.of());
            choix = choix.stream().filter(c -> cle(c.getExtension().getCode(), c.getNumeroAffiche()).equals(cle(l.extension(), l.numero())) && (l.variante().isBlank() || c.getVariante().name().equals(l.variante())) && (l.codeVariante().isBlank() || l.codeVariante().equals(c.getCodeVarianteSource()))).toList();
            if (choix.size() != 1) {
                catalogue.stream().filter(c -> !l.identifiantSource().isBlank() ? c.getIdentifiantSource().equals(l.identifiantSource()) : cle(c.getExtension().getCode(), c.getNumeroAffiche()).equals(cle(l.extension(), l.numero()))).forEach(c -> idsBloques.add(c.getId()));
                resultats.add(bloquee(l, choix.isEmpty() ? "Impression absente ou métadonnées incompatibles. Actualisez le catalogue." : "Impression ambiguë : renseignez Identifiant source ou Code variante."));
                continue;
            }
            Carte c = choix.get(0);
            boolean foil = l.finition().equals("FOIL");
            Possession p = stock.get(c.getId());
            boolean historique = foil && p != null && p.getQuantiteFoil() > 0 && l.total() <= p.getQuantiteFoil() && !l.identifiantSource().isBlank() && mode == Mode.ACTUALISER;
            if (!c.getFinition().autorise(foil) && !historique) {
                idsBloques.add(c.getId());
                resultats.add(bloquee(l, "La finition ne correspond pas à cette impression."));
                continue;
            }
            if (!l.series().isEmpty() && c.getVariante() != VarianteCarte.PRESTIGE_SERIALISEE) {
                idsBloques.add(c.getId());
                resultats.add(bloquee(l, "Des numéros de série sont indiqués pour une impression non sérialisée."));
                continue;
            }
            groupes.computeIfAbsent(c.getId(), id -> new ArrayList<>()).add(new LigneResolue(l, c, ""));
        }
        for (var g : groupes.entrySet()) {
            List<LigneResolue> rows = g.getValue();
            var premiere = rows.get(0).source();
            Carte carte = rows.get(0).carte();
            Set<String> finitions = new HashSet<>();
            int total = 0, foils = 0;
            List<String> series = new ArrayList<>();
            boolean doublon = false;
            for (var row : rows) {
                var l = row.source();
                if (!finitions.add(l.finition())) doublon = true;
                total += l.total();
                if (l.finition().equals("FOIL")) foils += l.total();
                series.addAll(l.series());
            }
            var fusion = new CollectionImportLecture.Ligne(premiere.source(), premiere.extension(), premiere.numero(), premiere.nom(), total, 0, 0, 0, 0, 0, 0, "", "");
            try {
                if (idsBloques.contains(carte.getId()))
                    throw new IllegalArgumentException("Une autre ligne de cette impression est invalide : corrigez toutes ses finitions avant import.");
                if (doublon)
                    throw new IllegalArgumentException("Impression et finition présentes plusieurs fois dans le fichier.");
                if (new HashSet<>(series).size() != series.size())
                    throw new IllegalArgumentException("Numéro de série présent plusieurs fois.");
                Cible cible = cible(carte.getId(), total, foils, series, stock, mode);
                resultats.add(prete(fusion, carte.getTitre() + " — " + carte.getLibelleVariante(), List.of(cible), "Impression exacte, finition et numéros de série vérifiés."));
            } catch (IllegalArgumentException e) {
                resultats.add(bloquee(fusion, e.getMessage()));
            }
        }
        return resultats;
    }

    private static Cible cible(Long id, int total, int foils, List<String> series, Map<Long, Possession> stock, Mode mode) {
        Possession p = stock.get(id);
        int avant = p == null ? 0 : p.getQuantite(), avantFoil = p == null ? 0 : p.getQuantiteFoil();
        List<String> connues = p == null ? List.of() : p.getNumerosSerie().stream().sorted().toList();
        int apres = total + (mode == Mode.AJOUTER ? avant : 0), apresFoil = foils + (mode == Mode.AJOUTER ? avantFoil : 0);
        List<String> finales = new ArrayList<>(series == null ? connues : series);
        if (series != null && mode == Mode.AJOUTER) finales.addAll(connues);
        if (apres < 0 || apres > 9999 || apresFoil < 0 || apresFoil > apres)
            throw new IllegalArgumentException("La quantité dépasserait la limite de 9999 ou la répartition est invalide.");
        if (new HashSet<>(finales).size() != finales.size())
            throw new IllegalArgumentException("Un exemplaire sérialisé est déjà possédé.");
        if (finales.size() > apres)
            throw new IllegalArgumentException("Le total serait inférieur aux numéros de série connus. Utilisez un import détaillé pour modifier ces exemplaires.");
        finales.sort(String::compareTo);
        return new Cible(id, avant, avantFoil, apres, apresFoil, connues, List.copyOf(finales));
    }

    private static LigneApercu prete(CollectionImportLecture.Ligne l, String nom, List<Cible> cibles, String detail) {
        return new LigneApercu(l.source(), l.extension() + " · " + l.numero(), nom, l.total(), l.total(), cibles.stream().mapToInt(Cible::avant).sum(), cibles.stream().mapToInt(Cible::apres).sum(), true, false, detail, cibles);
    }

    private static LigneApercu bloquee(CollectionImportLecture.Ligne l, String erreur) {
        return new LigneApercu(l.source(), l.extension() + " · " + l.numero(), l.nom(), l.total(), 0, 0, 0, false, false, erreur, List.of());
    }

    private static String cle(String code, String numero) {
        String c = code.equalsIgnoreCase("HWD") ? "HMW" : code.toUpperCase(Locale.ROOT);
        try {
            return c + ":" + ReferenceCarte.normaliser(numero);
        } catch (IllegalArgumentException e) {
            return c + ":" + numero;
        }
    }
}
