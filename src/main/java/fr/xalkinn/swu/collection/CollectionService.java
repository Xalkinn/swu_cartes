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
import org.springframework.data.domain.PageRequest;
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

        Possession possession = possessionRepository
                .findByUtilisateurIdAndCarteId(
                        utilisateur.getId(),
                        carteId
                )
                .orElse(null);

        if (possession == null) {
            if (variation == 1) {
                possessionRepository.save(
                        new Possession(utilisateur, carte, foil)
                );
            }

            return;
        }

        possession.ajuster(variation, foil);

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
        return consulter(email, recherche, extensionId, version, recherches, page, fr.xalkinn.swu.carte.FiltresIdentite.VIDE);
    }

    @Transactional(readOnly = true)
    public Page<CartePossedee> consulter(String email, String recherche, Long extensionId,
                                         String version, boolean recherches, int page, fr.xalkinn.swu.carte.FiltresIdentite identite) {
        return consulter(email, recherche, extensionId, version, recherches, page, identite, null);
    }

    // Le propriétaire de la collection reste email ; seul le propriétaire de la liste
    // de recherches varie. Le contrôleur public obtient celui-ci depuis la session.
    @Transactional(readOnly = true)
    public Page<CartePossedee> consulter(String email, String recherche, Long extensionId,
                                         String version, boolean recherches, int page, fr.xalkinn.swu.carte.FiltresIdentite identite,
                                         Long recherchesUtilisateurId) {
        identite.verifier();
        String q = recherche == null ? "" : recherche.strip();
        if (q.length() > 100) throw new IllegalArgumentException("La recherche est limitée à 100 caractères.");
        if (!List.of("TOUTES", "STANDARD", "HYPERSPACE", "FOIL", "NONFOIL").contains(version)) {
            throw new IllegalArgumentException("Version possédée invalide.");
        }
        Integer numero = q.matches("[0-9]{1,6}") ? Integer.valueOf(q) : null;
        Page<Long> groupes = possessionRepository.trouverGroupesFiltresIdentitePour(
                email, q, numero, extensionId, version, recherches,
                !identite.arenes().isEmpty(), identite.arenes().isEmpty() ? List.of("") : identite.arenes(),
                !identite.traits().isEmpty(), identite.traits().isEmpty() ? List.of("") : identite.traits(),
                "TOUS".equals(identite.modeTraits()), (long) identite.traits().size(), recherchesUtilisateurId,
                PageRequest.of(Math.max(0, page), 24)
        );

        // Après une suppression, la page demandée peut ne plus exister.
        if (groupes.getTotalPages() > 0
                && groupes.getNumber() >= groupes.getTotalPages()) {

            groupes = possessionRepository.trouverGroupesFiltresIdentitePour(
                    email, q, numero, extensionId, version, recherches,
                    !identite.arenes().isEmpty(), identite.arenes().isEmpty() ? List.of("") : identite.arenes(),
                    !identite.traits().isEmpty(), identite.traits().isEmpty() ? List.of("") : identite.traits(),
                    "TOUS".equals(identite.modeTraits()), (long) identite.traits().size(), recherchesUtilisateurId,
                    PageRequest.of(groupes.getTotalPages() - 1, 24)
            );
        }

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
                        possession.getCarte().isHyperspace() ? 1 : 0
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

                String libelle = carte.isHyperspace()
                        ? "Hyperspace"
                        : "Standard";

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
                            possession.getQuantite()
                    ));
                }

                if (possession.getQuantiteFoil() > 0) {
                    versions.add(new VersionPossedee(
                            carte.getId(),
                            libelle + " foil",
                            carte.getNumeroAffiche(),
                            carte.getImageRecto(),
                            true,
                            possession.getQuantiteFoil(),
                            possession.getQuantite()
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
            int totalSurCarte
    ) {
    }
}