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

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "possession_series", joinColumns = @JoinColumn(name = "possession_id"))
    @Column(name = "numero_serie", nullable = false, length = 30)
    private java.util.Set<String> numerosSerie = new java.util.HashSet<>();

    public java.util.Set<String> getNumerosSerie(){return java.util.Set.copyOf(numerosSerie);}
    public static String normaliserSerie(String valeur){
        String v=valeur==null?"":valeur.strip();
        if(v.isBlank())return "";
        if(!v.matches("[0-9]{1,6}/[0-9]{1,6}"))throw new IllegalArgumentException("Numéro de série attendu, par exemple 042/250.");
        String[] parties=v.split("/");int numero=Integer.parseInt(parties[0]),tirage=Integer.parseInt(parties[1]);
        if(numero<1||tirage<1||numero>tirage)throw new IllegalArgumentException("Numéro de série hors tirage.");
        return String.format(java.util.Locale.ROOT,"%0"+Math.max(3,Integer.toString(tirage).length())+"d/%d",numero,tirage);
    }
    public void definirSeries(java.util.Collection<String> series){
        java.util.Set<String> choix=new java.util.HashSet<>();
        for(String serie:series){String normalisee=normaliserSerie(serie);if(normalisee.isEmpty()||!choix.add(normalisee))throw new IllegalArgumentException("Numéro de série vide ou en double.");}
        if(choix.size()>quantite||!choix.isEmpty()&&carte.getVariante()!=fr.xalkinn.swu.carte.VarianteCarte.PRESTIGE_SERIALISEE)throw new IllegalArgumentException("Numéros de série incompatibles avec la possession.");
        numerosSerie.clear();numerosSerie.addAll(choix);
    }

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
        ajuster(variation, foil, "");
    }
    public void ajuster(int variation, boolean foil, String numeroSerie) {
        String serie=normaliserSerie(numeroSerie);
        if(!serie.isEmpty()&&carte.getVariante()!=fr.xalkinn.swu.carte.VarianteCarte.PRESTIGE_SERIALISEE)throw new IllegalArgumentException("Cette carte n'est pas une Prestige sérialisée.");
        if(variation==1&&!serie.isEmpty()&&numerosSerie.contains(serie))throw new IllegalArgumentException("Cet exemplaire sérialisé est déjà dans votre collection.");
        if(variation==-1){
            if(!serie.isEmpty()&&!numerosSerie.contains(serie))throw new IllegalArgumentException("Cet exemplaire sérialisé n'est pas possédé.");
            if(serie.isEmpty()&&quantite<=numerosSerie.size()&&!numerosSerie.isEmpty())throw new IllegalArgumentException("Sélectionnez le numéro de série de l'exemplaire à retirer.");
        }
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
        if(!serie.isEmpty()){if(variation==1)numerosSerie.add(serie);else numerosSerie.remove(serie);}
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