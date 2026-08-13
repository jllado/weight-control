package com.jllado.weightcontrol.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.jllado.weightcontrol.security.ChatGptActionAuthenticationFilter;
import com.jllado.weightcontrol.security.PushReleaseAuthenticationFilter;
import com.jllado.weightcontrol.security.SessionAuthenticationFilter;
import java.util.Arrays;
import java.util.Collections;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class SecurityConfig {

    @Bean
    GoogleIdTokenVerifier googleIdTokenVerifier(AppProperties properties) {
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(Collections.singleton(properties.auth().googleClientId()))
            .build();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        ChatGptActionAuthenticationFilter chatGptActionAuthenticationFilter,
        PushReleaseAuthenticationFilter pushReleaseAuthenticationFilter,
        SessionAuthenticationFilter sessionAuthenticationFilter
    ) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/auth/google", "/api/auth/logout").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/chatgpt-actions/**").hasRole("CHATGPT_ACTION")
                .requestMatchers(HttpMethod.POST, "/api/push/release-notification").hasRole("PUSH_RELEASE")
                .anyRequest().authenticated()
            )
            .addFilterBefore(chatGptActionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(pushReleaseAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    UsernamePasswordAuthenticationToken anonymousAuthentication() {
        return new UsernamePasswordAuthenticationToken("anonymous", null, AuthorityUtils.NO_AUTHORITIES);
    }
}
