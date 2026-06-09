package com.codegym.mathclass.api;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class TestApi2 {
    public static void main(String[] args) throws Exception {
        String email = "test2@gmail.com";
        String password = "password";

        // 1. Register
        URL regUrl = new URL("http://localhost:8080/api/auth/register");
        HttpURLConnection regConn = (HttpURLConnection) regUrl.openConnection();
        regConn.setRequestMethod("POST");
        regConn.setRequestProperty("Content-Type", "application/json");
        regConn.setDoOutput(true);
        String regJson = "{\"email\":\"" + email + "\",\"password\":\"" + password
                + "\",\"fullName\":\"Test\",\"phoneNumber\":\"012\",\"role\":\"STUDENT\"}";
        try (OutputStream os = regConn.getOutputStream()) {
            os.write(regJson.getBytes(StandardCharsets.UTF_8));
        }
        System.out.println("Register Status: " + regConn.getResponseCode());

        // 2. We need to activate the user. Since we can't easily, let's login with an
        // existing user if possible, or wait, we can't login without activation.
        // Wait, does login require activation? Yes, CustomUserDetails has isActive().
        // If not active, what happens? Spring Security throws DisabledException?

        // Let's just try to call the API without token to see if it returns 401.
        URL api = new URL("http://localhost:8080/api/assignments?status=PUBLISHED");
        HttpURLConnection apiConn = (HttpURLConnection) api.openConnection();
        apiConn.setRequestMethod("GET");
        int apiStatus = apiConn.getResponseCode();
        System.out.println("API Status without token: " + apiStatus);

        // Let's call OPTIONS
        HttpURLConnection optConn = (HttpURLConnection) api.openConnection();
        optConn.setRequestMethod("OPTIONS");
        optConn.setRequestProperty("Origin", "http://localhost:3000");
        optConn.setRequestProperty("Access-Control-Request-Method", "GET");
        optConn.setRequestProperty("Access-Control-Request-Headers", "Authorization");
        int optStatus = optConn.getResponseCode();
        System.out.println("OPTIONS Status: " + optStatus);
    }
}
