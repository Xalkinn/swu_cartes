package fr.xalkinn.swu.meta;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class MetaStockage {
    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public Etat lire() {
        var s = em.find(MetaSnapshot.class, 1L);
        return s == null ? new Etat("", null, null, false, null) : new Etat(s.payload, s.derniereReussite, s.derniereTentative, s.verrouJusqua != null && s.verrouJusqua.isAfter(Instant.now()), s.erreur);
    }

    @Transactional
    public String commencer(boolean force) {
        var s = em.find(MetaSnapshot.class, 1L, LockModeType.PESSIMISTIC_WRITE);
        if (s == null) throw new IllegalStateException("La migration de la sélection méta est absente.");
        var maintenant = Instant.now();
        if (s.verrouJusqua != null && s.verrouJusqua.isAfter(maintenant)) return null;
        if (s.derniereTentative != null && s.derniereTentative.isAfter(maintenant.minusSeconds(force ? 60 : 900)))
            return null;
        if (!force && s.derniereReussite != null && s.derniereReussite.isAfter(maintenant.minusSeconds(86400)))
            return null;
        s.derniereTentative = maintenant;
        s.verrouJusqua = maintenant.plusSeconds(600);
        s.jeton = UUID.randomUUID().toString();
        return s.jeton;
    }

    @Transactional
    public void terminer(String jeton, String payload) {
        var s = em.find(MetaSnapshot.class, 1L, LockModeType.PESSIMISTIC_WRITE);
        if (s == null || !Objects.equals(s.jeton, jeton)) return;
        s.payload = payload;
        s.derniereReussite = Instant.now();
        s.erreur = null;
        s.verrouJusqua = null;
        s.jeton = null;
    }

    @Transactional
    public void echouer(String jeton) {
        var s = em.find(MetaSnapshot.class, 1L, LockModeType.PESSIMISTIC_WRITE);
        if (s == null || !Objects.equals(s.jeton, jeton)) return;
        s.erreur = "La source n'a pas pu être actualisée. La dernière sélection disponible est conservée.";
        s.verrouJusqua = null;
        s.jeton = null;
    }

    public record Etat(String payload, Instant reussite, Instant tentative, boolean chargement, String erreur) {
    }
}
