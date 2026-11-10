package fr.xalkinn.swu;

import fr.xalkinn.swu.utilisateur.InscriptionForm;
import fr.xalkinn.swu.utilisateur.Utilisateur;
import fr.xalkinn.swu.utilisateur.UtilisateurRepository;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Controller
public class InscriptionController {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    public InscriptionController(
            UtilisateurRepository utilisateurRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/inscription")
    public String afficherFormulaire(Model model) {
        model.addAttribute(
                "formulaire",
                new InscriptionForm("", "", "", "")
        );

        return "inscription";
    }

    @PostMapping("/inscription")
    public String inscrire(
            @Valid @ModelAttribute("formulaire") InscriptionForm formulaire,
            BindingResult erreurs
    ) {
        // Contrôles des annotations présentes dans InscriptionForm.
        if (erreurs.hasErrors()) {
            return "inscription";
        }

        if (!formulaire.motDePasse().equals(formulaire.confirmation())) {
            erreurs.rejectValue(
                    "confirmation",
                    "confirmation.differente",
                    "Les deux mots de passe ne correspondent pas."
            );
        }

        // BCrypt, utilisé par notre encodeur, accepte au plus 72 octets.
        if (formulaire.motDePasse()
                .getBytes(StandardCharsets.UTF_8).length > 72) {

            erreurs.rejectValue(
                    "motDePasse",
                    "motDePasse.tropLong",
                    "Le mot de passe contient trop de caractères multioctets. Raccourcissez-le."
            );
        }

        String pseudo = formulaire.pseudo();
        String email = formulaire.email().toLowerCase(Locale.ROOT);

        if (utilisateurRepository.existsByPseudoIgnoreCase(pseudo)) {
            erreurs.rejectValue(
                    "pseudo",
                    "pseudo.existant",
                    "Ce pseudo est déjà utilisé."
            );
        }

        if (utilisateurRepository.existsByEmailIgnoreCase(email)) {
            erreurs.rejectValue(
                    "email",
                    "email.existant",
                    "Cette adresse e-mail est déjà utilisée."
            );
        }

        if (erreurs.hasErrors()) {
            return "inscription";
        }

        // Le mot de passe brut n'est jamais enregistré en base.
        String hash = passwordEncoder.encode(formulaire.motDePasse());

        // Le constructeur impose USER et actif = true.
        Utilisateur utilisateur = new Utilisateur(pseudo, email, hash);

        try {
            utilisateurRepository.saveAndFlush(utilisateur);
        } catch (DataIntegrityViolationException exception) {
            // Protège aussi contre deux inscriptions simultanées
            // utilisant le même pseudo ou la même adresse e-mail.
            erreurs.reject(
                    "inscription.conflit",
                    "Impossible de créer ce compte. Le pseudo ou l'adresse e-mail peut déjà être utilisé."
            );

            return "inscription";
        }

        // Après création, l'utilisateur se connecte normalement.
        return "redirect:/login?inscrit";
    }
}