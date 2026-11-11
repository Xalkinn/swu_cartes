package fr.xalkinn.swu.deck;

import fr.xalkinn.swu.carte.Carte;
import jakarta.persistence.*;

@Entity
@Table(name = "deck_reserve", uniqueConstraints = @UniqueConstraint(columnNames = {"deck_id", "carte_id"}))
public class DeckReserve {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", nullable = false) private Deck deck;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carte_id", nullable = false) private Carte carte;
    @Column(nullable = false) private int quantite;
    protected DeckReserve() { }
    DeckReserve(Deck deck, Carte carte, int quantite) {
        this.deck = deck; this.carte = carte; modifierQuantite(quantite);
    }
    void modifierQuantite(int quantite) {
        if (quantite < 1 || quantite > 99) throw new IllegalArgumentException("La quantité doit être comprise entre 1 et 99.");
        this.quantite = quantite;
    }
    public Carte getCarte() { return carte; }
    public int getQuantite() { return quantite; }
}
