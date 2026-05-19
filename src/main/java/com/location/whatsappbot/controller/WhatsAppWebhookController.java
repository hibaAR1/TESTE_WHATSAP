package com.location.whatsappbot.controller;

import com.location.whatsappbot.dto.FormulaireAudio;
import com.location.whatsappbot.service.OpenAiWhisperService;
import com.location.whatsappbot.service.WhatsAppSenderService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppWebhookController {

    // ✅ Mémoire des conversations par numéro
    private static final Map<String, FormulaireAudio> sessions = new HashMap<>();

    @Autowired
    private OpenAiWhisperService openAiWhisperService;

    @Autowired
    private WhatsAppSenderService whatsAppSenderService;

    @PostMapping("/webhook")
    public ResponseEntity<String> receiveWebhook(
            @RequestParam(value = "From", required = false) String from,
            @RequestParam(value = "Body", required = false) String body,
            @RequestParam(value = "MediaUrl0", required = false) String mediaUrl,
            @RequestParam(value = "NumMedia", required = false, defaultValue = "0") String numMedia) {

        // ✅ Vérification sécurité
        if (from == null || from.isEmpty()) {
            System.out.println("⚠️ Requête ignorée : aucun numéro reçu.");
            return ResponseEntity.ok("Ignored");
        }

        System.out.println("====== WHATSAPP BOT ======");
        System.out.println("De : " + from);
        System.out.println("Message : " + body);
        System.out.println("NumMedia : " + numMedia);
        System.out.println("MediaUrl : " + mediaUrl);

        String fromNumber = from
                .replace("whatsapp:+", "")
                .replace("whatsapp:", "");

        // ✅ Récupère session existante ou crée nouvelle session
        FormulaireAudio data = sessions.getOrDefault(
                fromNumber,
                new FormulaireAudio(fromNumber));

        FormulaireAudio nouvelles = null;

        // =========================================================
        // 🎙️ CAS AUDIO
        // =========================================================
        if (mediaUrl != null && !mediaUrl.isEmpty()) {

            System.out.println("📥 Téléchargement audio : " + mediaUrl);

            String filePath = whatsAppSenderService.downloadAudioFile(
                    mediaUrl,
                    fromNumber);

            // ✅ fallback si téléchargement échoue
            if (filePath == null) {

                String userDir = System.getProperty("user.dir");

                filePath = userDir + "/documents/vocal_test.ogg";

                System.out.println("⚠️ Fallback fichier test : " + filePath);
            }

            nouvelles = extraireDonneesLocation(
                    filePath,
                    fromNumber);
        }

        // =========================================================
        // 💬 CAS TEXTE
        // =========================================================
        else if (body != null && !body.isEmpty()) {

            System.out.println("💬 Texte reçu : " + body);

            if (!body.equalsIgnoreCase("Test")
                    && body.length() > 2) {

                nouvelles = extraireDonneesDepuisTexteDirect(
                        body,
                        fromNumber);

            } else {

                String reply = "Robot 🤖 : Bonjour ! "
                        + "Envoyez un vocal ou un message contenant :\n"
                        + "- votre prénom\n"
                        + "- votre nom\n"
                        + "- le modèle de voiture\n"
                        + "- la date de départ\n"
                        + "- la durée.";

                whatsAppSenderService.sendWhatsAppResponse(
                        fromNumber,
                        reply);

                return ResponseEntity.ok("OK");
            }
        }

        // =========================================================
        // ❌ CAS NON GÉRÉ
        // =========================================================
        else {

            String reply = "Je gère seulement les messages texte et audio.";

            whatsAppSenderService.sendWhatsAppResponse(
                    fromNumber,
                    reply);

            return ResponseEntity.ok("OK");
        }

        // =========================================================
        // ✅ FUSION DES DONNÉES
        // =========================================================
        if (nouvelles != null) {

            if (nouvelles.getPrenom() != null
                    && !nouvelles.getPrenom().isEmpty()
                    && !"null".equalsIgnoreCase(nouvelles.getPrenom())) {

                data.setPrenom(nouvelles.getPrenom());
            }

            if (nouvelles.getNom() != null
                    && !nouvelles.getNom().isEmpty()
                    && !"null".equalsIgnoreCase(nouvelles.getNom())) {

                data.setNom(nouvelles.getNom());
            }

            if (nouvelles.getTypeVoiture() != null
                    && !nouvelles.getTypeVoiture().isEmpty()
                    && !"null".equalsIgnoreCase(nouvelles.getTypeVoiture())) {

                data.setTypeVoiture(nouvelles.getTypeVoiture());
            }

            if (nouvelles.getDuree() != null
                    && !nouvelles.getDuree().isEmpty()
                    && !"null".equalsIgnoreCase(nouvelles.getDuree())) {

                data.setDuree(nouvelles.getDuree());
            }

            if (nouvelles.getDateDepart() != null
                    && !nouvelles.getDateDepart().isEmpty()
                    && !"null".equalsIgnoreCase(nouvelles.getDateDepart())) {

                data.setDateDepart(nouvelles.getDateDepart());
            }
        }

        // ✅ Sauvegarde session
        sessions.put(fromNumber, data);

        // ✅ Génère réponse
        String reply = genererReponseDepuisData(
                data,
                fromNumber);

        // ✅ Si réservation complète → supprimer session
        if (reply.startsWith("Reservation enregistree")) {

            sessions.remove(fromNumber);

            System.out.println("🗑️ Session supprimée pour : " + fromNumber);
        }

        // ✅ Envoi WhatsApp
        whatsAppSenderService.sendWhatsAppResponse(
                fromNumber,
                reply);

        return ResponseEntity.ok("OK");
    }

    // =========================================================
    // 🎙️ EXTRACTION AUDIO
    // =========================================================
    private FormulaireAudio extraireDonneesLocation(
            String cheminFichier,
            String telephone) {

        FormulaireAudio formulaire = new FormulaireAudio(telephone);

        try {

            String transcription = openAiWhisperService
                    .transcribeAudio(cheminFichier);

            if (transcription == null
                    || transcription.isEmpty()) {

                return formulaire;
            }

            System.out.println("📝 Transcription : " + transcription);

            formulaire.setTexteComplet(transcription);

            String jsonGptString = openAiWhisperService
                    .analyserTexteAvecGPT(transcription);

            if (jsonGptString != null
                    && !jsonGptString.isEmpty()) {

                ObjectMapper mapper = new ObjectMapper();

                JsonNode json = mapper.readTree(jsonGptString);

                if (json.hasNonNull("prenom"))
                    formulaire.setPrenom(
                            json.get("prenom").asText());

                if (json.hasNonNull("nom"))
                    formulaire.setNom(
                            json.get("nom").asText());

                if (json.hasNonNull("typeVoiture"))
                    formulaire.setTypeVoiture(
                            json.get("typeVoiture").asText());

                if (json.hasNonNull("duree"))
                    formulaire.setDuree(
                            json.get("duree").asText());

                if (json.hasNonNull("dateDepart"))
                    formulaire.setDateDepart(
                            json.get("dateDepart").asText());

                System.out.println(
                        "✅ Données extraites : "
                                + formulaire.getPrenom()
                                + " "
                                + formulaire.getNom()
                                + " | "
                                + formulaire.getTypeVoiture()
                                + " | "
                                + formulaire.getDateDepart()
                                + " | "
                                + formulaire.getDuree());
            }

        } catch (Exception e) {

            System.out.println("❌ Erreur IA : " + e.getMessage());
        }

        return formulaire;
    }

    // =========================================================
    // 💬 EXTRACTION TEXTE
    // =========================================================
    private FormulaireAudio extraireDonneesDepuisTexteDirect(
            String texte,
            String telephone) {

        FormulaireAudio formulaire = new FormulaireAudio(telephone);

        formulaire.setTexteComplet(texte);

        try {

            String jsonGptString = openAiWhisperService
                    .analyserTexteAvecGPT(texte);

            if (jsonGptString != null
                    && !jsonGptString.isEmpty()) {

                ObjectMapper mapper = new ObjectMapper();

                JsonNode json = mapper.readTree(jsonGptString);

                if (json.hasNonNull("prenom"))
                    formulaire.setPrenom(
                            json.get("prenom").asText());

                if (json.hasNonNull("nom"))
                    formulaire.setNom(
                            json.get("nom").asText());

                if (json.hasNonNull("typeVoiture"))
                    formulaire.setTypeVoiture(
                            json.get("typeVoiture").asText());

                if (json.hasNonNull("duree"))
                    formulaire.setDuree(
                            json.get("duree").asText());

                if (json.hasNonNull("dateDepart"))
                    formulaire.setDateDepart(
                            json.get("dateDepart").asText());
            }

        } catch (Exception e) {

            System.out.println("❌ Erreur analyse texte : " + e.getMessage());
        }

        return formulaire;
    }

    // =========================================================
    // 🤖 GÉNÉRATION RÉPONSE
    // =========================================================
    private String genererReponseDepuisData(
            FormulaireAudio data,
            String fromNumber) {

        boolean prenomManquant = data.getPrenom() == null
                || data.getPrenom().isEmpty()
                || "null".equalsIgnoreCase(data.getPrenom());

        boolean nomManquant = data.getNom() == null
                || data.getNom().isEmpty()
                || "null".equalsIgnoreCase(data.getNom());

        boolean voitureManquante = data.getTypeVoiture() == null
                || data.getTypeVoiture().isEmpty()
                || "null".equalsIgnoreCase(data.getTypeVoiture());

        boolean dureeManquante = data.getDuree() == null
                || data.getDuree().isEmpty()
                || "null".equalsIgnoreCase(data.getDuree());

        boolean dateManquante = data.getDateDepart() == null
                || data.getDateDepart().isEmpty()
                || "null".equalsIgnoreCase(data.getDateDepart());

        // =========================================================
        // ✅ TOUT EST COMPLET
        // =========================================================
        if (!prenomManquant
                && !nomManquant
                && !voitureManquante
                && !dureeManquante
                && !dateManquante) {

            return "Reservation enregistree ! Client : "
                    + data.getPrenom()
                    + " "
                    + data.getNom()
                    + " - Vehicule : "
                    + data.getTypeVoiture()
                    + " - Date de depart : "
                    + data.getDateDepart()
                    + " - Duree : "
                    + data.getDuree()
                    + " - Tel : "
                    + fromNumber
                    + ". Un agent va vous contacter !";
        }

        // =========================================================
        // ❌ INFOS MANQUANTES
        // =========================================================
        StringBuilder buildReply = new StringBuilder();

        buildReply.append("Robot 🤖 : ");
        buildReply.append("Il manque des informations :\n");

        if (prenomManquant) {
            buildReply.append("❌ Prénom manquant\n");
        }

        if (nomManquant) {
            buildReply.append("❌ Nom manquant\n");
        }

        if (voitureManquante) {
            buildReply.append("❌ Voiture manquante\n");
        }

        if (dateManquante) {
            buildReply.append("❌ Date de départ manquante\n");
        }

        if (dureeManquante) {
            buildReply.append("❌ Durée manquante\n");
        }

        buildReply.append(
                "\nMerci d'envoyer uniquement les informations manquantes 😊");

        return buildReply.toString();
    }
}