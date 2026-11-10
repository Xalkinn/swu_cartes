package fr.xalkinn.swu.collection;

import fr.xalkinn.swu.carte.Carte;
import fr.xalkinn.swu.utilisateur.Utilisateur;

import jakarta.persistence.*;

@Entity
@Table(name = "possessions")
public class Possession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carte_id", nullable = false)
    private Carte carte;

    // Total : non-foils + foils.
    @Column(nullable = false)
    private int quantite;

    // Nombre de foils inclus dans le total.
    @Column(name = "quantite_foil", nullable = false)
    private int quantiteFoil;

    protected Possession() {
    }

    // Compatible avec les anciens ajouts.
    public Possession(Utilisateur utilisateur, Carte carte) {
        this(utilisateur, carte, false);
    }

    public Possession(
            Utilisateur utilisateur,
            Carte carte,
            boolean foil
    ) {
        this.utilisateur = utilisateur;
        this.carte = carte;
        this.quantite = 1;
        this.quantiteFoil = foil ? 1 : 0;
    }

    public void modifierQuantite(int quantite) {
        if (quantite < 1
                || quantite > 9999
                || quantite < quantiteFoil) {

            throw new IllegalArgumentException(
                    "Le total doit être compris entre 1 et 9999 "
                            + "et ne peut pas être inférieur au nombre de foils."
            );
        }

        this.quantite = quantite;
    }

    public void ajuster(int variation, boolean foil) {
        if (variation != 1 && variation != -1) {
            throw new IllegalArgumentException(
                    "Seuls les ajustements +1 et -1 sont autorisés."
            );
        }

        if (variation == -1) {
            int disponibles = foil
                    ? quantiteFoil
                    : getQuantiteClassique();

            if (disponibles == 0) {
                throw new IllegalArgumentException(
                        foil
                                ? "Vous ne possédez aucun exemplaire foil."
                                : "Vous ne possédez aucun exemplaire non-foil."
                );
            }
        }

        int nouveauTotal = quantite + variation;
        int nouveauFoil = quantiteFoil + (foil ? variation : 0);

        if (nouveauTotal > 9999) {
            throw new IllegalArgumentException(
                    "La quantité maximale est de 9999 exemplaires."
            );
        }

        if (nouveauTotal < 0
                || nouveauFoil < 0
                || nouveauFoil > nouveauTotal) {

            throw new IllegalArgumentException(
                    "La répartition des exemplaires est invalide."
            );
        }

        // Si le total atteint zéro, le service supprimera cette possession.
        this.quantite = nouveauTotal;
        this.quantiteFoil = nouveauFoil;
    }

    // Remplacement atomique de la répartition, après validation de l'import.
    public void definirQuantites(int total, int foils) {
        if (total < 1 || total > 9999 || foils < 0 || foils > total) {
            throw new IllegalArgumentException("Répartition de quantités invalide.");
        }
        this.quantite = total;
        this.quantiteFoil = foils;
    }

    public Long getId() {
        return id;
    }

    public Carte getCarte() {
        return carte;
    }

    public int getQuantite() {
        return quantite;
    }

    public int getQuantiteFoil() {
        return quantiteFoil;
    }

    public int getQuantiteClassique() {
        return quantite - quantiteFoil;
    }
}