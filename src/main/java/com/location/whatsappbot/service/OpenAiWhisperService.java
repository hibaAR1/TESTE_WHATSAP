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

        String promptSystem = "Tu es un assistant d'extraction d'informations pour une agence de location de voiture au Maroc. "
                + "Les clients parlent en français, darija marocaine, ou anglais — parfois un mélange des trois. "
                + "Analyse le texte et extrait UNIQUEMENT un objet JSON avec ces cinq clés :\n\n"
                + "1) 'prenom' : le prénom. Exemples darija/phonétique → correction :\n"
                + "   - Mhmd/Mohamed/محمد → Mohamed\n"
                + "   - Fatma/Fatima/فاطمة → Fatima\n"
                + "   - Youssef/Yousef/يوسف → Youssef\n"
                + "   - Hiba/Heba/هيبا → Hiba\n"
                + "   - Karim/Krim → Karim\n\n"
                + "2) 'nom' : le nom de famille. Même logique de correction. Si absent → null\n\n"
                + "3) 'typeVoiture' : toute voiture citée même approximatif. Exemples :\n"
                + "   - Klio/Clio/كليو → Renault Clio\n"
                + "   - Sandero/سانديرو → Dacia Sandero\n"
                + "   - Polo/بولو → Volkswagen Polo\n"
                + "   - Corolla/كورولا → Toyota Corolla\n"
                + "   - Dokker/دوكر → Dacia Dokker\n"
                + "   - Logan/لوغان → Dacia Logan\n"
                + "   - Ne mets JAMAIS null si une voiture est mentionnée\n\n"
                + "4) 'duree' : durée de location. Exemples darija :\n"
                + "   - joj iyam/jouj jours → 2 jours\n"
                + "   - tlata iyam → 3 jours\n"
                + "   - semana/semaine/week → 1 semaine\n"
                + "   - chhar/mois/month → 1 mois\n"
                + "   - Ne mets JAMAIS null si une durée est mentionnée\n\n"
                + "5) 'dateDepart' : date de début. Exemples darija :\n"
                + "   - ghda/غدا/tomorrow → demain\n"
                + "   - had/aujourd'hui/today → aujourd'hui\n"
                + "   - jemaa/vendredi/friday → vendredi\n"
                + "   - Si vraiment absente → null\n\n"
                + "⚠️ REGLES ABSOLUES :\n"
                + "- Sois GENEREUX, ne mets null QUE si vraiment absent\n"
                + "- Ne prends JAMAIS 'ana/je/I' comme prénom\n"
                + "- JSON pur uniquement, pas de markdown";

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