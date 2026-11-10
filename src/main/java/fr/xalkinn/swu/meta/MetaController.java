package fr.xalkinn.swu.meta;

import fr.xalkinn.swu.recherche.RechercheService;
import fr.xalkinn.swu.securite.UtilisateurConnecte;
import fr.xalkinn.swu.utilisateur.UtilisateurRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Controller
public class MetaController {
    private final MetaAffichageService affichage;
    private final MetaSynchronisation synchronisation;
    private final MetaStockage stockage;
    private final UtilisateurRepository utilisateurs;
    private final RechercheService recherches;

    public MetaController(MetaAffichageService affichage, MetaSynchronisation synchronisation, MetaStockage stockage,
                          UtilisateurRepository utilisateurs, RechercheService recherches) {
        this.affichage = affichage;
        this.synchronisation = synchronisation;
        this.stockage = stockage;
        this.utilisateurs = utilisateurs;
        this.recherches = recherches;
    }

    @GetMapping("/decks-du-moment")
    public String afficher(Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        model.addAttribute("vue", affichage.consulter());
        return "meta";
    }

    @GetMapping("/decks-du-moment/{id}")
    public String detail(@PathVariable UUID id, @AuthenticationPrincipal UtilisateurConnecte principal,
                         Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        var vue = affichage.consulter();
        var deck = vue.decks().stream().filter(d -> d.id().equals(id.toString())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Set<Long> selection = principal == null ? Set.of() : recherches.selection(principal.getUtilisateurId(),
                deck.toutes().stream().map(MetaAffichageService.Carte::id).filter(Objects::nonNull).distinct().toList());
        model.addAttribute("vue", vue);
        model.addAttribute("deck", deck);
        model.addAttribute("selectionRecherches", selection);
        model.addAttribute("retourMeta", "/decks-du-moment/" + id);
        return "meta-detail";
    }

    @GetMapping("/api/meta/etat")
    @ResponseBody
    public Map<String, Object> etat(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var e = stockage.lire();
        return Map.of("chargement", e.chargement(), "disponible", !e.payload().isBlank(), "actualisation", e.reussite() == null ? "" : e.reussite().toString(), "erreur", e.erreur() != null);
    }

    @PostMapping("/admin/meta/actualiser")
    public String actualiser(@AuthenticationPrincipal UtilisateurConnecte principal, RedirectAttributes redirect) {
        if (principal == null || utilisateurs.findById(principal.getUtilisateurId())
                .filter(u -> u.isActif() && "ADMIN".equals(u.getRole())).isEmpty())
            throw new AccessDeniedException("Droits administrateur requis.");
        boolean lance = synchronisation.demander(true);
        redirect.addFlashAttribute("messageMeta", lance ? "Actualisation lancée en arrière-plan." : "Une actualisation est déjà en cours, récente ou désactivée.");
        return "redirect:/decks-du-moment";
    }
}
