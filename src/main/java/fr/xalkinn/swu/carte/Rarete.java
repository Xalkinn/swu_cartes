package fr.xalkinn.swu.carte;

import java.util.Locale;

public enum Rarete {
    C("Commune"), U("Peu commune"), R("Rare"), L("Légendaire"), S("Spéciale"), INCONNUE("Non renseignée");
    private final String libelle;

    Rarete(String libelle) {
        this.libelle = libelle;
    }

    public String getCode() {
        return name();
    }

    public String getLibelle() {
        return libelle;
    }

    public static String libelle(String code) {
        if (code == null) return INCONNUE.libelle;
        try {
            return valueOf(code).libelle;
        } catch (IllegalArgumentException e) {
            return INCONNUE.libelle;
        }
    }

    // Le caractère stable fourni par la source prime sur son libellé traduit.
    public static String depuisSource(String caractere, String nomAnglais) {
        String c = caractere == null ? "" : caractere.strip().toUpperCase(Locale.ROOT);
        if (!c.isEmpty()) return switch (c) {
            case "C", "U", "R", "L", "S" -> c;
            default -> null;
        };
        String nom = nomAnglais == null ? "" : nomAnglais.strip().toUpperCase(Locale.ROOT);
        return switch (nom) {
            case "COMMON" -> "C";
            case "UNCOMMON" -> "U";
            case "RARE" -> "R";
            case "LEGENDARY" -> "L";
            case "SPECIAL" -> "S";
            default -> null;
        };
    }
}
