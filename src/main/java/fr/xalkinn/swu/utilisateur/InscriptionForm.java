package fr.xalkinn.swu.utilisateur;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Objet réservé au formulaire : aucun champ de rôle ou de droits.
public record InscriptionForm(

        @NotBlank(message = "Le pseudo est obligatoire.")
        @Pattern(
                regexp = "[a-zA-Z0-9_-]{3,50}",
                message = "Le pseudo doit contenir 3 à 50 caractères : lettres, chiffres, tirets ou underscores."
        )
        String pseudo,

        @NotBlank(message = "L'adresse e-mail est obligatoire.")
        @Email(message = "L'adresse e-mail est invalide.")
        @Size(max = 254, message = "L'adresse e-mail est trop longue.")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(
                min = 12,
                max = 64,
                message = "Le mot de passe doit contenir entre 12 et 64 caractères."
        )
        String motDePasse,

        @NotBlank(message = "Confirmez votre mot de passe.")
        @Size(max = 64, message = "La confirmation est trop longue.")
        String confirmation

) {
}