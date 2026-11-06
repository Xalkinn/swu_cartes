package fr.xalkinn.swu.extension;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Locale;

public record ExtensionForm(

        @NotBlank(message = "Le code est obligatoire.")
        @Pattern(
                regexp = "[A-Z0-9-]{2,12}",
                message = "Le code doit contenir 2 à 12 lettres, chiffres ou tirets."
        )
        String code,

        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères.")
        String nom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dateSortie

) {

    public ExtensionForm {
        code = code == null
                ? ""
                : code.strip().toUpperCase(Locale.ROOT);

        nom = nom == null ? "" : nom.strip();
    }
}