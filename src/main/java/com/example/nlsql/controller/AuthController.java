package com.example.nlsql.controller;

import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.nlsql.config.JwtIssuerService;
import com.example.nlsql.entity.UserEntity;
import com.example.nlsql.repo.UserRepository;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtIssuerService jwtIssuerService;

	public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
			JwtIssuerService jwtIssuerService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtIssuerService = jwtIssuerService;
	}

	// SIGNUP
	@PostMapping("/signup")
	public ResponseEntity<?> signup(@RequestBody Map<String, String> request) {

		String username = request.get("username");
		String password = request.get("password");

		if (userRepository.existsByUsername(username)) {
			return ResponseEntity.badRequest().body("Username already exists");
		}

		UserEntity user = new UserEntity();
		user.setUsername(username);
		user.setPassword(passwordEncoder.encode(password));
		user.setRoles(Set.of("ROLE_USER"));

		userRepository.save(user);

		return ResponseEntity.ok("User created");
	}

	// LOGIN
	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody Map<String, String> request) {

		String username = request.get("username");
		String password = request.get("password");

		UserEntity user = userRepository.findByUsername(username)
				.orElseThrow(() -> new RuntimeException("Invalid credentials"));

		if (!passwordEncoder.matches(password, user.getPassword())) {
			return ResponseEntity.status(401).body("Invalid credentials");
		}

		String token = jwtIssuerService.issueToken(user.getUsername(), user.getRoles());

		return ResponseEntity.ok(Map.of("token", token));
	}
}
