package fr.xalkinn.swu.collection;

import fr.xalkinn.swu.carte.Affinite;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

/** Classeur OOXML sans macro, formule externe ni dépendance supplémentaire. */
final class CollectionXlsx {
    private CollectionXlsx() {}
    private static final String NS="http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String XML="<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    static byte[] ecrire(List<CollectionExportService.Ligne> lignes,Instant date) throws IOException {
        return ecrire(lignes,List.of(),date);
    }
    static byte[] ecrire(List<CollectionExportService.Ligne> lignes,List<CollectionExportService.Detail> details,Instant date)throws IOException {
        Map<String,List<CollectionExportService.Ligne>> groupes=new TreeMap<>();
        for(var l:lignes)groupes.computeIfAbsent(l.extension(),k->new ArrayList<>()).add(l);
        if(groupes.isEmpty())groupes.put("Collection",List.of());
        groupes=new LinkedHashMap<>(groupes);groupes.put("Détail versions",List.of());
        StringBuilder wb=new StringBuilder(XML+"<workbook xmlns=\""+NS+"\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        StringBuilder rels=new StringBuilder(XML+"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        StringBuilder types=new StringBuilder(XML+"<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out,StandardCharsets.UTF_8)) {
            ajouter(zip,"_rels/.rels",XML+"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            Set<String> noms=new HashSet<>();int i=0;
            for(var g:groupes.entrySet()) {
                i++;String nom=sanitiserNom(g.getKey(),noms);
                wb.append("<sheet name=\"").append(xml(nom)).append("\" sheetId=\"").append(i).append("\" r:id=\"rId").append(i).append("\"/>");
                rels.append("<Relationship Id=\"rId").append(i).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
                String override="<Override PartName=\"/xl/worksheets/sheet"+i+".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>";
                types.insert(types.indexOf("</Types>"),override);
                ajouter(zip,"xl/worksheets/sheet"+i+".xml",g.getKey().equals("Détail versions")?detail(details):feuille(g.getKey(),g.getValue(),date));
            }
            wb.append("</sheets><calcPr calcId=\"191029\" fullCalcOnLoad=\"1\"/></workbook>");
            rels.append("<Relationship Id=\"rStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            ajouter(zip,"xl/workbook.xml",wb.toString());ajouter(zip,"xl/_rels/workbook.xml.rels",rels.toString());ajouter(zip,"[Content_Types].xml",types.toString());ajouter(zip,"xl/styles.xml",styles());
        }
        return out.toByteArray();
    }
    private static String feuille(String code,List<CollectionExportService.Ligne> lignes,Instant date) {
        String titre=lignes.isEmpty()?"Ma collection — aucune carte dans cette sélection":code+" — "+lignes.get(0).nomExtension();
        long total=lignes.stream().mapToLong(CollectionExportService.Ligne::total).sum();
        String horodatage=DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm",Locale.FRANCE).withZone(ZoneId.of("Europe/Paris")).format(date);
        StringBuilder s=new StringBuilder(XML+"<worksheet xmlns=\""+NS+"\"><sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\"><pane ySplit=\"5\" topLeftCell=\"A6\" activePane=\"bottomLeft\" state=\"frozen\"/><selection pane=\"bottomLeft\" activeCell=\"A6\" sqref=\"A6\"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"22\"/><cols>");
        int[] largeurs={49,34,25,20,12,11,12,12,16,22,14,14,18,22,16,16};for(int i=0;i<16;i++)s.append("<col min=\"").append(i+1).append("\" max=\"").append(i+1).append("\" width=\"").append(largeurs[i]).append("\" customWidth=\"1\"/>");
        s.append("</cols><sheetData><row r=\"1\" ht=\"34\" customHeight=\"1\">");texte(s,"A1",titre,5);s.append("</row><row r=\"2\" ht=\"25\" customHeight=\"1\">");
        texte(s,"A2","Export du "+horodatage+" · "+lignes.size()+" référence(s) · "+total+" exemplaire(s)",6);s.append("</row><row r=\"3\" ht=\"26\" customHeight=\"1\">");
        texte(s,"A3","Total = toutes les versions. Foil = standard foil. Hyperspace = non-foil. Toutes les variantes sont incluses dans le total. L'onglet Détail versions conserve les impressions et séries pour l'import.",6);s.append("</row><row r=\"5\" ht=\"30\" customHeight=\"1\">");
        String[] entetes={"Nom de la carte","Affinité couleur","Affinité alignement","Type","Numéro","Rareté","Total","Foil","Hyperspace","Hyperspace foil","Showcase","Prestige","Prestige foil","Prestige sérialisée","Promos","Autres"};
        for(int i=0;i<16;i++)texte(s,""+(char)('A'+i)+5,entetes[i],4);s.append("</row>");
        int n=5;
        for(var l:lignes) {
            n++;boolean alt=n%2!=0;
            int lignesNom=Arrays.stream(l.nom().split("\\R",-1)).mapToInt(t->Math.max(1,(int)Math.ceil(t.length()/43.0))).sum();
            int lignesAff=Math.max(1,(int)Math.ceil(CollectionExportService.affinites(l.affinites()).length()/31.0));
            int hauteur=Math.max(32,Math.max(lignesNom,lignesAff)*15+5);
            s.append("<row r=\"").append(n).append("\" ht=\"").append(hauteur).append("\" customHeight=\"1\">");
            texte(s,"A"+n,l.nom(),alt?3:2);affinites(s,"B"+n,CollectionExportService.affinitesColonne(l.affinites(),false),alt?1:0,false);affinites(s,"C"+n,CollectionExportService.affinitesColonne(l.affinites(),true),alt?1:0,true);texte(s,"D"+n,l.type(),alt?1:0);texte(s,"E"+n,l.numero(),alt?10:9);
            texte(s,"F"+n,l.rarete()==null?"?":l.rarete(),alt?10:9);nombre(s,"G"+n,l.total(),alt?8:7);nombre(s,"H"+n,l.foil(),alt?8:7);nombre(s,"I"+n,l.hyperspace(),alt?8:7);nombre(s,"J"+n,l.hyperspaceFoil(),alt?8:7);nombre(s,"K"+n,l.showcase(),alt?8:7);nombre(s,"L"+n,l.prestige(),alt?8:7);nombre(s,"M"+n,l.prestigeFoil(),alt?8:7);nombre(s,"N"+n,l.serialisees(),alt?8:7);nombre(s,"O"+n,l.promos(),alt?8:7);nombre(s,"P"+n,l.autres(),alt?8:7);s.append("</row>");
        }
        int somme=n+2;s.append("<row r=\"").append(somme).append("\" ht=\"27\" customHeight=\"1\">");texte(s,"A"+somme,"TOTAL DES LIGNES VISIBLES",4);
        long[] totaux={total,lignes.stream().mapToLong(CollectionExportService.Ligne::foil).sum(),lignes.stream().mapToLong(CollectionExportService.Ligne::hyperspace).sum(),lignes.stream().mapToLong(CollectionExportService.Ligne::hyperspaceFoil).sum(),lignes.stream().mapToLong(CollectionExportService.Ligne::showcase).sum(),lignes.stream().mapToLong(CollectionExportService.Ligne::prestige).sum(),lignes.stream().mapToLong(CollectionExportService.Ligne::prestigeFoil).sum(),lignes.stream().mapToLong(CollectionExportService.Ligne::serialisees).sum(),lignes.stream().mapToLong(CollectionExportService.Ligne::promos).sum(),lignes.stream().mapToLong(CollectionExportService.Ligne::autres).sum()};
        for(int i=0;i<10;i++){char col=(char)('G'+i);s.append("<c r=\"").append(col).append(somme).append("\" s=\"11\">");if(!lignes.isEmpty())s.append("<f>SUBTOTAL(109,").append(col).append("6:").append(col).append(n).append(")</f>");s.append("<v>").append(totaux[i]).append("</v></c>");}s.append("</row>");
        int legende=somme+2;s.append("<row r=\"").append(legende).append("\">");texte(s,"A"+legende,"Rareté : C = commune · U = peu commune · R = rare · L = légendaire · S = spéciale · ? = non renseignée",6);s.append("</row></sheetData>");
        if(!lignes.isEmpty())s.append("<autoFilter ref=\"A5:P").append(n).append("\"/>");
        s.append("<mergeCells count=\"5\"><mergeCell ref=\"A1:P1\"/><mergeCell ref=\"A2:P2\"/><mergeCell ref=\"A3:P3\"/><mergeCell ref=\"A").append(somme).append(":F").append(somme).append("\"/><mergeCell ref=\"A").append(legende).append(":P").append(legende).append("\"/></mergeCells><pageMargins left=\"0.25\" right=\"0.25\" top=\"0.5\" bottom=\"0.5\" header=\"0.2\" footer=\"0.2\"/><pageSetup orientation=\"landscape\" paperSize=\"9\" fitToWidth=\"1\" fitToHeight=\"0\"/></worksheet>");
        return s.toString();
    }
    private static String detail(List<CollectionExportService.Detail> details){
        StringBuilder s=new StringBuilder(XML+"<worksheet xmlns=\""+NS+"\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" state=\"frozen\"/></sheetView></sheetViews><cols>");
        int[] widths={14,49,12,24,17,15,13,24,35,24,24,12,20};
        for(int i=0;i<widths.length;i++)s.append("<col min=\"").append(i+1).append("\" max=\"").append(i+1).append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
        s.append("</cols><sheetData><row r=\"1\" ht=\"32\" customHeight=\"1\">");String[] headers=CollectionExportService.entetesDetail();
        for(int i=0;i<headers.length;i++)texte(s,""+(char)('A'+i)+1,headers[i],4);s.append("</row>");int n=1;
        for(var d:details){n++;s.append("<row r=\"").append(n).append("\" ht=\"42\" customHeight=\"1\">");var valeurs=CollectionExportService.cellules(d);for(int i=0;i<valeurs.size();i++){String adresse=""+(char)('A'+i)+n;if(i==6)nombre(s,adresse,d.quantite(),n%2==0?7:8);else texte(s,adresse,valeurs.get(i),i==2||i==7||i==10?9:(n%2==0?0:1));}s.append("</row>");}
        s.append("</sheetData>");if(n>1)s.append("<autoFilter ref=\"A1:M").append(n).append("\"/>");return s.append("</worksheet>").toString();
    }
    private static void texte(StringBuilder s,String adresse,String valeur,int style){s.append("<c r=\"").append(adresse).append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(valeur)).append("</t></is></c>");}
    private static void nombre(StringBuilder s,String a,long v,int style){s.append("<c r=\"").append(a).append("\" s=\"").append(style).append("\"><v>").append(v).append("</v></c>");}
    private static void affinites(StringBuilder s,String a,Map<String,Integer> codes,int style,boolean alignement) {
        s.append("<c r=\"").append(a).append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is>");int n=0;
        String[] couleurs={"2866A4","247344","AB3F43","806315","596579","252D38"};
        for(Affinite aff:Affinite.values())if(codes.getOrDefault(aff.name(),0)>0){
            if(n++>0)s.append("<r><t xml:space=\"preserve\"> / </t></r>");
            s.append("<r><rPr><b/><color rgb=\"FF").append(couleurs[aff.ordinal()]).append("\"/></rPr><t>").append(xml(aff.getLibelle()+(codes.get(aff.name())>1?" ×"+codes.get(aff.name()):""))).append("</t></r>");
        }
        for(String code:new TreeSet<>(codes.keySet())) { try { Affinite.valueOf(code); } catch(IllegalArgumentException e) { if(n++>0)s.append("<r><t> / </t></r>");s.append("<r><t>").append(xml(code)).append("</t></r>"); } }
        if(n==0)s.append("<t>").append(xml(alignement?"Sans alignement":"Neutre")).append("</t>");s.append("</is></c>");
    }
    private static String sanitiserNom(String code,Set<String> utilises) {
        String base=code.replaceAll("[\\\\/?*\\[\\]:]"," ").replace("'","").strip();if(base.isBlank())base="Extension";if(base.length()>26)base=base.substring(0,26);
        String nom=base;int i=1;while(!utilises.add(nom.toLowerCase(Locale.ROOT)))nom=base+" "+(++i);return nom;
    }
    private static void ajouter(ZipOutputStream z,String nom,String xml)throws IOException{z.putNextEntry(new ZipEntry(nom));z.write(xml.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    private static String xml(String v){if(v==null)return "";StringBuilder s=new StringBuilder();v.codePoints().filter(c->c==9||c==10||c==13||c>=32&&c<=0xD7FF||c>=0xE000&&c<=0xFFFD||c>=0x10000&&c<=0x10FFFF).forEach(s::appendCodePoint);return s.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    private static String styles() {
        StringBuilder s=new StringBuilder(XML+"<styleSheet xmlns=\""+NS+"\"><fonts count=\"5\"><font><sz val=\"11\"/><color rgb=\"FF223349\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"18\"/><color rgb=\"FFF7D17D\"/><name val=\"Calibri\"/></font><font><sz val=\"10\"/><color rgb=\"FF56677B\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FF223349\"/><name val=\"Calibri\"/></font></fonts><fills count=\"4\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF10223A\"/><bgColor indexed=\"64\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF0F4F8\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border><border><left/><right/><top/><bottom style=\"hair\"><color rgb=\"FFD4DFE9\"/></bottom><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"12\">");
        int[][] definitions={{0,0,0,1},{0,0,3,1},{0,4,0,1},{0,4,3,1},{0,1,2,1},{0,2,2,0},{0,3,0,0},{0,0,0,1},{0,0,3,1},{49,0,0,1},{49,0,3,1},{0,1,2,1}};
        for(int i=0;i<definitions.length;i++){int[] d=definitions[i];s.append("<xf numFmtId=\"").append(d[0]).append("\" fontId=\"").append(d[1]).append("\" fillId=\"").append(d[2]).append("\" borderId=\"").append(d[3]).append("\" xfId=\"0\" applyAlignment=\"1\" applyFill=\"1\" applyFont=\"1\" applyNumberFormat=\"1\"><alignment vertical=\"center\" wrapText=\"1\"");if(i>=7)s.append(" horizontal=\"center\"");s.append("/></xf>");}
        return s.append("</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>").toString();
    }
}
