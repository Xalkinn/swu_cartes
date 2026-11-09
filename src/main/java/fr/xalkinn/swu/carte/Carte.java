package fr.xalkinn.swu.carte;

import fr.xalkinn.swu.extension.Extension;

import jakarta.persistence.*;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "cartes")
public class Carte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "identifiant_source",
            nullable = false,
            unique = true,
            length = 100
    )
    private String identifiantSource;

    @Column(nullable = false, length = 255)
    private String titre;

    @Column(name = "sous_titre", length = 255)
    private String sousTitre;

    @Column(nullable = false)
    private Integer numero;

    @Column(name = "image_recto", columnDefinition = "TEXT")
    private String imageRecto;

    @Column(name = "image_verso", columnDefinition = "TEXT")
    private String imageVerso;

    @Column(name = "type_carte", length = 40)
    private String typeCarte;

    public String getTypeCarte() { return typeCarte; }

    public void actualiserType(String typeCarte) {
        if (typeCarte == null || typeCarte.isBlank() || typeCarte.length() > 40) {
            throw new IllegalArgumentException("Type de carte invalide.");
        }
        this.typeCarte = typeCarte;
    }

    @Column(name = "rarete", length = 2)
    private String rarete;

    public String getRarete() { return rarete; }
    public void actualiserRarete(String code) {
        if (code != null && (Rarete.valueOf(code) == Rarete.INCONNUE)) throw new IllegalArgumentException("Code de rareté invalide.");
        this.rarete = code;
    }

    @Column(name = "cout")
    private Integer cout;

    @Column(nullable = false)
    private boolean hyperspace;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "carte_affinites",
            joinColumns = @JoinColumn(name = "carte_id")
    )
    @MapKeyColumn(name = "code_affinite", length = 30)
    @Column(name = "nombre_symboles", nullable = false)
    private Map<String, Integer> affinites = new HashMap<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carte_standard_id")
    private Carte carteStandard;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "extension_id", nullable = false)
    private Extension extension;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "carte_arenes", joinColumns = @JoinColumn(name = "carte_id"))
    @Column(name = "code_arene", nullable = false, length = 20)
    private java.util.Set<String> arenes = new java.util.HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "carte_traits", joinColumns = @JoinColumn(name = "carte_id"))
    @Column(name = "trait", nullable = false, length = 80)
    private java.util.Set<String> traits = new java.util.HashSet<>();

    public void actualiserIdentite(java.util.Set<String> arenes, java.util.Set<String> traits) {
        this.arenes.clear(); this.arenes.addAll(arenes);
        this.traits.clear(); this.traits.addAll(traits);
    }
    public java.util.Set<String> getArenes() { return java.util.Set.copyOf(arenes); }
    public java.util.Set<String> getTraits() { return java.util.Set.copyOf(traits); }

    protected Carte() {
    }

    public Carte(
            String identifiantSource,
            String titre,
            String sousTitre,
            Integer numero,
            Extension extension
    ) {
        this.identifiantSource = identifiantSource;
        actualiser(titre, sousTitre, numero, extension);
    }

    // Réutilisé lorsqu'une carte déjà importée est mise à jour.
    public void actualiser(
            String titre,
            String sousTitre,
            Integer numero,
            Extension extension
    ) {
        this.titre = titre;
        this.sousTitre = sousTitre;
        this.numero = numero;
        this.extension = extension;
    }

    public Long getId() {
        return id;
    }

    public String getIdentifiantSource() {
        return identifiantSource;
    }

    public String getTitre() {
        return titre;
    }

    public String getSousTitre() {
        return sousTitre;
    }

    public Integer getNumero() {
        return numero;
    }

    // Conserve un nombre en BDD, mais permet l'affichage « 005 ».
    public String getNumeroAffiche() {
        return String.format(java.util.Locale.ROOT, "%03d", numero);
    }

    public Extension getExtension() {
        return extension;
    }
    public void actualiserImages(String imageRecto, String imageVerso) {
        this.imageRecto = imageRecto;
        this.imageVerso = imageVerso;
    }

    public String getImageRecto() {
        return imageRecto;
    }

    public String getImageVerso() {
        return imageVerso;
    }

    public void actualiserFiltres(
            Integer cout,
            Map<String, Integer> nouvellesAffinites
    ) {
        this.cout = cout;

        // On remplace le contenu en conservant la collection gérée par JPA.
        this.affinites.clear();
        this.affinites.putAll(nouvellesAffinites);
    }

    public Integer getCout() {
        return cout;
    }

    public Map<String, Integer> getAffinites() {
        return Map.copyOf(affinites);
    }

    public void actualiserHyperspace(boolean hyperspace) {
        this.hyperspace = hyperspace;
    }

    public boolean isHyperspace() {
        return hyperspace;
    }

    public Carte getCarteStandard() {
        return carteStandard;
    }

    public void rattacherA(Carte standard) {
        if (standard != null) {
            if (standard == this
                    || (id != null && id.equals(standard.getId()))) {
                throw new IllegalArgumentException(
                        "Une carte ne peut pas être sa propre variante."
                );
            }

            if (standard.isHyperspace()) {
                throw new IllegalArgumentException(
                        "La carte de référence doit être une carte standard."
                );
            }

            if (!extension.getId().equals(
                    standard.getExtension().getId()
            )) {
                throw new IllegalArgumentException(
                        "Les deux cartes doivent appartenir à la même extension."
                );
            }
        }

        this.carteStandard = standard;
    }
}