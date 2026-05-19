package com.location.whatsappbot.controller;

import com.location.whatsappbot.dto.FormulaireAudio;
import com.location.whatsappbot.service.OpenAiWhisperService;
import com.location.whatsappbot.service.WhatsAppSenderService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppWebhookController {

    @Autowired
    private OpenAiWhisperService openAiWhisperService;

    @Autowired
    private WhatsAppSenderService whatsAppSenderService;

    // 🧠 Mémoire des sessions par numéro
    private static final Map<String, FormulaireAudio> sessions = new HashMap<>();

    @PostMapping("/webhook")
    public ResponseEntity<String> receiveWebhook(@RequestBody String payload) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(payload);

            System.out.println("====== WAHA WEBHOOK ======");
            System.out.println("Payload : " + payload);

            // Récupère le type de message
            String type = json.path("payload").path("type").asText();
            String from = json.path("payload").path("from").asText();

            if (from == null || from.isEmpty()) {
                return ResponseEntity.ok("Ignored");
            }

            // Nettoie le numéro (enlève @c.us)
            String fromNumber = from.replace("@c.us", "").replace("+", "");

            System.out.println("De : " + fromNumber);
            System.out.println("Type : " + type);

            String reply = "";

            // 🎙️ Message Audio
            if (type.equals("audio") || type.equals("ptt")) {
                String mediaUrl = json.path("payload").path("media").path("url").asText();
                System.out.println("📥 Audio URL : " + mediaUrl);

                String filePath = whatsAppSenderService.downloadAudioFile(mediaUrl, fromNumber);

                if (filePath == null) {
                    reply = "❌ Impossible de télécharger l'audio.";
                } else {
                    FormulaireAudio nouvelles = extraireDonneesLocation(filePath, fromNumber);
                    FormulaireAudio data = fusionnerSession(fromNumber, nouvelles);
                    reply = genererReponseDepuisData(data, fromNumber);
                }

            // 💬 Message Texte
            } else if (type.equals("text")) {
                String body = json.path("payload").path("body").asText();
                System.out.println("💬 Texte : " + body);

                if (body.length() > 3) {
                    FormulaireAudio nouvelles = extraireDonneesDepuisTexteDirect(body, fromNumber);
                    FormulaireAudio data = fusionnerSession(fromNumber, nouvelles);
                    reply = genererReponseDepuisData(data, fromNumber);
                } else {
                    reply = "Robot 🤖 : Bonjour ! Envoyez un vocal contenant votre nom, prénom, le modèle de voiture, la date de départ et la durée.";
                }
            } else {
                return ResponseEntity.ok("Type ignoré : " + type);
            }

            whatsAppSenderService.sendWhatsAppResponse(fromNumber, reply);

        } catch (Exception e) {
            System.out.println("❌ Erreur webhook : " + e.getMessage());
        }

        return ResponseEntity.ok("OK");
    }

    // 🧠 Fusionne les nouvelles infos avec la session existante
    private FormulaireAudio fusionnerSession(String fromNumber, FormulaireAudio nouvelles) {
        FormulaireAudio data = sessions.getOrDefault(fromNumber, new FormulaireAudio(fromNumber));

        if (estValide(nouvelles.getPrenom())) data.setPrenom(nouvelles.getPrenom());
        if (estValide(nouvelles.getNom())) data.setNom(nouvelles.getNom());
        if (estValide(nouvelles.getTypeVoiture())) data.setTypeVoiture(nouvelles.getTypeVoiture());
        if (estValide(nouvelles.getDuree())) data.setDuree(nouvelles.getDuree());
        if (estValide(nouvelles.getDateDepart())) data.setDateDepart(nouvelles.getDateDepart());

        sessions.put(fromNumber, data);
        return data;
    }

    private boolean estValide(String val) {
        return val != null && !val.isEmpty() && !val.equalsIgnoreCase("null");
    }

    private FormulaireAudio extraireDonneesLocation(String cheminFichier, String telephone) {
        FormulaireAudio formulaire = new FormulaireAudio(telephone);
        try {
            String transcription = openAiWhisperService.transcribeAudio(cheminFichier);
            if (transcription == null || transcription.isEmpty()) return formulaire;

            System.out.println("📝 Transcription : " + transcription);
            formulaire.setTexteComplet(transcription);

            String jsonGptString = openAiWhisperService.analyserTexteAvecGPT(transcription);
            if (jsonGptString != null && !jsonGptString.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode json = mapper.readTree(jsonGptString);

                if (json.hasNonNull("prenom")) formulaire.setPrenom(json.get("prenom").asText());
                if (json.hasNonNull("nom")) formulaire.setNom(json.get("nom").asText());
                if (json.hasNonNull("typeVoiture")) formulaire.setTypeVoiture(json.get("typeVoiture").asText());
                if (json.hasNonNull("duree")) formulaire.setDuree(json.get("duree").asText());
                if (json.hasNonNull("dateDepart")) formulaire.setDateDepart(json.get("dateDepart").asText());
            }
        } catch (Exception e) {
            System.out.println("❌ Erreur IA : " + e.getMessage());
        }
        return formulaire;
    }

    private FormulaireAudio extraireDonneesDepuisTexteDirect(String texte, String telephone) {
        FormulaireAudio formulaire = new FormulaireAudio(telephone);
        formulaire.setTexteComplet(texte);
        try {
            String jsonGptString = openAiWhisperService.analyserTexteAvecGPT(texte);
            if (jsonGptString != null && !jsonGptString.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode json = mapper.readTree(jsonGptString);

                if (json.hasNonNull("prenom")) formulaire.setPrenom(json.get("prenom").asText());
                if (json.hasNonNull("nom")) formulaire.setNom(json.get("nom").asText());
                if (json.hasNonNull("typeVoiture")) formulaire.setTypeVoiture(json.get("typeVoiture").asText());
                if (json.hasNonNull("duree")) formulaire.setDuree(json.get("duree").asText());
                if (json.hasNonNull("dateDepart")) formulaire.setDateDepart(json.get("dateDepart").asText());
            }
        } catch (Exception e) {
            System.out.println("❌ Erreur analyse texte : " + e.getMessage());
        }
        return formulaire;
    }

    private String genererReponseDepuisData(FormulaireAudio data, String fromNumber) {
        boolean prenomManquant = !estValide(data.getPrenom());
        boolean nomManquant = !estValide(data.getNom());
        boolean voitureManquante = !estValide(data.getTypeVoiture());
        boolean dureeManquante = !estValide(data.getDuree());
        boolean dateManquante = !estValide(data.getDateDepart());

        if (!prenomManquant && !nomManquant && !voitureManquante && !dureeManquante && !dateManquante) {
            sessions.remove(fromNumber);
            return "Reservation enregistree ! Client: " + data.getPrenom()
                    + " " + data.getNom()
                    + " - Vehicule: " + data.getTypeVoiture()
                    + " - Date de depart: " + data.getDateDepart()
                    + " - Duree: " + data.getDuree()
                    + " - Tel: " + fromNumber
                    + ". Un agent va vous contacter !";
        }

        StringBuilder reply = new StringBuilder("Robot 🤖 : Il manque des informations :\n");
        if (prenomManquant) reply.append("❌ Votre prénom\n");
        if (nomManquant) reply.append("❌ Votre nom de famille\n");
        if (voitureManquante) reply.append("❌ Le modèle de voiture\n");
        if (dateManquante) reply.append("❌ La date de départ\n");
        if (dureeManquante) reply.append("❌ La durée de location\n");
        reply.append("\nPouvez-vous me renvoyer un message avec ces éléments ? 😊");

        return reply.toString();
    }
}