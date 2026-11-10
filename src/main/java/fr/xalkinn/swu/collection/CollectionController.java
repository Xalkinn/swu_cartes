package fr.xalkinn.swu.collection;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import fr.xalkinn.swu.carte.*;
import java.util.*;
import fr.xalkinn.swu.extension.ExtensionRepository;
import fr.xalkinn.swu.extension.Extension;
import fr.xalkinn.swu.web.FiltreActif;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import fr.xalkinn.swu.recherche.RechercheService;
import fr.xalkinn.swu.securite.UtilisateurConnecte;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Controller
public class CollectionController {

    private final CollectionService collectionService;

    private final RechercheService rechercheService;

    private final ExtensionRepository extensionRepository;
    public CollectionController(CollectionService collectionService, RechercheService rechercheService,
                                ExtensionRepository extensionRepository) {
        this.collectionService = collectionService;
        this.rechercheService = rechercheService;
        this.extensionRepository = extensionRepository;
    }

    @GetMapping("/collection")
    public String afficher(
            @AuthenticationPrincipal UtilisateurConnecte utilisateurConnecte,
            Principal principal,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Long extensionId,
            @RequestParam(defaultValue = "TOUTES") String version,
            @RequestParam(defaultValue = "false") boolean recherches,
            @RequestParam(required = false) List<String> arenes,
            @RequestParam(required = false) List<String> traits,
            @RequestParam(defaultValue = "TOUS") String modeTraits,
            @RequestParam(required = false) Integer coutMin,
            @RequestParam(required = false) Integer coutMax,
            @RequestParam(required = false) List<String> affinites,
            @RequestParam(defaultValue = "TOUTES") String mode,
            @RequestParam(required = false) List<String> raretes,
            @RequestParam(required = false) List<String> types,
            @RequestParam(defaultValue = "TOUTES") String variante,
            @RequestParam(defaultValue = "TOUTES") String finition,
            HttpServletRequest requete,
            Model model
    ) {
        FiltresIdentite identite = new FiltresIdentite(arenes,traits,modeTraits);
        FiltresCollection avances = new FiltresCollection(coutMin, coutMax, affinites, mode, raretes, types, variante, finition);
        model.addAttribute("identite", identite);
        model.addAttribute("coutMin", coutMin); model.addAttribute("coutMax", coutMax);
        model.addAttribute("affinitesDisponibles", Affinite.values()); model.addAttribute("selectionAffinites", avances.affinites());
        model.addAttribute("mode", avances.mode()); model.addAttribute("selectionRaretes", avances.raretes());
        model.addAttribute("selectionTypes", avances.types()); model.addAttribute("raretesDisponibles", Rarete.values());
        model.addAttribute("typesDisponibles", TypeCarteFiltre.values()); model.addAttribute("variantesDisponibles", VarianteCarte.values());
        model.addAttribute("varianteSelectionnee", variante); model.addAttribute("finitionSelectionnee", finition);
        model.addAttribute("nombreFiltresAvances", avances.nombre() + identite.arenes().size() + identite.traits().size()
                + (recherches ? 1 : 0) + ("TOUTES".equals(version) ? 0 : 1));
        List<Extension> extensions = extensionRepository.findAll(Sort.by("code"));
        model.addAttribute("extensions", extensions);
        model.addAttribute("q", q.strip()); model.addAttribute("extensionId", extensionId);
        model.addAttribute("version", version); model.addAttribute("filtreRecherches", recherches);
        Page<CollectionService.CartePossedee> resultat;
        try { resultat = collectionService.consulter(principal.getName(), q, extensionId, version, recherches, page, identite, null, avances); }
        catch (IllegalArgumentException e) { model.addAttribute("erreurCollection", e.getMessage()); resultat = Page.empty(); }
        Map<String,List<String>> params = new LinkedHashMap<>();
        if (!q.isBlank()) params.put("q", List.of(q.strip()));
        if (extensionId != null) params.put("extensionId", List.of(extensionId.toString()));
        if (!"TOUTES".equals(version)) params.put("version", List.of(version));
        if (recherches) params.put("recherches", List.of("true"));
        identite.parametres(params);
        avances.parametres(params);
        List<FiltreActif> filtres = new ArrayList<>();
        identite.filtresActifs("/collection",params,filtres);
        if (!q.isBlank()) filtres.add(FiltreActif.retirer("Recherche : " + q.strip(), "/collection", params, "q", null));
        if (extensionId != null) filtres.add(FiltreActif.retirer(extensions.stream().filter(e -> e.getId().equals(extensionId)).map(Extension::getCode).findFirst().orElse("Extension"), "/collection", params, "extensionId", null));
        if (!"TOUTES".equals(version)) {
            String libelle = switch(version) { case "STANDARD" -> "Standard"; case "HYPERSPACE" -> "Hyperspace"; case "SHOWCASE" -> "Showcase"; case "PRESTIGE" -> "Prestige"; case "PRESTIGE_SERIALISEE" -> "Prestige sérialisée"; case "PROMO" -> "Promotions"; case "AUTRE" -> "Autres traitements"; case "FOIL" -> "Avec foil"; case "NONFOIL" -> "Avec non-foil"; default -> "Version"; };
            filtres.add(FiltreActif.retirer(libelle, "/collection", params, "version", null));
        }
        if (recherches) filtres.add(FiltreActif.retirer("Mes recherches", "/collection", params, "recherches", null));
        if (coutMin != null) filtres.add(FiltreActif.retirer("Coût ≥ " + coutMin, "/collection", params, "coutMin", null));
        if (coutMax != null) filtres.add(FiltreActif.retirer("Coût ≤ " + coutMax, "/collection", params, "coutMax", null));
        if ("SANS".equals(avances.mode())) filtres.add(FiltreActif.retirer("Sans affinité", "/collection", params, "mode", null));
        for (String code : avances.affinites()) {
            String libelle; try { libelle = Affinite.valueOf(code).getLibelle(); } catch (IllegalArgumentException e) { libelle = code; }
            filtres.add(FiltreActif.retirer(libelle, "/collection", params, "affinites", code));
        }
        for (String rarete : avances.raretes()) filtres.add(FiltreActif.retirer(Rarete.libelle(rarete), "/collection", params, "raretes", rarete));
        for (String type : avances.types()) {
            String libelle; try { libelle = TypeCarteFiltre.valueOf(type).getLibelle(); } catch (IllegalArgumentException e) { libelle = type; }
            filtres.add(FiltreActif.retirer(libelle, "/collection", params, "types", type));
        }
        if (!"TOUTES".equals(variante)) {
            String libelle; try { libelle = VarianteCarte.valueOf(variante).getLibelle(); } catch (IllegalArgumentException e) { libelle = variante; }
            filtres.add(FiltreActif.retirer(libelle, "/collection", params, "variante", null));
        }
        if (!"TOUTES".equals(finition)) filtres.add(FiltreActif.retirer("FOIL".equals(finition) ? "Foil possédée" : "Non foil possédée", "/collection", params, "finition", null));
        model.addAttribute("filtresActifs", filtres);
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
            @RequestParam(defaultValue = "") String serie,
            @RequestParam(name = "retour", defaultValue = "/collection")
            String retour,
            RedirectAttributes messages
    ) {
        try {
            collectionService.ajuster(
                    principal.getName(),
                    carteId,
                    variation,
                    foil,serie
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