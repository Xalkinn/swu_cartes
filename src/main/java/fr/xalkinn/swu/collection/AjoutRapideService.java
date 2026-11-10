package fr.xalkinn.swu.collection;

import fr.xalkinn.swu.carte.Carte;
import fr.xalkinn.swu.carte.CarteRepository;
import fr.xalkinn.swu.extension.Extension;
import fr.xalkinn.swu.extension.ExtensionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AjoutRapideService {

    private final ExtensionRepository extensionRepository;
    private final CarteRepository carteRepository;
    private final CollectionService collectionService;

    public AjoutRapideService(
            ExtensionRepository extensionRepository,
            CarteRepository carteRepository,
            CollectionService collectionService
    ) {
        this.extensionRepository = extensionRepository;
        this.carteRepository = carteRepository;
        this.collectionService = collectionService;
    }

    @Transactional
    public ResultatAjout ajouter(
            String email,
            Long extensionId,
            String numeroSaisi,
            Finition finition,
            Long carteId
    ) {
        return ajouter(email, extensionId, numeroSaisi, finition, carteId, "");
    }

    @Transactional
    public ResultatAjout ajouter(String email, Long extensionId, String numeroSaisi, Finition finition, Long carteId, String serie) {
        if (finition == null) {
            throw new IllegalArgumentException(
                    "Choisissez une finition."
            );
        }

        String saisie = numeroSaisi == null
                ? ""
                : numeroSaisi.strip();

        int numero = fr.xalkinn.swu.carte.ReferenceCarte.numero(saisie);
        boolean jeton = fr.xalkinn.swu.carte.ReferenceCarte.jeton(saisie);

        Extension extension = extensionRepository.findById(extensionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Extension introuvable."
                ));

        // Le numéro recherché est celui imprimé sur la carte.
        // Une hyperspace conserve donc son propre numéro.
        List<Carte> correspondances = carteRepository
                .findByExtensionCodeIgnoreCaseAndNumeroOrderByIdAsc(
                        extension.getCode(),
                        numero
                )
                .stream()
                .filter(carte ->
                        carte.isJeton() == jeton && carte.getVariante() == finition.getFamille() && carte.getFinition().autorise(finition.isFoil())
                )
                .toList();

        if (correspondances.isEmpty()) {
            throw new IllegalArgumentException(
                    "Aucune carte trouvée pour ce numéro et cette finition. "
                            + "Vérifiez le numéro et l'import de l'extension."
            );
        }

        Carte carte;

        if (carteId != null) {
            // Le choix envoyé doit appartenir à la recherche actuelle.
            carte = correspondances.stream()
                    .filter(candidate -> candidate.getId().equals(carteId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cette carte ne correspond plus à votre recherche."
                    ));

        } else if (correspondances.size() > 1) {
            // Aucun ajout tant que l'utilisateur n'a pas choisi.
            return new ResultatAjout(
                    null,
                    correspondances.stream()
                            .map(AjoutRapideService::afficher)
                            .toList(),
                    0,
                    0
            );

        } else {
            carte = correspondances.get(0);
        }

        // Réutilise le service existant et son verrou par utilisateur.
        collectionService.ajuster(
                email,
                carte.getId(),
                1,
                finition.isFoil(), serie
        );

        List<Long> ids = List.of(carte.getId());

        int total = collectionService.quantites(email, ids)
                .getOrDefault(carte.getId(), 0);

        int foils = collectionService.quantitesFoil(email, ids)
                .getOrDefault(carte.getId(), 0);

        return new ResultatAjout(
                afficher(carte),
                List.of(),
                total,
                foils
        );
    }

    private static CarteRapide afficher(Carte carte) {
        return new CarteRapide(
                carte.getId(),
                carte.getTitre(),
                carte.getSousTitre(),
                carte.getNumeroAffiche(),
                carte.getExtension().getCode(),
                carte.getImageRecto()
        );
    }

    public enum Finition {

        CLASSIQUE("Standard", fr.xalkinn.swu.carte.VarianteCarte.STANDARD, false),
        FOIL("Standard foil", fr.xalkinn.swu.carte.VarianteCarte.STANDARD, true),
        HYPERSPACE("Hyperspace", fr.xalkinn.swu.carte.VarianteCarte.HYPERSPACE, false),
        HYPERSPACE_FOIL("Hyperspace foil", fr.xalkinn.swu.carte.VarianteCarte.HYPERSPACE, true),
        SHOWCASE("Showcase", fr.xalkinn.swu.carte.VarianteCarte.SHOWCASE, true),
        PRESTIGE("Prestige", fr.xalkinn.swu.carte.VarianteCarte.PRESTIGE, false),
        PRESTIGE_FOIL("Prestige foil", fr.xalkinn.swu.carte.VarianteCarte.PRESTIGE, true),
        PRESTIGE_SERIALISEE("Prestige sérialisée", fr.xalkinn.swu.carte.VarianteCarte.PRESTIGE_SERIALISEE, true),
        PROMO("Promotionnelle non foil", fr.xalkinn.swu.carte.VarianteCarte.PROMO, false),
        PROMO_FOIL("Promotionnelle foil", fr.xalkinn.swu.carte.VarianteCarte.PROMO, true),
        AUTRE("Autre traitement non foil", fr.xalkinn.swu.carte.VarianteCarte.AUTRE, false),
        AUTRE_FOIL("Autre traitement foil", fr.xalkinn.swu.carte.VarianteCarte.AUTRE, true);

        private final String libelle;
        private final fr.xalkinn.swu.carte.VarianteCarte famille;
        private final boolean foil;

        Finition(String libelle, fr.xalkinn.swu.carte.VarianteCarte famille, boolean foil) {
            this.libelle = libelle;
            this.famille = famille;
            this.foil = foil;
        }

        public String getLibelle() {
            return libelle;
        }

        public boolean isHyperspace() {
            return famille == fr.xalkinn.swu.carte.VarianteCarte.HYPERSPACE;
        }

        public fr.xalkinn.swu.carte.VarianteCarte getFamille() {
            return famille;
        }

        public boolean isFoil() {
            return foil;
        }
    }

    public record CarteRapide(
            Long id,
            String titre,
            String sousTitre,
            String numero,
            String extension,
            String image
    ) {
    }

    public record ResultatAjout(
            CarteRapide carte,
            List<CarteRapide> candidats,
            int total,
            int foils
    ) {
    }
}