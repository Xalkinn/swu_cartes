package fr.xalkinn.swu.meta;

import fr.xalkinn.swu.carte.Carte;
import fr.xalkinn.swu.deck.Deck;
import fr.xalkinn.swu.deck.DeckCarte;
import fr.xalkinn.swu.utilisateur.Utilisateur;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class MetaCopieService {
    @PersistenceContext private EntityManager em;
    private final MetaAffichageService affichage;
    public MetaCopieService(MetaAffichageService affichage) { this.affichage = affichage; }

    @Transactional(readOnly = true)
    public Long existante(Long utilisateurId, UUID sourceId) {
        return trouver(utilisateurId, sourceId);
    }
    private Long trouver(Long utilisateurId, UUID sourceId) {
        return em.createQuery("select d.id from Deck d where d.utilisateur.id = :joueur and d.metaSourceId = :source", Long.class)
                .setParameter("joueur", utilisateurId).setParameter("source", sourceId.toString())
                .getResultStream().findFirst().orElse(null);
    }

    @Transactional
    public Resultat copier(Long utilisateurId, UUID sourceId) {
        if (utilisateurId == null) throw new AccessDeniedException("Connectez-vous pour copier ce deck.");
        // Sérialise les copies du même compte, y compris lorsque le bouton est pressé deux fois.
        Utilisateur utilisateur = em.find(Utilisateur.class, utilisateurId, LockModeType.PESSIMISTIC_WRITE);
        if (utilisateur == null) throw new AccessDeniedException("Compte introuvable.");
        em.refresh(utilisateur, LockModeType.PESSIMISTIC_WRITE);
        if (!utilisateur.isActif()) throw new AccessDeniedException("Compte désactivé.");
        Long existante = trouver(utilisateurId, sourceId);
        if (existante != null) return new Resultat(existante, true);
        var source = affichage.consulterPourCopie().decks().stream().filter(d -> d.id().equals(sourceId.toString())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cette liste n'est plus dans la sélection."));
        var absentes = source.toutes().stream().filter(c -> c.id() == null).map(MetaAffichageService.Carte::titre).distinct().toList();
        if (!absentes.isEmpty()) throw new IllegalArgumentException("Copie impossible : " + absentes.size()
                + " carte(s) sans correspondance dans le catalogue français (" + String.join(", ", absentes.stream().limit(3).toList())
                + "). Actualisez les extensions concernées puis réessayez.");
        // Vérifie toutes les références avant de créer le deck. Une erreur ne produit jamais une liste tronquée.
        Map<Long,Carte> cartes = new HashMap<>();
        for (var ligne : source.toutes()) {
            Carte carte = em.find(Carte.class, ligne.id());
            if (carte != null && carte.getCarteStandard() != null) carte = carte.getCarteStandard();
            if (carte == null || !carte.isReference() || carte.isJeton() || carte.getTypeCarte() == null)
                throw new IllegalArgumentException("Une carte n'est pas disponible pour la construction de deck. Actualisez son extension.");
            String attendu = ligne.section().equals("Leader") ? "Leader" : ligne.section().equals("Base") ? "Base" : null;
            if ((attendu != null && !attendu.equals(carte.getTypeCarte())) || (attendu == null && Set.of("Leader", "Base").contains(carte.getTypeCarte())))
                throw new IllegalArgumentException("Le type d'une carte ne correspond pas à sa place dans la liste. Actualisez son extension.");
            cartes.put(ligne.id(), carte);
        }
        Map<Long,Integer> principal = quantites(source.principale(), cartes);
        Map<Long,Integer> reserve = quantites(source.reserve(), cartes);
        String nom = "Copie · " + source.nom(); if (nom.length() > 80) nom = nom.substring(0, 80);
        String notes = "Liste de " + source.joueur() + " — Top " + source.rang() + " — " + source.tournoi() + " — " + source.date()
                + ". Source : SWU Meta Stats. Copie personnelle modifiable.";
        Deck deck = new Deck(utilisateur, nom, notes);
        deck.marquerCopieMeta(sourceId);
        deck.choisirLeader(cartes.get(source.leader().id())); deck.choisirBase(cartes.get(source.base().id()));
        for (var q : reserve.entrySet()) deck.definirReserve(em.getReference(Carte.class, q.getKey()), q.getValue());
        em.persist(deck);
        for (var q : principal.entrySet()) em.persist(new DeckCarte(deck, em.getReference(Carte.class, q.getKey()), q.getValue()));
        em.flush(); return new Resultat(deck.getId(), false);
    }
    private Map<Long,Integer> quantites(List<MetaAffichageService.Carte> lignes, Map<Long,Carte> cartes) {
        Map<Long,Integer> resultat = new LinkedHashMap<>();
        for (var ligne : lignes) {
            if (ligne.quantite() < 1 || ligne.quantite() > 99) throw new IllegalArgumentException("Quantité incompatible avec l'éditeur.");
            int total = resultat.merge(cartes.get(ligne.id()).getId(), ligne.quantite(), Integer::sum);
            if (total > 99) throw new IllegalArgumentException("Une carte dépasse la limite de 99 exemplaires dans cette section.");
        }
        return resultat;
    }
    public record Resultat(Long id, boolean existante) { }
}
