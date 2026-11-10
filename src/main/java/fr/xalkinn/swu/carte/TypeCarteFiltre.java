package fr.xalkinn.swu.carte;

import java.util.List;

public enum TypeCarteFiltre {
    JETON("Jeton", ReferenceCarte.TYPES_JETON),
    UNITE("Unité", List.of("Unit", "Ground Unit", "Space Unit")),
    AMELIORATION("Amélioration", List.of("Upgrade")),
    EVENEMENT("Événement", List.of("Event")),
    LEADER("Leader", List.of("Leader")),
    BASE("Base", List.of("Base"));
    private final String libelle;
    private final List<String> valeurs;
    TypeCarteFiltre(String libelle, List<String> valeurs) { this.libelle = libelle; this.valeurs = valeurs; }
    public String getCode() { return name(); }
    public String getLibelle() { return libelle; }
    public List<String> getValeurs() { return valeurs; }
    public static String libelle(String valeur) {
        if (valeur == null || valeur.isBlank()) return "Type non renseigné";
        for (TypeCarteFiltre type : values()) if (type.valeurs.contains(valeur)) return type.libelle;
        return valeur;
    }
}
