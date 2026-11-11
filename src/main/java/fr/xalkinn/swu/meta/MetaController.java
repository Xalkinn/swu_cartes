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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;

@Controller
public class MetaController {
    private final MetaAffichageService affichage;
    private final MetaSynchronisation synchronisation;
    private final MetaStockage stockage;
    private final UtilisateurRepository utilisateurs;
    private final RechercheService recherches;
    private final MetaCopieService copies;
    public MetaController(MetaAffichageService affichage, MetaSynchronisation synchronisation, MetaStockage stockage,
                          UtilisateurRepository utilisateurs, RechercheService recherches, MetaCopieService copies) {
        this.affichage = affichage; this.synchronisation = synchronisation; this.stockage = stockage;
        this.utilisateurs = utilisateurs; this.recherches = recherches; this.copies = copies;
    }
    @GetMapping("/decks-du-moment")
    public String afficher(Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        model.addAttribute("vue", affichage.consulter()); return "meta";
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
        model.addAttribute("vue", vue); model.addAttribute("deck", deck); model.addAttribute("selectionRecherches", selection);
        model.addAttribute("copieMetaId", principal == null ? null : copies.existante(principal.getUtilisateurId(), id));
        model.addAttribute("retourMeta", "/decks-du-moment/" + id); return "meta-detail";
    }
    @PostMapping("/decks-du-moment/{id}/copier")
    public String copier(@PathVariable UUID id, @AuthenticationPrincipal UtilisateurConnecte principal, RedirectAttributes redirect) {
        if (principal == null) throw new AccessDeniedException("Connectez-vous pour copier ce deck.");
        try {
            var copie = copies.copier(principal.getUtilisateurId(), id);
            redirect.addFlashAttribute("succesDeck", copie.existante() ? "Votre copie existe déjà. Vos modifications ont été conservées."
                    : "Deck copié dans vos decks, avec son leader, sa base et sa réserve.");
            return "redirect:/decks/" + copie.id();
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("erreurCopieMeta", e.getMessage());
            return "redirect:/decks-du-moment/" + id;
        }
    }
    @GetMapping("/api/meta/etat") @ResponseBody
    public Map<String,Object> etat(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); var e = stockage.lire();
        return Map.of("chargement", e.chargement(), "disponible", !e.payload().isBlank(), "actualisation", e.reussite() == null ? "" : e.reussite().toString(), "erreur", e.erreur() != null);
    }
    @PostMapping("/admin/meta/actualiser")
    public String actualiser(@AuthenticationPrincipal UtilisateurConnecte principal, RedirectAttributes redirect) {
        if (principal == null || utilisateurs.findById(principal.getUtilisateurId())
                .filter(u -> u.isActif() && "ADMIN".equals(u.getRole())).isEmpty()) throw new AccessDeniedException("Droits administrateur requis.");
        boolean lance = synchronisation.demander(true);
        redirect.addFlashAttribute("messageMeta", lance ? "Actualisation lancée en arrière-plan." : "Une actualisation est déjà en cours, récente ou désactivée.");
        return "redirect:/decks-du-moment";
    }
}
