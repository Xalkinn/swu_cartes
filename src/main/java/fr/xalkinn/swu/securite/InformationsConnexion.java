package fr.xalkinn.swu.securite;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class InformationsConnexion {

    // Ces informations seront disponibles dans toutes les pages Thymeleaf.
    @ModelAttribute
    public void ajouterInformations(Authentication authentication, Model model) {

        boolean connecte = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        boolean administrateur = connecte
                && authentication.getAuthorities().stream()
                .anyMatch(droit -> droit.getAuthority().equals("ROLE_ADMIN"));

        model.addAttribute("connecte", connecte);
        model.addAttribute("administrateur", administrateur);
        model.addAttribute(
                "emailConnecte",
                connecte ? authentication.getName() : ""
        );
    }
}