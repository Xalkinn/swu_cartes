package fr.xalkinn.swu.securite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Permet de stocker et vérifier les mots de passe hachés.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UtilisateurDetailsService utilisateurDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(utilisateurDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DaoAuthenticationProvider authenticationProvider
    ) throws Exception {

        http
                .authenticationProvider(authenticationProvider)

                .authorizeHttpRequests(acces -> acces
                        // Pages accessibles sans connexion.
                        .requestMatchers(
                                "/",
                                "/inscription",
                                "/error",
                                "/css/**",
                                "/js/**",
                                "/images/**"
                        ).permitAll()

                        // Espace réservé aux administrateurs.
                        .requestMatchers("/admin", "/admin/**")
                        .hasRole("ADMIN")

                        // Toutes les autres pages nécessitent un compte.
                        .anyRequest().authenticated()
                )

                // Page de connexion fournie par Spring Security.
                .formLogin(connexion -> connexion
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )

                .logout(deconnexion -> deconnexion
                        .logoutSuccessUrl("/")
                        .permitAll()
                );

        return http.build();
    }
}