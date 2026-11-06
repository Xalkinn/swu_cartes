package fr.xalkinn.swu.importation;

import fr.xalkinn.swu.carte.Carte;
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

            // Premier passage : création ou mise à jour des fiches.
            for (CarteSource source : donnees) {
                Carte carte = carteRepository.findByIdentifiantSource(source.id()).orElse(null);

                if (carte == null) {
                    carte = new Carte(source.id(), source.titre(), source.sousTitre(),
                            source.numero(), extensionActuelle);
                    carteRepository.save(carte);
                    ajouts++;
                } else {
                    carte.actualiser(source.titre(), source.sousTitre(),
                            source.numero(), extensionActuelle);
                    misesAJour++;
                }

                carte.actualiserImages(source.imageRecto(), source.imageVerso());
                carte.actualiserFiltres(source.cout(), source.affinites());
                carte.actualiserHyperspace(source.hyperspace());
                cartesImportees.put(source.id(), carte);
            }

            // Deuxième passage : les standards existent avant de relier les variantes.
            for (CarteSource source : donnees) {
                Carte carte = cartesImportees.get(source.id());

                if (source.identifiantStandard() == null) {
                    carte.rattacherA(null);
                    continue;
                }

                Carte standard = cartesImportees.get(source.identifiantStandard());
                if (standard == null) {
                    standard = carteRepository.findByIdentifiantSource(source.identifiantStandard())
                            .orElseThrow(() -> new IllegalStateException(
                                    "La carte standard associée à une hyperspace est absente. "
                                            + "L'import a été annulé."));
                }
                carte.rattacherA(standard);
            }

            carteRepository.flush();
            return new BilanImport(code, ajouts, misesAJour);
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
                        bilan.misesAJour(), null));
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

    private List<CarteSource> recupererCartes(String code) {
        List<CarteSource> cartes = new ArrayList<>();
        Set<String> identifiants = new HashSet<>();
        int page = 1;
        int nombrePages;
        int totalAttendu = -1;

        do {
            final int pageDemandee = page;
            Map<String, Object> reponse = client.get()
                    .uri(builder -> builder
                            .path("/api/card-list")
                            .queryParam("locale", "fr")
                            .queryParam("pagination[page]", pageDemandee)
                            .queryParam("pagination[pageSize]", 50)
                            .queryParam("sort", "id:asc")
                            .queryParam("filters[expansion][code][$eq]", code)
                            .queryParam("filters[$or][0][variantOf][id][$null]", true)
                            // Le type 02 évite les indicateurs hyperspace/hasFoil
                            // incohérents sur certaines extensions.
                            .queryParam("filters[$or][1][variantTypes][variantId][$eq]", "02")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (reponse == null || !(reponse.get("data") instanceof List<?> donnees)) {
                throw new IllegalStateException("La source a renvoyé une réponse inattendue.");
            }

            Map<?, ?> pagination = objet(objet(reponse.get("meta")).get("pagination"));
            nombrePages = entier(pagination.get("pageCount"));
            int total = entier(pagination.get("total"));

            if (entier(pagination.get("page")) != page
                    || nombrePages < 1 || nombrePages > 100 || total < 1) {
                throw new IllegalStateException(
                        "Aucune carte trouvée ou pagination invalide. Vérifiez le code de l'extension.");
            }

            if (totalAttendu == -1) {
                totalAttendu = total;
            } else if (total != totalAttendu) {
                throw new IllegalStateException(
                        "Le catalogue source a changé pendant la lecture. Réessayez.");
            }

            for (Object element : donnees) {
                Map<?, ?> fiche = objet(element);
                Map<?, ?> attributs = objet(fiche.get("attributes"));
                String langue = texte(attributs.get("locale"));
                Map<?, ?> expansion = objet(objet(attributs.get("expansion")).get("data"));
                String codeRecu = texte(objet(expansion.get("attributes")).get("code"));
                boolean carteDeBase = objet(attributs.get("variantOf")).get("data") == null;
                boolean hyperspace = estHyperspaceNonFoil(attributs);

                if (!"fr".equals(langue) || !code.equalsIgnoreCase(codeRecu)
                        || (!carteDeBase && !hyperspace)) {
                    throw new IllegalStateException(
                            "Une carte reçue ne correspond pas aux critères de l'import.");
                }

                String id = texte(fiche.get("id"));
                String titre = texte(attributs.get("title")).strip();
                String sousTitre = texte(attributs.get("subtitle")).strip();
                int numero = entier(attributs.get("cardNumber"));

                if (id.isBlank() || id.length() > 100 || titre.isBlank()
                        || titre.length() > 255 || sousTitre.length() > 255
                        || numero < 1 || !identifiants.add(id)) {
                    throw new IllegalStateException(
                            "Une fiche source est invalide ou apparaît plusieurs fois.");
                }

                cartes.add(new CarteSource(
                        id, titre, sousTitre.isEmpty() ? null : sousTitre, numero,
                        recupererImage(attributs.get("artFront")),
                        recupererImage(attributs.get("artBack")),
                        recupererCout(attributs.get("cost")),
                        recupererAffinites(attributs), hyperspace,
                        recupererIdentifiantStandard(attributs, hyperspace)));
            }
            page++;
        } while (page <= nombrePages);

        if (cartes.size() != totalAttendu) {
            throw new IllegalStateException(
                    "La liste reçue est incomplète. Aucune carte n'a été enregistrée.");
        }
        return cartes;
    }

    private static boolean estHyperspaceNonFoil(Map<?, ?> attributs) {
        Object donnees = objet(attributs.get("variantTypes")).get("data");
        if (!(donnees instanceof List<?> variantes)) {
            return false;
        }
        for (Object variante : variantes) {
            Map<?, ?> informations = objet(objet(variante).get("attributes"));
            if ("02".equals(texte(informations.get("variantId")).strip())) {
                return true;
            }
        }
        return false;
    }

    private static String recupererIdentifiantStandard(Map<?, ?> attributs, boolean hyperspace) {
        if (!hyperspace) {
            return null;
        }
        Map<?, ?> standard = objet(objet(attributs.get("variantOf")).get("data"));
        String identifiant = texte(standard.get("id")).strip();
        if (identifiant.isEmpty() || identifiant.length() > 100) {
            throw new IllegalStateException(
                    "Une hyperspace ne possède pas de lien valide vers sa carte standard.");
        }
        return identifiant;
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

    private record CarteSource(
            String id, String titre, String sousTitre, Integer numero,
            String imageRecto, String imageVerso, Integer cout,
            Map<String, Integer> affinites, boolean hyperspace, String identifiantStandard
    ) {}

    public record BilanImport(String codeExtension, int ajouts, int misesAJour) {}

    public record DetailImport(String codeExtension, int ajouts, int misesAJour, String erreur) {}

    public record BilanImportGlobal(int total, int reussites, List<DetailImport> details) {}
}
