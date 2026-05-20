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

        String whisperPrompt = "prénom, nom, louer, réserver, départ, durée, jours, semaines, mois, demain, " +
                "Renault Clio, Dacia Sandero, Logan, Dokker, Toyota, Volkswagen Polo, Mercedes, BMW, " +
                "smiyti, ismi, bghit nkri, iyam, semana, chhar, ghda, lioum, jemaa, " +
                "Klio, Sandiru, Lojan, Dokir, Korola, " +
                "my name is, rent a car, days, weeks, tomorrow";

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(file, MediaType.parse("audio/ogg")))
                .addFormDataPart("model", "whisper-large-v3")
                .addFormDataPart("response_format", "json")
                .addFormDataPart("language", "fr")
                .addFormDataPart("prompt", whisperPrompt)
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

    // 🧠 ÉTAPE 2 : EXTRACTION STRUCTURÉE AVEC LLAMA 3.1
    public String analyserTexteAvecGPT(String transcription) {
        System.out.println("✅ Analyse de la phrase par LLaMA (Groq)...");

        String promptSystem = "INSTRUCTION CRITIQUE : Ta réponse doit commencer DIRECTEMENT par { et finir par }. " +
                "JAMAIS de texte avant ou après. JAMAIS de 'Voici', 'JSON:', ou toute explication. " +
                "UNIQUEMENT l'objet JSON pur.\n\n" +

                "Tu es un assistant d'extraction pour une agence de location de voiture au Maroc. " +
                "Les clients parlent français, darija marocaine, anglais, ou un mélange. " +
                "Extrait ces 5 clés JSON :\n\n" +

                "1) 'prenom' : prénom du client.\n" +
                "   - Mhmd / Mhammed → Mohamed\n" +
                "   - Fatma → Fatima\n" +
                "   - Youssef / Yousef → Youssef\n" +
                "   - Hiba / Heba / Iba / Hba → Hiba\n" +
                "   - Karim / Krim → Karim\n" +
                "   - Amine / Amyn → Amine\n" +
                "   - Imane / Iman → Imane\n" +
                "   - Zineb / Zneb → Zineb\n" +
                "   ⚠️ Ne prends JAMAIS 'ana/je/I/moi/smiyti/ismi' comme prénom\n" +
                "   Si absent → null\n\n" +

                "2) 'nom' : nom de famille.\n" +
                "   - Arbel / Arbeel / Arbail / Arbal → Arbel\n" +
                "   - Benali / Ben Ali → Benali\n" +
                "   - Alaoui / Alawy → Alaoui\n" +
                "   - Tazi / Tazy → Tazi\n" +
                "   - Idrissi / Idrisi → Idrissi\n" +
                "   Si absent → null\n\n" +

                "3) 'typeVoiture' : voiture demandée.\n" +
                "   - Klio / Clio → Renault Clio\n" +
                "   - Sandiru / Sandero → Dacia Sandero\n" +
                "   - Lojan / Logan → Dacia Logan\n" +
                "   - Dokir / Dokker → Dacia Dokker\n" +
                "   - Korola / Corolla → Toyota Corolla\n" +
                "   - Bolo / Polo → Volkswagen Polo\n" +
                "   - Bimo / BMW → BMW\n" +
                "   - Mersidis / Mercedes → Mercedes\n" +
                "   - I10 / Ayi10 → Hyundai i10\n" +
                "   - Pikanto / Picanto → Kia Picanto\n" +
                "   ⚠️ Ne mets JAMAIS null si une voiture est mentionnée\n\n" +

                "4) 'duree' : durée de location.\n" +
                "   - joj iyam / jouj jours → 2 jours\n" +
                "   - tlata iyam → 3 jours\n" +
                "   - reb3a iyam → 4 jours\n" +
                "   - khamsa iyam → 5 jours\n" +
                "   - semana / semaine / week → 1 semaine\n" +
                "   - joj semana → 2 semaines\n" +
                "   - chhar / mois / month → 1 mois\n" +
                "   ⚠️ Ne mets JAMAIS null si une durée est mentionnée\n\n" +

                "5) 'dateDepart' : date de début.\n" +
                "   - ghda / tomorrow → demain\n" +
                "   - lioum / had / today → aujourd'hui\n" +
                "   - jemaa / vendredi / friday → vendredi\n" +
                "   - jemaa jaya → vendredi prochain\n" +
                "   Si vraiment absente → null\n\n" +

                "⚠️ RAPPEL FINAL : Commence par { et finis par }. Rien d'autre !";

        JSONObject messageSystem = new JSONObject()
                .put("role", "system")
                .put("content", promptSystem);

        JSONObject messageUser = new JSONObject()
                .put("role", "user")
                .put("content", transcription);

        JSONObject jsonRequestBody = new JSONObject()
                .put("model", "llama-3.1-8b-instant")
                .put("messages", new JSONObject[] { messageSystem, messageUser })
                .put("temperature", 0.0);

        RequestBody body = RequestBody.create(
                jsonRequestBody.toString(),
                MediaType.parse("application/json; charset=utf-8"));

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

                // 🔒 Sécurité : extrait JSON même si LLaMA ajoute du texte
                if (!content.startsWith("{")) {
                    int start = content.indexOf("{");
                    int end = content.lastIndexOf("}");
                    if (start != -1 && end != -1) {
                        content = content.substring(start, end + 1);
                        System.out.println("⚠️ JSON nettoyé : " + content);
                    }
                }

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