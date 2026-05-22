package com.location.whatsappbot.service;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;

@Service
public class OpenAiWhisperService {

    @Value("${groq.api.key}")
    private String groqApiKey;
    @Value("${huggingface.api.key}")
    private String hfApiKey;
    // ✅ SUPPRIME geminiApiKey — on utilise groqApiKey pour les deux

    private final OkHttpClient client = new OkHttpClient();

    // =========================================================
    // 🎙️ ÉTAPE 1 : TRANSCRIPTION AUDIO VIA GROQ WHISPER
    // (inchangé — exactement comme avant)
    // =========================================================
    public String transcribeAudio(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("❌ Fichier audio introuvable : " + filePath);
            return null;
        }

        System.out.println("🎙️ Transcription Darija via HuggingFace...");

        try {
            // Lire le fichier audio en bytes
            byte[] audioBytes = java.nio.file.Files.readAllBytes(file.toPath());

            RequestBody body = RequestBody.create(audioBytes,
                    MediaType.parse("audio/ogg"));

            Request request = new Request.Builder()
                    .url("https://api-inference.huggingface.co/models/speechbrain/asr-wav2vec2-dvoice-darija")
                    .addHeader("Authorization", "Bearer " + hfApiKey)
                    .addHeader("Content-Type", "audio/ogg")
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                System.out.println("📡 HuggingFace réponse raw : " + responseBody);

                if (response.isSuccessful()) {
                    // HuggingFace renvoie : {"text": "transcription ici"}
                    JSONObject json = new JSONObject(responseBody);
                    String transcription = json.optString("text", "").trim();
                    System.out.println("✅ Transcription darija : " + transcription);
                    return transcription;

                } else if (response.code() == 503) {
                    // Modèle en train de charger — attendre et réessayer
                    System.out.println("⏳ Modèle en chargement, attendre 20s...");
                    Thread.sleep(20000);
                    return transcribeAudio(filePath); // réessaye une fois

                } else {
                    System.out.println("❌ Erreur HuggingFace statut : "
                            + response.code() + " → " + responseBody);
                    // Fallback sur Groq Whisper si HF échoue
                    return transcribeAvecGroqFallback(filePath);
                }
            }

        } catch (Exception e) {
            System.out.println("❌ Erreur HuggingFace : " + e.getMessage());
            return transcribeAvecGroqFallback(filePath);
        }
    }

    // ✅ Fallback Groq Whisper si HuggingFace échoue
    private String transcribeAvecGroqFallback(String filePath) {
        System.out.println("🔄 Fallback → Groq Whisper...");
        File file = new File(filePath);

        String whisperPrompt = "Hiba, Arbel, Abdessamad, Benmbarka, Soufiane, "
                + "El Mamoun, Fatima, Mohamed, Youssef, Aicha, Omar, "
                + "smiyti, ismi, ana smiyti, bghit nkri, iyam, simana, chhar, "
                + "Hyundai, Hyundai i10, Hyundai i20, Hyundai Tucson, "
                + "Renault Clio, Dacia Sandero, Logan, Peugeot 3008, BMW, "
                + "rent a car, rent a series";

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(file, MediaType.parse("audio/ogg")))
                .addFormDataPart("model", "whisper-large-v3")
                .addFormDataPart("response_format", "json")
                .addFormDataPart("language", "ar")
                .addFormDataPart("prompt", whisperPrompt)
                .build();

        Request request = new Request.Builder()
                .url("https://api.groq.com/openai/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer " + groqApiKey)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JSONObject json = new JSONObject(response.body().string());
                return json.optString("text", "").trim();
            }
        } catch (IOException e) {
            System.out.println("❌ Erreur Groq fallback : " + e.getMessage());
        }
        return null;
    }

    // =========================================================
    // 🧠 ÉTAPE 2 : EXTRACTION JSON VIA GROQ LLAMA
    // (remplace analyserTexteAvecGemini — même clé groqApiKey)
    // =========================================================
    public String analyserTexteAvecGemini(String transcription) {
        System.out.println("🤖 Analyse en cours par Groq LLaMA...");

        String aujourdhui = java.time.LocalDate.now().toString();

        String prompt = "Aujourd'hui nous sommes le " + aujourdhui + ".\n\n"
                + "Tu es expert d'une agence de location de voitures au Maroc. "
                + "Analyse ce texte (darija, français ou anglais) et renvoie "
                + "UNIQUEMENT un JSON pur avec ces 5 clés : "
                + "prenom, nom, typeVoiture, duree, dateDepart.\n\n"
                + "CORRECTIONS PHONÉTIQUES :\n"
                + "- 'Iba' ou 'Eba' → corriger en 'Hiba'\n"
                + "- 'Undai' ou 'Yundai' → corriger en 'Hyundai'\n"
                + "- 'rent a series' ou 'series [année]' → 'BMW Série'\n\n"
                + "RÈGLES DARIJA :\n"
                + "- 'smiyti' / 'ismi' / 'ana smiyti' = prénom\n"
                + "- 'bghit nkri' = je veux louer\n"
                + "- 'iyam' = jours, 'simana' = semaine, 'chhar' = mois\n"
                + "- 'lioum' = aujourd'hui = " + aujourdhui + "\n"
                + "- 'ghda' = demain\n\n"
                + "RÈGLES DATES :\n"
                + "- 'demain' ou 'dès demain' ou 'ghda' = "
                + java.time.LocalDate.now().plusDays(1).toString() + "\n"
                + "- 'aujourd'hui' ou 'lioum' = " + aujourdhui + "\n"
                + "- Toujours recaler sur l'année 2026\n\n"
                + "RÈGLES GÉNÉRALES :\n"
                + "- Si une info est absente → null\n"
                + "- duree : toujours format '2 jours' ou '1 semaine'\n\n"
                + "⚠️ Renvoie UNIQUEMENT le JSON, sans texte avant ni après.\n\n"
                + "Texte : " + transcription;

        // ... reste du code inchangé
        JSONObject message = new JSONObject()
                .put("role", "user")
                .put("content", prompt);

        JSONObject requestBody = new JSONObject()
                .put("model", "llama-3.3-70b-versatile")
                .put("max_tokens", 256)
                .put("temperature", 0.1) // ✅ Réponses stables et précises
                .put("messages", new JSONArray().put(message))
                .put("response_format", new JSONObject().put("type", "json_object"));
        // ✅ Force le JSON pur — plus fiable que Gemini

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + groqApiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JSONObject json = new JSONObject(response.body().string());
                String result = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim();
                System.out.println("✅ Réponse Groq LLaMA : " + result);
                return result;
            } else {
                System.out.println("❌ Erreur Groq LLM statut : " + response.code());
            }
        } catch (IOException e) {
            System.out.println("❌ Erreur connexion Groq LLM : " + e.getMessage());
        }

        return "{\"prenom\":null,\"nom\":null,\"typeVoiture\":null,\"duree\":null,\"dateDepart\":null}";
    }
}