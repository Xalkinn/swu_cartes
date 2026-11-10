package fr.xalkinn.swu.meta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "meta_snapshot")
public class MetaSnapshot {
    @Id
    Long id;
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    String payload = "";
    @Column(name = "derniere_reussite")
    Instant derniereReussite;
    @Column(name = "derniere_tentative")
    Instant derniereTentative;
    @Column(name = "verrou_jusqua")
    Instant verrouJusqua;
    @Column(length = 36)
    String jeton;
    @Column(length = 250)
    String erreur;

    protected MetaSnapshot() {
    }
}
