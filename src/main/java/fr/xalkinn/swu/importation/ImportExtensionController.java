package fr.xalkinn.swu.importation;

import fr.xalkinn.swu.extension.ExtensionRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

//@Controller
public class ImportExtensionController {

    private final ExtensionRepository extensionRepository;
    private final ImportCatalogueService importCatalogueService;

    public ImportExtensionController(
            ExtensionRepository extensionRepository,
            ImportCatalogueService importCatalogueService
    ) {
        this.extensionRepository = extensionRepository;
        this.importCatalogueService = importCatalogueService;
    }

    @GetMapping("/admin/import/extensions")
    public String afficher(Model model) {
        model.addAttribute(
                "extensions",
                extensionRepository.findAll(Sort.by("code").ascending())
        );

        return "admin/import-extensions";
    }

    @PostMapping("/admin/import/extensions")
    public String importer(
            @RequestParam("extensionId") Long extensionId,
            RedirectAttributes messages
    ) {
        try {
            ImportCatalogueService.BilanImport bilan =
                    importCatalogueService.importer(extensionId);

            messages.addFlashAttribute(
                    "succes",
                    "Import " + bilan.codeExtension() + " terminé : "
                            + bilan.ajouts() + " carte(s) ajoutée(s), "
                            + bilan.misesAJour()
                            + " fiche(s) existante(s) synchronisée(s)."
            );

        } catch (RestClientException exception) {
            messages.addFlashAttribute(
                    "erreur",
                    "La source est inaccessible ou sa réponse est illisible. Aucun changement n'a été enregistré."
            );

        } catch (DataIntegrityViolationException exception) {
            messages.addFlashAttribute(
                    "erreur",
                    "Un conflit a empêché l'enregistrement. L'import a été annulé. Réessayez sans lancer plusieurs imports simultanément."
            );

        } catch (IllegalStateException exception) {
            messages.addFlashAttribute("erreur", exception.getMessage());
        }

        return "redirect:/admin/import/extensions";
    }
}