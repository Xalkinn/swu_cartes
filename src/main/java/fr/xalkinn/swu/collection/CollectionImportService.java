package fr.xalkinn.swu.collection;

import fr.xalkinn.swu.carte.Carte;
import fr.xalkinn.swu.utilisateur.Utilisateur;
import fr.xalkinn.swu.utilisateur.UtilisateurRepository;
import jakarta.persistence.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CollectionImportService {
    public enum Mode {
        ACTUALISER("Mettre à jour les quantités"), AJOUTER("Ajouter à mes quantités");
        private final String libelle;Mode(String libelle){this.libelle=libelle;}public String getLibelle(){return libelle;}
    }
    public record Cible(Long carteId,int avant,int avantFoil,int apres,int apresFoil,List<String> seriesAvant,List<String> seriesApres) {
        public boolean modifiee(){return avant!=apres||avantFoil!=apresFoil||!seriesAvant.equals(seriesApres);}
    }
    public record LigneApercu(String source,String reference,String nom,int fichier,int importable,int actuel,int apres,
            boolean prete,boolean ignoree,String detail,List<Cible> cibles) {
        public LigneApercu { cibles=List.copyOf(cibles); }
        public boolean modifiee(){return cibles.stream().anyMatch(Cible::modifiee);}
    }
    public record Preparation(String jeton,Long utilisateurId,Instant expiration,CollectionImportLecture.Releve releve,Mode mode,
            List<LigneApercu> lignes,int exemplaires,int nonGeres) {
        public Preparation { lignes=List.copyOf(lignes); }
        public long pretes(){return lignes.stream().filter(LigneApercu::prete).count();}
        public long bloquees(){return lignes.stream().filter(l->!l.prete()&&!l.ignoree()).count();}
        public long changements(){return lignes.stream().filter(LigneApercu::modifiee).count();}
        public boolean partiel(){return bloquees()>0||nonGeres>0;}
        public List<LigneApercu> visibles(){return lignes.stream().filter(l->l.fichier()>0||!l.prete()&&!l.ignoree()||l.modifiee()).toList();}
    }
    public record Bilan(long references,int exemplaires,Mode mode) {}
    private final UtilisateurRepository utilisateurs;
    private final PossessionRepository possessions;
    @PersistenceContext private EntityManager entityManager;
    public CollectionImportService(UtilisateurRepository utilisateurs,PossessionRepository possessions){this.utilisateurs=utilisateurs;this.possessions=possessions;}

    @Transactional(readOnly=true)
    public Preparation preparer(Long utilisateurId,CollectionImportLecture.Releve releve,Mode mode){
        if(mode==null)throw new IllegalArgumentException("Choisissez un mode d'import.");
        utilisateur(utilisateurId);
        List<Carte> catalogue=entityManager.createQuery("select c from Carte c join fetch c.extension left join fetch c.carteStandard order by c.id",Carte.class).getResultList();
        Map<Long,Possession> stock=chargerStock(utilisateurId);
        List<LigneApercu> lignes=ImportVariantes.preparer(releve,catalogue,stock,mode);
        int exemplaires=lignes.stream().filter(LigneApercu::prete).mapToInt(LigneApercu::importable).sum();
        return new Preparation(UUID.randomUUID().toString(),utilisateurId,Instant.now().plus(Duration.ofMinutes(30)),releve,mode,lignes,exemplaires,0);
    }

    @Transactional
    public Bilan confirmer(Long utilisateurId,Preparation plan,boolean accepterPartiel){
        if(plan==null||!plan.utilisateurId().equals(utilisateurId)||Instant.now().isAfter(plan.expiration()))throw new IllegalArgumentException("Cet aperçu a expiré. Sélectionnez à nouveau le fichier.");
        Utilisateur utilisateur=utilisateur(utilisateurId);entityManager.lock(utilisateur,LockModeType.PESSIMISTIC_WRITE);
        if(plan.partiel()&&!accepterPartiel)throw new IllegalArgumentException("Confirmez explicitement l'import partiel des lignes prêtes.");
        if(plan.pretes()==0)throw new IllegalArgumentException("Aucune ligne prête à importer.");
        Preparation actuel=preparer(utilisateurId,plan.releve(),plan.mode());
        if(!actuel.lignes().equals(plan.lignes()))throw new IllegalArgumentException("Votre collection ou le catalogue a changé depuis l'aperçu. Analysez à nouveau le fichier avant de confirmer.");
        Map<Long,Possession> stock=chargerStock(utilisateurId);
        for(var ligne:plan.lignes())if(ligne.prete())for(Cible cible:ligne.cibles()){
            if(!cible.modifiee())continue;
            Possession possession=stock.get(cible.carteId());
            if(cible.apres()==0){if(possession!=null)possessions.delete(possession);continue;}
            if(possession==null){possession=new Possession(utilisateur,entityManager.getReference(Carte.class,cible.carteId()));possessions.save(possession);}
            possession.definirQuantites(cible.apres(),cible.apresFoil());possession.definirSeries(cible.seriesApres());
        }
        possessions.flush();
        return new Bilan(plan.changements(),plan.exemplaires(),plan.mode());
    }
    private Utilisateur utilisateur(Long id){var u=utilisateurs.findById(id).orElseThrow(()->new AccessDeniedException("Compte introuvable."));if(!u.isActif())throw new AccessDeniedException("Compte désactivé.");return u;}
    private Map<Long,Possession> chargerStock(Long id){return entityManager.createQuery("select p from Possession p join fetch p.carte where p.utilisateur.id=:id",Possession.class).setParameter("id",id).getResultList().stream().collect(Collectors.toMap(p->p.getCarte().getId(),p->p));}
    private static String cle(String code,String numero){try{return code.toUpperCase(Locale.ROOT)+":"+Integer.parseInt(numero);}catch(NumberFormatException e){return code+":"+numero;}}
}
