package fr.xalkinn.swu.tournoi;

import fr.xalkinn.swu.utilisateur.Utilisateur;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "tournoi_inscriptions", uniqueConstraints = @UniqueConstraint(columnNames = {"tournoi_id", "utilisateur_id"}))
public class InscriptionTournoi {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournoi_id", nullable = false)
    Tournoi tournoi;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    Utilisateur utilisateur;
    @Column(name = "date_inscription", nullable = false)
    Instant date = Instant.now();

    protected InscriptionTournoi() {
    }

    InscriptionTournoi(Tournoi t, Utilisateur u) {
        tournoi = t;
        utilisateur = u;
    }
}
