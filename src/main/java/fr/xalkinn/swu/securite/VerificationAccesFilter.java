package fr.xalkinn.swu.securite;

import fr.xalkinn.swu.utilisateur.Utilisateur;
import fr.xalkinn.swu.utilisateur.UtilisateurRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Pas de @Component : ce filtre est installé une seule fois, dans la chaîne SecurityConfig.
public class VerificationAccesFilter extends OncePerRequestFilter {
    private final UtilisateurRepository repository;
    private final SecurityContextRepository contextRepository;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public VerificationAccesFilter(UtilisateurRepository repository,
                                   SecurityContextRepository contextRepository) {
        this.repository = repository;
        this.contextRepository = contextRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String chemin = request.getServletPath();
        return chemin.startsWith("/css/") || chemin.startsWith("/js/")
                || chemin.startsWith("/images/") || chemin.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }

        // Les sessions ouvertes avant cette mise à jour doivent se reconnecter
        // une fois pour disposer de l'identifiant utilisateur stable.
        if (!(authentication.getPrincipal() instanceof UtilisateurConnecte principal)) {
            deconnecter(request, response, authentication);
            return;
        }

        Utilisateur utilisateur = repository.findById(principal.getUtilisateurId()).orElse(null);
        if (utilisateur == null || !utilisateur.isActif()
                || !utilisateur.getEmail().equals(principal.getUsername())
                || (!"ADMIN".equals(utilisateur.getRole()) && !"USER".equals(utilisateur.getRole()))) {
            deconnecter(request, response, authentication);
            return;
        }

        String autorite = "ROLE_" + utilisateur.getRole();
        boolean changement = authentication.getAuthorities().size() != 1
                || authentication.getAuthorities().stream()
                .noneMatch(droit -> autorite.equals(droit.getAuthority()));

        if (changement) {
            UtilisateurConnecte nouveauPrincipal = new UtilisateurConnecte(
                    utilisateur.getId(), utilisateur.getEmail(), "", utilisateur.getRole(), true);
            UsernamePasswordAuthenticationToken nouvelleAuthentification =
                    UsernamePasswordAuthenticationToken.authenticated(
                            nouveauPrincipal, null, nouveauPrincipal.getAuthorities());
            nouvelleAuthentification.setDetails(authentication.getDetails());

            SecurityContext contexte = SecurityContextHolder.createEmptyContext();
            contexte.setAuthentication(nouvelleAuthentification);
            SecurityContextHolder.setContext(contexte);
            contextRepository.saveContext(contexte, request, response);
        }
        chain.doFilter(request, response);
    }

    private void deconnecter(HttpServletRequest request, HttpServletResponse response,
                             Authentication authentication) throws IOException {
        logoutHandler.logout(request, response, authentication);
        response.sendRedirect(response.encodeRedirectURL(
                request.getContextPath() + "/login?sessionExpiree"));
    }
}
