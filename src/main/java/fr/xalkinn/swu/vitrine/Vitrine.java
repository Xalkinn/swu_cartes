package fr.xalkinn.swu.vitrine;
import fr.xalkinn.swu.utilisateur.Utilisateur;
import jakarta.persistence.*;

@Entity @Table(name="vitrines")
public class Vitrine {
    @Id @Column(name="utilisateur_id") private Long id;
    @MapsId @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="utilisateur_id") private Utilisateur utilisateur;
    @Column(nullable=false) private boolean publiee;
    @Column(name="partager_collection",nullable=false) private boolean partagerCollection;
    @Column(name="partager_decks",nullable=false) private boolean partagerDecks;
    @Column(name="partager_recherches",nullable=false) private boolean partagerRecherches;
    protected Vitrine(){}
    public Vitrine(Utilisateur utilisateur){this.utilisateur=utilisateur;}
    public void configurer(boolean publiee,boolean collection,boolean decks,boolean recherches){this.publiee=publiee;this.partagerCollection=collection;this.partagerDecks=decks;this.partagerRecherches=recherches;}
    public Utilisateur getUtilisateur(){return utilisateur;}
    public boolean isPubliee(){return publiee;}
    public boolean isPartagerCollection(){return partagerCollection;}
    public boolean isPartagerDecks(){return partagerDecks;}
    public boolean isPartagerRecherches(){return partagerRecherches;}
}
