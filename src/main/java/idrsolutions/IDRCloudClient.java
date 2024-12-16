/**
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Used to interact with IDRsolutions' Microservice examples
 * For detailed usage instructions, see the GitHub repository:
 * https://github.com/idrsolutions/idrsolutions-java-client
 *
 **/
package idrsolutions;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class IDRCloudClient {
    public static final String DOWNLOAD = "download";
    public static final String UPLOAD = "upload";
    public static final String JPEDAL = "jpedal";
    public static final String BUILDVU = "buildvu";
    public static final String FORMVU = "formvu";

    private final String endPoint;
    private final int requestTimeout;
    private final int conversionTimeout;

    /**
     * Constructor, setup the converter details
     *
     * @param url The URL of Microservice to connect to.
     */
    public IDRCloudClient(final String url) {
        endPoint = url;
        requestTimeout = 60000;
        conversionTimeout = -1;
    }

    /**
     * Constructor with timeout, setup the converter details
     *
     * @param url The URL of Microservice to connect to.
     * @param requestTimeout The time to wait (in milliseconds) before timing out each request. Set to 60000ms (60s) by default.
     * @param conversionTimeout The time to wait (in seconds) before timing out the conversion. If value <= 0 then the conversion does not time out. Set to -1 by default.
     */
    public IDRCloudClient(final String url, final int requestTimeout, final int conversionTimeout) {
        endPoint = url;
        this.requestTimeout = requestTimeout;
        this.conversionTimeout = conversionTimeout;
    }

    /**
     * Starts the conversion of a file and returns a dictionary with the response from the server.
     * Details for the parameters passed can be found at one of the following depending on the product:
     * https://github.com/idrsolutions/buildvu-microservice-example/blob/master/API.md
     * https://github.com/idrsolutions/jpedal-microservice-example/blob/master/API.md
     * https://github.com/idrsolutions/formvu-microservice-example/blob/master/API.md
     *
     * @param parameters
     * @return A Map of String keys and values containing the conversion status
     * @throws ClientException
     * @throws InterruptedException
     */
    public Map<String, String> convert(final Map<String, String> parameters) throws ClientException, InterruptedException {

// Upload file and get conversion ID
        final String uuid = upload(parameters);

        JsonObject responseContent;
// Check conversion status once every second until complete or error / timeout
        int i = 0;
        while (true) {

            Thread.sleep(1000);

            responseContent = pollStatus(uuid);
            final String state;
            if (responseContent.has("state")) {
                state = responseContent.get("state").getAsString();
            } else {
                state = "";
            }

            if ("error".equals(state)) {
                throw new ClientException("Failed: Error with conversion\n" + responseContent);
            }

            if ("processed".equals(state) || parameters.containsKey("callbackUrl")) {
                break;
            }

            if (conversionTimeout != -1 && i >= conversionTimeout) {
                throw new ClientException("Failed: File took longer than " + conversionTimeout + " seconds to convert.");
            }

            i++;

        }

        return new Gson().fromJson(responseContent.toString(), HashMap.class);
    }

    /**
     * Download results of conversion from the service
     *
     * @param results
     * @param outputFilePath
     * @param fileName
     * @param username
     * @param password
     * @throws ClientException
     */
    public static void downloadResults(final Map<String, String> results, String outputFilePath, final String fileName, final String username, final String password) throws ClientException {
        final String encodedAuth;

        if (!results.containsKey("downloadUrl")) {
            throw new ClientException("Failed: No URL to download from provided");
        }

        if (username != null && password != null && !username.isEmpty() && !password.isEmpty()) {
            final String auth = username + ':' + password;
            encodedAuth = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        } else {
            encodedAuth = "";
        }

        if (fileName != null) {
            outputFilePath += '/' + fileName + ".zip";
        } else {
            outputFilePath += '/' + Paths.get(results.get("downloadUrl")).getFileName().toString() + ".zip";
        }

        download(results.get("downloadUrl"), outputFilePath, encodedAuth);
    }

    /**
     * Download results of conversion from the service
     *
     * @param results
     * @param outputFilePath
     * @param fileName
     * @throws ClientException
     */
    public static void downloadResults(final Map<String, String> results, final String outputFilePath, final String fileName) throws ClientException {
        downloadResults(results, outputFilePath, fileName, "", "");
    }

    private String upload(final Map<String, String> parameters) throws ClientException {

        validateInput(parameters);

        final String uuid;
        try {
            final URL url = new URL(endPoint);
            final HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(requestTimeout);
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setDoInput(true);
            final String username = parameters.remove("username");
            final String password = parameters.remove("password");
            if (username != null && password != null && !username.isEmpty() && !password.isEmpty()) {
                final String auth = username + ':' + password;
                final String encodedAuth = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                con.setRequestProperty("Authorization", encodedAuth);
            }

            if (UPLOAD.equals(parameters.get("input")) && parameters.containsKey("file")) {
                file(parameters, con);
            } else {
                url(parameters, con);
            }
            final int responseCode = con.getResponseCode();

            final InputStream is = con.getInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String temp = reader.readLine();
            final StringBuilder response = new StringBuilder();
            while (temp != null) {
                response.append(temp);
                temp = reader.readLine();
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new ClientException("Error uploading file:\nServer returned response\n" + responseCode + ":\n"
                        + response);
            }
            final JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();

            if (!json.has("uuid")) {
                throw new ClientException("Error uploading file:\nServer returned null UUID");
            } else {
                uuid = json.get("uuid").getAsString();
            }
            reader.close();
        } catch (final IOException e) {
            throw new ClientException("Error connecting to service", e);
        }
        return uuid;
    }

    private static void file(final Map<String, String> parameters, final HttpURLConnection con) throws ClientException {

        final File file = new File(parameters.get("file"));
        final String fileName = Paths.get(file.toURI()).getFileName().toString();
        final String format = fileName.substring(fileName.lastIndexOf('.')+ 1);
        final String boundary = String.valueOf(System.currentTimeMillis());
        try {
            con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final Iterator<String> keys = parameters.keySet().iterator();
            StringBuilder post = new StringBuilder();
            while (keys.hasNext()) {
                final String key = keys.next();
                post.append("--").append(boundary).append("\r\n");
                if ("file".equals(key)) {
                    post.append("Content-Disposition: form-data; name=\"").append(key).append("\"; filename=\"").append(fileName).append("\";").append("\r\n");
                    post.append("Content-Type: application/").append(format).append("\r\n\r\n");
                    baos.write(post.toString().getBytes());
                    baos.write(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
                    baos.write("\r\n".getBytes());
                    post = new StringBuilder();

                } else {
                    post.append("Content-Disposition: form-data; name=\"").append(key).append('\"').append("\r\n");
                    post.append("Content-Type: text/plain; charset=UTF-8\r\n\r\n").append(parameters.get(key)).append("\r\n");
                }
            }

            post.append("--").append(boundary).append("--\r\n");
            baos.write(post.toString().getBytes());
            con.setRequestProperty("Content-Length", String.valueOf(baos.size()));
            con.getOutputStream().write(baos.toByteArray());
            baos.close();
        } catch (final IOException e) {
            throw new ClientException("Error creating request for file conversion", e);
        }
    }

    private static void url(final Map<String, String> parameters, final HttpURLConnection con) throws ClientException {
        final StringBuilder postData = new StringBuilder();
        final Iterator<String> keys = parameters.keySet().iterator();

        try {
            while (keys.hasNext()) {
                if (postData.length() != 0) {
                    postData.append('&');
                }
                final String key = keys.next();
                postData.append(URLEncoder.encode(key, StandardCharsets.UTF_8.toString()));
                postData.append('=');
                postData.append(URLEncoder.encode(parameters.get(key), StandardCharsets.UTF_8.toString()));
            }
            final byte[] postDataBytes = postData.toString().getBytes(StandardCharsets.UTF_8);

            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            con.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));

            con.getOutputStream().write(postDataBytes);
            con.getOutputStream().flush();

        } catch (final IOException e) {
            throw new ClientException("Error creating request for url conversion", e);
        }
    }

    private JsonObject pollStatus(final String uuid) throws ClientException {
        final JsonObject json;
        final String uuidParam = "?uuid=" + uuid;
        try {
            final URL url = new URL(endPoint + uuidParam);

            final HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setDoInput(true);

            final byte[] bytes = uuidParam.getBytes();
            con.setRequestProperty("Content-Length", String.valueOf(bytes.length));

            con.setConnectTimeout(requestTimeout);

            final int responseCode = con.getResponseCode();
            final InputStream is;
            if (responseCode != HttpURLConnection.HTTP_OK) {
                is = con.getErrorStream();
            } else {
                is = con.getInputStream();
            }
            final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String temp = reader.readLine();
            final StringBuilder response = new StringBuilder();
            while (temp != null) {
                response.append(temp);
                temp = reader.readLine();
            }

            reader.close();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new ClientException("Error checking conversion status:\n Server returned response\n"
                        + responseCode + " - " + response);
            }
            json = JsonParser.parseString(response.toString()).getAsJsonObject();
        } catch (final IOException e) {
            throw new ClientException("Connection issues whilst polling status", e);
        }
        return json;
    }

    private static void download(final String downloadUrl, final String outputFilePath, final String authorization) throws ClientException {

        try {
            final URL url = new URL(downloadUrl);
            final HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setDoInput(true);
            if (!authorization.isEmpty()) {
                con.setRequestProperty("Authorization", authorization);
            }
            try (InputStream inputStream = con.getInputStream()) {

                // opens an output stream to save into file
                try (final FileOutputStream outputStream = new FileOutputStream(outputFilePath)) {

                    int bytesRead;
                    final byte[] buffer = new byte[8192];
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
            }
        } catch (final Exception e) {
            throw new ClientException("Error downloading conversion output:\n" + e.getMessage(), e);
        }
    }

    private void validateInput(final Map<String, String> parameters) throws ClientException {
        if (endPoint == null) {
            throw new ClientException("Error: Missing endpoint");
        }
        if (parameters.isEmpty()) {
            throw new ClientException("Error: Missing parameters");
        }
        if (parameters.containsKey("input") && UPLOAD.equals(parameters.get("input")) && parameters.get("file").isEmpty()) {
            throw new ClientException("Error: Missing file");
        }
        if (parameters.containsKey("input") && DOWNLOAD.equals(parameters.get("input")) && parameters.get("url").isEmpty()) {
            throw new ClientException("Error: Missing url");
        }
    }

}