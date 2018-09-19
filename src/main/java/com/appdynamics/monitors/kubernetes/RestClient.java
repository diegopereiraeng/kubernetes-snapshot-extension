package com.appdynamics.monitors.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.util.encoders.UrlBase64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_CONTROLLER_API_USER;
import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_CONTROLLER_URL;
import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_DASH_TEMPLATE_PATH;

public class RestClient {
    private static final Logger logger = LoggerFactory.getLogger(RestClient.class);

    public static JsonNode doRequest(URL url, String accountName, String apiKey, String requestBody, String method) {

        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            if (method.equals("PATCH")) {
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                conn.setRequestMethod("POST");
            } else {
                conn.setRequestMethod(method);
            }
            if (method.equals("POST") || method.equals("PATCH")) {
                conn.setRequestProperty("Content-Type", "application/vnd.appd.events+json;v=2");
            }
            conn.setRequestProperty("Accept", "application/vnd.appd.events+json;v=2");
            conn.setRequestProperty("X-Events-API-AccountName", accountName);
            conn.setRequestProperty("X-Events-API-Key", apiKey);
            if (method.equals("POST") || method.equals("PATCH")) {
                OutputStream output = conn.getOutputStream();
                output.write(requestBody.getBytes("UTF-8"));
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    (conn.getInputStream())));
            String response = "";
            //noinspection StatementWithEmptyBody
            for (String line; (line = br.readLine()) != null; response += line) ;
            conn.disconnect();
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readTree(response);
        } catch (IOException e) {
            logger.error("Error while processing {} on URL {}. Reason {}", method, url, e.toString());
            return null;
        }
    }

    public static AppDRestAuth getAuthToken(Map<String, String> config) {
        AppDRestAuth authObj = new AppDRestAuth();
        HttpURLConnection conn = null;
        String path = config.get(CONFIG_CONTROLLER_URL) + "auth?action=login";
        URL url = Utilities.getUrl(path);
        String user = config.get(CONFIG_CONTROLLER_API_USER);
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            byte[] message = (user).getBytes("UTF-8");
            String encoded = Base64.getEncoder().encodeToString(message);
            conn.setRequestProperty("Authorization", "Basic " + encoded);

            int responseCode = conn.getResponseCode();

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            if (responseCode == 200) {
//                Cookie: X-CSRF-TOKEN=8701b4cf97ee0e6b08dc003495f46c8028c87c23; JSESSIONID=0b904b9034e1e853a4674de94885
                String sessionID = "";
                for (Map.Entry<String, List<String>> headers : conn.getHeaderFields().entrySet()) {
                    if (headers != null) {
                        if (headers.getKey() != null && headers.getKey().toLowerCase().equals("set-cookie")) {
                            for (String cookie : headers.getValue()) {
                                if (cookie.contains("X-CSRF-TOKEN")) {
                                    Pattern pattern = Pattern.compile("=(.*?);");
                                    Matcher matcher = pattern.matcher(cookie);
                                    if (matcher.find()) {
                                        authObj.setToken(matcher.group(1));
                                    }
                                }

                                if (cookie.contains("JSESSIONID")) {
                                    Pattern pattern = Pattern.compile("=(.*?);");
                                    Matcher matcher = pattern.matcher(cookie);
                                    if (matcher.find()) {
                                        sessionID = matcher.group(1);
                                    }
                                }

                            }
                        }
                    }
                }
                if (sessionID.length() > 0) {
                    authObj.setCookie(String.format("X-CSRF-TOKEN=%s; JSESSIONID=%s", authObj.getToken(), sessionID));
                }
            }
        } catch (Exception ex) {
            logger.error("Issues when getting the auth token for restui calls", ex);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return authObj;
    }

    public static JsonNode callControllerAPI(String urlPath, Map<String, String> config, String requestBody, String method) {
        AppDRestAuth authObj = getAuthToken(config);
        HttpURLConnection conn = null;
        try {
            String path = config.get(CONFIG_CONTROLLER_URL) + urlPath;
            URL url = Utilities.getUrl(path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            if (method.equals("PATCH")) {
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                conn.setRequestMethod("POST");
            } else {
                conn.setRequestMethod(method);
            }
            if (method.equals("POST") || method.equals("PATCH")) {
                conn.setRequestProperty("Content-Type", "application/json");
            }
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-CSRF-TOKEN", authObj.getToken());
            conn.setRequestProperty("Cookie", authObj.getCookie());


            if (method.equals("POST") || method.equals("PATCH")) {
                OutputStream output = conn.getOutputStream();
                output.write(requestBody.getBytes("UTF-8"));
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    (conn.getInputStream())));
            String response = "";

            for (String line; (line = br.readLine()) != null; response += line) ;

            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readTree(response);

        } catch (IOException e) {
            logger.error("Error while processing {} on URL {}. Reason {}", method, urlPath, e.toString());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static JsonNode createDashboard(Map<String, String> config) {
        HttpURLConnection conn = null;
        String path = config.get(CONFIG_CONTROLLER_URL) + "CustomDashboardImportExportServlet";
        URL url = Utilities.getUrl(path);
        String user = config.get(CONFIG_CONTROLLER_API_USER);
        File templateFile = new File(config.get(CONFIG_DASH_TEMPLATE_PATH));
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            byte[] message = (user).getBytes("UTF-8");
            String encoded = Base64.getEncoder().encodeToString(message);
            conn.setRequestProperty("Authorization", "Basic " + encoded);

            String boundary = UUID.randomUUID().toString();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

            DataOutputStream request = new DataOutputStream(conn.getOutputStream());
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + templateFile.getName() + "\"\r\n\r\n");
            FileInputStream inputStream = new FileInputStream(templateFile);
            byte[] buffer = new byte[4096];
            int bytesRead = -1;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                request.write(buffer, 0, bytesRead);
            }
            request.writeBytes("\r\n");

            request.writeBytes("--" + boundary + "--\r\n");
            request.flush();

            int respCode = conn.getResponseCode();
            logger.info("Dashboard create response code = {}", respCode);

            BufferedReader br = new BufferedReader(new InputStreamReader(
                    (conn.getInputStream())));
            String response = "";

            for (String line; (line = br.readLine()) != null; response += line) ;

            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readTree(response);
        }
        catch (Exception ex) {

            logger.error("Error while creating dashboard from template {} . Reason {}", config.get(CONFIG_DASH_TEMPLATE_PATH),  ex.toString());
            return null;
        }
    }
}