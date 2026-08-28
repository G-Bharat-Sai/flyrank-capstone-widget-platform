package com.flyrank.capstone.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        log.info("JWT filter: {} {} - Authorization header present: {}", request.getMethod(), request.getRequestURI(), authHeader != null);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.info("JWT filter: no Bearer header, passing through unauthenticated");
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String email;

        try {
            email = jwtService.extractEmail(token);
            log.info("JWT filter: extracted email = {}", email);
        } catch (Exception e) {
            log.warn("JWT filter: failed to extract email from token", e);
            filterChain.doFilter(request, response);
            return;
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            boolean valid = jwtService.isTokenValid(token, userDetails.getUsername());
            log.info("JWT filter: token valid = {}", valid);

            if (valid) {
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.info("JWT filter: authentication set in SecurityContext");
            }
        } else {
            log.info("JWT filter: skipped - email null? {} , existing auth present? {}", email == null, SecurityContextHolder.getContext().getAuthentication() != null);
        }

        filterChain.doFilter(request, response);
    }
}
