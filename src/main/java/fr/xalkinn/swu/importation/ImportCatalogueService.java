package fr.xalkinn.swu.importation;

import fr.xalkinn.swu.carte.Carte;
import fr.xalkinn.swu.carte.VarianteCarte;
import fr.xalkinn.swu.carte.FinitionCarte;
import fr.xalkinn.swu.carte.Rarete;
import fr.xalkinn.swu.carte.CarteRepository;
import fr.xalkinn.swu.extension.Extension;
import fr.xalkinn.swu.extension.ExtensionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Sort;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ImportCatalogueService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportCatalogueService.class);

    private final ExtensionRepository extensionRepository;
    private final CarteRepository carteRepository;
    private final RestClient client;
    private final TransactionTemplate transaction;

    public ImportCatalogueService(
            ExtensionRepository extensionRepository,
            CarteRepository carteRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.extensionRepository = extensionRepository;
        this.carteRepository = carteRepository;
        this.transaction = new TransactionTemplate(transactionManager);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(20));

        this.client = RestClient.builder()
                .baseUrl("https://admin.starwarsunlimited.com")
                .requestFactory(factory)
                .build();
    }

    public BilanImport importer(Long extensionId) {
        Extension extension = extensionRepository.findById(extensionId)
                .orElseThrow(() -> new IllegalStateException("Extension introuvable."));

        String code = extension.getCode();

        // Aucune écriture avant la réception de toutes les pages.
        List<CarteSource> donnees = recupererCartes(code);

        return transaction.execute(statut -> {
            Extension extensionActuelle = extensionRepository.findById(extensionId)
                    .orElseThrow(() -> new IllegalStateException("Extension introuvable."));

            if (!extensionActuelle.getCode().equalsIgnoreCase(code)) {
                throw new IllegalStateException("L'extension a changé pendant l'import. Réessayez.");
            }

            int ajouts = 0;
            int misesAJour = 0;
            Map<String, Carte> cartesImportees = new HashMap<>();

            Map<String, Extension> editions=new HashMap<>();
            editions.put(codeOfficiel(code),extensionActuelle);
            // Premier passage : création ou mise à jour des fiches.
            for (CarteSource source : donnees) {
                Carte carte = carteRepository.findByIdentifiantSource(source.id()).orElse(null);

                Extension edition=editions.computeIfAbsent(source.codeEdition(),c->extensionRepository.findByCodeIgnoreCase(c)
                        .orElseGet(()->extensionRepository.save(new Extension(c,source.nomEdition(),null))));
                if (carte == null) {
                    carte = new Carte(source.id(), source.titre(), source.sousTitre(),
                            source.numero(), edition);
                    carteRepository.save(carte);
                    ajouts++;
                } else {
                    carte.actualiser(source.titre(), source.sousTitre(),
                            source.numero(), edition);
                    misesAJour++;
                }

                carte.actualiserImages(source.imageRecto(), source.imageVerso());
                carte.actualiserFiltres(source.cout(), source.affinites());
                carte.actualiserVariante(source.variante().famille(),source.variante().finition(),source.variante().code(),source.variante().libelle(),source.identifiantStandard()!=null||source.variante().famille()!=VarianteCarte.STANDARD||source.variante().finition()==FinitionCarte.FOIL);
                carte.actualiserType(source.typeCarte());
                carte.actualiserRarete(source.rarete());
                carte.actualiserIdentite(source.arenes(), source.traits());
                cartesImportees.put(source.id(), carte);
            }

            // Les liens de référence sont aplatis. Une promotion peut appartenir à une autre édition.
            int nonReliees=0;
            Map<String,CarteSource> sourcesParId=donnees.stream().collect(java.util.stream.Collectors.toMap(CarteSource::id,c->c));
            for (CarteSource source : donnees) {
                Carte carte=cartesImportees.get(source.id());
                if(!carte.isRattachementAttendu()){carte.rattacherA(null);continue;}
                Carte standard=reference(source,cartesImportees,sourcesParId);
                if(standard==null){carte.rattacherA(null);nonReliees++;continue;}
                carte.rattacherA(standard);
                // Même carte de jeu : privilégier son nom français de référence pour la recherche.
                carte.actualiser(standard.getTitre(),standard.getSousTitre(),carte.getNumero(),carte.getExtension());
            }
            carteRepository.flush();
            return new BilanImport(code, ajouts, misesAJour,nonReliees);
        });
    }

    // Ne pas annoter cette méthode avec @Transactional : chaque extension
    // est enregistrée séparément par le TransactionTemplate d'importer().
    public BilanImportGlobal importerToutes() {
        List<Extension> extensions = extensionRepository.findAll(Sort.by("code"));
        if (extensions.isEmpty()) {
            throw new IllegalStateException("Aucune extension n'est enregistrée.");
        }

        List<DetailImport> details = new ArrayList<>();
        for (Extension extension : extensions) {
            try {
                BilanImport bilan = importer(extension.getId());
                details.add(new DetailImport(extension.getCode(), bilan.ajouts(),
                        bilan.misesAJour(), null,bilan.nonReliees()));
            } catch (RuntimeException exception) {
                LOGGER.warn("Échec de l'import de l'extension {}", extension.getCode(), exception);
                String message = exception instanceof IllegalStateException
                        ? exception.getMessage()
                        : "Échec de l'import. Consultez les logs de l'application.";
                if (message == null || message.isBlank()) {
                    message = "Échec de l'import.";
                }
                details.add(new DetailImport(extension.getCode(), 0, 0, message));
            }
        }

        int reussites = (int) details.stream().filter(detail -> detail.erreur() == null).count();
        return new BilanImportGlobal(extensions.size(), reussites, List.copyOf(details));
    }

    private static String codeOfficiel(String code){return "HWD".equalsIgnoreCase(code)?"HMW":code.toUpperCase(Locale.ROOT);}

    private Carte reference(CarteSource source,Map<String,Carte> cartes,Map<String,CarteSource> sources){
        String id=source.identifiantStandard();Set<String> visites=new HashSet<>();
        while(id!=null&&visites.add(id)&&visites.size()<20){
            Carte cible=cartes.get(id);if(cible==null)cible=carteRepository.findByIdentifiantSource(id).orElse(null);
            if(cible!=null&&cible.isReference())return cible;
            CarteSource suivante=sources.get(id);
            if(suivante!=null)id=suivante.identifiantStandard();
            else if(cible!=null&&cible.getCarteStandard()!=null)return cible.getCarteStandard().isReference()?cible.getCarteStandard():null;
            else return null;
        }
        return null;
    }

    private List<CarteSource> recupererCartes(String code){
        String officiel=codeOfficiel(code);Map<String,CarteSource> sources=new java.util.LinkedHashMap<>();
        Map<String,String> criteres=new java.util.LinkedHashMap<>();
        criteres.put("filters[$or][0][expansion][code][$eq]",officiel);
        criteres.put("filters[$or][1][variantOf][expansion][code][$eq]",officiel);
        criteres.put("filters[$or][2][reprintOf][expansion][code][$eq]",officiel);
        for(Map<?,?> fiche:telecharger(criteres,false)){CarteSource source=convertir(fiche);sources.put(source.id(),source);}
        // Certaines références de promotion sont publiées dans une édition distincte.
        Set<String> tentees=new HashSet<>();
        for(int profondeur=0;profondeur<10;profondeur++){
            List<CarteSource> manquantes=sources.values().stream().filter(c->c.identifiantStandard()!=null&&!sources.containsKey(c.identifiantStandard())&&!tentees.contains(c.identifiantStandard())).toList();
            if(manquantes.isEmpty())break;
            for(CarteSource c:manquantes){
                tentees.add(c.identifiantStandard());
                List<Map<?,?>> parents=telecharger(Map.of("filters[id][$eq]",c.identifiantStandard()),true);
                if(parents.isEmpty()&&c.uidStandard()!=null)parents=telecharger(Map.of("filters[cardUid][$eq]",c.uidStandard()),true);
                if(parents.size()==1){CarteSource parent=convertir(parents.get(0));sources.putIfAbsent(parent.id(),parent);
                    if(!parent.id().equals(c.identifiantStandard()))sources.put(c.id(),c.avecParent(parent.id()));}
            }
        }
        if(sources.size()>15000)throw new IllegalStateException("Trop de cartes reçues pour cette extension.");
        reparerRattachements(sources);
        return List.copyOf(sources.values());
    }

    // Les liens officiels sont prioritaires. Une absence, un cycle ou un lien cassé
    // ne sont compensés que par UNE référence de la même édition, aux règles identiques.
    private static void reparerRattachements(Map<String, CarteSource> sources) {
        record Cle(String edition, List<Object> identite) { }
        Map<Cle, List<CarteSource>> references = new HashMap<>();
        for (CarteSource c : sources.values()) {
            if (estReference(c) && !c.identite().isEmpty()) {
                references.computeIfAbsent(new Cle(c.codeEdition(), c.identite()), k -> new ArrayList<>()).add(c);
            }
        }
        int repares = 0;
        for (CarteSource c : List.copyOf(sources.values())) {
            if (racinePresente(c, sources) || c.identite().isEmpty()) continue;
            List<CarteSource> choix = references.getOrDefault(new Cle(c.codeEdition(), c.identite()), List.of());
            if (choix.size() == 1 && !choix.get(0).id().equals(c.id())) {
                sources.put(c.id(), c.avecParent(choix.get(0).id()));
                repares++;
            }
        }
        if (repares > 0) LOGGER.info("{} rattachement(s) retrouvé(s) par identité exacte dans l'édition", repares);
    }

    private static boolean estReference(CarteSource c) {
        return c.identifiantStandard() == null && c.variante().famille() == VarianteCarte.STANDARD
                && c.variante().finition() != FinitionCarte.FOIL;
    }

    private static boolean racinePresente(CarteSource c, Map<String, CarteSource> sources) {
        Set<String> visites = new HashSet<>();
        while (c != null && visites.add(c.id()) && visites.size() <= 20) {
            if (estReference(c)) return true;
            c = sources.get(c.identifiantStandard());
        }
        return false;
    }

    private List<Map<?,?>> telecharger(Map<String,String> criteres,boolean videAutorise){
        List<Map<?,?>> cartes=new ArrayList<>();Set<String> ids=new HashSet<>();int page=1,nombrePages=1,totalAttendu=-1;
        do{
            final int pageDemandee=page;
            Map<String,Object> reponse=client.get().uri(builder->{
                builder.path("/api/card-list").queryParam("locale","fr").queryParam("pagination[page]",pageDemandee).queryParam("pagination[pageSize]",50).queryParam("sort","id:asc");
                criteres.forEach(builder::queryParam);return builder.build();
            }).retrieve().body(new ParameterizedTypeReference<Map<String,Object>>(){});
            if(reponse==null||!(reponse.get("data") instanceof List<?> donnees))throw new IllegalStateException("Réponse inattendue de la source.");
            Map<?,?> pagination=objet(objet(reponse.get("meta")).get("pagination"));int total=entier(pagination.get("total"));nombrePages=entier(pagination.get("pageCount"));
            if(total==0&&videAutorise)return List.of();
            if(entier(pagination.get("page"))!=page||nombrePages<1||nombrePages>300||total<1)throw new IllegalStateException("Pagination invalide ou extension absente de la source.");
            if(totalAttendu<0)totalAttendu=total;else if(totalAttendu!=total)throw new IllegalStateException("Le catalogue source a changé. Réessayez.");
            for(Object valeur:donnees){Map<?,?> fiche=objet(valeur);String id=texte(fiche.get("id"));if(id.isBlank()||!ids.add(id))throw new IllegalStateException("Fiche reçue plusieurs fois.");cartes.add(fiche);}
            page++;
        }while(page<=nombrePages);
        if(cartes.size()!=totalAttendu)throw new IllegalStateException("Liste de cartes incomplète : aucune écriture effectuée.");
        return cartes;
    }
    private static CarteSource convertir(Map<?,?> fiche){
        Map<?,?> a=objet(fiche.get("attributes"));if(!"fr".equals(texte(a.get("locale"))))throw new IllegalStateException("La source n'a pas renvoyé une fiche française.");
        String id=texte(fiche.get("id")),titre=texte(a.get("title")).strip(),sousTitre=texte(a.get("subtitle")).strip();int numero=entier(a.get("cardNumber"));
        if(id.isBlank()||id.length()>100||titre.isBlank()||titre.length()>255||sousTitre.length()>255||numero<1)throw new IllegalStateException("Fiche source invalide.");
        Map<?,?> ed=objet(objet(objet(a.get("expansion")).get("data")).get("attributes"));String code=texte(ed.get("code")).strip(),nom=texte(ed.get("name")).strip();
        if(!code.matches("[A-Za-z0-9]{2,12}")||nom.isBlank()||nom.length()>150)throw new IllegalStateException("Édition source invalide.");
        VarianteSource variante=VarianteSource.lire(a);
        if(variante.famille()==VarianteCarte.AUTRE&&code.matches("(?:P|C|J)[0-9]{2}|[A-Z0-9]{3,4}P"))variante=new VarianteSource(VarianteCarte.PROMO,FinitionCarte.MIXTE,null,"Promotion — traitement non renseigné");
        Map<?,?> parent=objet(objet(a.get("variantOf")).get("data"));
        if(parent.isEmpty()&&variante.famille()!=VarianteCarte.STANDARD)parent=objet(objet(a.get("reprintOf")).get("data"));
        String parentId=texte(parent.get("id")),parentUid=texte(objet(parent.get("attributes")).get("cardUid"));
        return new CarteSource(id,titre,sousTitre.isEmpty()?null:sousTitre,numero,recupererImage(a.get("artFront")),recupererImage(a.get("artBack")),recupererCout(a.get("cost")),recupererAffinites(a),variante,parentId.isBlank()?null:parentId,parentUid.isBlank()?null:parentUid,recupererType(a),recupererRarete(a),recupererArenes(a),recupererTraits(a),code,nom,IdentiteCarteSource.lire(a));
    }

    private static Set<String> nomsRelation(Map<?, ?> attributs, String champ) {
        Object data = objet(attributs.get(champ)).get("data");
        if (data == null) return Set.of();
        if (!(data instanceof List<?> liste)) throw new IllegalStateException("Relation " + champ + " illisible.");
        Set<String> noms = new HashSet<>();
        for (Object item : liste) {
            String nom = fr.xalkinn.swu.carte.FiltresIdentite.normaliserTrait(texte(objet(objet(item).get("attributes")).get("name")));
            if (nom.isBlank() || nom.length() > 80 || nom.codePoints().anyMatch(Character::isISOControl))
                throw new IllegalStateException("Libellé " + champ + " invalide.");
            noms.add(nom);
        }
        return Set.copyOf(noms);
    }
    private static Set<String> recupererTraits(Map<?, ?> attributs) { return nomsRelation(attributs,"traits"); }
    private static Set<String> recupererArenes(Map<?, ?> attributs) {
        Set<String> arenes = new HashSet<>();
        for (String nom : nomsRelation(attributs,"arenas")) {
            String code = switch(nom.toLowerCase(Locale.ROOT)) {
                case "terrestre", "ground" -> "TERRESTRE";
                case "spatiale", "spatial", "space" -> "SPATIALE";
                default -> throw new IllegalStateException("Arène inconnue reçue : " + nom);
            };
            arenes.add(code);
        }
        return Set.copyOf(arenes);
    }

    private static String recupererImage(Object valeur) {
        Map<?, ?> attributs = objet(objet(objet(valeur).get("data")).get("attributes"));
        String url = texte(attributs.get("url")).strip();
        return url.isEmpty() ? null : url;
    }

    private static Integer recupererCout(Object valeur) {
        if (valeur == null) {
            return null;
        }
        int cout = entier(valeur);
        if (cout < 0) {
            throw new IllegalStateException("Une carte possède un coût invalide.");
        }
        return cout;
    }

    private static Map<String, Integer> recupererAffinites(Map<?, ?> attributs) {
        Map<String, Integer> affinites = new HashMap<>();
        ajouterSymboles(affinites, attributs.get("aspects"));
        ajouterSymboles(affinites, attributs.get("aspectDuplicates"));
        return Map.copyOf(affinites);
    }

    private static void ajouterSymboles(Map<String, Integer> affinites, Object relation) {
        Object donnees = objet(relation).get("data");
        if (!(donnees instanceof List<?> symboles)) {
            throw new IllegalStateException("Les affinités d'une carte sont absentes ou illisibles.");
        }
        for (Object symbole : symboles) {
            Map<?, ?> attributs = objet(objet(symbole).get("attributes"));
            String code = texte(attributs.get("englishName")).strip().toUpperCase(Locale.ROOT);
            if (code.isEmpty() || code.length() > 30) {
                throw new IllegalStateException("Une affinité reçue possède un code invalide.");
            }
            affinites.merge(code, 1, Integer::sum);
        }
    }

    private static Map<?, ?> objet(Object valeur) {
        return valeur instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String texte(Object valeur) {
        return valeur == null ? "" : valeur.toString();
    }

    private static int entier(Object valeur) {
        if (!(valeur instanceof Number nombre)) {
            throw new IllegalStateException("Une valeur numérique attendue est absente de la source.");
        }
        return nombre.intValue();
    }

    private static String recupererRarete(Map<?, ?> attributs) {
        Map<?, ?> data = objet(objet(attributs.get("rarity")).get("data"));
        Map<?, ?> infos = objet(data.get("attributes"));
        return Rarete.depuisSource(texte(infos.get("character")), texte(infos.get("englishName")));
    }

    private static String recupererType(Map<?, ?> attributs) {
        Map<?, ?> data = objet(objet(attributs.get("type")).get("data"));
        String type = texte(objet(data.get("attributes")).get("value")).strip();
        if (type.isEmpty() || type.length() > 40) {
            throw new IllegalStateException("Le type d'une carte est absent ou invalide. Import annulé.");
        }
        return type;
    }

    private record CarteSource(String id,String titre,String sousTitre,Integer numero,String imageRecto,String imageVerso,Integer cout,
            Map<String,Integer> affinites,VarianteSource variante,String identifiantStandard,String uidStandard,String typeCarte,String rarete,Set<String> arenes,Set<String> traits,String codeEdition,String nomEdition,List<Object> identite){
        CarteSource avecParent(String parent){return new CarteSource(id,titre,sousTitre,numero,imageRecto,imageVerso,cout,affinites,variante,parent,uidStandard,typeCarte,rarete,arenes,traits,codeEdition,nomEdition,identite);}
    }
    public record BilanImport(String codeExtension,int ajouts,int misesAJour,int nonReliees){public BilanImport(String code,int ajouts,int maj){this(code,ajouts,maj,0);}}
    public record DetailImport(String codeExtension,int ajouts,int misesAJour,String erreur,int nonReliees){public DetailImport(String code,int ajouts,int maj,String erreur){this(code,ajouts,maj,erreur,0);}}
    public record BilanImportGlobal(int total,int reussites,List<DetailImport> details){}
}
