package fr.xalkinn.swu.collection;

import fr.xalkinn.swu.carte.FiltresIdentite;
import fr.xalkinn.swu.securite.UtilisateurConnecte;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.List;

@Controller
public class CollectionExportController {
    private final CollectionExportService service;

    public CollectionExportController(CollectionExportService service) {
        this.service = service;
    }

    @GetMapping("/collection/export")
    public ResponseEntity<byte[]> exporter(@AuthenticationPrincipal UtilisateurConnecte principal,
                                           @RequestParam(defaultValue = "xlsx") String format, @RequestParam(defaultValue = "TOUT") String portee,
                                           @RequestParam(defaultValue = "") String q, @RequestParam(required = false) Long extensionId,
                                           @RequestParam(defaultValue = "TOUTES") String version, @RequestParam(defaultValue = "false") boolean recherches,
                                           @RequestParam(required = false) List<String> arenes, @RequestParam(required = false) List<String> traits,
                                           @RequestParam(defaultValue = "TOUS") String modeTraits,
                                           @RequestParam(required = false) Integer coutMin, @RequestParam(required = false) Integer coutMax,
                                           @RequestParam(required = false) List<String> affinites, @RequestParam(defaultValue = "TOUTES") String mode,
                                           @RequestParam(required = false) List<String> raretes, @RequestParam(required = false) List<String> types,
                                           @RequestParam(defaultValue = "TOUTES") String variante, @RequestParam(defaultValue = "TOUTES") String finition) throws IOException {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        var fichier = service.exporter(principal.getUtilisateurId(), format, portee, q, extensionId, version, recherches, new FiltresIdentite(arenes, traits, modeTraits), new FiltresCollection(coutMin, coutMax, affinites, mode, raretes, types, variante, finition));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fichier.nom()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(fichier.type())).contentLength(fichier.contenu().length).body(fichier.contenu());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalide(IllegalArgumentException e) {
        return ResponseEntity.badRequest().header(HttpHeaders.CACHE_CONTROL, "private, no-store").contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8)).body(e.getMessage());
    }
}
