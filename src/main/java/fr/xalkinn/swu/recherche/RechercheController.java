package fr.xalkinn.swu.recherche;
import fr.xalkinn.swu.extension.ExtensionRepository;
import fr.xalkinn.swu.carte.FiltresIdentite;
import fr.xalkinn.swu.web.FiltreActif;
import fr.xalkinn.swu.securite.UtilisateurConnecte;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.net.URI;
import java.util.*;

@Controller
public class RechercheController {
    private final RechercheService service;private final ExtensionRepository extensions;
    public RechercheController(RechercheService service,ExtensionRepository extensions){this.service=service;this.extensions=extensions;}
    @GetMapping("/recherches")
    public String afficher(@AuthenticationPrincipal UtilisateurConnecte principal,@RequestParam(defaultValue="") String q,
                           @RequestParam(required=false) Long extensionId,@RequestParam(defaultValue="0") int page,
                           @RequestParam(required=false) List<String> arenes,@RequestParam(required=false) List<String> traits,
                           @RequestParam(defaultValue="TOUS") String modeTraits,Model model){
        FiltresIdentite identite = new FiltresIdentite(arenes,traits,modeTraits);
        model.addAttribute("q",q.strip());model.addAttribute("extensionId",extensionId);model.addAttribute("extensions",extensions.findAll(Sort.by("code")));
        try{model.addAttribute("resultat",service.consulter(principal.getUtilisateurId(),q,extensionId,page,identite));}
        catch(IllegalArgumentException e){model.addAttribute("erreurRecherche",e.getMessage());model.addAttribute("resultat",Page.empty());}
        var retour=org.springframework.web.util.UriComponentsBuilder.fromPath("/recherches").queryParam("q",q.strip()).queryParam("page",Math.max(0,page));
        if(extensionId!=null)retour.queryParam("extensionId",extensionId);
        Map<String,List<String>> params = new LinkedHashMap<>();
        if (!q.isBlank()) params.put("q",List.of(q.strip()));
        if (extensionId!=null) params.put("extensionId",List.of(extensionId.toString()));
        identite.parametres(params); List<FiltreActif> actifs=new ArrayList<>();
        identite.filtresActifs("/recherches",params,actifs); model.addAttribute("filtresActifsIdentite",actifs);
        if (!identite.arenes().isEmpty()) retour.queryParam("arenes",identite.arenes());
        if (!identite.traits().isEmpty()) retour.queryParam("traits",identite.traits()).queryParam("modeTraits",identite.modeTraits());
        model.addAttribute("retourRecherches",retour.build().encode().toUriString());
        return "recherches";
    }
    @PostMapping("/recherches/cartes")
    public String enregistrer(@AuthenticationPrincipal UtilisateurConnecte principal,@RequestParam Long carteId,@RequestParam boolean recherche,
                              @RequestParam(defaultValue="/recherches") String retour,RedirectAttributes redirect){
        try{service.enregistrer(principal.getUtilisateurId(),carteId,recherche);redirect.addFlashAttribute("succesRecherche",recherche?"Carte ajoutée à vos recherches.":"Carte retirée de vos recherches.");}
        catch(IllegalArgumentException e){redirect.addFlashAttribute("erreurRecherche",e.getMessage());}
        return "redirect:"+retourSur(retour);
    }
    @PostMapping("/api/recherches/cartes") @ResponseBody
    public ResponseEntity<?> enregistrerApi(@AuthenticationPrincipal UtilisateurConnecte principal,@RequestParam Long carteId,@RequestParam boolean recherche){
        try{return ResponseEntity.ok(service.enregistrer(principal.getUtilisateurId(),carteId,recherche));}
        catch(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
    }
    public static String retourSur(String retour){
        try{
            if(retour==null||retour.contains("\\")||retour.contains("\r")||retour.contains("\n"))return "/recherches";
            URI uri=URI.create(retour);
            if(!uri.isAbsolute()&&uri.getRawAuthority()==null&&(Set.of("/catalogue","/collection","/recherches","/classeur").contains(uri.getRawPath()) || uri.getRawPath().matches("/(?:decks|joueurs)/[1-9][0-9]*") || uri.getRawPath().matches("/joueurs/[1-9][0-9]*/classeur")))return retour;
        }catch(IllegalArgumentException ignored){}
        return "/recherches";
    }
}
