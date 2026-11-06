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

    // La date est générée par MySQL lors de l'insertion.
    @Column(
            name = "date_creation",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Instant dateCreation;

    // Constructeur obligatoire pour JPA.
    protected Utilisateur() {
    }

    // Une inscription crée toujours un compte USER actif.
    // Le mot de passe doit être haché AVANT cet appel.
    public Utilisateur(
            String pseudo,
            String email,
            String motDePasseHash
    ) {
        this.pseudo = pseudo;
        this.email = email;
        this.motDePasseHash = motDePasseHash;
    }

    public Long getId() {
        return id;
    }

    public String getPseudo() {
        return pseudo;
    }

    public String getEmail() {
        return email;
    }

    public String getMotDePasseHash() {
        return motDePasseHash;
    }

    public String getRole() {
        return role;
    }

    public boolean isActif() {
        return actif;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}