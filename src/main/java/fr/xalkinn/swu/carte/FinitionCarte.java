package fr.xalkinn.swu.carte;

public enum FinitionCarte {
    NON_FOIL, FOIL, MIXTE;
    public boolean autorise(boolean foil){return this==MIXTE||(foil?this==FOIL:this==NON_FOIL);}
}
