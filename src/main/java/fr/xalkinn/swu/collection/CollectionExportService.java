package fr.xalkinn.swu.collection;

import fr.xalkinn.swu.carte.*;
import fr.xalkinn.swu.utilisateur.UtilisateurRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CollectionExportService {
    private static final int MAX_REFERENCES = 100_000;
    private final CollectionService collection;
    private final CarteRepository cartes;
    private final UtilisateurRepository utilisateurs;
    public CollectionExportService(CollectionService collection, CarteRepository cartes, UtilisateurRepository utilisateurs) {
        this.collection=collection; this.cartes=cartes; this.utilisateurs=utilisateurs;
    }
    public record Ligne(String extension, String nomExtension, String nom, String type, String numero,
                        String rarete, Map<String,Integer> affinites, int total, int foil, int hyperspace, int hyperspaceFoil) {
        public Ligne { affinites=Map.copyOf(affinites); }
    }
    public record Fichier(byte[] contenu,String nom,String type) {}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Fichier exporter(Long utilisateurId,String format,String portee,String q,Long extensionId,
                            String version,boolean recherches,FiltresIdentite identite) throws IOException {
        var utilisateur=utilisateurs.findById(utilisateurId).orElseThrow(()->new AccessDeniedException("Compte introuvable."));
        if(!utilisateur.isActif()) throw new AccessDeniedException("Compte désactivé.");
        if(!List.of("xlsx","csv").contains(format)) throw new IllegalArgumentException("Choisissez le format Excel ou CSV.");
        if(!List.of("TOUT","FILTRES").contains(portee)) throw new IllegalArgumentException("Contenu de l’export invalide.");
        boolean filtrer="FILTRES".equals(portee);
        List<Ligne> lignes=new ArrayList<>();
        for(int page=0;;page++) {
            var resultat=collection.consulter(utilisateur.getEmail(),filtrer?q:"",filtrer?extensionId:null,
                    filtrer?version:"TOUTES",filtrer&&recherches,page,filtrer?identite:FiltresIdentite.VIDE);
            if(resultat.getTotalElements()>MAX_REFERENCES) throw new IllegalArgumentException("L’export est limité à 100 000 références. Affinez les filtres.");
            Set<Long> ids=new HashSet<>();
            for(var c:resultat) { ids.add(c.idCarte()); for(var v:c.versions()) ids.add(v.idCarte()); }
            Map<Long,Carte> fiches=cartes.findAllById(ids).stream().collect(Collectors.toMap(Carte::getId,c->c));
            for(var groupe:resultat) {
                Carte c=fiches.get(groupe.idCarte());
                if(c==null) throw new IllegalStateException("Une carte de l’export est introuvable.");
                int foil=0,hyper=0,hf=0,total=0;
                for(var v:groupe.versions()) {
                    Carte impression=fiches.get(v.idCarte());
                    if(impression==null) throw new IllegalStateException("Une version de l’export est introuvable.");
                    total+=v.quantite();
                    if(impression.isHyperspace()) { if(v.foil()) hf+=v.quantite(); else hyper+=v.quantite(); }
                    else if(v.foil()) foil+=v.quantite();
                }
                if(total!=groupe.quantite() || foil+hyper+hf>total) throw new IllegalStateException("Les quantités de l’export sont incohérentes.");
                String nom=c.getTitre()+(c.getSousTitre()==null||c.getSousTitre().isBlank()?"":" — "+c.getSousTitre());
                lignes.add(new Ligne(c.getExtension().getCode(),c.getExtension().getNom(),nom,
                        TypeCarteFiltre.libelle(c.getTypeCarte()),c.getNumeroAffiche(),c.getRarete(),c.getAffinites(),total,foil,hyper,hf));
            }
            if(!resultat.hasNext()) break;
        }
        Instant date=Instant.now();
        String nom="collection-swu-"+LocalDate.ofInstant(date,ZoneId.of("Europe/Paris"))+(filtrer?"-selection":"")+"."+format;
        if(format.equals("csv"))return new Fichier(csv(lignes),nom,"text/csv;charset=UTF-8");
        return new Fichier(CollectionXlsx.ecrire(lignes,date),nom,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }
    public static String affinites(Map<String,Integer> valeurs) {
        List<String> labels=new ArrayList<>();
        for(Affinite a:Affinite.values())if(valeurs.getOrDefault(a.name(),0)>0) labels.add(a.getLibelle()+(valeurs.get(a.name())>1?" ×"+valeurs.get(a.name()):""));
        valeurs.keySet().stream().filter(c->{try{Affinite.valueOf(c);return false;}catch(IllegalArgumentException e){return true;}}).sorted().forEach(labels::add);
        return labels.isEmpty()?"Neutre":String.join(" / ",labels);
    }
    /** Deux filtres indépendants, sans perdre une troisième affinité ou un doublon. */
    static Map<String,Integer> affinitesColonne(Map<String,Integer> valeurs,boolean alignement) {
        Map<String,Integer> resultat=new LinkedHashMap<>();
        valeurs.forEach((code,nombre)->{
            boolean estAlignement=code.equals("HEROISM")||code.equals("VILLAINY");
            if(estAlignement==alignement) resultat.put(code,nombre);
        });
        return resultat;
    }
    static String libelleAffinites(Map<String,Integer> valeurs,boolean alignement) {
        var codes=affinitesColonne(valeurs,alignement);
        return codes.isEmpty()?(alignement?"Sans alignement":"Neutre"):affinites(codes);
    }
    private static byte[] csv(List<Ligne> lignes) {
        StringBuilder s=new StringBuilder("\uFEFF");
        ligneCsv(s,List.of("Extension","Nom de la carte","Affinité couleur","Affinité alignement","Type","Numéro","Rareté","Total","Foil","Hyperspace","Hyperspace foil"));
        for(Ligne l:lignes)ligneCsv(s,List.of(l.extension(),l.nom(),libelleAffinites(l.affinites(),false),libelleAffinites(l.affinites(),true),l.type(),l.numero(),l.rarete()==null?"?":l.rarete(),
                Integer.toString(l.total()),Integer.toString(l.foil()),Integer.toString(l.hyperspace()),Integer.toString(l.hyperspaceFoil())));
        return s.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static void ligneCsv(StringBuilder s,List<String> valeurs) {
        for(int i=0;i<valeurs.size();i++) {
            if(i>0)s.append(';');String v=valeurs.get(i);String debut=v.stripLeading();
            // Les textes du catalogue doivent rester du texte dans un tableur.
            if(!debut.isEmpty() && "=+-@".indexOf(debut.charAt(0))>=0 || v.startsWith("\t") || v.startsWith("\r") || v.startsWith("\n"))v="'"+v;
            s.append('"').append(v.replace("\"","\"\"")).append('"');
        }
        s.append("\r\n");
    }
}
