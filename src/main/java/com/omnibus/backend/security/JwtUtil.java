package com.omnibus.backend.security;

import com.omnibus.backend.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}") // Define esto en application.properties
    private String secretString;

    @Value("${jwt.expiration.ms}") // Define esto en application.properties (ej: 3600000 para 1 hora)
    private long jwtExpirationMs;

    private SecretKey getSigningKey() {
        // Para HS512, la clave debe tener al menos 64 bytes.
        // Si tu secretString es más corto, puedes usar HMAC-SHA256 y una clave más corta.
        // O generar una clave segura y codificarla en Base64 para el properties.
        // Esta es una forma simple, pero para producción considera una generación de clave más robusta.
        byte[] keyBytes = secretString.getBytes();
        if (keyBytes.length < 64) { // Asegura longitud mínima para HS512
            byte[] paddedKeyBytes = new byte[64];
            System.arraycopy(keyBytes, 0, paddedKeyBytes, 0, Math.min(keyBytes.length, 64));
            keyBytes = paddedKeyBytes;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token).getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        // Puedes añadir claims personalizados aquí, por ejemplo, el rol
        if (userDetails instanceof Usuario) { // Para obtener el rol de tu clase Usuario
            claims.put("rol", ((Usuario) userDetails).getRol());
            claims.put("nombre", ((Usuario) userDetails).getNombre());
        }
        return createToken(claims, userDetails.getUsername());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject) // El email del usuario
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}