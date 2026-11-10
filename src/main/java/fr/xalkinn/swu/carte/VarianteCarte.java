package fr.xalkinn.swu.carte;

public enum VarianteCarte {
    STANDARD("Standard"), HYPERSPACE("Hyperspace"), SHOWCASE("Showcase"),
    PRESTIGE("Prestige"), PRESTIGE_SERIALISEE("Prestige sérialisée"), PROMO("Promotionnelle"), AUTRE("Autre traitement");
    private final String libelle;

    VarianteCarte(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
