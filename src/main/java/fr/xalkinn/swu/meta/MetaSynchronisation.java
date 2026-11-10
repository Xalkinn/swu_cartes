package fr.xalkinn.swu.meta;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

@Service
public class MetaSynchronisation {
    private static final Logger LOG = LoggerFactory.getLogger(MetaSynchronisation.class);
    private final MetaStockage stockage;
    private final RestClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "swu-meta-synchronisation");
        t.setDaemon(true);
        return t;
    });
    @Value("${gestion-swu.meta.active:true}")
    private boolean active = true;

    public MetaSynchronisation(MetaStockage stockage) {
        this.stockage = stockage;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build());
        factory.setReadTimeout(Duration.ofSeconds(25));
        client = RestClient.builder().baseUrl("https://www.swumetastats.com").requestFactory(factory).defaultHeader("User-Agent", "GestionSWU/1.0").build();
    }

    @EventListener(ContextRefreshedEvent.class)
    public void demarrage() {
        demander(false);
    }

    @Scheduled(cron = "${gestion-swu.meta.cron:0 0 6 * * *}", zone = "Europe/Paris")
    public void quotidien() {
        demander(true);
    }

    public boolean demander(boolean force) {
        if (!active) return false;
        String jeton = stockage.commencer(force);
        if (jeton == null) return false;
        try {
            executor.submit(() -> executer(jeton));
            return true;
        } catch (RejectedExecutionException e) {
            stockage.echouer(jeton);
            return false;
        }
    }

    private void executer(String jeton) {
        try {
            String html = client.get().uri("/decklists").retrieve().body(String.class);
            var selection = MetaSource.lire(html, LocalDate.now(ZoneOffset.UTC));
            stockage.terminer(jeton, MetaSource.ecrire(selection));
            LOG.info("Sélection méta actualisée : {} listes Top 8", selection.decks().size());
        } catch (Exception e) {
            LOG.warn("Échec de l'actualisation méta : {}", e.getClass().getSimpleName());
            stockage.echouer(jeton);
        }
    }

    @PreDestroy
    public void fermer() {
        executor.shutdownNow();
    }
}
