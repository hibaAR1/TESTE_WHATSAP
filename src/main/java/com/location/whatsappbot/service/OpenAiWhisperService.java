package com.location.whatsappbot.service;

import okhttp3.*;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;

@Service
public class OpenAiWhisperService {

    @Value("${groq.api.key}")
    private String apiKey;

    private final OkHttpClient client = new OkHttpClient();

    // 🎙️ ÉTAPE 1 : TRANSCRIPTION AUDIO AVEC WHISPER
    public String transcribeAudio(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("❌ Fichier audio introuvable pour Whisper : " + filePath);
            return null;
        }

        System.out.println("📊 Taille fichier : " + file.length() + " bytes");

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(file, MediaType.parse("audio/ogg")))
                .addFormDataPart("model", "whisper-large-v3")
                .addFormDataPart("response_format", "json")
                .addFormDataPart("language", "fr")
                .build();

        Request request = new Request.Builder()
                .url("https://api.groq.com/openai/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            System.out.println("✅ Whisper statut : " + response.code());
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                System.out.println("✅ Whisper réponse : " + responseBody);
                JSONObject json = new JSONObject(responseBody);
                return json.optString("text", "").trim();
            } else if (response.body() != null) {
                System.out.println("❌ Erreur Whisper brute : " + response.body().string());
            }
        } catch (IOException e) {
            System.out.println("❌ Erreur connexion Whisper : " + e.getMessage());
        }
        return null;
    }

    // 🧠 ÉTAPE 2 : EXTRACTION STRUCTUREE AVEC LLAMA 3.1
    public String analyserTexteAvecGPT(String transcription) {
        System.out.println("✅ Analyse de la phrase par LLaMA (Groq)...");

        String promptSystem = "Tu es un assistant d'extraction d'informations pour une agence de location de voiture. "
                + "Analyse le texte de l'utilisateur et extrait UNIQUEMENT un objet JSON contenant exactement ces cinq clés :\n"
                + "1) 'prenom' (le prénom de la personne, corrige les fautes de prononciation)\n"
                + "2) 'nom' (le nom de famille de la personne, corrige les fautes de prononciation. Si non mentionné, mets null)\n"
                + "3) 'typeVoiture' (toute marque ou modèle de voiture cité, même approximatif. Toyota 2025, Clio devient Renault Clio, etc. Ne mets JAMAIS null si une voiture est mentionnée)\n"
                + "4) 'duree' (la durée demandée, exemple: 3 jours, une semaine. Ne mets JAMAIS null si une durée est mentionnée)\n"
                + "5) 'dateDepart' (la date où commence la location. Si vraiment absente, mets null)\n\n"
                + "⚠️ REGLE ABSOLUE : Sois GENEREUX dans l'extraction, ne mets null QUE si l'information est vraiment absente.\n"
                + "Ne réponds rien d'autre que le JSON pur. Pas de blocs markdown.";

        JSONObject messageSystem = new JSONObject().put("role", "system").put("content", promptSystem);
        JSONObject messageUser = new JSONObject().put("role", "user").put("content", transcription);

        JSONObject jsonRequestBody = new JSONObject()
                .put("model", "llama-3.1-8b-instant")
                .put("messages", new JSONObject[] { messageSystem, messageUser })
                .put("temperature", 0.0);

        RequestBody body = RequestBody.create(
                jsonRequestBody.toString(), MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            System.out.println("✅ LLaMA statut : " + response.code());
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                System.out.println("✅ LLaMA réponse brute : " + responseBody);

                JSONObject jsonResponse = new JSONObject(responseBody);
                String content = jsonResponse.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content").trim();

                System.out.println("✅ JSON extrait : " + content);
                return content;
            } else if (response.body() != null) {
                System.out.println("❌ Erreur LLaMA Groq : " + response.body().string());
            }
        } catch (IOException e) {
            System.out.println("❌ Erreur connexion LLaMA : " + e.getMessage());
        }
        return null;
    }
}