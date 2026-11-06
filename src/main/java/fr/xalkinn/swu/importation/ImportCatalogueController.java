package fr.xalkinn.swu.importation;

import fr.xalkinn.swu.extension.ExtensionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ImportCatalogueController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportCatalogueController.class);
    private final ExtensionRepository extensionRepository;
    private final ImportCatalogueService importCatalogueService;

    public ImportCatalogueController(
            ExtensionRepository extensionRepository,
            ImportCatalogueService importCatalogueService
    ) {
        this.extensionRepository = extensionRepository;
        this.importCatalogueService = importCatalogueService;
    }

    @GetMapping("/admin/import/extensions")
    public String afficher(Model model) {
        model.addAttribute("extensions", extensionRepository.findAll(Sort.by("code")));
        return "import-extensions";
    }

    @PostMapping("/admin/import/extensions")
    public String importer(
            @RequestParam Long extensionId,
            RedirectAttributes redirect
    ) {
        redirect.addFlashAttribute("extensionSelectionnee", extensionId);
        try {
            redirect.addFlashAttribute("bilanImport", importCatalogueService.importer(extensionId));
        } catch (RuntimeException exception) {
            LOGGER.warn("Échec de l'import de l'extension d'identifiant {}", extensionId, exception);
            redirect.addFlashAttribute("erreurImport", messageErreur(exception));
        }
        return "redirect:/admin/import/extensions";
    }

    private static String messageErreur(RuntimeException exception) {
        if (exception instanceof IllegalStateException
                && exception.getMessage() != null
                && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        return "L'import a échoué. Consultez les logs de l'application pour le détail.";
    }
}
