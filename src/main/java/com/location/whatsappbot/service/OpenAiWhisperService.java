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

    public String transcribeAudio(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("❌ Fichier audio introuvable pour Whisper : " + filePath);
            return null;
        }

        System.out.println("📊 Taille fichier : " + file.length() + " bytes");

        String whisperPrompt = "prénom, nom, louer, réserver, départ, durée, jours, semaines, mois, demain, " +
                "Renault Clio, Dacia Sandero, Logan, Dokker, Toyota, Mercedes, BMW, Hyundai, Peugeot, " +
                "smiyti, ismi, bghit nkri, iyam, semana, chhar, ghda, lioum, jemaa, " +
                "my name is, rent a car, days, weeks, tomorrow";

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(file, MediaType.parse("audio/ogg")))
                .addFormDataPart("model", "whisper-large-v3-turbo")
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

    public String analyserTexteAvecGPT(String transcription) {
        System.out.println("✅ Analyse de la phrase par LLaMA (Groq)...");

        String promptSystem = "Tu es un assistant d'extraction pour une agence de location de voiture au Maroc. " +
                "Les clients parlent français, darija marocaine, anglais, ou un mélange. " +
                "Extrait UNIQUEMENT ce qui est dit dans CE message — rien d'autre.\n\n" +

                "Réponds UNIQUEMENT avec un JSON pur contenant ces 5 clés. " +
                "Commence par { et finis par }. JAMAIS de texte avant ou après.\n\n" +

                "1) 'prenom' : prénom du client.\n" +
                "   Corrige intelligemment : Mhmd→Mohamed, Fatma→Fatima, Heba→Hiba, Krim→Karim\n" +
                "   ⚠️ JAMAIS ces mots comme prénom : je/I/moi/ana/smiyti/ismi/bghit/nkri/mbghit\n" +
                "   Si absent → null\n\n" +

                "2) 'nom' : nom de famille.\n" +
                "   Transcris EXACTEMENT ce qui est dit, sans inventer ni supprimer de lettres.\n" +
                "   Si absent → null\n\n" +

                "3) 'typeVoiture' : voiture mentionnée.\n" +
                "   Corrige : Klio→Renault Clio, Sandiru→Dacia Sandero, Lojan→Dacia Logan,\n" +
                "   Korola→Toyota Corolla, Bolo→Volkswagen Polo, Bimo→BMW, I10→Hyundai i10\n" +
                "   ⚠️ Ne mets JAMAIS null si une voiture est mentionnée\n" +
                "   Si absent → null\n\n" +

                "4) 'duree' : durée de location.\n" +
                "   Corrige : joj iyam→2 jours, tlata iyam→3 jours, reb3a→4 jours,\n" +
                "   khamsa→5 jours, semana/semaine→1 semaine, chhar/mois→1 mois\n" +
                "   ⚠️ Ne mets JAMAIS null si une durée est mentionnée\n" +
                "   Si absent → null\n\n" +

                "5) 'dateDepart' : date de début.\n" +
                "   Corrige : ghda/tomorrow→demain, lioum/today→aujourd'hui,\n" +
                "   jemaa/vendredi→vendredi\n" +
                "   Si absent → null\n\n" +

                "⚠️ REGLE ABSOLUE : Extrait UNIQUEMENT ce qui est dans CE message. " +
                "Si une info est absente → null. Ne devine RIEN. Ne complète RIEN.";

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

                // 🔒 Sécurité : supprime les clés inattendues comme "langue"
                try {
                    org.json.JSONObject jsonCheck = new org.json.JSONObject(content);
                    org.json.JSONObject jsonClean = new org.json.JSONObject();
                    jsonClean.put("prenom",
                            jsonCheck.optString("prenom", "null").equals("null") ? org.json.JSONObject.NULL
                                    : jsonCheck.opt("prenom"));
                    jsonClean.put("nom", jsonCheck.optString("nom", "null").equals("null") ? org.json.JSONObject.NULL
                            : jsonCheck.opt("nom"));
                    jsonClean.put("typeVoiture",
                            jsonCheck.optString("typeVoiture", "null").equals("null") ? org.json.JSONObject.NULL
                                    : jsonCheck.opt("typeVoiture"));
                    jsonClean.put("duree",
                            jsonCheck.optString("duree", "null").equals("null") ? org.json.JSONObject.NULL
                                    : jsonCheck.opt("duree"));
                    jsonClean.put("dateDepart",
                            jsonCheck.optString("dateDepart", "null").equals("null") ? org.json.JSONObject.NULL
                                    : jsonCheck.opt("dateDepart"));
                    content = jsonClean.toString();
                    System.out.println("✅ JSON nettoyé final : " + content);
                } catch (Exception e) {
                    System.out.println("⚠️ Nettoyage JSON échoué : " + e.getMessage());
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