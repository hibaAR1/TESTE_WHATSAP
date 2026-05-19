package com.location.whatsappbot.service;

import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class WhatsAppSenderService {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.whatsapp.number}")
    private String fromNumber;

    private final OkHttpClient client = new OkHttpClient();

    public void sendWhatsAppResponse(String to, String message) {
        String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";
        String credentials = Base64.getEncoder()
                .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

        RequestBody body = new FormBody.Builder()
                .add("From", "whatsapp:" + fromNumber)
                .add("To", "whatsapp:+" + to)
                .add("Body", message)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Basic " + credentials)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            System.out.println("📨 Twilio envoi — statut : " + response.code());
            if (!response.isSuccessful() && response.body() != null) {
                System.out.println("❌ Erreur : " + response.body().string());
            }
        } catch (IOException e) {
            System.out.println("❌ Erreur envoi Twilio : " + e.getMessage());
        }
    }

    public String downloadAudioFile(String mediaUrl, String fromNumber) {
        String credentials = Base64.getEncoder()
                .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

        Request request = new Request.Builder()
                .url(mediaUrl)
                .addHeader("Authorization", "Basic " + credentials)
                .get()
                .build();

        File dir = new File("downloads");
        if (!dir.exists())
            dir.mkdirs();
        String localPath = "downloads/audio_" + fromNumber + ".ogg";

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                try (InputStream is = response.body().byteStream();
                        FileOutputStream fos = new FileOutputStream(new File(localPath))) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                    System.out.println("💾 Audio téléchargé avec succès : " + localPath);
                    return localPath;
                }
            } else {
                System.out.println("❌ Erreur téléchargement Twilio [" + response.code() + "]");
            }
        } catch (IOException e) {
            System.out.println("❌ IOException Twilio : " + e.getMessage());
        }
        return null;
    }
}