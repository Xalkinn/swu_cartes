package fr.xalkinn.swu.securite;

import fr.xalkinn.swu.utilisateur.UtilisateurRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UtilisateurDetailsService utilisateurDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(utilisateurDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DaoAuthenticationProvider authenticationProvider,
            UtilisateurRepository utilisateurRepository,
            SecurityContextRepository securityContextRepository
    ) throws Exception {
        http.authenticationProvider(authenticationProvider)
                .securityContext(contexte -> contexte.securityContextRepository(securityContextRepository))
                // Vérifie les droits en base avant la décision d'autorisation.
                .addFilterBefore(new VerificationAccesFilter(
                        utilisateurRepository, securityContextRepository), AuthorizationFilter.class)
                .authorizeHttpRequests(acces -> acces
                        .requestMatchers("/", "/inscription", "/error", "/css/**", "/js/**",
                                "/images/**", "/api/accueil/actualites", "/decks-du-moment").permitAll()
                        .requestMatchers(HttpMethod.GET, "/decks-du-moment/**", "/api/meta/etat", "/joueurs", "/joueurs/**", "/communaute/decks", "/communaute/decks/**").permitAll()
                        .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(connexion -> connexion
                        .loginPage("/login").loginProcessingUrl("/login")
                        .usernameParameter("email").defaultSuccessUrl("/", true)
                        .failureUrl("/login?error").permitAll())
                .logout(deconnexion -> deconnexion.logoutSuccessUrl("/").permitAll());

        // La protection CSRF reste activée, notamment pour les changements de droits.
        return http.build();
    }
}
