package fr.xalkinn.swu.collection;

import fr.xalkinn.swu.securite.UtilisateurConnecte;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.Instant;

@Controller
public class CollectionImportController {
    private static final String SESSION = "collection.import.apercu";
    private final CollectionImportService service;

    public CollectionImportController(CollectionImportService service) {
        this.service = service;
    }

    @PostMapping("/collection/import/analyser")
    public String analyser(@AuthenticationPrincipal UtilisateurConnecte principal, @RequestParam MultipartFile fichier,
                           @RequestParam(defaultValue = "ACTUALISER") CollectionImportService.Mode mode, HttpSession session, RedirectAttributes redirect) {
        verifier(principal);
        synchronized (session) {
            session.removeAttribute(SESSION);
            try {
                if (fichier.getSize() > CollectionImportLecture.MAX_OCTETS)
                    throw new IllegalArgumentException("Le fichier doit faire 5 Mo maximum.");
                var releve = CollectionImportLecture.lire(fichier.getOriginalFilename(), fichier.getBytes());
                var plan = service.preparer(principal.getUtilisateurId(), releve, mode);
                session.setAttribute(SESSION, plan);
                return "redirect:/collection/import/apercu";
            } catch (IllegalArgumentException e) {
                redirect.addFlashAttribute("erreurImport", e.getMessage());
            } catch (IOException e) {
                redirect.addFlashAttribute("erreurImport", "Impossible de lire le fichier. Sélectionnez-le à nouveau.");
            }
        }
        return "redirect:/collection/ajout-rapide#import-fichier";
    }

    @GetMapping("/collection/import/apercu")
    public String apercu(@AuthenticationPrincipal UtilisateurConnecte principal, HttpSession session, HttpServletResponse response, Model model, RedirectAttributes redirect) {
        verifier(principal);
        response.setHeader("Cache-Control", "private, no-store");
        synchronized (session) {
            var plan = plan(session, principal.getUtilisateurId());
            if (plan == null) {
                redirect.addFlashAttribute("erreurImport", "L'aperçu a expiré. Sélectionnez à nouveau votre fichier.");
                return "redirect:/collection/ajout-rapide#import-fichier";
            }
            model.addAttribute("plan", plan);
            return "collection-import";
        }
    }

    @PostMapping("/collection/import/confirmer")
    public String confirmer(@AuthenticationPrincipal UtilisateurConnecte principal, @RequestParam String jeton,
                            @RequestParam(defaultValue = "false") boolean accepterPartiel, HttpSession session, RedirectAttributes redirect) {
        verifier(principal);
        synchronized (session) {
            var plan = plan(session, principal.getUtilisateurId());
            if (plan == null || !plan.jeton().equals(jeton)) {
                redirect.addFlashAttribute("erreurImport", "Cet aperçu a expiré ou a déjà été importé. Sélectionnez à nouveau le fichier.");
                return "redirect:/collection/ajout-rapide#import-fichier";
            }
            try {
                var bilan = service.confirmer(principal.getUtilisateurId(), plan, accepterPartiel);
                session.removeAttribute(SESSION);
                redirect.addFlashAttribute("succesImport", bilan.references() + " référence(s) modifiée(s). " + bilan.exemplaires() + " exemplaire(s) du fichier pris en compte. Mode : " + bilan.mode().getLibelle() + ".");
                return "redirect:/collection";
            } catch (IllegalArgumentException e) {
                redirect.addFlashAttribute("erreurImport", e.getMessage());
                return "redirect:/collection/import/apercu";
            }
        }
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String fichierTropGrand(RedirectAttributes redirect) {
        redirect.addFlashAttribute("erreurImport", "Le fichier dépasse la taille autorisée par le serveur. Utilisez un fichier plus petit ou ajustez la limite d'envoi.");
        return "redirect:/collection/ajout-rapide#import-fichier";
    }

    private static void verifier(UtilisateurConnecte principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    private static CollectionImportService.Preparation plan(HttpSession session, Long utilisateurId) {
        Object valeur = session.getAttribute(SESSION);
        if (!(valeur instanceof CollectionImportService.Preparation plan) || !plan.utilisateurId().equals(utilisateurId) || Instant.now().isAfter(plan.expiration()))
            return null;
        return plan;
    }
}
