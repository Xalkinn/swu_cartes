package fr.xalkinn.swu.deck;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/communaute/decks")
public class DeckCommunauteController {
    private final DeckCommunauteService service;

    public DeckCommunauteController(DeckCommunauteService service) {
        this.service = service;
    }

    @GetMapping
    public String lister(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("q", q.strip());
        try {
            model.addAttribute("resultat", service.lister(q, page));
        } catch (IllegalArgumentException e) {
            model.addAttribute("erreurCommunaute", e.getMessage());
            model.addAttribute("resultat", Page.empty());
        }
        return "communaute/decks";
    }

    @GetMapping("/{id}")
    public String afficher(@PathVariable Long id, Model model) {
        model.addAttribute("vue", service.consulter(id));
        return "communaute/deck";
    }
}
