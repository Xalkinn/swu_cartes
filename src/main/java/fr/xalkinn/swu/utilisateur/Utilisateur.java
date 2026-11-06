package fr.xalkinn.swu.utilisateur;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "utilisateurs")
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String pseudo;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "mot_de_passe_hash", nullable = false, length = 255)
    private String motDePasseHash;

    @Column(nullable = false, length = 20)
    private String role = "USER";

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "date_creation", nullable = false, insertable = false, updatable = false)
    private Instant dateCreation;

    protected Utilisateur() {}

    public Utilisateur(String pseudo, String email, String motDePasseHash) {
        this.pseudo = pseudo;
        this.email = email;
        this.motDePasseHash = motDePasseHash;
    }

    // Les champs sont validés par le service avant l'appel.
    // Les paramètres du compte n'utilisent que cette méthode de profil.
    public void modifierProfil(String pseudo, String email) {
        this.pseudo = pseudo;
        this.email = email;
    }

    public void modifierMotDePasseHash(String motDePasseHash) {
        this.motDePasseHash = motDePasseHash;
    }

    // Réservé au service d'administration, qui contrôle les droits et le dernier administrateur.
    public void modifierAcces(String role, boolean actif) {
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            throw new IllegalArgumentException("Rôle invalide.");
        }
        this.role = role;
        this.actif = actif;
    }

    public Long getId() { return id; }
    public String getPseudo() { return pseudo; }
    public String getEmail() { return email; }
    public String getMotDePasseHash() { return motDePasseHash; }
    public String getRole() { return role; }
    public boolean isActif() { return actif; }
    public Instant getDateCreation() { return dateCreation; }
}
