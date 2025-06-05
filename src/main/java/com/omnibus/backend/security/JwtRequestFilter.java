package com.omnibus.backend.security;

import com.omnibus.backend.service.CustomUserDetailsService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        final String authorizationHeader = request.getHeader("Authorization");
        String username = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                username = jwtUtil.extractUsername(jwt); // Extrae el email del token
            } catch (IllegalArgumentException e) {
                logger.warn("No se pudo obtener el username del token JWT");
            } catch (ExpiredJwtException e) {
                logger.warn("El token JWT ha expirado");
            }
        } else {
            // Esto es normal para rutas públicas, no necesariamente un warning si la ruta no requiere auth.
            // logger.warn("El encabezado de autorización no comienza con Bearer String o está ausente");
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username); // Llama al servicio

            if (jwtUtil.validateToken(jwt, userDetails)) {
                // Log para verificar las autoridades antes de establecer la autenticación
                System.out.println("JwtRequestFilter: Validando token para: " + userDetails.getUsername() + ". Autoridades: " + userDetails.getAuthorities());

                UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()); // Se usan las autoridades del userDetails
                usernamePasswordAuthenticationToken
                        .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
                System.out.println("JwtRequestFilter: Autenticación establecida en SecurityContext para: " + userDetails.getUsername());
            } else {
                System.out.println("JwtRequestFilter: Token inválido para usuario: " + userDetails.getUsername());
            }
        }
        chain.doFilter(request, response);
    }
}