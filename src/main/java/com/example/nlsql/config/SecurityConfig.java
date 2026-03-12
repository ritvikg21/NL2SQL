package com.example.nlsql.config;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	private static final String SECRET = "rdrtsesrf6t778687gyg7t7g77yiuy89hig78fyv";

	@Bean
	public JwtDecoder jwtDecoder() {
		SecretKey key = new SecretKeySpec(SECRET.getBytes(), "HmacSHA256");
		return NimbusJwtDecoder.withSecretKey(key).build();
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

		http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth
				// Admin-only endpoints
				.requestMatchers("/api/admin/**").hasRole("ADMIN")

				// User history (user identity checked internally)
				.requestMatchers("/api/history/**").hasAnyRole("USER", "ADMIN")

				// Query runs require login
				.requestMatchers("/api/query").hasAnyRole("USER", "ADMIN")
				
				// Allow login and signup for all
				.requestMatchers("/api/auth/*").permitAll()

				.anyRequest().authenticated()).oauth2ResourceServer(oauth -> oauth.jwt());
		// ^ Automatically extracts userId + roles from JWT

		return http.build();
	}

	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();

		// IMPORTANT: this must match your JWT claim name
		authoritiesConverter.setAuthoritiesClaimName("roles");

		// IMPORTANT: empty because roles already contain ROLE_
		authoritiesConverter.setAuthorityPrefix("");

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

		return converter;
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}
}
