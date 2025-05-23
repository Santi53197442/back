package com.omnibus.backend.security;

import com.omnibus.backend.model.Usuario; // Tu clase base Usuario
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority; // Importar
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List; // Importar
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors; // Importar

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretString;

    @Value("${jwt.expiration.ms}")
    private long jwtExpirationMs;

    private SecretKey signingKey; // Cachear la clave para no regenerarla en cada llamada

    // Método para inicializar la clave (se puede llamar en el constructor o al necesitarla)
    private SecretKey getSigningKey() {
        if (signingKey == null) {
            byte[] keyBytes = secretString.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            // Para HS512, la clave debe ser de al menos 512 bits (64 bytes).
            // Es mejor generar una clave segura y guardarla codificada en Base64 en las properties.
            // Si la clave es más corta que lo requerido por el algoritmo, JJWT podría dar error o ser inseguro.
            // Aquí se asume que secretString es una representación adecuada de una clave (ej. Base64 de una clave suficientemente larga)
            // o que el padding es una medida temporal y se usa una clave fuerte en producción.
            // Por ejemplo, para HS512, si keyBytes.length < 64, se debería manejar mejor.
            // Una forma simple pero NO IDEAL para desarrollo si la clave es corta:
            if (keyBytes.length * 8 < SignatureAlgorithm.HS512.getMinKeyLength()) {
                // System.err.println("ADVERTENCIA: La clave JWT proporcionada es demasiado corta para HS512. Se usará padding, pero esto no es seguro para producción.");
                // byte[] paddedKeyBytes = new byte[64]; // 64 bytes = 512 bits
                // System.arraycopy(keyBytes, 0, paddedKeyBytes, 0, Math.min(keyBytes.length, 64));
                // keyBytes = paddedKeyBytes;
                // Alternativa más segura: generar una clave si la proporcionada no es adecuada o lanzar error.
                // Por ahora, confiamos en que la clave sea suficientemente larga o que el algoritmo se ajuste.
                // La librería Keys.hmacShaKeyFor se encargará de esto si la clave es adecuada.
            }
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        }
        return signingKey;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public List<String> extractAuthorities(String token) {
        final Claims claims = extractAllClaims(token);
        return claims.get("authorities", List.class);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (Exception e) { // Capturar posibles errores de parseo si el token está malformado o expiró de forma extraña
            return true;
        }
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();

        // Añadir las autoridades (roles) a las claims
        List<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        claims.put("authorities", authorities);

        // Añadir información adicional si es necesario (ej. nombre, ID de usuario)
        // Asegúrate de que userDetails sea una instancia de tu clase Usuario (o subclase)
        if (userDetails instanceof Usuario) {
            Usuario usuario = (Usuario) userDetails;
            claims.put("nombre", usuario.getNombre());
            claims.put("userId", usuario.getId()); // Es común incluir el ID del usuario
        }

        // Si necesitas un campo "rol" simplificado para el frontend, además de "authorities":
        // (Esto es opcional, ya que AuthResponseDTO ya podría estar enviándolo)
        /*
        String rolPrincipal = determinePrincipalRole(userDetails);
        if (rolPrincipal != null) {
            claims.put("rol", rolPrincipal);
        }
        */

        return createToken(claims, userDetails.getUsername()); // userDetails.getUsername() es el email
    }

    // Método opcional para determinar un rol principal (ejemplo)
    /*
    private String determinePrincipalRole(UserDetails userDetails) {
        if (userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"))) {
            return "administrador";
        } else if (userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_VENDEDOR"))) {
            return "vendedor";
        } else if (userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE"))) {
            return "cliente";
        }
        return "desconocido"; // O un rol por defecto, o null
    }
    */

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject) // El "subject" es el username (email en tu caso)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS512) // Asegúrate que la clave sea para HS512
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        if (token == null || userDetails == null) {
            return false;
        }
        try {
            final String username = extractUsername(token);
            return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
        } catch (Exception e) { // Capturar cualquier error durante la extracción/validación
            // Esto podría incluir SignatureException, MalformedJwtException, ExpiredJwtException, etc.
            // System.err.println("Error validando token: " + e.getMessage());
            return false;
        }
    }
}