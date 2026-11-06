package fr.xalkinn.swu.carte;

import fr.xalkinn.swu.extension.ExtensionRepository;
import fr.xalkinn.swu.collection.CollectionService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import fr.xalkinn.swu.recherche.RechercheService;
import fr.xalkinn.swu.securite.UtilisateurConnecte;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.List;

@Controller
public class CatalogueController {

    private final CatalogueService catalogueService;
    private final ExtensionRepository extensionRepository;
    private final CollectionService collectionService;

    private final RechercheService rechercheService;

    public CatalogueController(
            CatalogueService catalogueService,
            ExtensionRepository extensionRepository,
            CollectionService collectionService,
            RechercheService rechercheService
    ) {
        this.catalogueService = catalogueService;
        this.extensionRepository = extensionRepository;
        this.collectionService = collectionService;
        this.rechercheService = rechercheService;
    }

    @GetMapping("/catalogue")
    public String afficher(
            @RequestParam(name = "q", defaultValue = "") String q,

            @RequestParam(name = "extensionId", required = false)
            Long extensionId,

            @RequestParam(name = "coutMin", required = false)
            Integer coutMin,

            @RequestParam(name = "coutMax", required = false)
            Integer coutMax,

            @RequestParam(name = "affinites", required = false)
            List<String> affinites,

            @RequestParam(name = "mode", defaultValue = "TOUTES")
            String mode,

            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @AuthenticationPrincipal UtilisateurConnecte utilisateurConnecte,
            Principal principal,
            HttpServletRequest requete,
            Model model
    ) {
        String recherche = q.strip();

        List<String> selection = affinites == null
                ? List.of()
                : affinites.stream()
                .filter(code -> !code.isBlank())
                .distinct()
                .toList();

        // Le mode "sans affinité" ignore les couleurs cochées.
        if ("SANS".equals(mode)) {
            selection = List.of();
        }

        model.addAttribute(
                "extensions",
                extensionRepository.findAll(Sort.by("code").ascending())
        );

        model.addAttribute("affinitesDisponibles", Affinite.values());
        model.addAttribute("selectionAffinites", selection);
        model.addAttribute("recherche", recherche);
        model.addAttribute("extensionSelectionnee", extensionId);
        model.addAttribute("coutMin", coutMin);
        model.addAttribute("coutMax", coutMax);
        model.addAttribute("mode", mode);

        Page<CatalogueService.CarteAffichee> resultat;

        try {
            resultat = catalogueService.rechercher(
                    recherche,
                    extensionId,
                    coutMin,
                    coutMax,
                    selection,
                    mode,
                    page
            );

        } catch (IllegalArgumentException exception) {
            model.addAttribute("erreur", exception.getMessage());
            resultat = Page.empty();
        }

        model.addAttribute("resultat", resultat);
        model.addAttribute("cartes", resultat.getContent());
        model.addAttribute("recherchesSelection", rechercheService.selection(utilisateurConnecte.getUtilisateurId(), resultat.map(CatalogueService.CarteAffichee::id).getContent()));
        model.addAttribute(
                "quantites",
                collectionService.quantites(
                        principal.getName(),
                        resultat.getContent().stream()
                                .map(CatalogueService.CarteAffichee::id)
                                .toList()
                )
        );

        model.addAttribute(
                "retourCatalogue",
                requete.getRequestURI()
                        + (requete.getQueryString() == null
                        ? ""
                        : "?" + requete.getQueryString())
        );
        return "catalogue";
    }
}