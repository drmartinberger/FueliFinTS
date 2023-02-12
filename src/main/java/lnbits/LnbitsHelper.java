package lnbits;

import com.prowidesoftware.swift.model.field.*;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class LnbitsHelper {
    private static String lnbitsUrl;

    private static String uriWallet;
    private static String uriPayments;

    private static String xApiKey;

    private static void loadConfiguration() {
        Properties properties = new Properties();
        try {
            BufferedInputStream stream = new BufferedInputStream(new FileInputStream("config/lnbits.properties"));
            properties.load(stream);
            stream.close();

            lnbitsUrl = properties.getProperty("lnbitsUrl");
            uriWallet = properties.getProperty("uriWallet");
            uriPayments = properties.getProperty("uriPayments");
            xApiKey = properties.getProperty("xApiKey");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static Collection<Field> getMt940Fields(java.util.Date from, java.util.Date to) throws IOException {
        loadConfiguration();

        Collection<Field> mt940Fields = new ArrayList<>();
        Long currentBalance = getCurrentBalance();
        Collection<LnTransaction> afterFromSettledPayments = getPayments()
                .stream()
                .filter(tx -> tx.getTime().after(from) && !tx.getPending())
                .collect(Collectors.toList());
        Long fromBalance = currentBalance - afterFromSettledPayments
                .stream()
                .map(tx -> tx.getAmount() + tx.getFee())
                .reduce(0L, Long::sum);
        Collection<LnTransaction> fromToPayments = afterFromSettledPayments
                .stream()
                .filter(tx -> tx.getTime().before(to))
                .collect(Collectors.toList());
        Long toBalance = fromBalance + fromToPayments
                .stream()
                .map(tx -> tx.getAmount() + tx.getFee())
                .reduce(0L, Long::sum);
        // initialBalance
        String simpleFrom = new SimpleDateFormat("yyMMdd").format(from);
        mt940Fields.add(new Field60F()
                .setDCMark("C")
                .setDate(simpleFrom)
                .setCurrency("EUR")
                .setAmount(fromBalance/1000L)
        );

        for (LnTransaction tx : fromToPayments) {
            mt940Fields.add(tx.toField61());
            mt940Fields.add(tx.toField86());
        }

        // finalBalance
        String simpleTo = new SimpleDateFormat("yyMMdd").format(from);
        mt940Fields.add(new Field62F()
                .setDCMark("C")
                .setDate(simpleTo)
                .setCurrency("EUR")
                .setAmount(toBalance/1000L)
        );

        return mt940Fields;
    }

    private static Collection<LnTransaction> getPayments() throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            HttpGet request = new HttpGet(lnbitsUrl + uriPayments);
            request.addHeader("X-API-KEY", xApiKey);
            request.addHeader(HttpHeaders.ACCEPT, "application/json");

            CloseableHttpResponse response = httpClient.execute(request);
            try {
                HttpEntity entity = response.getEntity();
                if (entity != null && response.getStatusLine().getStatusCode() == 200) {
                    String result = EntityUtils.toString(entity);
                    return new ObjectMapper().readValue(result, new TypeReference<List<LnTransaction>>(){});
                } else {
                    throw new RuntimeException("Request failed");
                }
            } finally {
                response.close();
            }
        } catch (ClientProtocolException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            httpClient.close();
        }
    }
    private static Long getCurrentBalance() throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            HttpGet request = new HttpGet(lnbitsUrl + uriWallet);
            request.addHeader("X-API-KEY", xApiKey);
            request.addHeader(HttpHeaders.ACCEPT, "application/json");

            CloseableHttpResponse response = httpClient.execute(request);
            try {
                HttpEntity entity = response.getEntity();
                if (entity != null && response.getStatusLine().getStatusCode() == 200) {
                    String result = EntityUtils.toString(entity);
                    WalletDetails walletDetails = new ObjectMapper().readValue(result, WalletDetails.class);
                    return walletDetails.getBalance();
                } else {
                    throw new RuntimeException("Request failed");
                }
            } finally {
                response.close();
            }
        } catch (ClientProtocolException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            httpClient.close();
        }
    }
}
