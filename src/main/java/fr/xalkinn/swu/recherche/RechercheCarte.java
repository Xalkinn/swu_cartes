package fr.xalkinn.swu.recherche;
import fr.xalkinn.swu.carte.Carte;
import fr.xalkinn.swu.utilisateur.Utilisateur;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="recherches_cartes", uniqueConstraints=@UniqueConstraint(columnNames={"utilisateur_id","carte_id"}))
public class RechercheCarte {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="utilisateur_id",nullable=false) private Utilisateur utilisateur;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="carte_id",nullable=false) private Carte carte;
    @Column(name="date_ajout",nullable=false) private Instant dateAjout;
    protected RechercheCarte() {}
    public RechercheCarte(Utilisateur utilisateur,Carte carte) { this.utilisateur=utilisateur;this.carte=carte;this.dateAjout=Instant.now(); }
    public Carte getCarte(){return carte;}
}
