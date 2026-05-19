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
                + "Analyse le texte de l'utilisateur et extrait UNIQUEMENT un objet JSON contenant exactement ces quatre clés :\n"
                + "1) 'prenom' (corrige intelligemment les mauvaises prononciations ou orthographes, exemple: Yépa ou Hiba devient Hiba, Mohamd devient Mohamed)\n"
                + "2) 'typeVoiture' (toute marque ou modèle de voiture cité, même approximatif. Exemples: Toyota, Toyota 2025 devient Toyota 2025, Clio devient Renault Clio, pliou devient Renault Clio, Tayo devient Toyota. Ne mets JAMAIS null si une marque ou un mot ressemblant à une voiture est mentionné)\n"
                + "3) 'duree' (la durée demandée, exemple: 3 jours, une semaine, 7 jours. Ne mets JAMAIS null si une durée ou période est mentionnée)\n"
                + "4) 'dateDepart' (la date où commence la location. Exemples: demain, lundi prochain, le 25 mai, 5-19-2026. Si vraiment aucune date n'est mentionnée, mets null)\n\n"
                + "⚠️ REGLE ABSOLUE : Sois GENEREUX dans l'extraction, ne mets null QUE si l'information est vraiment absente. Ne devine rien d'inventé, mais utilise tout ce qui est dit.\n"
                + "Ne réponds rien d'autre que le JSON pur. Pas de texte explicatif avant ou après, pas de blocs de code markdown comme ```json.";

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