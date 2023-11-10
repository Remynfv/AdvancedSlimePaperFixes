package com.grinderwolf.swm.plugin.loaders.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.grinderwolf.swm.plugin.config.DatasourcesConfig;
import com.infernalsuite.aswm.api.exceptions.UnknownWorldException;
import com.infernalsuite.aswm.api.loaders.SlimeLoader;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class APILoader implements SlimeLoader {

    private final Gson gson;
    private final boolean ignoreSslCertificate;
    private String apiUrl;
    private String authorizationHeader;

    private SSLContext createTrustAllSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{};
                    }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext;
    }

    private HttpClient createHttpClient() {
        try {
            if(ignoreSslCertificate)
            {
                SSLContext sslContext = createTrustAllSSLContext();

                return HttpClient.newBuilder()
                        .sslContext(sslContext)
                        .connectTimeout(Duration.ofSeconds(30))
                        .build();
            }

            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private List<MapStructure> GetMapList() throws IOException, InterruptedException {
        HttpClient client = createHttpClient();
        HttpRequest request = HttpRequest.newBuilder()

                .uri(URI.create(this.apiUrl))
                .GET()
                .header("Authorization", this.authorizationHeader)
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        Type listType = new TypeToken<List<MapStructure>>(){}.getType();
        return gson.fromJson(response.body(), listType);
    }

    private byte[] downloadFile(String worldId) throws IOException, InterruptedException {
        HttpClient client = createHttpClient();

        // Check file size with HEAD request
        HttpRequest headRequest = HttpRequest.newBuilder()
                .uri(URI.create(this.apiUrl + worldId))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .header("Authorization", this.authorizationHeader)
                .build();

        HttpResponse<Void> headResponse = client.send(headRequest, HttpResponse.BodyHandlers.discarding());
        long fileSize = headResponse.headers().firstValueAsLong("content-length").orElse(0L);

        if (fileSize > Integer.MAX_VALUE) {
            throw new IndexOutOfBoundsException("World is too big!");
        }

        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(this.apiUrl + worldId))
                .GET()
                .header("Authorization", this.authorizationHeader)
                .build();

        HttpResponse<InputStream> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofInputStream());

        try (InputStream inputStream = getResponse.body();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            return outputStream.toByteArray();
        }
    }

    public APILoader(DatasourcesConfig.APIConfig apiConfig) {
        this.gson = new Gson();
        this.ignoreSslCertificate = apiConfig.isIgnoreSslCertificate();
        this.apiUrl = apiConfig.getUrl();
        if (!this.apiUrl.endsWith("/")) {
            this.apiUrl += "/";
        }

        String username = apiConfig.getUsername();
        String token = apiConfig.getToken();
        if (username != null && !username.isEmpty() && token != null && !token.isEmpty()) {
            String auth = username + ":" + token;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            this.authorizationHeader = "Basic " + encodedAuth;
        }
    }

    @Override
    public byte[] loadWorld(String worldName) throws UnknownWorldException, IOException {
        try {
            return downloadFile(worldName);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean worldExists(String worldName) {
        return false;
    }

    @Override
    public List<String> listWorlds() {
        List<MapStructure> mapList;

        try {
            mapList = GetMapList();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        return mapList.stream()
                .map(c -> c.getName().substring(0, c.getName().length() - 6))
                .collect(Collectors.toList());
    }

    @Override
    public void saveWorld(String worldName, byte[] serializedWorld) throws IOException {
        // System.out.println("API Worlds cannot be saved. They're always read-only.");
    }

    @Override
    public void unlockWorld(String worldName) throws UnknownWorldException, IOException {
        // System.out.println("API Worlds are always unlocked.");
    }

    @Override
    public boolean isWorldLocked(String worldName) {
        return false;
    }

    @Override
    public void deleteWorld(String worldName) {
        // System.out.println("API Worlds do not need to be deleted.");
    }

    @Override
    public void acquireLock(String worldName) {
        // System.out.println("API Worlds cannot be locked.");
    }
}
