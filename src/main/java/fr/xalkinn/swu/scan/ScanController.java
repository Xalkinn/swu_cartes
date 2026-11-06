package fr.xalkinn.swu.scan;

import fr.xalkinn.swu.carte.CarteRepository;
import fr.xalkinn.swu.collection.CollectionService;
import fr.xalkinn.swu.extension.ExtensionRepository;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Locale;

@Controller
public class ScanController {

    private final CarteRepository carteRepository;
    private final ExtensionRepository extensionRepository;
    private final CollectionService collectionService;

    public ScanController(
            CarteRepository carteRepository,
            ExtensionRepository extensionRepository,
            CollectionService collectionService
    ) {
        this.carteRepository = carteRepository;
        this.extensionRepository = extensionRepository;
        this.collectionService = collectionService;
    }

    @ModelAttribute
    public void preparerPage(Model model) {
        model.addAttribute(
                "extensions",
                extensionRepository.findAll(Sort.by("code").ascending())
        );

        model.addAttribute("codeSelectionne", "");
        model.addAttribute("numeroSelectionne", null);
        model.addAttribute("rechercheEffectuee", false);
        model.addAttribute("candidats", List.of());
    }

    @GetMapping("/scan")
    public String afficher() {
        return "scan";
    }

    @PostMapping("/scan/rechercher")
    public String rechercher(
            @RequestParam("extensionCode") String extensionCode,
            @RequestParam("numero") Integer numero,
            Model model
    ) {
        String code = extensionCode.strip().toUpperCase(Locale.ROOT);

        model.addAttribute("codeSelectionne", code);
        model.addAttribute("numeroSelectionne", numero);

        if (code.length() > 12
                || !extensionRepository.existsByCodeIgnoreCase(code)) {

            model.addAttribute(
                    "erreurScan",
                    "Sélectionnez une extension enregistrée."
            );

            return "scan";
        }

        if (numero == null || numero < 1 || numero > 9999) {
            model.addAttribute(
                    "erreurScan",
                    "Le numéro doit être compris entre 1 et 9999."
            );

            return "scan";
        }

        List<CarteDetectee> candidats = carteRepository
                .findByExtensionCodeIgnoreCaseAndNumeroOrderByIdAsc(
                        code,
                        numero
                )
                .stream()
                .map(carte -> new CarteDetectee(
                        carte.getId(),
                        carte.getTitre(),
                        carte.getSousTitre(),
                        carte.getExtension().getCode()
                                + " · " + carte.getNumeroAffiche(),
                        carte.getImageRecto()
                ))
                .toList();

        model.addAttribute("candidats", candidats);
        model.addAttribute("rechercheEffectuee", true);

        return "scan";
    }

    @PostMapping("/scan/ajouter")
    public String ajouter(
            Principal principal,
            @RequestParam("carteId") Long carteId,
            RedirectAttributes messages
    ) {
        try {
            collectionService.ajuster(
                    principal.getName(),
                    carteId,
                    1
            );

            messages.addFlashAttribute(
                    "succesScan",
                    "Un exemplaire a été ajouté à votre collection."
            );

        } catch (IllegalArgumentException exception) {
            messages.addFlashAttribute(
                    "erreurScan",
                    exception.getMessage()
            );
        }

        return "redirect:/scan";
    }

    public record CarteDetectee(
            Long id,
            String titre,
            String sousTitre,
            String reference,
            String imageRecto
    ) {
    }
}