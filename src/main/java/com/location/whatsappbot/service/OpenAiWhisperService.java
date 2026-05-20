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

    // 🎙️ ÉTAPE 1 : TRANSCRIPTION AUDIO MULTILINGUE
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
                .addFormDataPart("language", "fr") // On garde "fr" car le modèle turbo gère le code-switching Darija/FR
                                                   // s'il est guidé par le prompt.
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

    // 🧠 ÉTAPE 2 : EXTRACTION FLUIDE ET FIABLE
    public String analyserTexteAvecGPT(String transcription) {
        System.out.println("✅ Analyse de la phrase par LLaMA (Groq)...");

        String promptSystem = "Tu es un expert en extraction de données JSON pour une agence de location de voitures au Maroc.\n"
                +
                "Le client s'exprime en français, anglais, darija marocaine ou un mélange des trois.\n\n" +
                "Analyse le message reçu et génère UNIQUEMENT un objet JSON standard contenant ces 5 clés :\n" +
                "1) 'prenom' : Extrait le prénom s'il est mentionné. Si absent -> null.\n" +
                "2) 'nom' : Extrait le nom de famille s'il est mentionné. Si absent -> null.\n" +
                "3) 'typeVoiture' : La marque ou modèle de voiture mentionné. Si le client donne juste une marque comme 'Hyundai', écris 'Hyundai'. Ne devine pas le modèle (ex: n'ajoute pas i10 de toi-même). Si absent -> null.\n"
                +
                "4) 'duree' : Traduis et normalise la durée en français (ex: '6 jours' -> '6 jours', 'tlt iyam' -> '3 jours', 'semana' -> '1 semaine'). Si absent -> null.\n"
                +
                "5) 'dateDepart' : Extrait la date exacte ou l'expression temporelle mentionnée par le client (ex: '20 mai 2026' -> '20 mai 2026', 'ghda' -> 'demain'). Ne remplace JAMAIS une date brute par 'aujourd'hui' sauf si le client dit explicitement 'aujourd'hui' ou 'lioum'. Si absent -> null.\n\n"
                +
                "⚠️ CONSIGNES STRICTES :\n" +
                "- Ne prends pas d'exemples au pied de la lettre. Repose-toi uniquement sur les faits du texte.\n" +
                "- Reste fidèle aux chiffres cités (ne transforme pas 6 jours en sbatat iyam).\n" +
                "- Renvoie uniquement le JSON pur. Aucun texte explicatif.";

        JSONObject messageSystem = new JSONObject().put("role", "system").put("content", promptSystem);
        JSONObject messageUser = new JSONObject().put("role", "user").put("content", transcription);

        JSONObject jsonRequestBody = new JSONObject()
                .put("model", "llama-3.1-8b-instant")
                .put("messages", new JSONObject[] { messageSystem, messageUser })
                .put("temperature", 0.1); // Légère augmentation pour éviter les boucles de répétition d'exemples du
                                          // prompt

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

                // 🔒 Sécurité : extrait JSON si texte parasite autour
                if (!content.startsWith("{")) {
                    int start = content.indexOf("{");
                    int end = content.lastIndexOf("}");
                    if (start != -1 && end != -1) {
                        content = content.substring(start, end + 1);
                    }
                }

                // 🔒 Sécurité : reconstruction du JSON propre
                try {
                    JSONObject jsonCheck = new JSONObject(content);
                    JSONObject jsonClean = new JSONObject();
                    jsonClean.put("prenom", jsonCheck.optString("prenom", "null").equals("null") ? JSONObject.NULL
                            : jsonCheck.opt("prenom"));
                    jsonClean.put("nom",
                            jsonCheck.optString("nom", "null").equals("null") ? JSONObject.NULL : jsonCheck.opt("nom"));
                    jsonClean.put("typeVoiture",
                            jsonCheck.optString("typeVoiture", "null").equals("null") ? JSONObject.NULL
                                    : jsonCheck.opt("typeVoiture"));
                    jsonClean.put("duree", jsonCheck.optString("duree", "null").equals("null") ? JSONObject.NULL
                            : jsonCheck.opt("duree"));
                    jsonClean.put("dateDepart",
                            jsonCheck.optString("dateDepart", "null").equals("null") ? JSONObject.NULL
                                    : jsonCheck.opt("dateDepart"));
                    content = jsonClean.toString();
                } catch (Exception e) {
                    System.out.println("⚠️ Nettoyage JSON échoué : " + e.getMessage());
                }

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