package fr.xalkinn.swu.importation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ImportToutesExtensionsController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportToutesExtensionsController.class);
    private final ImportCatalogueService importCatalogueService;

    public ImportToutesExtensionsController(ImportCatalogueService importCatalogueService) {
        this.importCatalogueService = importCatalogueService;
    }

    @PostMapping("/admin/import/extensions/toutes")
    public String importerToutes(RedirectAttributes redirect) {
        try {
            redirect.addFlashAttribute("bilanToutes", importCatalogueService.importerToutes());
        } catch (RuntimeException exception) {
            LOGGER.warn("Échec de l'import global", exception);
            String message = exception instanceof IllegalStateException
                    && exception.getMessage() != null
                    ? exception.getMessage()
                    : "L'import global a échoué. Consultez les logs de l'application.";
            redirect.addFlashAttribute("erreurImport", message);
        }
        return "redirect:/admin/import/extensions";
    }
}
