package fr.xalkinn.swu.tournoi;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "tournois")
public class Tournoi {
    public enum Etat {BROUILLON, OUVERT, FERME, EN_COURS, TERMINE, ANNULE}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false, length = 100)
    String nom;
    @Column(nullable = false, columnDefinition = "TEXT")
    String description = "";
    @Column(nullable = false, columnDefinition = "TEXT")
    String recompenses = "";
    @Column(nullable = false, columnDefinition = "TEXT")
    String conditions = "";
    @Column(nullable = false)
    int capacite;
    @Column(name = "meilleur_des", nullable = false)
    int meilleurDes = 3;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    Etat etat = Etat.BROUILLON;
    @Column(name = "date_debut")
    LocalDateTime debut;
    @Column(name = "createur_id", nullable = false)
    Long createurId;
    @Column(name = "date_creation", nullable = false)
    Instant creation = Instant.now();

    protected Tournoi() {
    }

    public Long getId() {
        return id;
    }
}
