package fr.xalkinn.swu;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ConnexionController {

    @GetMapping("/login")
    public String connexion() {
        // Charge templates/connexion.html
        return "connexion";
    }
}