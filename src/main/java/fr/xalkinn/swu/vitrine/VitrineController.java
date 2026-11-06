package fr.xalkinn.swu.vitrine;
import fr.xalkinn.swu.securite.UtilisateurConnecte;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class VitrineController {
    private final VitrineService service;
    public VitrineController(VitrineService service){this.service=service;}
    @GetMapping("/ma-vitrine")
    public String reglages(@AuthenticationPrincipal UtilisateurConnecte principal,Model model){model.addAttribute("reglages",service.reglages(principal.getUtilisateurId()));return "vitrine/reglages";}
    @PostMapping("/ma-vitrine")
    public String enregistrer(@AuthenticationPrincipal UtilisateurConnecte principal,@RequestParam(defaultValue="false") boolean publiee,
                              @RequestParam(defaultValue="false") boolean collection,@RequestParam(defaultValue="false") boolean decks,
                              @RequestParam(defaultValue="false") boolean recherches,RedirectAttributes redirect){
        service.enregistrer(principal.getUtilisateurId(),publiee,collection,decks,recherches);
        redirect.addFlashAttribute("succesVitrine",publiee?"Votre vitrine et ses rubriques ont été mises à jour.":"Votre vitrine est privée.");return "redirect:/ma-vitrine";
    }
    @GetMapping("/joueurs")
    public String annuaire(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,Model model){
        model.addAttribute("q",q.strip());try{model.addAttribute("resultat",service.annuaire(q,page));}catch(IllegalArgumentException e){model.addAttribute("erreur",e.getMessage());model.addAttribute("resultat",Page.empty());}return "vitrine/joueurs";
    }
    @GetMapping("/joueurs/{id}")
    public String afficher(@PathVariable Long id,@RequestParam(defaultValue="") String rubrique,@RequestParam(defaultValue="0") int page,Model model){model.addAttribute("vue",service.consulter(id,rubrique,page));return "vitrine/joueur";}
    @GetMapping("/joueurs/{joueurId}/decks/{deckId}")
    public String deck(@PathVariable Long joueurId,@PathVariable Long deckId,Model model){model.addAttribute("deck",service.deckPublic(joueurId,deckId));return "vitrine/deck";}
}
