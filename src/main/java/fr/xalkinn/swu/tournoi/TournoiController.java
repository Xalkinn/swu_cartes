package fr.xalkinn.swu.tournoi;

import fr.xalkinn.swu.securite.UtilisateurConnecte;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TournoiController {
    private final TournoiService service;

    public TournoiController(TournoiService service) {
        this.service = service;
    }

    private static Long id(UtilisateurConnecte p) {
        if (p == null) throw new AccessDeniedException("Connexion requise.");
        return p.getUtilisateurId();
    }

    @GetMapping({"/tournois", "/admin/tournois"})
    public String liste(@AuthenticationPrincipal UtilisateurConnecte p, @RequestParam(defaultValue = "TOUS") String etat, @RequestParam(defaultValue = "0") int page, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        model.addAttribute("resultat", service.lister(id(p), etat, page));
        model.addAttribute("filtreEtat", etat);
        model.addAttribute("adminTournois", service.administrateur(id(p)));
        return "tournoi/liste";
    }

    @GetMapping("/tournois/{tournoiId}")
    public String voir(@PathVariable Long tournoiId, @AuthenticationPrincipal UtilisateurConnecte p, Model model, HttpServletResponse response) {
        return page(tournoiId, p, false, model, response);
    }

    @GetMapping("/admin/tournois/{tournoiId}")
    public String gerer(@PathVariable Long tournoiId, @AuthenticationPrincipal UtilisateurConnecte p, Model model, HttpServletResponse response) {
        service.formulaire(id(p), tournoiId);
        return page(tournoiId, p, true, model, response);
    }

    private String page(Long tournoiId, UtilisateurConnecte p, boolean gestion, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        model.addAttribute("vue", service.consulter(tournoiId, id(p)));
        model.addAttribute("gestion", gestion);
        return "tournoi/detail";
    }

    @GetMapping("/admin/tournois/nouveau")
    public String nouveau(@AuthenticationPrincipal UtilisateurConnecte p, Model model) {
        model.addAttribute("formulaire", service.formulaire(id(p), null));
        model.addAttribute("tournoiId", null);
        return "tournoi/formulaire";
    }

    @GetMapping("/admin/tournois/{tournoiId}/modifier")
    public String modifier(@PathVariable Long tournoiId, @AuthenticationPrincipal UtilisateurConnecte p, Model model) {
        model.addAttribute("formulaire", service.formulaire(id(p), tournoiId));
        model.addAttribute("tournoiId", tournoiId);
        return "tournoi/formulaire";
    }

    @PostMapping("/admin/tournois/enregistrer")
    public String enregistrer(@AuthenticationPrincipal UtilisateurConnecte p, @RequestParam(required = false) Long tournoiId,
                              @RequestParam String nom, @RequestParam(defaultValue = "") String description, @RequestParam(defaultValue = "") String recompenses, @RequestParam String conditions, @RequestParam int capacite, @RequestParam int meilleurDes, @RequestParam(defaultValue = "") String debut, Model model, RedirectAttributes redirect) {
        var f = new TournoiService.Formulaire(nom, description, recompenses, conditions, capacite, meilleurDes, debut);
        try {
            Long tid = service.enregistrer(id(p), tournoiId, f);
            redirect.addFlashAttribute("succesTournoi", "Tournoi enregistré.");
            return "redirect:/admin/tournois/" + tid;
        } catch (IllegalArgumentException e) {
            model.addAttribute("formulaire", f);
            model.addAttribute("tournoiId", tournoiId);
            model.addAttribute("erreurTournoi", e.getMessage());
            return "tournoi/formulaire";
        }
    }

    @PostMapping("/tournois/{tournoiId}/inscription")
    public String inscrire(@PathVariable Long tournoiId, @AuthenticationPrincipal UtilisateurConnecte p, @RequestParam(defaultValue = "false") boolean accepte, RedirectAttributes redirect) {
        return action(tournoiId, false, redirect, () -> service.inscrire(id(p), tournoiId, accepte), "Inscription enregistrée.");
    }

    @PostMapping("/tournois/{tournoiId}/desinscription")
    public String quitter(@PathVariable Long tournoiId, @AuthenticationPrincipal UtilisateurConnecte p, RedirectAttributes redirect) {
        return action(tournoiId, false, redirect, () -> service.retirer(id(p), tournoiId, id(p)), "Désinscription enregistrée.");
    }

    @PostMapping("/admin/tournois/{tournoiId}/etat")
    public String etat(@PathVariable Long tournoiId, @AuthenticationPrincipal UtilisateurConnecte p, @RequestParam String action, @RequestParam(defaultValue = "false") boolean confirme, RedirectAttributes redirect) {
        return action(tournoiId, true, redirect, () -> {
            if ("annuler".equals(action) && !confirme)
                throw new IllegalArgumentException("Confirmez l’annulation du tournoi.");
            service.etat(id(p), tournoiId, action);
        }, "État du tournoi mis à jour.");
    }

    @PostMapping("/admin/tournois/{tournoiId}/demarrer")
    public String demarrer(@PathVariable Long tournoiId, @AuthenticationPrincipal UtilisateurConnecte p, @RequestParam(defaultValue = "false") boolean confirme, RedirectAttributes redirect) {
        return action(tournoiId, true, redirect, () -> {
            if (!confirme) throw new IllegalArgumentException("Confirmez le tirage et le gel des inscriptions.");
            service.demarrer(id(p), tournoiId);
        }, "Tableau généré : le tournoi commence.");
    }

    @PostMapping("/admin/tournois/{tournoiId}/retirer")
    public String retirer(@PathVariable Long tournoiId, @AuthenticationPrincipal UtilisateurConnecte p, @RequestParam Long joueurId, RedirectAttributes redirect) {
        return action(tournoiId, true, redirect, () -> service.retirer(id(p), tournoiId, joueurId), "Participant retiré.");
    }

    @PostMapping("/admin/tournois/{tournoiId}/resultat")
    public String resultat(@PathVariable Long tournoiId, @AuthenticationPrincipal UtilisateurConnecte p, @RequestParam Long matchId, @RequestParam int score1, @RequestParam int score2, RedirectAttributes redirect) {
        return action(tournoiId, true, redirect, () -> service.resultat(id(p), tournoiId, matchId, score1, score2), "Résultat enregistré. Le tableau a été mis à jour.");
    }

    @PostMapping("/admin/tournois/{tournoiId}/effacer-resultat")
    public String effacer(@PathVariable Long tournoiId, @AuthenticationPrincipal UtilisateurConnecte p, @RequestParam Long matchId, RedirectAttributes redirect) {
        return action(tournoiId, true, redirect, () -> service.effacerResultat(id(p), tournoiId, matchId), "Résultat effacé : vous pouvez saisir le score corrigé.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public void invalide(IllegalArgumentException e) {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private String action(Long id, boolean admin, RedirectAttributes r, Runnable travail, String message) {
        try {
            travail.run();
            r.addFlashAttribute("succesTournoi", message);
        } catch (IllegalArgumentException e) {
            r.addFlashAttribute("erreurTournoi", e.getMessage());
        }
        return "redirect:" + (admin ? "/admin/tournois/" : "/tournois/") + id;
    }
}
