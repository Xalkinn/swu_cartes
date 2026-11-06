package fr.xalkinn.swu.actualite;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ActualitesService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActualitesService.class);
    private final RestClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(tache -> {
        Thread thread = new Thread(tache, "swu-actualites");
        thread.setDaemon(true);
        return thread;
    });

    // Ces champs sont lus et modifiés sous le même verrou.
    private List<Actualite> articles = List.of();
    private Instant prochaineLecture = Instant.EPOCH;
    private Instant actualiseLe;
    private boolean chargement;
    private boolean indisponible;

    public ActualitesService() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(12));
        client = RestClient.builder()
                .baseUrl("https://admin.starwarsunlimited.com")
                .requestFactory(factory).build();
    }

    // Réponse immédiate : le réseau officiel ne bloque pas le rendu de l'accueil.
    public synchronized EtatActualites consulter() {
        if (!chargement && !Instant.now().isBefore(prochaineLecture)) {
            chargement = true;
            prochaineLecture = Instant.now().plus(Duration.ofMinutes(30));
            executor.submit(this::actualiser);
        }
        return new EtatActualites(articles, chargement, indisponible, actualiseLe);
    }

    private void actualiser() {
        try {
            List<Actualite> nouvelles = telecharger();
            synchronized (this) {
                articles = nouvelles;
                actualiseLe = Instant.now();
                indisponible = false;
                prochaineLecture = Instant.now().plus(Duration.ofMinutes(30));
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Impossible d'actualiser les actualités officielles SWU", exception);
            synchronized (this) {
                // Conserve le dernier résultat réussi pendant une panne.
                indisponible = true;
                prochaineLecture = Instant.now().plus(Duration.ofMinutes(5));
            }
        } finally {
            synchronized (this) {
                chargement = false;
            }
        }
    }

    private List<Actualite> telecharger() {
        Instant maintenant = Instant.now();
        Map<String, Object> reponse = client.get()
                .uri(builder -> builder.path("/api/articles")
                        .queryParam("locale", "fr")
                        .queryParam("sort", "publishOn:desc")
                        .queryParam("pagination[pageSize]", 5)
                        .queryParam("filters[publishOn][$lte]", maintenant.toString())
                        .queryParam("populate", "mainImage")
                        .queryParam("fields[0]", "title")
                        .queryParam("fields[1]", "description")
                        .queryParam("fields[2]", "slug")
                        .queryParam("fields[3]", "publishOn")
                        .queryParam("fields[4]", "locale")
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (reponse == null || !(reponse.get("data") instanceof List<?> donnees)) {
            throw new IllegalStateException("Réponse d'actualités inattendue.");
        }

        List<Actualite> resultat = new ArrayList<>();
        for (Object donnee : donnees) {
            Map<?, ?> attributs = objet(objet(donnee).get("attributes"));
            if (!"fr".equals(texte(attributs.get("locale")))) {
                continue;
            }
            String titre = texte(attributs.get("title")).strip();
            String slug = texte(attributs.get("slug")).strip();
            Instant date;
            try {
                date = Instant.parse(texte(attributs.get("publishOn")));
            } catch (RuntimeException exception) {
                continue;
            }
            if (titre.isBlank() || !slug.matches("[A-Za-z0-9_-]+") || date.isAfter(maintenant)) {
                continue;
            }

            Map<?, ?> image = objet(objet(objet(attributs.get("mainImage"))
                    .get("data")).get("attributes"));
            Map<?, ?> formats = objet(image.get("formats"));
            String imageUrl = texte(objet(formats.get("large")).get("url"));
            if (imageUrl.isBlank()) {
                imageUrl = texte(image.get("url"));
            }
            if (!imageOfficielle(imageUrl)) {
                imageUrl = null;
            }

            String description = texte(attributs.get("description"))
                    .replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").strip();
            if (description.length() > 220) {
                description = description.substring(0, 217) + "…";
            }
            resultat.add(new Actualite(titre, description,
                    "https://starwarsunlimited.com/fr/articles/" + slug, imageUrl, date.toString()));
        }
        if (resultat.isEmpty()) {
            throw new IllegalStateException("Aucune actualité publiée n'a été reçue.");
        }
        resultat.sort(Comparator.comparing(Actualite::date).reversed());
        return List.copyOf(resultat);
    }

    private static boolean imageOfficielle(String valeur) {
        try {
            URI uri = URI.create(valeur);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "cdn.starwarsunlimited.com".equalsIgnoreCase(uri.getHost());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static Map<?, ?> objet(Object valeur) {
        return valeur instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String texte(Object valeur) {
        return valeur == null ? "" : valeur.toString();
    }

    @PreDestroy
    public void fermer() {
        executor.shutdownNow();
    }

    public record Actualite(String titre, String description, String url, String image, String date) {}

    public record EtatActualites(
            List<Actualite> articles, boolean chargement, boolean indisponible, Instant actualiseLe
    ) {}
}
