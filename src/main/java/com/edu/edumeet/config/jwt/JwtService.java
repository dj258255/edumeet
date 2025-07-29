package com.edu.edumeet.config.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.function.Function;

@Component
public class JwtService {
    @Value("${jwt.secret}")
    private String key;

    @Value("${jwt.expiration_time}")
    private long accessTokenExpTime;

    private static final String MEMBER_ID_CLAIM = "memberId";
    private static final long REFRESH_TOKEN_MULTIPLIER = 6; // AccessToken의 6배 (7일)

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractMemberId(String token) {
        return extractClaim(token, claims -> claims.get(MEMBER_ID_CLAIM, Long.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    private String generateToken(HashMap<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, accessTokenExpTime);
    }

    public String generateAccessToken(Long memberId, String email) {
        HashMap<String, Object> claims = new HashMap<>();
        claims.put(MEMBER_ID_CLAIM, memberId);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpTime))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(Long memberId, String email) {
        HashMap<String, Object> claims = new HashMap<>();
        claims.put(MEMBER_ID_CLAIM, memberId);

        long refreshTokenExpTime = accessTokenExpTime * REFRESH_TOKEN_MULTIPLIER;

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpTime))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public long getAccessTokenExpTime() {
        return accessTokenExpTime;
    }

    public long getRefreshTokenExpTime() {
        return accessTokenExpTime * REFRESH_TOKEN_MULTIPLIER;
    }

    public String generateRefreshToken(UserDetails userDetails) {
        long refreshTokenExpTime = accessTokenExpTime * REFRESH_TOKEN_MULTIPLIER;
        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpTime))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private String buildToken(
            HashMap<String, Object> extraClaims,
            UserDetails userDetails,
            long accessTokenExpTime) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpTime))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    public boolean isTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(key);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public Long getMemberIdFromToken(String token) {
        return extractMemberId(token);
    }
}
