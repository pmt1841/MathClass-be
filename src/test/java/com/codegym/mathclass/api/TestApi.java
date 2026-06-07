package com.codegym.mathclass.api;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestApi {
    public static void main(String[] args) throws Exception {
        // 1. Login
        URL loginUrl = new URL("http://localhost:8080/api/auth/login");
        HttpURLConnection loginConn = (HttpURLConnection) loginUrl.openConnection();
        loginConn.setRequestMethod("POST");
        loginConn.setRequestProperty("Content-Type", "application/json");
        loginConn.setDoOutput(true);

        String loginJson = "{\"email\":\"phamminhtien1841@gmail.com\",\"password\":\"pmt1841\"}";
        try (OutputStream os = loginConn.getOutputStream()) {
            os.write(loginJson.getBytes(StandardCharsets.UTF_8));
        }

        int loginStatus = loginConn.getResponseCode();
        System.out.println("Login Status: " + loginStatus);
        if (loginStatus != 200) {
            System.out.println("Login Failed");
            return;
        }

        Scanner scanner = new Scanner(loginConn.getInputStream(), StandardCharsets.UTF_8);
        String responseBody = scanner.useDelimiter("\\A").next();
        scanner.close();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(responseBody);
        String token = root.get("token").asText();
        System.out.println("Token: " + token);

        // 2. Fetch Assignments
        URL api = new URL("http://localhost:8080/api/assignments?status=PUBLISHED");
        HttpURLConnection apiConn = (HttpURLConnection) api.openConnection();
        apiConn.setRequestMethod("GET");
        apiConn.setRequestProperty("Authorization", "Bearer " + token);

        int apiStatus = apiConn.getResponseCode();
        System.out.println("API Status: " + apiStatus);

        if (apiStatus == 200) {
            Scanner apiScanner = new Scanner(apiConn.getInputStream(), StandardCharsets.UTF_8);
            System.out.println("Response: " + apiScanner.useDelimiter("\\A").next());
            apiScanner.close();
        } else {
            Scanner apiScanner = new Scanner(apiConn.getErrorStream(), StandardCharsets.UTF_8);
            System.out.println("Error: " + apiScanner.useDelimiter("\\A").next());
            apiScanner.close();
        }
    }
}
