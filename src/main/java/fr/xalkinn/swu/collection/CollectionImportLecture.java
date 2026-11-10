package fr.xalkinn.swu.collection;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Lecture limitée aux tableaux d'inventaire exportés par Gestion SWU.
 */
public final class CollectionImportLecture {
    public static final int MAX_OCTETS = 5 * 1024 * 1024, MAX_LIGNES = 20_000;

    public record Ligne(String source, String extension, String numero, String nom, int total, int foil,
                        int hyperspace, int hyperspaceFoil, int showcase, int prestige, int prestigeFoil,
                        String remarque, String erreur, int serialisees, int promos, int autres, String variante,
                        String codeVariante, String finition, String identifiantSource, List<String> series,
                        boolean detail, boolean complet) {
        public Ligne(String source, String extension, String numero, String nom, int total, int foil, int hyperspace, int hyperspaceFoil, int showcase, int prestige, int prestigeFoil, String remarque, String erreur) {
            this(source, extension, numero, nom, total, foil, hyperspace, hyperspaceFoil, showcase, prestige, prestigeFoil, remarque, erreur, 0, 0, 0, "", "", "", "", List.of(), false, false);
        }

        public Ligne {
            series = List.copyOf(series);
        }

        public int speciaux() {
            return showcase + prestige + prestigeFoil + serialisees + promos + autres;
        }
    }

    public record Releve(String nom, List<Ligne> lignes) {
        public Releve {
            lignes = List.copyOf(lignes);
        }
    }

    private record Cellule(String valeur, boolean formule) {
    }

    private CollectionImportLecture() {
    }

    public static Releve lire(String nom, byte[] contenu) {
        if (contenu.length == 0 || contenu.length > MAX_OCTETS)
            throw erreur("Choisissez un fichier non vide de 5 Mo maximum.");
        String fichier = Objects.toString(nom, "").replace('\\', '/');
        fichier = fichier.substring(fichier.lastIndexOf('/') + 1);
        List<Ligne> lignes = new ArrayList<>();
        String extension = fichier.toLowerCase(Locale.ROOT);
        if (extension.endsWith(".xlsx")) xlsx(contenu, lignes);
        else if (extension.endsWith(".csv")) csv(contenu, lignes);
        else
            throw erreur("Utilisez le classeur .xlsx préparé pour Gestion SWU ou un export .csv UTF-8 de l'application.");
        if (lignes.isEmpty())
            throw erreur("Aucune ligne de carte trouvée. Utilisez le fichier préparé ou un export de la collection.");
        return new Releve(fichier.length() > 150 ? fichier.substring(0, 150) : fichier, lignes);
    }

    private static void xlsx(byte[] octets, List<Ligne> lignes) {
        Map<String, byte[]> fichiers = new HashMap<>();
        long taille = 0;
        int nombre = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(octets))) {
            ZipEntry e;
            byte[] tampon = new byte[8192];
            while ((e = zip.getNextEntry()) != null) {
                if (++nombre > 1000) throw erreur("Ce classeur contient trop de fichiers internes.");
                boolean conserver = (e.getName().startsWith("xl/") && e.getName().endsWith(".xml")) || e.getName().equals("xl/_rels/workbook.xml.rels");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int lu;
                long entree = 0;
                while ((lu = zip.read(tampon)) != -1) {
                    taille += lu;
                    entree += lu;
                    if (taille > 32L * 1024 * 1024 || entree > 12L * 1024 * 1024)
                        throw erreur("Le contenu décompressé du classeur dépasse la limite autorisée.");
                    if (conserver) out.write(tampon, 0, lu);
                }
                if (conserver && fichiers.put(e.getName(), out.toByteArray()) != null)
                    throw erreur("Le classeur contient une entrée interne en double.");
            }
        } catch (IOException e) {
            throw erreur("Le classeur est illisible ou endommagé.");
        }
        List<String> textes = new ArrayList<>();
        if (fichiers.containsKey("xl/sharedStrings.xml"))
            for (Element si : elements(xml(fichiers.get("xl/sharedStrings.xml")), "si")) textes.add(texte(si));
        Map<String, String> relations = new HashMap<>();
        for (Element r : elements(xml(fichiers.get("xl/_rels/workbook.xml.rels")), "Relationship")) {
            if ("External".equals(r.getAttribute("TargetMode"))) continue;
            String cible = r.getAttribute("Target");
            String chemin = cible.startsWith("/") ? cible.substring(1) : Path.of("xl").resolve(cible).normalize().toString().replace('\\', '/');
            relations.put(r.getAttribute("Id"), chemin);
        }
        int feuilles = 0;
        boolean detailTrouve = false;
        for (Element sh : elements(xml(fichiers.get("xl/workbook.xml")), "sheet")) {
            if (++feuilles > 100) throw erreur("Le classeur est limité à 100 onglets.");
            String nom = sh.getAttribute("name"), rid = sh.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
            String chemin = relations.get(rid);
            if (chemin == null || !chemin.matches("xl/worksheets/[^/]+\\.xml"))
                throw erreur("Une feuille du classeur est introuvable.");
            if (detailTrouve && !nom.equals("Détail versions")) continue;
            Map<String, Integer> entetes = null;
            int index = 0;
            for (Element row : elements(xml(fichiers.get(chemin)), "row")) {
                if (++index > MAX_LIGNES + 1000) throw erreur("Une feuille contient trop de lignes.");
                Map<Integer, Cellule> valeurs = new HashMap<>();
                for (Element c : elements(row, "c")) {
                    String adresse = c.getAttribute("r");
                    int col = colonne(adresse);
                    if (col < 0 || col > 100) continue;
                    String type = c.getAttribute("t"), v = contenu(c, "v");
                    if ("inlineStr".equals(type)) v = texte(c);
                    if ("s".equals(type)) {
                        try {
                            v = textes.get(Integer.parseInt(v));
                        } catch (RuntimeException ex) {
                            throw erreur("Une cellule texte du classeur est invalide.");
                        }
                    }
                    if (v.length() > 2000) throw erreur("Une cellule du classeur contient un texte trop long.");
                    valeurs.put(col, new Cellule(v, !elements(c, "f").isEmpty() || "e".equals(type)));
                }
                if (entetes == null) {
                    if (index <= 20) entetes = entetes(valeurs);
                    if (entetes != null && entetes.containsKey("quantite") && nom.equals("Détail versions")) {
                        lignes.clear();
                        detailTrouve = true;
                    }
                    continue;
                }
                ajouter(lignes, nom + " · ligne " + row.getAttribute("r"), nom, entetes, valeurs);
            }
        }
    }

    private static void csv(byte[] octets, List<Ligne> lignes) {
        String texte;
        try {
            texte = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(octets)).toString();
        } catch (CharacterCodingException ex) {
            throw erreur("Enregistrez le CSV en UTF-8, avec le séparateur point-virgule.");
        }
        if (texte.startsWith("\uFEFF")) texte = texte.substring(1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder champ = new StringBuilder();
        boolean guillemets = false, ferme = false;
        int debut = 0;
        for (int i = 0; i <= texte.length(); i++) {
            char c = i == texte.length() ? '\n' : texte.charAt(i);
            if (guillemets) {
                if (c == '"') {
                    if (i + 1 < texte.length() && texte.charAt(i + 1) == '"') {
                        champ.append('"');
                        i++;
                    } else {
                        guillemets = false;
                        ferme = true;
                    }
                } else champ.append(c);
            } else if (c == '"' && champ.length() == 0 && !ferme) {
                guillemets = true;
            } else if (c == ';' || c == '\r' || c == '\n') {
                row.add(champ.toString());
                champ.setLength(0);
                ferme = false;
                if (c != ';') {
                    if (row.stream().anyMatch(v -> !v.isBlank())) rows.add(row);
                    row = new ArrayList<>();
                    debut = i + 1;
                    if (c == '\r' && i + 1 < texte.length() && texte.charAt(i + 1) == '\n') i++;
                }
            } else {
                if (ferme) throw erreur("CSV invalide : caractère après un guillemet fermant.");
                champ.append(c);
            }
            if (champ.length() > 2000 || row.size() > 100 || rows.size() > MAX_LIGNES + 1 || i - debut > 150_000)
                throw erreur("Le CSV dépasse les limites de lecture.");
        }
        if (guillemets) throw erreur("CSV invalide : guillemet non fermé.");
        if (rows.isEmpty()) throw erreur("Le CSV est vide.");
        Map<String, Integer> entetes = entetes(cellules(rows.get(0)));
        if (entetes == null || !entetes.containsKey("extension"))
            throw erreur("Le CSV doit contenir Extension, Nom de la carte, Numéro et Total, séparés par des points-virgules.");
        for (int i = 1; i < rows.size(); i++)
            ajouter(lignes, "CSV · ligne " + (i + 1), "", entetes, cellules(rows.get(i)));
    }

    private static Map<Integer, Cellule> cellules(List<String> row) {
        Map<Integer, Cellule> map = new HashMap<>();
        for (int i = 0; i < row.size(); i++) map.put(i, new Cellule(row.get(i), false));
        return map;
    }

    private static Map<String, Integer> entetes(Map<Integer, Cellule> row) {
        Map<String, Integer> map = new HashMap<>();
        for (var c : row.entrySet()) {
            String cle = normaliser(c.getValue().valeur());
            if (!cle.isEmpty() && map.put(cle, c.getKey()) != null)
                throw erreur("Le tableau comporte un en-tête en double.");
        }
        if (map.keySet().containsAll(List.of("extension", "nomdelacarte", "numero", "quantite", "finition")))
            return map;
        if (!map.keySet().containsAll(List.of("nomdelacarte", "numero", "total"))) return null;
        for (String obligatoire : List.of("foil", "hyperspace", "hyperspacefoil"))
            if (!map.containsKey(obligatoire))
                throw erreur("Il manque la colonne " + obligatoire + " dans le tableau.");
        return map;
    }

    private static void ajouter(List<Ligne> lignes, String source, String feuille, Map<String, Integer> h, Map<Integer, Cellule> v) {
        if (h.containsKey("quantite") && !h.containsKey("total")) {
            ajouterDetail(lignes, source, h, v);
            return;
        }
        String numero = valeur(h, v, "numero").strip(), nom = valeur(h, v, "nomdelacarte");
        if (numero.isBlank()) return; // Sous-totaux, titres et légendes ne sont pas des cartes.
        String ext = h.containsKey("extension") ? valeur(h, v, "extension").strip() : feuille.strip();
        int total = 0, foil = 0, hyper = 0, hf = 0, show = 0, pres = 0, pf = 0, serial = 0, promos = 0, autres = 0;
        String message = "";
        try {
            if (!ext.matches("[A-Za-z0-9]{2,12}")) throw erreur("Code d'extension invalide.");
            numero = reference(h, v);
            total = nombre(h, v, "total", false);
            foil = nombre(h, v, "foil", false);
            hyper = nombre(h, v, "hyperspace", false);
            hf = nombre(h, v, "hyperspacefoil", false);
            show = nombre(h, v, "showcase", true);
            pres = nombre(h, v, "prestige", true);
            pf = nombre(h, v, "prestigefoil", true);
            serial = nombre(h, v, "prestigeserialisee", true);
            promos = nombre(h, v, "promos", true);
            autres = nombre(h, v, "autres", true);
            if (foil + hyper + hf + show + pres + pf + serial + promos + autres > total)
                throw erreur("Les versions indiquées dépassent le total.");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        String observation = valeur(h, v, "averifier");
        if (normaliser(observation).contains("blocspecial"))
            message = "Référence du bloc SPECIAL à vérifier : " + observation;
        lignes.add(new Ligne(source, ext.toUpperCase(Locale.ROOT), numero, nom, total, foil, hyper, hf, show, pres, pf, valeur(h, v, "remarquessource"), message, serial, promos, autres, "", "", "", "", List.of(), false, h.containsKey("promos") && h.containsKey("autres") && h.containsKey("prestigeserialisee")));
        if (lignes.size() > MAX_LIGNES) throw erreur("L'import est limité à 20 000 lignes de cartes.");
    }

    private static void ajouterDetail(List<Ligne> lignes, String source, Map<String, Integer> h, Map<Integer, Cellule> v) {
        String ext = valeur(h, v, "extension").strip().toUpperCase(Locale.ROOT), numero = valeur(h, v, "numero"), nom = valeur(h, v, "nomdelacarte");
        if (numero.isBlank()) return;
        String variante = valeur(h, v, "variante").strip(), code = valeur(h, v, "codevariante").strip(), finition = valeur(h, v, "finition").strip(), id = valeur(h, v, "identifiantsource").strip(), erreur = "";
        int quantite = 0;
        List<String> series = new ArrayList<>();
        try {
            if (code.matches("[0-9]")) code = "0" + code;
            if (!ext.matches("[A-Z0-9]{2,12}") || id.length() > 100 || code.length() > 24)
                throw erreur("Référence d'impression invalide.");
            numero = reference(h, v);
            quantite = nombre(h, v, "quantite", false);
            if (!finition.equals("FOIL") && !finition.equals("NON_FOIL"))
                throw erreur("Finition FOIL ou NON_FOIL attendue.");
            if (!variante.isBlank()) fr.xalkinn.swu.carte.VarianteCarte.valueOf(variante);
            String liste = valeur(h, v, "numerosdeserie");
            Set<String> uniques = new HashSet<>();
            if (!liste.isBlank()) for (String element : liste.split("[;,]")) {
                String serie = Possession.normaliserSerie(element);
                if (serie.isBlank() || !uniques.add(serie)) throw erreur("Numéro de série vide ou en double.");
                series.add(serie);
            }
            if (series.size() > quantite) throw erreur("Plus de numéros de série que d'exemplaires.");
        } catch (IllegalArgumentException e) {
            erreur = e.getMessage();
        }
        lignes.add(new Ligne(source, ext, numero, nom, quantite, 0, 0, 0, 0, 0, 0, "", erreur, 0, 0, 0, variante, code, finition, id, series, true, true));
        if (lignes.size() > MAX_LIGNES) throw erreur("L'import est limité à 20 000 lignes.");
    }

    private static String reference(Map<String, Integer> h, Map<Integer, Cellule> v) {
        Cellule cellule = v.get(h.getOrDefault("numero", -1));
        if (cellule == null || cellule.formule())
            throw erreur("Le numéro de carte doit être une référence, sans formule.");
        String numero = cellule.valeur().strip();
        String type = valeur(h, v, "type").strip();
        if ((type.equalsIgnoreCase("Jeton") || fr.xalkinn.swu.carte.ReferenceCarte.estJeton(type)) && !fr.xalkinn.swu.carte.ReferenceCarte.jeton(numero))
            numero = "T" + numero;
        return fr.xalkinn.swu.carte.ReferenceCarte.normaliser(numero);
    }

    private static int nombre(Map<String, Integer> h, Map<Integer, Cellule> v, String cle, boolean facultatif) {
        Cellule c = v.get(h.getOrDefault(cle, -1));
        if (c == null || c.valeur().isBlank()) {
            if (facultatif) return 0;
            throw erreur("Quantité ou numéro manquant dans " + cle + ". Utilisez 0 pour une quantité nulle.");
        }
        if (c.formule()) throw erreur("Remplacez les formules et erreurs par des nombres dans " + cle + ".");
        int maximum = cle.equals("numero") ? 999999 : cle.equals("total") ? 1000000 : 9999;
        try {
            int n = new BigDecimal(c.valeur().strip()).intValueExact();
            if (n < 0 || n > maximum) throw new ArithmeticException();
            return n;
        } catch (RuntimeException e) {
            throw erreur("Valeur entière attendue entre 0 et " + maximum + " dans " + cle + ".");
        }
    }

    private static String valeur(Map<String, Integer> h, Map<Integer, Cellule> v, String cle) {
        return v.getOrDefault(h.getOrDefault(cle, -1), new Cellule("", false)).valeur();
    }

    private static int colonne(String ref) {
        int col = 0, n = 0;
        for (char c : ref.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c < 'A' || c > 'Z') break;
            col = col * 26 + c - 'A' + 1;
            if (++n > 3) return -1;
        }
        return n == 0 ? -1 : col - 1;
    }

    private static String normaliser(String texte) {
        return Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static List<Element> elements(Node node, String nom) {
        NodeList list = node instanceof Document d ? d.getElementsByTagNameNS("*", nom) : ((Element) node).getElementsByTagNameNS("*", nom);
        List<Element> out = new ArrayList<>();
        for (int i = 0; i < list.getLength(); i++) out.add((Element) list.item(i));
        return out;
    }

    private static String texte(Element element) {
        StringBuilder s = new StringBuilder();
        for (Element t : elements(element, "t")) s.append(t.getTextContent());
        return s.toString();
    }

    private static String contenu(Element e, String tag) {
        var list = elements(e, tag);
        return list.isEmpty() ? "" : list.get(0).getTextContent();
    }

    private static Document xml(byte[] bytes) {
        if (bytes == null) throw erreur("Le classeur n'est pas un fichier Excel .xlsx valide.");
        try {
            var f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            var builder = f.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override
                public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                    throw e;
                }
            });
            return builder.parse(new InputSource(new ByteArrayInputStream(bytes)));
        } catch (Exception e) {
            throw erreur("Une partie XML du classeur est illisible ou non autorisée.");
        }
    }

    private static IllegalArgumentException erreur(String message) {
        return new IllegalArgumentException(message);
    }
}
