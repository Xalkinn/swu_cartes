package fr.xalkinn.swu.carte;

public enum Affinite {

    VIGILANCE("Vigilance · bleu", "#5896ec"),
    COMMAND("Commandement · vert", "#54bc88"),
    AGGRESSION("Agressivité · rouge", "#ef7979"),
    CUNNING("Ruse · jaune", "#f0ce65"),
    HEROISM("Héroïsme · blanc", "#edf2fa"),
    VILLAINY("Infâmie · noir", "#303644");

    private final String libelle;
    private final String couleur;

    Affinite(String libelle, String couleur) {
        this.libelle = libelle;
        this.couleur = couleur;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getCouleur() {
        return couleur;
    }
}