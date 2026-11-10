package fr.xalkinn.swu.carte;

import java.util.List;
import java.util.Locale;

/** Le numéro imprimé reste numérique en base ; T distingue l'espace des jetons. */
public final class ReferenceCarte {
    public static final List<String> TYPES_JETON=List.of("Token","Token Unit","Token Upgrade");
    private ReferenceCarte(){}
    public static boolean estJeton(String type){return type!=null&&TYPES_JETON.stream().anyMatch(t->t.equalsIgnoreCase(type.strip()));}
    public static boolean jeton(String valeur){return valeur!=null&&valeur.strip().toUpperCase(Locale.ROOT).startsWith("T");}
    public static int numero(String valeur){
        String v=valeur==null?"":valeur.strip().toUpperCase(Locale.ROOT);
        if(!v.matches("T?[0-9]{1,6}(?:\\.0+)?"))throw new IllegalArgumentException("Référence attendue : 001 pour une carte ou T01 pour un jeton.");
        if(v.startsWith("T"))v=v.substring(1);
        int n=new java.math.BigDecimal(v).intValueExact();
        if(n<1||n>999999)throw new IllegalArgumentException("Le numéro doit être compris entre 1 et 999999.");
        return n;
    }
    public static String afficher(int numero,boolean jeton){return String.format(Locale.ROOT,jeton?"T%02d":"%03d",numero);}
    public static String normaliser(String valeur){return afficher(numero(valeur),jeton(valeur));}
}
