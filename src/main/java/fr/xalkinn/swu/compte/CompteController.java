package fr.xalkinn.swu.compte;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CompteController {

    private final CompteService service;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public CompteController(CompteService service) {
        this.service = service;
    }

    @InitBinder("formulaireCompte")
    public void limiterLesChamps(WebDataBinder binder) {
        binder.setAllowedFields("pseudo", "email", "motDePasseActuel",
                "nouveauMotDePasse", "confirmationMotDePasse");
    }

    @GetMapping("/compte")
    public String afficher(Authentication authentication, Model model,
                           HttpServletRequest request, HttpServletResponse response) {
        try {
            CompteService.CompteVue compte = service.consulter(authentication.getName());
            model.addAttribute("compte", compte);
            model.addAttribute("formulaireCompte", new CompteForm(compte.pseudo(), compte.email()));
            return "compte";
        } catch (UsernameNotFoundException exception) {
            logoutHandler.logout(request, response, authentication);
            return "redirect:/login?sessionExpiree";
        }
    }

    @PostMapping("/compte")
    public String enregistrer(
            @Valid @ModelAttribute("formulaireCompte") CompteForm formulaire,
            BindingResult erreurs,
            Authentication authentication,
            Model model,
            RedirectAttributes redirect,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            if (!erreurs.hasErrors()) {
                try {
                    CompteService.ResultatModification resultat = service.modifier(
                            authentication.getName(), formulaire);
                    if (resultat.reconnexionNecessaire()) {
                        // L'enregistrement est déjà validé et la transaction terminée.
                        // Ferme cette session ; le prochain login utilise les nouvelles données.
                        logoutHandler.logout(request, response, authentication);
                        return "redirect:/login?compteModifie";
                    }
                    redirect.addFlashAttribute("succesCompte", resultat.modifie()
                            ? "Vos informations ont été enregistrées."
                            : "Aucune modification à enregistrer.");
                    return "redirect:/compte";
                } catch (CompteService.ChampCompteException exception) {
                    erreurs.rejectValue(exception.getChamp(), "compte.invalide", exception.getMessage());
                } catch (DataIntegrityViolationException exception) {
                    // Une autre inscription/modification peut avoir eu lieu entre-temps.
                    // L'échec de la transaction annule toutes ses modifications.
                    erreurs.reject("compte.doublon",
                            "Ce pseudo ou cette adresse e-mail vient d'être utilisé. Choisissez-en un autre.");
                }
            }
            model.addAttribute("compte", service.consulter(authentication.getName()));
            return "compte";
        } catch (UsernameNotFoundException exception) {
            logoutHandler.logout(request, response, authentication);
            return "redirect:/login?sessionExpiree";
        }
    }
}
