package fr.xalkinn.swu.vitrine;
import fr.xalkinn.swu.utilisateur.*;
import fr.xalkinn.swu.carte.Carte;
import fr.xalkinn.swu.collection.CollectionService;
import fr.xalkinn.swu.deck.*;
import fr.xalkinn.swu.recherche.RechercheService;
import jakarta.persistence.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class VitrineService {
    private final VitrineRepository vitrines;private final UtilisateurRepository utilisateurs;
    private final CollectionService collection;private final RechercheService recherches;
    private final DeckService decks;private final DeckRepository deckRepository;private final DeckCarteRepository lignes;
    @PersistenceContext private EntityManager entityManager;
    public VitrineService(VitrineRepository vitrines,UtilisateurRepository utilisateurs,CollectionService collection,
                           RechercheService recherches,DeckService decks,DeckRepository deckRepository,DeckCarteRepository lignes){
        this.vitrines=vitrines;this.utilisateurs=utilisateurs;this.collection=collection;this.recherches=recherches;
        this.decks=decks;this.deckRepository=deckRepository;this.lignes=lignes;
    }
    @Transactional(readOnly=true)
    public Reglages reglages(Long utilisateurId){
        Utilisateur u=actif(utilisateurId);Vitrine v=vitrines.findById(utilisateurId).orElse(null);
        return new Reglages(u.getId(),u.getPseudo(),v!=null&&v.isPubliee(),v!=null&&v.isPartagerCollection(),v!=null&&v.isPartagerDecks(),v!=null&&v.isPartagerRecherches(),v!=null&&v.isPartagerClasseur());
    }
    @Transactional
    public void enregistrer(Long utilisateurId,boolean publiee,boolean collection,boolean decks,boolean recherches){
        enregistrer(utilisateurId,publiee,collection,decks,recherches,false);
    }
    @Transactional
    public void enregistrer(Long utilisateurId,boolean publiee,boolean collection,boolean decks,boolean recherches,boolean classeur){
        Utilisateur u=entityManager.find(Utilisateur.class,utilisateurId,LockModeType.PESSIMISTIC_WRITE);
        if(u==null)throw new AccessDeniedException("Compte introuvable.");
        entityManager.refresh(u,LockModeType.PESSIMISTIC_WRITE);
        if(!u.isActif())throw new AccessDeniedException("Compte désactivé.");
        Vitrine v=vitrines.findById(utilisateurId).orElseGet(()->new Vitrine(u));
        v.configurer(publiee,collection,decks,recherches,classeur);vitrines.save(v);
    }
    @Transactional(readOnly=true)
    public Page<Joueur> annuaire(String q,int page){
        String texte=q==null?"":q.strip();if(texte.length()>100)throw new IllegalArgumentException("Recherche limitée à 100 caractères.");
        Page<Vitrine> resultat=vitrines.publiques(texte,PageRequest.of(Math.max(0,page),12));
        if(resultat.getTotalPages()>0&&resultat.getNumber()>=resultat.getTotalPages())resultat=vitrines.publiques(texte,PageRequest.of(resultat.getTotalPages()-1,12));
        return resultat.map(this::joueur);
    }
    @Transactional(readOnly=true)
    public Vue consulter(Long id,String rubrique,int page){
        return consulter(id,rubrique,page,null,false);
    }
    @Transactional(readOnly=true)
    public Vue consulter(Long id,String rubrique,int page,Long visiteurId,boolean mesRecherches){
        Vitrine v=publique(id);Joueur joueur=joueur(v);
        if (visiteurId != null) actif(visiteurId);
        if (mesRecherches && visiteurId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Connectez-vous pour retrouver vos recherches.");
        String onglet=rubrique==null?"":rubrique;
        if(onglet.isEmpty())onglet=v.isPartagerCollection()?"collection":v.isPartagerDecks()?"decks":v.isPartagerRecherches()?"recherches":"profil";
        Page<CollectionService.CartePossedee> cartes=Page.empty();Page<DeckService.Resume> listes=Page.empty();Page<RechercheService.CarteRecherchee> souhaits=Page.empty();
        switch(onglet){
            case "collection" -> {if(!v.isPartagerCollection())throw introuvable();cartes=mesRecherches
                    ? collection.consulter(v.getUtilisateur().getEmail(),"",null,"TOUTES",true,page,
                        fr.xalkinn.swu.carte.FiltresIdentite.VIDE,visiteurId)
                    : collection.consulter(v.getUtilisateur().getEmail(),page);}
            case "decks" -> {if(!v.isPartagerDecks())throw introuvable();listes=decks.lister(id,page);}
            case "recherches" -> {if(!v.isPartagerRecherches())throw introuvable();souhaits=recherches.consulter(id,"",null,page);}
            case "profil" -> {}
            default -> throw introuvable();
        }
        return new Vue(joueur,onglet,cartes,listes,souhaits);
    }
    @Transactional(readOnly=true)
    public Joueur classeurPublic(Long id){
        Vitrine v=publique(id);
        if(!v.isPartagerClasseur())throw introuvable();
        return joueur(v);
    }
    @Transactional(readOnly=true)
    public DeckPublic deckPublic(Long joueurId,Long deckId){
        Vitrine v=publique(joueurId);if(!v.isPartagerDecks())throw introuvable();
        Deck d=deckRepository.findByIdAndUtilisateurId(deckId,joueurId).orElseThrow(VitrineService::introuvable);
        // Lecture publique dédiée : aucun stock personnel ni liste de cartes manquantes.
        List<LignePublique> contenu=lignes.findByDeckIdOrderByCarteCoutAscCarteTitreAscCarteIdAsc(deckId).stream()
                .map(l->new LignePublique(fiche(l.getCarte()),l.getQuantite())).toList();
        return new DeckPublic(joueur(v),d.getNom(),d.getDescription(),fiche(d.getLeader()),fiche(d.getBase()),contenu,contenu.stream().mapToInt(LignePublique::quantite).sum(),d.getReserve().stream().map(l->new LignePublique(fiche(l.getCarte()),l.getQuantite())).toList());
    }
    private Vitrine publique(Long id){Vitrine v=vitrines.findById(id).orElseThrow(VitrineService::introuvable);if(!v.isPubliee()||!v.getUtilisateur().isActif())throw introuvable();return v;}
    private Utilisateur actif(Long id){Utilisateur u=utilisateurs.findById(id).orElseThrow(()->new AccessDeniedException("Compte introuvable."));if(!u.isActif())throw new AccessDeniedException("Compte désactivé.");return u;}
    private Joueur joueur(Vitrine v){Utilisateur u=v.getUtilisateur();return new Joueur(u.getId(),u.getPseudo(),v.isPartagerCollection(),v.isPartagerDecks(),v.isPartagerRecherches(),v.isPartagerClasseur());}
    private CartePublique fiche(Carte c){return c==null?null:new CartePublique(c.getTitre(),c.getSousTitre(),c.getExtension().getCode()+" · "+c.getNumeroAffiche(),c.getImageRecto());}
    private static ResponseStatusException introuvable(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Vitrine ou rubrique introuvable.");}
    public record Reglages(Long id,String pseudo,boolean publiee,boolean collection,boolean decks,boolean recherches,boolean classeur){}
    public record Joueur(Long id,String pseudo,boolean collection,boolean decks,boolean recherches,boolean classeur){}
    public record Vue(Joueur joueur,String rubrique,Page<CollectionService.CartePossedee> collection,Page<DeckService.Resume> decks,Page<RechercheService.CarteRecherchee> recherches){}
    public record CartePublique(String titre,String sousTitre,String reference,String image){}
    public record LignePublique(CartePublique carte,int quantite){}
    public record DeckPublic(Joueur joueur,String nom,String description,CartePublique leader,CartePublique base,List<LignePublique> lignes,int total,List<LignePublique> reserve){}
}
