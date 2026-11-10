package fr.xalkinn.swu.carte;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CarteRepository
        extends JpaRepository<Carte, Long>,
        JpaSpecificationExecutor<Carte> {

    Optional<Carte> findByIdentifiantSource(String identifiantSource);

    @EntityGraph(attributePaths = {"extension", "affinites"})
    List<Carte> findByExtensionIdOrderByNumeroAsc(Long extensionId);

    long countByExtensionId(Long extensionId);

    // L'extension est chargée avec les cartes.
    // Les affinités seront lues dans le service transactionnel.
    @Override
    @EntityGraph(attributePaths = "extension")
    Page<Carte> findAll(
            Specification<Carte> specification,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "extension")
    @org.springframework.data.jpa.repository.Query("select c from Carte c join c.extension e where lower(e.code)=lower(:codeExtension) and c.numero=:numero order by c.id")
    List<Carte> findByExtensionCodeIgnoreCaseAndNumeroOrderByIdAsc(
            @org.springframework.data.repository.query.Param("codeExtension") String codeExtension,
            @org.springframework.data.repository.query.Param("numero") Integer numero
    );
    @EntityGraph(attributePaths="extension")
    @org.springframework.data.jpa.repository.Query("select c from Carte c where c.carteStandard.id in :ids")
    List<Carte> trouverImpressions(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
    long countByTypeCarteIsNull();
}