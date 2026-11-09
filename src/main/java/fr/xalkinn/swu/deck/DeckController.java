package fr.xalkinn.swu.deck;

import fr.xalkinn.swu.carte.Affinite;
import fr.xalkinn.swu.recherche.RechercheService;
import fr.xalkinn.swu.extension.ExtensionRepository;
import fr.xalkinn.swu.securite.UtilisateurConnecte;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/decks")
public class DeckController {
    private final DeckService service;
    private final ExtensionRepository extensions;
    private final RechercheService recherches;
    public DeckController(DeckService service, ExtensionRepository extensions, RechercheService recherches) {
        this.service = service; this.extensions = extensions; this.recherches = recherches;
    }
    @GetMapping
    public String lister(@AuthenticationPrincipal UtilisateurConnecte principal,
                         @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("resultat", service.lister(principal.getUtilisateurId(), page));
        return "decks/index";
    }
    @PostMapping
    public String creer(@AuthenticationPrincipal UtilisateurConnecte principal, @RequestParam String nom,
                        @RequestParam(defaultValue = "") String description, RedirectAttributes redirect) {
        try {
            Long id = service.creer(principal.getUtilisateurId(), nom, description);
            redirect.addFlashAttribute("succesDeck", "Votre deck est créé. Choisissez maintenant un leader et une base.");
            return "redirect:/decks/" + id;
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("erreurDeck", e.getMessage());
            redirect.addFlashAttribute("nomSaisi", nom);
            redirect.addFlashAttribute("descriptionSaisie", description);
            return "redirect:/decks";
        }
    }
    @GetMapping("/{id}")
    public String afficher(@AuthenticationPrincipal UtilisateurConnecte principal, @PathVariable Long id,
                           @ModelAttribute("filtres") RechercheDeck filtres, Model model) {
        var vue = service.consulter(principal.getUtilisateurId(), id);
        model.addAttribute("vue", vue);
        model.addAttribute("publieCommunaute", service.estPublieDansCommunaute(principal.getUtilisateurId(), id));
        model.addAttribute("recherchesSelection", recherches.selection(principal.getUtilisateurId(),
                vue.aTrouver().stream().map(ligne -> ligne.carte().id()).toList()));
        model.addAttribute("extensions", extensions.findAll(Sort.by("code")));
        model.addAttribute("affinitesDisponibles", Affinite.values());
        try {
            model.addAttribute("resultat", service.rechercher(principal.getUtilisateurId(), id, filtres.q(),
                    filtres.extensionId(), filtres.coutMin(), filtres.coutMax(), filtres.affinites(),
                    filtres.mode(), filtres.zone(), filtres.page()));
        } catch (IllegalArgumentException e) {
            model.addAttribute("erreurRechercheDeck", e.getMessage());
            model.addAttribute("resultat", org.springframework.data.domain.Page.empty());
        }
        return "decks/editeur";
    }
    @PostMapping("/{id}/informations")
    public String renommer(@AuthenticationPrincipal UtilisateurConnecte principal, @PathVariable Long id,
                           @RequestParam String nom, @RequestParam(defaultValue = "") String description,
                           RedirectAttributes redirect) {
        return action(() -> service.renommer(principal.getUtilisateurId(), id, nom, description),
                "/decks/" + id, "Les informations du deck ont été enregistrées.", redirect);
    }
    @PostMapping("/{id}/publication")
    public String publication(@AuthenticationPrincipal UtilisateurConnecte principal, @PathVariable Long id,
                              @RequestParam boolean publier, @ModelAttribute("filtres") RechercheDeck filtres,
                              RedirectAttributes redirect) {
        return action(() -> service.publierDansCommunaute(principal.getUtilisateurId(), id, publier),
                filtres.url(id, filtres.page()).replace("#recherche", "#publication"),
                publier ? "Votre deck est publié dans la communauté." : "Votre deck a été retiré de la communauté.", redirect);
    }
    @PostMapping("/{id}/supprimer")
    public String supprimer(@AuthenticationPrincipal UtilisateurConnecte principal, @PathVariable Long id,
                           RedirectAttributes redirect) {
        service.supprimer(principal.getUtilisateurId(), id);
        redirect.addFlashAttribute("succesDeck", "Le deck a été supprimé. Votre collection est conservée.");
        return "redirect:/decks";
    }
    @PostMapping("/{id}/choisir")
    public String choisir(@AuthenticationPrincipal UtilisateurConnecte principal, @PathVariable Long id,
                          @RequestParam(required = false) Long carteId, @RequestParam DeckService.Zone emplacement,
                          @ModelAttribute("filtres") RechercheDeck filtres, RedirectAttributes redirect) {
        return action(() -> service.choisir(principal.getUtilisateurId(), id, carteId, emplacement),
                filtres.url(id, filtres.page()), "La sélection du deck a été enregistrée.", redirect);
    }
    @PostMapping("/{id}/ajouter")
    public String ajouter(@AuthenticationPrincipal UtilisateurConnecte principal, @PathVariable Long id,
                          @RequestParam Long carteId, @RequestParam(defaultValue = "1") int quantite,
                          @ModelAttribute("filtres") RechercheDeck filtres, RedirectAttributes redirect) {
        return action(() -> service.ajouter(principal.getUtilisateurId(), id, carteId, quantite),
                filtres.url(id, filtres.page()), "Carte ajoutée au deck.", redirect);
    }
    @PostMapping("/{id}/quantite")
    public String quantite(@AuthenticationPrincipal UtilisateurConnecte principal, @PathVariable Long id,
                           @RequestParam Long carteId, @RequestParam int quantite,
                           @ModelAttribute("filtres") RechercheDeck filtres, RedirectAttributes redirect) {
        return action(() -> service.quantite(principal.getUtilisateurId(), id, carteId, quantite),
                filtres.url(id, filtres.page()).replace("#recherche", "#composition"), "La composition a été mise à jour.", redirect);
    }
    private String action(Runnable action, String retour, String succes, RedirectAttributes redirect) {
        try { action.run(); redirect.addFlashAttribute("succesDeck", succes); }
        catch (IllegalArgumentException e) { redirect.addFlashAttribute("erreurDeck", e.getMessage()); }
        return "redirect:" + retour;
    }
}
