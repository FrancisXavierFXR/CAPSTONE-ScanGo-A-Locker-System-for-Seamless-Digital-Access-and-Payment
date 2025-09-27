package application;

import java.io.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;
import java.util.Base64;

public class PaymentHandler {

    private static final String PUBLIC_KEY = "pk_test_Sc51Gk2aMzwt0s7sJy5YEP";
    private static final String SECRET_KEY = "sk_test_iK6VDTodaszJRCxoAnlwZh";

    public static String initiatePayment(int amountCentavos) {
        try {
            // Step 1: Create source
            System.out.println("⏳ Creating payment source...");

            URL sourceUrl = new URL("https://api.magpie.im/v2/sources");
            HttpsURLConnection sourceConnection = (HttpsURLConnection) sourceUrl.openConnection();
            sourceConnection.setRequestMethod("POST");
            sourceConnection.setRequestProperty("Authorization", "Basic " +
                    Base64.getEncoder().encodeToString((PUBLIC_KEY + ":").getBytes()));
            sourceConnection.setRequestProperty("Content-Type", "application/json");
            sourceConnection.setDoOutput(true);

            String sourceJson = "{" +
                    "\"currency\":\"PHP\"," +
                    "\"type\":\"paymaya\"," +
                    "\"redirect\":{" +
                    "\"success\":\"https://success.local\"," +
                    "\"fail\":\"https://fail.local\"}" +
                    "}";

            OutputStream sourceOs = sourceConnection.getOutputStream();
            sourceOs.write(sourceJson.getBytes("utf-8"));
            sourceOs.close();

            BufferedReader sourceBr = new BufferedReader(new InputStreamReader(sourceConnection.getInputStream(), "utf-8"));
            StringBuilder sourceResponse = new StringBuilder();
            String line;
            while ((line = sourceBr.readLine()) != null) {
                sourceResponse.append(line.trim());
            }
            sourceBr.close();

            String sourceId = extractJsonValue(sourceResponse.toString(), "id");

            // Step 2: Create charge
            System.out.println("⏳ Creating charge for amount: " + amountCentavos + " centavos");

            URL chargeUrl = new URL("https://api.magpie.im/v2/charges");
            HttpsURLConnection chargeConnection = (HttpsURLConnection) chargeUrl.openConnection();
            chargeConnection.setRequestMethod("POST");
            chargeConnection.setRequestProperty("Authorization", "Basic " +
                    Base64.getEncoder().encodeToString((SECRET_KEY + ":").getBytes()));
            chargeConnection.setRequestProperty("Content-Type", "application/json");
            chargeConnection.setDoOutput(true);

            String chargeJson = "{"
                    + "\"amount\": " + amountCentavos + ","
                    + "\"currency\": \"php\","
                    + "\"source\": \"" + sourceId + "\","
                    + "\"description\": \"Locker payment\","
                    + "\"statement_descriptor\": \"ScaNGo Lockers\","
                    + "\"capture\": true"
                    + "}";

            OutputStream chargeOs = chargeConnection.getOutputStream();
            chargeOs.write(chargeJson.getBytes("utf-8"));
            chargeOs.close();

            BufferedReader chargeBr = new BufferedReader(new InputStreamReader(chargeConnection.getInputStream(), "utf-8"));
            StringBuilder chargeResponse = new StringBuilder();
            while ((line = chargeBr.readLine()) != null) {
                chargeResponse.append(line.trim());
            }
            chargeBr.close();

            // Extract action.url
            String redirectUrl = extractActionUrl(chargeResponse.toString());
            System.out.println("🧾 Redirect URL: " + redirectUrl);

            return redirectUrl;

        } catch (Exception e) {
            System.err.println("❌ Error during payment:");
            e.printStackTrace();
            return null;
        }
    }

    // Basic JSON value extractor
    private static String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int index = json.indexOf(searchKey);
        if (index == -1) return null;

        int startQuote = json.indexOf('"', index + searchKey.length());
        int endQuote = json.indexOf('"', startQuote + 1);
        if (startQuote == -1 || endQuote == -1) return null;

        return json.substring(startQuote + 1, endQuote);
    }

    // Extracts action.url from nested JSON
    private static String extractActionUrl(String json) {
        String actionKey = "\"action\":{";
        int actionStart = json.indexOf(actionKey);
        if (actionStart == -1) return null;

        int urlKeyIndex = json.indexOf("\"url\":\"", actionStart);
        if (urlKeyIndex == -1) return null;

        int startQuote = urlKeyIndex + 7;
        int endQuote = json.indexOf('"', startQuote);
        if (endQuote == -1) return null;

        return json.substring(startQuote, endQuote);
    }
}