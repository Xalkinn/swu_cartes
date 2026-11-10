package fr.xalkinn.swu.collection;

import fr.xalkinn.swu.carte.Carte;
import fr.xalkinn.swu.carte.CarteRepository;
import fr.xalkinn.swu.utilisateur.Utilisateur;
import fr.xalkinn.swu.utilisateur.UtilisateurRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CollectionService {

    private final PossessionRepository possessionRepository;
    private final CarteRepository carteRepository;
    private final UtilisateurRepository utilisateurRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public CollectionService(
            PossessionRepository possessionRepository,
            CarteRepository carteRepository,
            UtilisateurRepository utilisateurRepository
    ) {
        this.possessionRepository = possessionRepository;
        this.carteRepository = carteRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    @Transactional
    public void ajuster(String email, Long carteId, int variation) {
        ajuster(email, carteId, variation, false);
    }

    @Transactional
    public void ajuster(
            String email,
            Long carteId,
            int variation,
            boolean foil
    ) {
        ajuster(email,carteId,variation,foil,"");
    }

    @Transactional
    public void ajuster(String email,Long carteId,int variation,boolean foil,String serie) {
        if (variation != 1 && variation != -1) {
            throw new IllegalArgumentException(
                    "Seuls les ajustements +1 et -1 sont autorisés."
            );
        }

        Utilisateur utilisateur = utilisateurRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Compte introuvable."
                ));

        // Sérialise les modifications de la collection de cet utilisateur.
        entityManager.lock(
                utilisateur,
                LockModeType.PESSIMISTIC_WRITE
        );

        Carte carte = carteRepository.findById(carteId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Carte introuvable."
                ));

        if(carte.getFinition()==fr.xalkinn.swu.carte.FinitionCarte.FOIL)foil=true;
        if(variation==1&&!carte.getFinition().autorise(foil)){
            boolean historique=foil&&(carte.getVariante()==fr.xalkinn.swu.carte.VarianteCarte.STANDARD||carte.getVariante()==fr.xalkinn.swu.carte.VarianteCarte.HYPERSPACE)&&possessionRepository.findByUtilisateurIdAndCarteId(utilisateur.getId(),carteId).map(p->p.getQuantiteFoil()>0).orElse(false);
            if(!historique)throw new IllegalArgumentException("Choisissez l'impression correspondant à cette finition dans le catalogue.");
            Long reference=carte.getCarteStandard()==null?carte.getId():carte.getCarteStandard().getId();
            fr.xalkinn.swu.carte.VarianteCarte famille=carte.getVariante();
            List<Carte> choix=carteRepository.trouverImpressions(List.of(reference)).stream().filter(c->c.getVariante()==famille&&c.getFinition()==fr.xalkinn.swu.carte.FinitionCarte.FOIL).toList();
            if(choix.size()!=1)throw new IllegalArgumentException("La finition historique ne possède pas de correspondance foil unique. Utilisez le catalogue.");
            carte=choix.get(0);carteId=carte.getId();
        }
        String numeroSerie=Possession.normaliserSerie(serie);
        if(!numeroSerie.isEmpty()&&carte.getVariante()!=fr.xalkinn.swu.carte.VarianteCarte.PRESTIGE_SERIALISEE)throw new IllegalArgumentException("Cette impression n'est pas sérialisée.");

        Possession possession = possessionRepository
                .findByUtilisateurIdAndCarteId(
                        utilisateur.getId(),
                        carteId
                )
                .orElse(null);

        if (possession == null) {
            if (variation == 1) {
                Possession nouvelle=new Possession(utilisateur,carte,foil);
                if(!numeroSerie.isBlank())nouvelle.definirSeries(List.of(numeroSerie));
                possessionRepository.save(nouvelle);
            }

            return;
        }

        possession.ajuster(variation, foil,numeroSerie);

        if (possession.getQuantite() == 0) {
            possessionRepository.delete(possession);
        }
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> quantites(
            String email,
            List<Long> carteIds
    ) {
        if (carteIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> resultat = new HashMap<>();

        for (Possession possession : possessionRepository
                .findByUtilisateurEmailIgnoreCaseAndCarteIdIn(
                        email,
                        carteIds
                )) {

            resultat.put(
                    possession.getCarte().getId(),
                    possession.getQuantite()
            );
        }

        return resultat;
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> quantitesFoil(
            String email,
            List<Long> carteIds
    ) {
        if (carteIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> resultat = new HashMap<>();

        for (Possession possession : possessionRepository
                .findByUtilisateurEmailIgnoreCaseAndCarteIdIn(
                        email,
                        carteIds
                )) {

            resultat.put(
                    possession.getCarte().getId(),
                    possession.getQuantiteFoil()
            );
        }

        return resultat;
    }

    @Transactional(readOnly = true)
    public Page<CartePossedee> consulter(String email, int page) {
        return consulter(email, "", null, "TOUTES", false, page);
    }

    @Transactional(readOnly = true)
    public Page<CartePossedee> consulter(String email, String recherche, Long extensionId,
                                        String version, boolean recherches, int page) {
        return consulter(email,recherche,extensionId,version,recherches,page,fr.xalkinn.swu.carte.FiltresIdentite.VIDE);
    }
    @Transactional(readOnly = true)
    public Page<CartePossedee> consulter(String email, String recherche, Long extensionId,
                                        String version, boolean recherches, int page, fr.xalkinn.swu.carte.FiltresIdentite identite) {
        return consulter(email,recherche,extensionId,version,recherches,page,identite,null);
    }
    // Le propriétaire de la collection reste email ; seul le propriétaire de la liste
    // de recherches varie. Le contrôleur public obtient celui-ci depuis la session.
    @Transactional(readOnly = true)
    public Page<CartePossedee> consulter(String email, String recherche, Long extensionId,
                                        String version, boolean recherches, int page, fr.xalkinn.swu.carte.FiltresIdentite identite,
                                        Long recherchesUtilisateurId) {
        return consulter(email, recherche, extensionId, version, recherches, page, identite, recherchesUtilisateurId, FiltresCollection.VIDE);
    }

    @Transactional(readOnly = true)
    public Page<CartePossedee> consulter(String email, String recherche, Long extensionId,
            String version, boolean recherches, int page, fr.xalkinn.swu.carte.FiltresIdentite identite,
            Long recherchesUtilisateurId, FiltresCollection filtres) {
        identite.verifier();
        filtres.verifier();
        String q = recherche == null ? "" : recherche.strip();
        if (q.length() > 100) throw new IllegalArgumentException("La recherche est limitée à 100 caractères.");
        if (!List.of("TOUTES", "STANDARD", "HYPERSPACE", "FOIL", "NONFOIL", "SHOWCASE", "PRESTIGE", "PRESTIGE_SERIALISEE", "PROMO", "AUTRE").contains(version)) {
            throw new IllegalArgumentException("Version possédée invalide.");
        }
        Integer numero = q.matches("(?i)T?[0-9]{1,6}") ? fr.xalkinn.swu.carte.ReferenceCarte.numero(q) : null;
        Page<Long> groupes = RechercheGroupesCollection.rechercher(entityManager, email, q, numero,
                extensionId, version, recherches, identite, recherchesUtilisateurId, filtres, page);

        if (groupes.isEmpty()) {
            return new PageImpl<>(
                    List.of(),
                    groupes.getPageable(),
                    groupes.getTotalElements()
            );
        }

        List<Long> ids = groupes.getContent();

        Map<Long, Carte> cartesDeReference = carteRepository
                .findAllById(ids)
                .stream()
                .collect(Collectors.toMap(
                        Carte::getId,
                        carte -> carte
                ));

        List<Possession> possessionsTrouvees =
                possessionRepository.trouverPossessionsDesGroupes(
                        email,
                        ids
                );

        Map<Long, List<Possession>> possessionsParCarte =
                possessionsTrouvees.stream()
                        .collect(Collectors.groupingBy(
                                (Possession possession) -> {
                                    Carte carte = possession.getCarte();
                                    Carte standard = carte.getCarteStandard();

                                    return standard == null
                                            ? carte.getId()
                                            : standard.getId();
                                }
                        ));

        List<CartePossedee> resultat = new ArrayList<>();

        Comparator<Possession> triVersions = Comparator
                .comparingInt((Possession possession) ->
                        possession.getCarte().getVariante().ordinal()
                )
                .thenComparing((Possession possession) ->
                        possession.getCarte().getNumero()
                )
                .thenComparing((Possession possession) ->
                        possession.getCarte().getId()
                );

        for (Long id : ids) {
            Carte reference = cartesDeReference.get(id);

            if (reference == null) {
                throw new IllegalStateException(
                        "Une carte de référence est introuvable."
                );
            }

            List<Possession> possessions = new ArrayList<>(
                    possessionsParCarte.getOrDefault(id, List.of())
            );

            possessions.sort(triVersions);

            int total = 0;
            int totalFoil = 0;

            List<VersionPossedee> versions = new ArrayList<>();

            for (Possession possession : possessions) {
                Carte carte = possession.getCarte();

                total += possession.getQuantite();
                totalFoil += possession.getQuantiteFoil();

                String libelle = carte.libelleVersion(false);

                int nonFoils = possession.getQuantite()
                        - possession.getQuantiteFoil();

                if (nonFoils > 0) {
                    versions.add(new VersionPossedee(
                            carte.getId(),
                            libelle,
                            carte.getNumeroAffiche(),
                            carte.getImageRecto(),
                            false,
                            nonFoils,
                            possession.getQuantite(), carte.getExtension().getCode(),
                            possession.getNumerosSerie().stream().sorted().toList()
                    ));
                }

                if (possession.getQuantiteFoil() > 0) {
                    versions.add(new VersionPossedee(
                            carte.getId(),
                            carte.libelleVersion(true),
                            carte.getNumeroAffiche(),
                            carte.getImageRecto(),
                            true,
                            possession.getQuantiteFoil(),
                            possession.getQuantite(), carte.getExtension().getCode(),
                            possession.getNumerosSerie().stream().sorted().toList()
                    ));
                }
            }

            resultat.add(new CartePossedee(
                    reference.getId(),
                    reference.getTitre(),
                    reference.getSousTitre(),
                    reference.getNumeroAffiche(),
                    reference.getExtension().getCode(),
                    reference.getImageRecto(),
                    total,
                    totalFoil,
                    List.copyOf(versions)
            ));
        }

        return new PageImpl<>(
                resultat,
                groupes.getPageable(),
                groupes.getTotalElements()
        );
    }

    public record CartePossedee(
            Long idCarte,
            String titre,
            String sousTitre,
            String numeroAffiche,
            String codeExtension,
            String imageRecto,
            int quantite,
            int quantiteFoil,
            List<VersionPossedee> versions
    ) {
    }

    public record VersionPossedee(
            Long idCarte,
            String libelle,
            String numeroAffiche,
            String imageRecto,
            boolean foil,
            int quantite,
            int totalSurCarte,
            String codeExtension,
            List<String> numerosSerie
    ) {
        public VersionPossedee(Long idCarte,String libelle,String numeroAffiche,String imageRecto,boolean foil,int quantite,int totalSurCarte){this(idCarte,libelle,numeroAffiche,imageRecto,foil,quantite,totalSurCarte,"",List.of());}
    }
}