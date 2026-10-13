package edu.university.badminton.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import edu.university.badminton.repository.StudentRepository;
import org.springframework.http.HttpMethod;

/**
 * Spring Security configuration enforcing stateless JWT authentication and role-based access control.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final StudentRepository studentRepository;

    public SecurityConfig(JwtService jwtService, StudentRepository studentRepository) {
        this.jwtService = jwtService;
        this.studentRepository = studentRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService, studentRepository);
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter().write("{\"error\":\"Full authentication is required to access this resource: "
                                    + authException.getMessage() + "\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType("application/json");
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write("{\"error\":\"Access denied: insufficient permissions. "
                                    + accessDeniedException.getMessage() + "\"}");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/courts/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/sessions/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/slots").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/students").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/queue/status").permitAll()

                        // Staff and Admin operations
                        .requestMatchers("/api/slots/generate").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/api/check-in/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/api/queue/admit-next").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/api/credits/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/courts/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/courts/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/sessions/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/sessions/**").hasAnyRole("STAFF", "ADMIN")

                        // Authenticated operations (ownership verified at controller/service level)
                        .requestMatchers("/api/students/*").authenticated()
                        .requestMatchers("/api/bookings/**").authenticated()
                        .requestMatchers("/api/queue/**").authenticated()

                        // Fallback
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
