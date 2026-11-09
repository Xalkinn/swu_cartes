package fr.xalkinn.swu.recherche;
import fr.xalkinn.swu.carte.*;
import fr.xalkinn.swu.utilisateur.*;
import jakarta.persistence.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class RechercheService {
    private final RechercheCarteRepository recherches;
    private final CarteRepository cartes;
    private final UtilisateurRepository utilisateurs;
    @PersistenceContext private EntityManager entityManager;
    public RechercheService(RechercheCarteRepository recherches,CarteRepository cartes,UtilisateurRepository utilisateurs){this.recherches=recherches;this.cartes=cartes;this.utilisateurs=utilisateurs;}
    @Transactional
    public Etat enregistrer(Long utilisateurId,Long carteId,boolean recherche) {
        // Première lecture verrouillée : évite aussi un instantané ancien sous MySQL REPEATABLE READ.
        Utilisateur utilisateur=entityManager.find(Utilisateur.class,utilisateurId,LockModeType.PESSIMISTIC_WRITE);
        if(utilisateur==null)throw new AccessDeniedException("Compte introuvable.");
        entityManager.refresh(utilisateur,LockModeType.PESSIMISTIC_WRITE);
        if(!utilisateur.isActif())throw new AccessDeniedException("Compte désactivé.");
        Carte carte=cartes.findById(carteId).orElseThrow(()->new IllegalArgumentException("Carte introuvable."));
        Carte standard=reference(carte);
        RechercheCarte existante=recherches.findByUtilisateurIdAndCarteId(utilisateurId,standard.getId()).orElse(null);
        if(recherche && existante==null)recherches.save(new RechercheCarte(utilisateur,standard));
        else if(!recherche && existante!=null)recherches.delete(existante);
        return new Etat(standard.getId(),recherches.famille(standard.getId()),recherche);
    }
    @Transactional(readOnly=true)
    public Set<Long> selection(Long utilisateurId,List<Long> ids) {
        utilisateur(utilisateurId);
        if(ids.isEmpty())return Set.of();
        Map<Long,Long> references=new HashMap<>();
        for(Carte carte:cartes.findAllById(ids)) {
            Carte standard=carte.getCarteStandard()==null?carte:carte.getCarteStandard();
            references.put(carte.getId(),standard.getId());
        }
        if(references.isEmpty())return Set.of();
        Set<Long> selection=new HashSet<>();
        for(RechercheCarte r:recherches.findByUtilisateurIdAndCarteIdIn(utilisateurId,new HashSet<>(references.values())))selection.add(r.getCarte().getId());
        Set<Long> resultat=new HashSet<>();
        references.forEach((id,standard)->{if(selection.contains(standard))resultat.add(id);});
        return resultat;
    }
    @Transactional(readOnly=true)
    public Page<CarteRecherchee> consulter(Long utilisateurId,String q,Long extensionId,int page) {
        return consulter(utilisateurId,q,extensionId,page,FiltresIdentite.VIDE);
    }
    @Transactional(readOnly=true)
    public Page<CarteRecherchee> consulter(Long utilisateurId,String q,Long extensionId,int page,FiltresIdentite identite) {
        identite.verifier();
        utilisateur(utilisateurId);
        String texte=q==null?"":q.strip();
        if(texte.length()>100)throw new IllegalArgumentException("Recherche limitée à 100 caractères.");
        org.springframework.data.jpa.domain.Specification<RechercheCarte> specification = (r,query,cb) -> {
            var c = r.<RechercheCarte,Carte>join("carte");
            var conditions = new ArrayList<jakarta.persistence.criteria.Predicate>();
            conditions.add(cb.equal(r.get("utilisateur").get("id"),utilisateurId));
            if (!texte.isBlank()) {
                var nom = cb.gt(cb.locate(cb.lower(c.get("titre")),texte.toLowerCase(Locale.ROOT)),0);
                var sousTitre = cb.gt(cb.locate(cb.lower(cb.coalesce(c.<String>get("sousTitre"),"")),texte.toLowerCase(Locale.ROOT)),0);
                if (texte.matches("[0-9]{1,6}")) conditions.add(cb.or(nom,sousTitre,cb.equal(c.get("numero"),Integer.valueOf(texte))));
                else conditions.add(cb.or(nom,sousTitre));
            }
            if (extensionId != null) conditions.add(cb.equal(c.get("extension").get("id"),extensionId));
            conditions.add(identite.condition(c,query,cb));
            return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Sort tri = Sort.by(Sort.Order.desc("dateAjout"),Sort.Order.desc("id"));
        Page<RechercheCarte> resultat = recherches.findAll(specification,PageRequest.of(Math.max(0,page),24,tri));
        if (resultat.getTotalPages()>0 && resultat.getNumber()>=resultat.getTotalPages())
            resultat = recherches.findAll(specification,PageRequest.of(resultat.getTotalPages()-1,24,tri));
        return resultat.map(r->fiche(r.getCarte()));
    }
    private Carte reference(Carte carte){
        Carte reference=carte.getCarteStandard()==null?carte:carte.getCarteStandard();
        if(reference.isHyperspace()||reference.getCarteStandard()!=null)throw new IllegalArgumentException("Cette variante n'est pas encore reliée à une carte standard. Actualisez l'extension.");
        return reference;
    }
    private Utilisateur utilisateur(Long id){Utilisateur u=utilisateurs.findById(id).orElseThrow(()->new AccessDeniedException("Compte introuvable."));if(!u.isActif())throw new AccessDeniedException("Compte désactivé.");return u;}
    private CarteRecherchee fiche(Carte c){return new CarteRecherchee(c.getId(),c.getTitre(),c.getSousTitre(),c.getExtension().getCode()+" · "+c.getNumeroAffiche(),c.getImageRecto());}
    public record Etat(Long referenceId,List<Long> carteIds,boolean recherche){}
    public record CarteRecherchee(Long id,String titre,String sousTitre,String reference,String image){}
}
