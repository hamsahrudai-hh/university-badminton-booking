package edu.university.badminton.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import edu.university.badminton.model.Role;
import edu.university.badminton.model.Student;
import edu.university.badminton.repository.StudentRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Filter that parses the Bearer JWT token from HTTP requests, verifies signature and expiry,
 * checks database for account active status, and populates the SecurityContextHolder with live roles.
 */
// Managed directly by SecurityConfig in Spring Security filter chain
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final StudentRepository studentRepository;

    public JwtAuthenticationFilter(JwtService jwtService, StudentRepository studentRepository) {
        this.jwtService = jwtService;
        this.studentRepository = studentRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                JwtClaims claims = jwtService.validateAndParseToken(token);
                if (claims != null && claims.studentId() != null) {
                    Role effectiveRole = claims.role();
                    String email = claims.email();
                    String admissionNumber = claims.admissionNumber();
                    String fullName = claims.fullName();

                    // Server-side safeguard: verify account is active in database and use live role
                    if (studentRepository != null) {
                        Optional<Student> studentOpt = studentRepository.findById(claims.studentId());
                        if (studentOpt.isEmpty() || !Boolean.TRUE.equals(studentOpt.get().getActive())) {
                            // Account is deactivated, revoked, or no longer exists
                            SecurityContextHolder.clearContext();
                            filterChain.doFilter(request, response);
                            return;
                        }
                        Student student = studentOpt.get();
                        if (student.getRole() != null) {
                            effectiveRole = student.getRole();
                        }
                        email = student.getUniversityEmail();
                        admissionNumber = student.getAdmissionNumber();
                        fullName = student.getFullName();
                    }

                    UserPrincipal principal = new UserPrincipal(
                            claims.studentId(),
                            email,
                            admissionNumber,
                            fullName,
                            effectiveRole
                    );

                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + effectiveRole.name());
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(authority)
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // Invalid or expired token: clear context and let Spring Security EntryPoint / AccessDenied handle
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
