package fr.xalkinn.swu.tournoi;

import fr.xalkinn.swu.utilisateur.Utilisateur;
import jakarta.persistence.*;

@Entity
@Table(name = "tournoi_matchs", uniqueConstraints = @UniqueConstraint(columnNames = {"tournoi_id", "ronde", "position_match"}))
public class MatchTournoi {
    public enum Etat {ATTENTE, PRET, TERMINE, EXEMPT}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournoi_id", nullable = false)
    Tournoi tournoi;
    @Column(nullable = false)
    int ronde;
    @Column(name = "position_match", nullable = false)
    int position;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "joueur1_id")
    Utilisateur joueur1;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "joueur2_id")
    Utilisateur joueur2;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vainqueur_id")
    Utilisateur vainqueur;
    @Column(name = "score1")
    Integer score1;
    @Column(name = "score2")
    Integer score2;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    Etat etat = Etat.ATTENTE;

    protected MatchTournoi() {
    }

    MatchTournoi(Tournoi t, int r, int p) {
        tournoi = t;
        ronde = r;
        position = p;
    }

    boolean resolu() {
        return etat == Etat.TERMINE || etat == Etat.EXEMPT;
    }
}
