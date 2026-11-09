package fr.xalkinn.swu.carte;

import fr.xalkinn.swu.collection.CollectionController;
import fr.xalkinn.swu.deck.DeckController;
import fr.xalkinn.swu.recherche.RechercheController;
import jakarta.persistence.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.transaction.annotation.Transactional;
import java.text.Collator;
import java.util.*;

@ControllerAdvice(assignableTypes = {CatalogueController.class, DeckController.class, CollectionController.class, RechercheController.class})
public class OptionsIdentiteCartes {
    @PersistenceContext private EntityManager entityManager;
    @ModelAttribute
    @Transactional(readOnly = true)
    public void options(HttpServletRequest requete, Model model) {
        var selection = new FiltresIdentite(liste(requete,"arenes"),liste(requete,"traits"),requete.getParameter("modeTraits"));
        List<String> traits = new ArrayList<>(entityManager.createQuery("select distinct t from Carte c join c.traits t",String.class).getResultList());
        // Un lien vers un trait sans résultat reste retirable et visible dans le formulaire.
        traits.addAll(selection.traits());
        traits = new ArrayList<>(new LinkedHashSet<>(traits)); traits.sort(Collator.getInstance(Locale.FRENCH));
        model.addAttribute("identite",selection);
        model.addAttribute("traitsDisponibles",traits);
    }
    private static List<String> liste(HttpServletRequest r,String nom) { String[] v=r.getParameterValues(nom); return v==null?List.of():Arrays.asList(v); }
}
