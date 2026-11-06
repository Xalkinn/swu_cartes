package fr.xalkinn.swu.actualite;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ActualitesController {

    private final ActualitesService service;

    public ActualitesController(ActualitesService service) {
        this.service = service;
    }

    @GetMapping("/api/accueil/actualites")
    public ResponseEntity<ActualitesService.EtatActualites> consulter() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.consulter());
    }
}
