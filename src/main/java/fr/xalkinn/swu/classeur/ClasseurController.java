package fr.xalkinn.swu.classeur;

import fr.xalkinn.swu.securite.UtilisateurConnecte;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class ClasseurController {
    private final ClasseurService service;

    public ClasseurController(ClasseurService service) {
        this.service = service;
    }

    @GetMapping("/classeur")
    public String personnel(@AuthenticationPrincipal UtilisateurConnecte principal,
                            @RequestParam(required = false) Long extensionId, @RequestParam(defaultValue = "TOUTES") String etat,
                            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                            HttpServletRequest requete, HttpServletResponse response, Model model) {
        if (principal == null) return "redirect:/login";
        return afficher(service.personnel(principal.getUtilisateurId(), extensionId, etat, q, page), requete, response, model);
    }

    @GetMapping("/joueurs/{id}/classeur")
    public String publicPour(@PathVariable Long id, @AuthenticationPrincipal UtilisateurConnecte principal,
                             @RequestParam(required = false) Long extensionId, @RequestParam(defaultValue = "TOUTES") String etat,
                             @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                             HttpServletRequest requete, HttpServletResponse response, Model model) {
        return afficher(service.publicPour(id, principal == null ? null : principal.getUtilisateurId(), extensionId, etat, q, page), requete, response, model);
    }

    private String afficher(ClasseurService.Vue vue, HttpServletRequest requete, HttpServletResponse response, Model model) {
        response.setHeader("Cache-Control", "private, no-store");
        String chemin = (vue.publique() ? "/joueurs/" + vue.joueurId() + "/classeur" : "/classeur");
        var retour = UriComponentsBuilder.fromPath(chemin).queryParam("etat", vue.etat()).queryParam("q", vue.q()).queryParam("page", vue.resultat().getNumber());
        if (vue.extension() != null) retour.queryParam("extensionId", vue.extension().id());
        model.addAttribute("classeur", vue);
        model.addAttribute("cheminClasseur", chemin);
        model.addAttribute("retourClasseur", retour.build().encode().toUriString());
        return "classeur";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String invalide(IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
