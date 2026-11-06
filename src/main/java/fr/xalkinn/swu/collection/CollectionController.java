package fr.xalkinn.swu.collection;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @GetMapping("/collection")
    public String afficher(
            Principal principal,
            @RequestParam(name = "page", defaultValue = "0") int page,
            HttpServletRequest requete,
            Model model
    ) {
        model.addAttribute(
                "resultat",
                collectionService.consulter(principal.getName(), page)
        );

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