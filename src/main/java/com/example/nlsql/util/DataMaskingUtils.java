package com.example.nlsql.util;

public class DataMaskingUtils {

	public static String maskEmail(String email) {
		if (email == null || !email.contains("@"))
			return email;

		String[] parts = email.split("@");
		String name = parts[0];
		String domain = parts[1];

		if (name.length() <= 1) {
			return "*" + "@" + domain;
		}

		String maskedName = name.charAt(0) + "***";
		return maskedName + "@" + domain;
	}
}
