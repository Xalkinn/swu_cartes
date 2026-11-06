package fr.xalkinn.swu.extension;

import jakarta.validation.Valid;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class ExtensionAdminController {

    private final ExtensionRepository extensionRepository;

    public ExtensionAdminController(
            ExtensionRepository extensionRepository
    ) {
        this.extensionRepository = extensionRepository;
    }

    // Liste actualisée pour l'affichage initial et les erreurs de formulaire.
    @ModelAttribute
    public void preparerPage(Model model) {
        model.addAttribute("pageActive", "admin");

        model.addAttribute(
                "extensions",
                extensionRepository.findAll(Sort.by("code").ascending())
        );
    }

    @GetMapping("/admin/extensions")
    public String afficher(Model model) {
        model.addAttribute(
                "formulaire",
                new ExtensionForm("", "", null)
        );

        return "admin/extensions";
    }

    @PostMapping("/admin/extensions")
    public String ajouter(
            @Valid @ModelAttribute("formulaire") ExtensionForm formulaire,
            BindingResult erreurs
    ) {
        // Une date invalide est également détectée lors du binding.
        if (erreurs.hasFieldErrors("dateSortie")) {
            erreurs.reject(
                    "date.invalide",
                    "La date de sortie est invalide."
            );
        }

        if (erreurs.hasErrors()) {
            return "admin/extensions";
        }

        if (extensionRepository.existsByCodeIgnoreCase(formulaire.code())) {
            erreurs.rejectValue(
                    "code",
                    "code.existant",
                    "Une extension utilise déjà ce code."
            );

            return "admin/extensions";
        }

        Extension extension = new Extension(
                formulaire.code(),
                formulaire.nom(),
                formulaire.dateSortie()
        );

        try {
            extensionRepository.saveAndFlush(extension);
        } catch (DataIntegrityViolationException exception) {
            // La contrainte SQL protège aussi les ajouts simultanés.
            erreurs.reject(
                    "extension.conflit",
                    "Impossible d'ajouter cette extension. Son code peut déjà être utilisé."
            );

            return "admin/extensions";
        }

        // Évite de refaire l'insertion en actualisant le navigateur.
        return "redirect:/admin/extensions?ajoute";
    }
}