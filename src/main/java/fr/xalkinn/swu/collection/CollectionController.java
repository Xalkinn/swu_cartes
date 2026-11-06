package fr.xalkinn.swu.collection;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import fr.xalkinn.swu.recherche.RechercheService;
import fr.xalkinn.swu.securite.UtilisateurConnecte;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Controller
public class CollectionController {

    private final CollectionService collectionService;

    private final RechercheService rechercheService;

    public CollectionController(CollectionService collectionService, RechercheService rechercheService) {
        this.collectionService = collectionService;
        this.rechercheService = rechercheService;
    }

    @GetMapping("/collection")
    public String afficher(
            @AuthenticationPrincipal UtilisateurConnecte utilisateurConnecte,
            Principal principal,
            @RequestParam(name = "page", defaultValue = "0") int page,
            HttpServletRequest requete,
            Model model
    ) {
        var resultat = collectionService.consulter(principal.getName(), page);
        model.addAttribute("resultat", resultat);
        model.addAttribute("recherchesSelection", rechercheService.selection(utilisateurConnecte.getUtilisateurId(), resultat.map(CollectionService.CartePossedee::idCarte).getContent()));

        model.addAttribute(
                "retour",
                requete.getRequestURI()
                        + (requete.getQueryString() == null
                        ? ""
                        : "?" + requete.getQueryString())
        );

        return "collection";
    }

    @PostMapping("/collection/ajuster")
    public String ajuster(
            Principal principal,
            @RequestParam("carteId") Long carteId,
            @RequestParam("variation") int variation,
            @RequestParam(defaultValue = "false") boolean foil,
            @RequestParam(name = "retour", defaultValue = "/collection")
            String retour,
            RedirectAttributes messages
    ) {
        try {
            collectionService.ajuster(
                    principal.getName(),
                    carteId,
                    variation,
                    foil
            );

        } catch (IllegalArgumentException exception) {
            messages.addFlashAttribute(
                    "erreurCollection",
                    exception.getMessage()
            );
        }

        // Retour limité aux deux pages de l'application.
        boolean autorise = retour != null
                && !retour.contains("\r")
                && !retour.contains("\n")
                && !retour.contains("\\")
                && (
                retour.equals("/catalogue")
                        || retour.startsWith("/catalogue?")
                        || retour.equals("/collection")
                        || retour.startsWith("/collection?")
        );

        return "redirect:" + (autorise ? retour : "/collection");
    }
}