package fr.xalkinn.swu.extension;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "extensions")
public class Extension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String code;

    @Column(nullable = false, length = 150)
    private String nom;

    @Column(name = "date_sortie")
    private LocalDate dateSortie;

    protected Extension() {
    }

    public Extension(String code, String nom, LocalDate dateSortie) {
        this.code = code;
        this.nom = nom;
        this.dateSortie = dateSortie;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getNom() {
        return nom;
    }

    public LocalDate getDateSortie() {
        return dateSortie;
    }
}