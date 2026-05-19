package com.location.whatsappbot.controller;

import com.location.whatsappbot.dto.FormulaireAudio;
import com.location.whatsappbot.service.OpenAiWhisperService;
import com.location.whatsappbot.service.WhatsAppSenderService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppWebhookController {

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

        // Sécurité si la requête n'a pas d'expéditeur
        if (from == null || from.isEmpty()) {
            System.out.println("⚠️ Requête ignorée : Aucun expéditeur (From est null).");
            return ResponseEntity.ok("Ignored: No phone number provided");
        }

        System.out.println("====== WHATSAPP BOT (TWILIO) ======");
        System.out.println("De : " + from);
        System.out.println("Message : " + body);
        System.out.println("NumMedia : " + numMedia);
        System.out.println("MediaUrl : " + mediaUrl);

        String fromNumber = from.replace("whatsapp:+", "").replace("whatsapp:", "");
        String reply = "";

        // 🎙️ CAS 1 : Gestion des messages Audio reçus de Twilio
        if (mediaUrl != null && !mediaUrl.isEmpty()) {

            System.out.println("📥 Téléchargement audio depuis Twilio : " + mediaUrl);
            String filePath = whatsAppSenderService.downloadAudioFile(mediaUrl, fromNumber);

            // Sécurité Fallback locale si le téléchargement échoue
            if (filePath == null) {
                String userDir = System.getProperty("user.dir");
                filePath = userDir + "/documents/vocal_test.ogg";
                System.out.println("⚠️ Fallback fichier test : " + filePath);
            }

            FormulaireAudio data = extraireDonneesLocation(filePath, fromNumber);
            reply = genererReponseDepuisData(data, fromNumber);

            // 💬 CAS 2 : Gestion des messages Texte alternatifs (Utile si l'audio bloque)
        } else if (body != null && !body.isEmpty()) {
            System.out.println("💬 Message texte intercepté à la place de l'audio : " + body);

            // Pour offrir une flexibilité totale pendant ton test, on permet aussi
            // l'analyse du texte direct
            if (!body.equalsIgnoreCase("Test") && body.length() > 3) {
                FormulaireAudio data = extraireDonneesDepuisTexteDirect(body, fromNumber);
                reply = genererReponseDepuisData(data, fromNumber);
            } else {
                reply = "Robot 🤖 : Bonjour ! Envoyez un vocal (ou un message) contenant votre prénom, le modèle de voiture, la date de départ et la durée.";
            }
        } else {
            reply = "Je gère seulement les messages texte et audio pour l'instant.";
        }

        whatsAppSenderService.sendWhatsAppResponse(fromNumber, reply);
        return ResponseEntity.ok("OK");
    }

    private FormulaireAudio extraireDonneesLocation(String cheminFichier, String telephone) {
        FormulaireAudio formulaire = new FormulaireAudio(telephone);
        try {
            String transcription = openAiWhisperService.transcribeAudio(cheminFichier);
            if (transcription == null || transcription.isEmpty())
                return formulaire;

            System.out.println("📝 Transcription : " + transcription);
            formulaire.setTexteComplet(transcription);

            String jsonGptString = openAiWhisperService.analyserTexteAvecGPT(transcription);
            if (jsonGptString != null && !jsonGptString.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode json = mapper.readTree(jsonGptString);

                if (json.hasNonNull("prenom"))
                    formulaire.setPrenom(json.get("prenom").asText());
                if (json.hasNonNull("typeVoiture"))
                    formulaire.setTypeVoiture(json.get("typeVoiture").asText());
                if (json.hasNonNull("duree"))
                    formulaire.setDuree(json.get("duree").asText());
                if (json.hasNonNull("dateDepart"))
                    formulaire.setDateDepart(json.get("dateDepart").asText());

                System.out.println("✅ Données extraites — Prénom: " + formulaire.getPrenom()
                        + " | Voiture: " + formulaire.getTypeVoiture()
                        + " | Durée: " + formulaire.getDuree()
                        + " | Départ: " + formulaire.getDateDepart());
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

                if (json.hasNonNull("prenom"))
                    formulaire.setPrenom(json.get("prenom").asText());
                if (json.hasNonNull("typeVoiture"))
                    formulaire.setTypeVoiture(json.get("typeVoiture").asText());
                if (json.hasNonNull("duree"))
                    formulaire.setDuree(json.get("duree").asText());
                if (json.hasNonNull("dateDepart"))
                    formulaire.setDateDepart(json.get("dateDepart").asText());
            }
        } catch (Exception e) {
            System.out.println("❌ Erreur analyse texte direct : " + e.getMessage());
        }
        return formulaire;
    }

    // 🤖 LOGIQUE DE VÉRIFICATION DES OUBLIS ET GÉNÉRATION DE LA RÉPONSE
    private String genererReponseDepuisData(FormulaireAudio data, String fromNumber) {
        boolean prenomManquant = (data.getPrenom() == null || data.getPrenom().isEmpty()
                || "null".equalsIgnoreCase(data.getPrenom()));
        boolean voitureManquante = (data.getTypeVoiture() == null || data.getTypeVoiture().isEmpty()
                || "null".equalsIgnoreCase(data.getTypeVoiture()));
        boolean dureeManquante = (data.getDuree() == null || data.getDuree().isEmpty()
                || "null".equalsIgnoreCase(data.getDuree()));
        boolean dateManquante = (data.getDateDepart() == null || data.getDateDepart().isEmpty()
                || "null".equalsIgnoreCase(data.getDateDepart()));

        // Si tout est complet, on valide la réservation !
        if (!prenomManquant && !voitureManquante && !dureeManquante && !dateManquante) {
            return "Reservation enregistree ! Client: " + data.getPrenom()
                    + " - Vehicule: " + data.getTypeVoiture()
                    + " - Date de depart: " + data.getDateDepart()
                    + " - Duree: " + data.getDuree()
                    + " - Tel: " + fromNumber
                    + ". Un agent va vous contacter !";
        }

        // Sinon, on liste intelligemment à l'utilisateur ce qu'il a oublié
        StringBuilder buildReply = new StringBuilder("Robot 🤖 : ");
        buildReply.append("Il manque des informations importantes pour votre réservation :\n");

        if (prenomManquant) {
            buildReply.append("❌ Votre prénom n'a pas été détecté.\n");
        }
        if (voitureManquante) {
            buildReply.append("❌ Le modèle ou type de voiture est manquant.\n");
        }
        if (dateManquante) {
            buildReply.append("❌ La date de début/départ de la location n'est pas précisée.\n");
        }
        if (dureeManquante) {
            buildReply.append("❌ La durée totale de la location est manquante.\n");
        }

        buildReply.append("\nPouvez-vous me renvoyer un message bien clair contenant ces éléments ? 😊");
        return buildReply.toString();
    }
}