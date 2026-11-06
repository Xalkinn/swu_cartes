package fr.xalkinn.swu.carte;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CatalogueService {

    private final CarteRepository carteRepository;

    public CatalogueService(CarteRepository carteRepository) {
        this.carteRepository = carteRepository;
    }

    @Transactional(readOnly = true)
    public Page<CarteAffichee> rechercher(
            String recherche,
            Long extensionId,
            Integer coutMin,
            Integer coutMax,
            List<String> affinites,
            String mode,
            int page
    ) {
        if (recherche.length() > 100) {
            throw new IllegalArgumentException(
                    "La recherche ne doit pas dépasser 100 caractères."
            );
        }

        if ((coutMin != null && coutMin < 0)
                || (coutMax != null && coutMax < 0)) {
            throw new IllegalArgumentException(
                    "Les coûts doivent être positifs ou nuls."
            );
        }

        if (coutMin != null && coutMax != null && coutMin > coutMax) {
            throw new IllegalArgumentException(
                    "Le coût minimum doit être inférieur ou égal au maximum."
            );
        }

        if (!List.of("TOUTES", "UNE", "SANS").contains(mode)) {
            throw new IllegalArgumentException(
                    "Le mode de sélection des affinités est invalide."
            );
        }

        for (String code : affinites) {
            try {
                Affinite.valueOf(code);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Une affinité sélectionnée est invalide."
                );
            }
        }

        Specification<Carte> specification = (racine, requete, cb) -> {
            List<Predicate> conditions = new ArrayList<>();

            if (!recherche.isBlank()) {
                String motif = "%"
                        + echapperRecherche(recherche.toLowerCase(Locale.ROOT))
                        + "%";

                conditions.add(cb.or(
                        cb.like(
                                cb.lower(racine.<String>get("titre")),
                                motif,
                                '\\'
                        ),
                        cb.like(
                                cb.lower(racine.<String>get("sousTitre")),
                                motif,
                                '\\'
                        )
                ));
            }

            if (extensionId != null) {
                conditions.add(cb.equal(
                        racine.get("extension").get("id"),
                        extensionId
                ));
            }

            if (coutMin != null) {
                conditions.add(cb.greaterThanOrEqualTo(
                        racine.<Integer>get("cout"),
                        coutMin
                ));
            }

            if (coutMax != null) {
                conditions.add(cb.lessThanOrEqualTo(
                        racine.<Integer>get("cout"),
                        coutMax
                ));
            }

            if ("SANS".equals(mode)) {
                conditions.add(cb.not(
                        possedeAffinite(racine, requete, cb, null)
                ));

            } else if (!affinites.isEmpty()) {
                List<Predicate> couleurs = new ArrayList<>();

                for (String code : affinites) {
                    couleurs.add(
                            possedeAffinite(racine, requete, cb, code)
                    );
                }

                conditions.add(
                        "UNE".equals(mode)
                                ? cb.or(couleurs.toArray(Predicate[]::new))
                                : cb.and(couleurs.toArray(Predicate[]::new))
                );
            }

            return cb.and(conditions.toArray(Predicate[]::new));
        };

        Sort tri = Sort.by(
                Sort.Order.asc("extension.code"),
                Sort.Order.asc("numero"),
                Sort.Order.asc("id")
        );

        Page<Carte> resultat = carteRepository.findAll(
                specification,
                PageRequest.of(Math.max(0, page), 24, tri)
        );

        // Si l'URL demande une page qui n'existe plus,
        // on revient à la dernière page disponible.
        if (resultat.getTotalPages() > 0
                && resultat.getNumber() >= resultat.getTotalPages()) {

            resultat = carteRepository.findAll(
                    specification,
                    PageRequest.of(resultat.getTotalPages() - 1, 24, tri)
            );
        }

        // Lecture des relations pendant la transaction.
        // Le HTML reçoit ensuite de simples données d'affichage.
        return resultat.map(carte -> new CarteAffichee(
                carte.getId(),
                carte.getTitre(),
                carte.getSousTitre(),
                carte.getNumeroAffiche(),
                carte.getExtension().getCode(),
                carte.getExtension().getNom(),
                carte.getImageRecto(),
                carte.getImageVerso(),
                carte.getCout(),
                carte.getAffinites()
        ));
    }

    private static Predicate possedeAffinite(
            Root<Carte> racine,
            CriteriaQuery<?> requete,
            CriteriaBuilder cb,
            String code
    ) {
        Subquery<Integer> sousRequete = requete.subquery(Integer.class);
        Root<Carte> carte = sousRequete.correlate(racine);

        MapJoin<Carte, String, Integer> affinite =
                carte.joinMap("affinites");

        sousRequete.select(cb.literal(1));

        if (code != null) {
            sousRequete.where(cb.equal(affinite.key(), code));
        }

        return cb.exists(sousRequete);
    }

    private static String echapperRecherche(String texte) {
        return texte
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    public record CarteAffichee(
            Long id,
            String titre,
            String sousTitre,
            String numeroAffiche,
            String codeExtension,
            String nomExtension,
            String imageRecto,
            String imageVerso,
            Integer cout,
            Map<String, Integer> affinites
    ) {
    }
}