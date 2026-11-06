package fr.xalkinn.swu;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccueilController {

    @GetMapping("/")
    public String accueil(Model model) {
        model.addAttribute("pageActive", "accueil");
        return "accueil";
    }

    @GetMapping("/espace")
    public String espacePersonnel(Model model) {
        model.addAttribute("pageActive", "espace");
        return "espace";
    }

    @GetMapping("/admin")
    public String administration(Model model) {
        model.addAttribute("pageActive", "admin");
        return "admin/index";
    }
}