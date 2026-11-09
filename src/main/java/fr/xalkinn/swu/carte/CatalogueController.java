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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import fr.xalkinn.swu.web.FiltreActif;
import fr.xalkinn.swu.extension.Extension;

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
            @RequestParam(required = false) List<String> raretes,
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) List<String> arenes,
            @RequestParam(required = false) List<String> traits,
            @RequestParam(defaultValue = "TOUS") String modeTraits,

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
        FiltresIdentite identite = new FiltresIdentite(arenes,traits,modeTraits);

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

        List<Extension> extensions = extensionRepository.findAll(Sort.by("code").ascending());
        model.addAttribute("extensions", extensions);

        List<String> selectionRaretes = FiltresCarte.nettoyer(raretes);
        List<String> selectionTypes = FiltresCarte.nettoyer(types);
        model.addAttribute("raretesDisponibles", Rarete.values());
        model.addAttribute("typesDisponibles", TypeCarteFiltre.values());
        model.addAttribute("selectionRaretes", selectionRaretes);
        model.addAttribute("selectionTypes", selectionTypes);
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
                    page, selectionRaretes, selectionTypes, identite
            );

        } catch (IllegalArgumentException exception) {
            model.addAttribute("erreur", exception.getMessage());
            resultat = Page.empty();
        }

        Map<String,List<String>> params = new LinkedHashMap<>();
        if (!recherche.isBlank()) params.put("q", List.of(recherche));
        if (extensionId != null) params.put("extensionId", List.of(extensionId.toString()));
        if (coutMin != null) params.put("coutMin", List.of(coutMin.toString()));
        if (coutMax != null) params.put("coutMax", List.of(coutMax.toString()));
        if (!selection.isEmpty()) params.put("affinites", selection);
        if (!"TOUTES".equals(mode)) params.put("mode", List.of(mode));
        if (!selectionRaretes.isEmpty()) params.put("raretes", selectionRaretes);
        if (!selectionTypes.isEmpty()) params.put("types", selectionTypes);
        identite.parametres(params);
        List<FiltreActif> filtres = new ArrayList<>();
        identite.filtresActifs("/catalogue",params,filtres);
        if (!recherche.isBlank()) filtres.add(FiltreActif.retirer("Recherche : " + recherche, "/catalogue", params, "q", null));
        if (extensionId != null) filtres.add(FiltreActif.retirer(extensions.stream().filter(e -> e.getId().equals(extensionId)).map(Extension::getCode).findFirst().orElse("Extension"), "/catalogue", params, "extensionId", null));
        if (coutMin != null) filtres.add(FiltreActif.retirer("Coût ≥ " + coutMin, "/catalogue", params, "coutMin", null));
        if (coutMax != null) filtres.add(FiltreActif.retirer("Coût ≤ " + coutMax, "/catalogue", params, "coutMax", null));
        if ("SANS".equals(mode)) filtres.add(FiltreActif.retirer("Sans affinité", "/catalogue", params, "mode", null));
        else for (String code : selection) {
            String libelle;
            try { libelle = Affinite.valueOf(code).getLibelle(); } catch (IllegalArgumentException e) { libelle = code; }
            filtres.add(FiltreActif.retirer(libelle, "/catalogue", params, "affinites", code));
        }
        for (String r : selectionRaretes) filtres.add(FiltreActif.retirer(Rarete.libelle(r), "/catalogue", params, "raretes", r));
        for (String t : selectionTypes) {
            String label; try { label = TypeCarteFiltre.valueOf(t).getLibelle(); } catch (IllegalArgumentException e) { label = t; }
            filtres.add(FiltreActif.retirer(label, "/catalogue", params, "types", t));
        }
        model.addAttribute("filtresActifs", filtres);
        model.addAttribute("nombreFiltresAvances", (coutMin == null ? 0 : 1) + (coutMax == null ? 0 : 1) + ("SANS".equals(mode) ? 1 : selection.size()) + selectionRaretes.size() + selectionTypes.size() + identite.arenes().size() + identite.traits().size());
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