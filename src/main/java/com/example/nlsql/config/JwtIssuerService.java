package com.example.nlsql.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Service
public class JwtIssuerService {
	private static final String SECRET = "rdrtsesrf6t778687gyg7t7g77yiuy89hig78fyv";

	public String issueToken(String username, Set<String> roles) {
		SecretKey key = new SecretKeySpec(SECRET.getBytes(), SignatureAlgorithm.HS256.getJcaName());

		return Jwts.builder().setSubject(username).claim("roles", roles).setIssuedAt(Date.from(Instant.now()))
				.setExpiration(Date.from(Instant.now().plus(365, ChronoUnit.DAYS)))
				.signWith(key, SignatureAlgorithm.HS256).compact();
	}

}
