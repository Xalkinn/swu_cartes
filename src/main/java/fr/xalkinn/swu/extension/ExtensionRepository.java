package fr.xalkinn.swu.extension;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExtensionRepository
        extends JpaRepository<Extension, Long> {

    boolean existsByCodeIgnoreCase(String code);

    // Retrouve notre extension à partir du code reçu de la source.
    Optional<Extension> findByCodeIgnoreCase(String code);
}