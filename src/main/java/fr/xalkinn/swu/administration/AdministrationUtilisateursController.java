package fr.xalkinn.swu.administration;

import fr.xalkinn.swu.securite.UtilisateurConnecte;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdministrationUtilisateursController {
    private final AdministrationUtilisateursService service;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AdministrationUtilisateursController(AdministrationUtilisateursService service) {
        this.service = service;
    }

    @GetMapping("/admin/utilisateurs")
    public String afficher(@AuthenticationPrincipal UtilisateurConnecte principal,
                           @RequestParam(defaultValue = "") String q,
                           @RequestParam(defaultValue = "") String role,
                           @RequestParam(defaultValue = "") String etat,
                           @RequestParam(defaultValue = "0") int page,
                           Model model, RedirectAttributes redirect) {
        try {
            model.addAttribute("resultat", service.consulter(principal.getUtilisateurId(), q, role, etat, page));
            model.addAttribute("q", q.strip());
            model.addAttribute("roleFiltre", role);
            model.addAttribute("etatFiltre", etat);
            return "admin/utilisateurs";
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("erreurUtilisateurs", exception.getMessage());
            return "redirect:/admin/utilisateurs";
        }
    }

    @PostMapping("/admin/utilisateurs/acces")
    public String modifier(@AuthenticationPrincipal UtilisateurConnecte principal,
                           @RequestParam Long utilisateurId,
                           @RequestParam String role,
                           @RequestParam boolean actif,
                           RedirectAttributes redirect,
                           Authentication authentication,
                           HttpServletRequest request, HttpServletResponse response) {
        try {
            var resultat = service.modifier(principal.getUtilisateurId(), utilisateurId, role, actif);
            if (resultat.moi() && !resultat.actif()) {
                logoutHandler.logout(request, response, authentication);
                return "redirect:/login?sessionExpiree";
            }
            if (resultat.moi() && !"ADMIN".equals(resultat.role())) {
                return "redirect:/espace";
            }
            redirect.addFlashAttribute("succesUtilisateurs", resultat.modifie()
                    ? "Les accès de " + resultat.pseudo() + " ont été mis à jour."
                    : "Aucune modification à enregistrer.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("erreurUtilisateurs", exception.getMessage());
        }
        return "redirect:/admin/utilisateurs";
    }
}
