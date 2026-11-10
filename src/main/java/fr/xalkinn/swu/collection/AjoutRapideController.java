package fr.xalkinn.swu.collection;

import fr.xalkinn.swu.collection.AjoutRapideService.Finition;
import fr.xalkinn.swu.collection.AjoutRapideService.ResultatAjout;
import fr.xalkinn.swu.extension.ExtensionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class AjoutRapideController {

    private final AjoutRapideService ajoutRapideService;
    private final ExtensionRepository extensionRepository;

    public AjoutRapideController(
            AjoutRapideService ajoutRapideService,
            ExtensionRepository extensionRepository
    ) {
        this.ajoutRapideService = ajoutRapideService;
        this.extensionRepository = extensionRepository;
    }

    @GetMapping("/collection/ajout-rapide")
    public String afficher(
            @RequestParam(required = false) Long extensionId,
            @RequestParam(defaultValue = "CLASSIQUE") Finition finition,
            Model model
    ) {
        model.addAttribute(
                "extensions",
                extensionRepository.findAll(Sort.by("code"))
        );

        model.addAttribute("extensionId", extensionId);
        model.addAttribute("finition", finition);
        model.addAttribute("finitions", Finition.values());

        // Une erreur ou une recherche ambiguë peut conserver la saisie.
        if (!model.containsAttribute("numero")) {
            model.addAttribute("numero", "");
        }

        return "ajout-rapide";
    }

    @PostMapping("/collection/ajout-rapide")
    public String ajouter(
            Principal principal,
            @RequestParam Long extensionId,
            @RequestParam String numero,
            @RequestParam Finition finition,
            @RequestParam(required = false) Long carteId,
            RedirectAttributes redirect
    ) {
        // Ces deux choix restent sélectionnés après chaque ajout.
        redirect.addAttribute("extensionId", extensionId);
        redirect.addAttribute("finition", finition.name());

        redirect.addFlashAttribute("numero", numero);

        try {
            ResultatAjout resultat = ajoutRapideService.ajouter(
                    principal.getName(),
                    extensionId,
                    numero,
                    finition,
                    carteId
            );

            if (resultat.carte() == null) {
                redirect.addFlashAttribute(
                        "candidats",
                        resultat.candidats()
                );

            } else {
                redirect.addFlashAttribute(
                        "derniereCarte",
                        resultat.carte()
                );

                redirect.addFlashAttribute(
                        "totalPossede",
                        resultat.total()
                );

                redirect.addFlashAttribute(
                        "totalFoil",
                        resultat.foils()
                );

                redirect.addFlashAttribute(
                        "finitionAjoutee",
                        finition.getLibelle()
                );

                // Prépare immédiatement le numéro suivant.
                redirect.addFlashAttribute("numero", "");
            }

        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute(
                    "erreurAjout",
                    exception.getMessage()
            );
        }

        // Rafraîchir la page après un ajout ne renvoie pas le formulaire.
        return "redirect:/collection/ajout-rapide";
    }
}