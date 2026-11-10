package fr.xalkinn.swu.compte;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CompteForm {

    @NotBlank(message = "Saisissez votre pseudo.")
    @Size(min = 3, max = 50, message = "Le pseudo doit comporter entre 3 et 50 caractères.")
    private String pseudo = "";

    @NotBlank(message = "Saisissez votre adresse e-mail.")
    @Email(message = "Saisissez une adresse e-mail valide.")
    @Size(max = 254, message = "L'adresse e-mail est trop longue.")
    private String email = "";

    @Size(min = 1, max = 1024, message = "Saisissez votre mot de passe actuel.")
    private String motDePasseActuel = "";

    @Size(max = 72, message = "Le nouveau mot de passe est trop long.")
    private String nouveauMotDePasse = "";

    @Size(max = 72, message = "La confirmation est trop longue.")
    private String confirmationMotDePasse = "";

    public CompteForm() {
    }

    public CompteForm(String pseudo, String email) {
        this.pseudo = pseudo;
        this.email = email;
    }

    public String getPseudo() {
        return pseudo;
    }

    public void setPseudo(String pseudo) {
        this.pseudo = pseudo == null ? "" : pseudo.strip();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? "" : email.strip();
    }

    public String getMotDePasseActuel() {
        return motDePasseActuel;
    }

    public void setMotDePasseActuel(String valeur) {
        motDePasseActuel = valeur == null ? "" : valeur;
    }

    public String getNouveauMotDePasse() {
        return nouveauMotDePasse;
    }

    public void setNouveauMotDePasse(String valeur) {
        nouveauMotDePasse = valeur == null ? "" : valeur;
    }

    public String getConfirmationMotDePasse() {
        return confirmationMotDePasse;
    }

    public void setConfirmationMotDePasse(String valeur) {
        confirmationMotDePasse = valeur == null ? "" : valeur;
    }
}
