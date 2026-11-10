package fr.xalkinn.swu.classeur;

import fr.xalkinn.swu.carte.Carte;
import fr.xalkinn.swu.carte.ReferenceCarte;
import fr.xalkinn.swu.collection.Possession;
import fr.xalkinn.swu.recherche.RechercheService;
import fr.xalkinn.swu.utilisateur.Utilisateur;
import fr.xalkinn.swu.utilisateur.UtilisateurRepository;
import fr.xalkinn.swu.vitrine.VitrineService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class ClasseurService {
    public static final int TAILLE_PAGE = 9;
    private final UtilisateurRepository utilisateurs;
    private final VitrineService vitrines;
    private final RechercheService recherches;
    @PersistenceContext
    private EntityManager entityManager;

    public ClasseurService(UtilisateurRepository utilisateurs, VitrineService vitrines, RechercheService recherches) {
        this.utilisateurs = utilisateurs;
        this.vitrines = vitrines;
        this.recherches = recherches;
    }

    public Vue personnel(Long utilisateurId, Long extensionId, String etat, String q, int page) {
        Utilisateur u = actif(utilisateurId);
        return construire(u, null, utilisateurId, extensionId, etat, q, page);
    }

    public Vue publicPour(Long joueurId, Long visiteurId, Long extensionId, String etat, String q, int page) {
        // Contrôle en service, y compris pour un appel direct ou une URL forgée.
        var joueur = vitrines.classeurPublic(joueurId);
        if (visiteurId != null) actif(visiteurId);
        return construire(actif(joueurId), joueur, visiteurId, extensionId, etat, q, page);
    }

    private Utilisateur actif(Long id) {
        Utilisateur u = utilisateurs.findById(id).orElseThrow(() -> new AccessDeniedException("Compte introuvable."));
        if (!u.isActif()) throw new AccessDeniedException("Compte désactivé.");
        return u;
    }

    private Vue construire(Utilisateur proprietaire, VitrineService.Joueur joueur, Long visiteurId,
                           Long extensionId, String etat, String q, int page) {
        if (!List.of("TOUTES", "POSSEDEES", "MANQUANTES").contains(etat))
            throw new IllegalArgumentException("État des emplacements invalide.");
        String recherche = q == null ? "" : q.strip();
        if (recherche.length() > 100) throw new IllegalArgumentException("La recherche est limitée à 100 caractères.");
        // Une seule case par référence standard. Les jetons gardent leurs références T01, T02…
        List<Object[]> editionsConnues = entityManager.createQuery("""
                select e.id,e.code,e.nom from Carte c join c.extension e
                where c.variante = fr.xalkinn.swu.carte.VarianteCarte.STANDARD
                  and c.finition <> fr.xalkinn.swu.carte.FinitionCarte.FOIL
                  and c.carteStandard is null and c.rattachementAttendu = false
                group by e.id,e.code,e.nom order by e.code,e.id
                """, Object[].class).getResultList();
        Map<Long, ExtensionClasseur> editions = new LinkedHashMap<>();
        for (Object[] edition : editionsConnues)
            editions.put((Long) edition[0],
                    new ExtensionClasseur((Long) edition[0], (String) edition[1], (String) edition[2]));
        Long selection = extensionId == null && !editions.isEmpty() ? editions.keySet().iterator().next() : extensionId;
        if (selection != null && !editions.containsKey(selection))
            throw new IllegalArgumentException("Cette extension ne contient pas de références standard importées.");
        List<Carte> serie = selection == null ? List.of() : entityManager.createQuery("""
                select c from Carte c join fetch c.extension
                where c.extension.id = :edition
                  and c.variante = fr.xalkinn.swu.carte.VarianteCarte.STANDARD
                  and c.finition <> fr.xalkinn.swu.carte.FinitionCarte.FOIL
                  and c.carteStandard is null and c.rattachementAttendu = false
                order by case when c.typeCarte in ('Token','Token Unit','Token Upgrade') then 1 else 0 end,c.numero,c.id
                """, Carte.class).setParameter("edition", selection).getResultList();
        // Agrégation des impressions : standard, foil, Hyperspace et promotions reliées.
        List<Object[]> stock = entityManager.createQuery("""
                select coalesce(s.id, c.id), sum(p.quantite), sum(p.quantiteFoil)
                from Possession p join p.carte c left join c.carteStandard s left join s.extension se
                where p.utilisateur.id = :joueur and p.quantite > 0
                  and coalesce(se.id,c.extension.id) = :edition
                group by coalesce(s.id, c.id)
                """, Object[].class).setParameter("joueur", proprietaire.getId()).setParameter("edition", selection).getResultList();
        Map<Long, int[]> quantites = new HashMap<>();
        for (Object[] ligne : stock)
            quantites.put((Long) ligne[0], new int[]{((Number) ligne[1]).intValue(), ((Number) ligne[2]).intValue()});
        int remplies = (int) serie.stream().filter(c -> quantites.containsKey(c.getId())).count();
        int total = serie.size();
        List<Emplacement> cases = new ArrayList<>();
        for (int position = 0; position < serie.size(); position++) {
            Carte c = serie.get(position);
            int[] quantite = quantites.getOrDefault(c.getId(), new int[2]);
            boolean possedee = quantite[0] > 0;
            if ("POSSEDEES".equals(etat) && !possedee || "MANQUANTES".equals(etat) && possedee) continue;
            String contenu = (c.getTitre() + " " + Objects.toString(c.getSousTitre(), "") + " " + c.getNumeroAffiche()).toLowerCase(Locale.ROOT);
            boolean numero = recherche.matches("(?i)T?[0-9]{1,6}")
                    && ReferenceCarte.jeton(recherche) == c.isJeton() && ReferenceCarte.numero(recherche) == c.getNumero();
            if (recherche.matches("(?i)T?[0-9]{1,6}") ? !numero : !recherche.isBlank() && !contenu.contains(recherche.toLowerCase(Locale.ROOT)))
                continue;
            cases.add(new Emplacement(c.getId(), position + 1, c.getTitre(), c.getSousTitre(), c.getNumeroAffiche(),
                    c.getExtension().getCode(), c.getImageRecto(), quantite[0], quantite[1], List.of()));
        }
        int pages = cases.isEmpty() ? 1 : (cases.size() + TAILLE_PAGE - 1) / TAILLE_PAGE;
        int index = Math.min(Math.max(page, 0), pages - 1);
        List<Emplacement> visibles = new ArrayList<>(cases.subList(index * TAILLE_PAGE, Math.min((index + 1) * TAILLE_PAGE, cases.size())));
        List<Long> ids = visibles.stream().map(Emplacement::id).toList();
        Map<Long, List<Version>> versions = new HashMap<>();
        if (!ids.isEmpty()) {
            List<Possession> exemplaires = entityManager.createQuery("""
                    select p from Possession p join fetch p.carte c join fetch c.extension
                    left join c.carteStandard s
                    where p.utilisateur.id = :joueur and coalesce(s.id,c.id) in :ids and p.quantite > 0
                    order by c.variante,c.numero,c.id
                    """, Possession.class).setParameter("joueur", proprietaire.getId()).setParameter("ids", ids).getResultList();
            for (Possession p : exemplaires) {
                Carte c = p.getCarte();
                Long reference = c.getCarteStandard() == null ? c.getId() : c.getCarteStandard().getId();
                var liste = versions.computeIfAbsent(reference, id -> new ArrayList<>());
                int normales = p.getQuantite() - p.getQuantiteFoil();
                if (normales > 0)
                    liste.add(new Version(c.getLibelleVariante() + " · non foil", c.getExtension().getCode() + " · " + c.getNumeroAffiche(), normales));
                if (p.getQuantiteFoil() > 0)
                    liste.add(new Version(c.getLibelleVariante() + " · foil", c.getExtension().getCode() + " · " + c.getNumeroAffiche(), p.getQuantiteFoil()));
            }
        }
        visibles = visibles.stream().map(c -> new Emplacement(c.id(), c.position(), c.titre(), c.sousTitre(), c.numero(), c.codeExtension(), c.image(), c.quantite(), c.foil(), List.copyOf(versions.getOrDefault(c.id(), List.of())))).toList();
        Set<Long> coeurs = visiteurId == null ? Set.of() : recherches.selection(visiteurId, ids);
        return new Vue(proprietaire.getId(), proprietaire.getPseudo(), joueur != null, joueur, List.copyOf(editions.values()),
                selection == null ? null : editions.get(selection), etat, recherche, total, remplies, total - remplies,
                total == 0 ? 0 : (int) Math.round(100.0 * remplies / total),
                new PageImpl<>(visibles, PageRequest.of(index, TAILLE_PAGE), cases.size()), coeurs, visiteurId != null);
    }

    public record ExtensionClasseur(Long id, String code, String nom) {
    }

    public record Version(String libelle, String reference, int quantite) {
    }

    public record Emplacement(Long id, int position, String titre, String sousTitre, String numero,
                              String codeExtension, String image, int quantite, int foil, List<Version> versions) {
        public boolean isPossedee() {
            return quantite > 0;
        }
    }

    public record Vue(Long joueurId, String pseudo, boolean publique, VitrineService.Joueur joueur,
                      List<ExtensionClasseur> extensions, ExtensionClasseur extension, String etat, String q,
                      int total, int remplies, int manquantes, int pourcentage, Page<Emplacement> resultat,
                      Set<Long> recherches, boolean peutRechercher) {
    }
}
