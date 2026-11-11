package fr.xalkinn.swu.deck;

import fr.xalkinn.swu.carte.Carte;
import fr.xalkinn.swu.utilisateur.Utilisateur;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "decks", uniqueConstraints = @UniqueConstraint(name = "uk_deck_copie_meta", columnNames = {"utilisateur_id", "meta_source_id"}))
public class Deck {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;
    @Column(nullable = false, length = 80)
    private String nom;
    @Column(nullable = false, length = 1000)
    private String description;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "leader_id")
    private Carte leader;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "base_id")
    private Carte base;
    @Column(name = "date_creation", nullable = false, updatable = false)
    private Instant dateCreation;
    @Column(name = "date_modification", nullable = false)
    private Instant dateModification;

    @Column(name = "publie_communaute", nullable = false)
    private boolean publieCommunaute;
    @Column(name = "date_publication_communaute")
    private Instant datePublicationCommunaute;

    @Column(name = "meta_source_id", length = 36)
    private String metaSourceId;
    @OneToMany(mappedBy = "deck", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<DeckReserve> reserve = new java.util.ArrayList<>();

    public void marquerCopieMeta(java.util.UUID id) { this.metaSourceId = id.toString(); }
    public String getMetaSourceId() { return metaSourceId; }
    public java.util.List<DeckReserve> getReserve() { return java.util.List.copyOf(reserve); }
    public void definirReserve(Carte carte, int quantite) {
        if (quantite < 0 || quantite > 99) throw new IllegalArgumentException("La quantité doit être comprise entre 0 et 99.");
        DeckReserve ligne = reserve.stream().filter(l -> l.getCarte().getId().equals(carte.getId())).findFirst().orElse(null);
        if (quantite == 0) { if (ligne != null) reserve.remove(ligne); }
        else if (ligne == null) {
            if (reserve.size() >= 300) throw new IllegalArgumentException("La réserve est limitée à 300 références.");
            reserve.add(new DeckReserve(this, carte, quantite));
        } else ligne.modifierQuantite(quantite);
        toucher();
    }
    protected Deck() {}
    public Deck(Utilisateur utilisateur, String nom, String description) {
        this.utilisateur = utilisateur;
        renommer(nom, description);
        this.dateCreation = this.dateModification;
    }
    public void renommer(String nom, String description) {
        String n = nom == null ? "" : nom.strip();
        String d = description == null ? "" : description.strip();
        if (n.isEmpty() || n.length() > 80) throw new IllegalArgumentException("Le nom doit contenir entre 1 et 80 caractères.");
        if (d.length() > 1000) throw new IllegalArgumentException("La description ne doit pas dépasser 1000 caractères.");
        this.nom = n; this.description = d; toucher();
    }
    public void choisirLeader(Carte carte) { this.leader = carte; toucher(); }
    public void choisirBase(Carte carte) { this.base = carte; toucher(); }
    public void toucher() { this.dateModification = Instant.now(); }
    public void publierDansCommunaute(boolean publier) {
        if (publieCommunaute == publier) return;
        publieCommunaute = publier;
        datePublicationCommunaute = publier ? Instant.now() : null;
        toucher();
    }
    public boolean isPublieCommunaute() { return publieCommunaute; }
    public Instant getDatePublicationCommunaute() { return datePublicationCommunaute; }
    public Utilisateur getUtilisateur() { return utilisateur; }
    public Long getId() { return id; }
    public String getNom() { return nom; }
    public String getDescription() { return description; }
    public Carte getLeader() { return leader; }
    public Carte getBase() { return base; }
    public Instant getDateModification() { return dateModification; }
}
