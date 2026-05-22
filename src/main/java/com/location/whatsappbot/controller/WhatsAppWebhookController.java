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

    // ✅ Mémoire des conversations par numéro (Session persistante)
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

        String fromNumber = from
                .replace("whatsapp:+", "")
                .replace("whatsapp:", "")
                .trim();

        System.out.println("\n====== WHATSAPP BOT ======");
        System.out.println("De : " + fromNumber);
        if (body != null)
            System.out.println("Message : " + body);
        System.out.println("NumMedia : " + numMedia);
        if (mediaUrl != null)
            System.out.println("MediaUrl : " + mediaUrl);

        // ✅ Récupère la session existante pour préserver la mémoire, ou en crée une
        // nouvelle
        FormulaireAudio data = sessions.getOrDefault(fromNumber, new FormulaireAudio(fromNumber));

        FormulaireAudio nouvelles = null;

        // =========================================================
        // 🎙️ CAS AUDIO
        // =========================================================
        if (mediaUrl != null && !mediaUrl.isEmpty()) {

            System.out.println("📥 Téléchargement audio : " + mediaUrl);

            String filePath = whatsAppSenderService.downloadAudioFile(mediaUrl, fromNumber);

            // ✅ fallback si téléchargement échoue
            if (filePath == null) {
                String userDir = System.getProperty("user.dir");
                filePath = userDir + "/documents/vocal_test.ogg";
                System.out.println("⚠️ Fallback fichier test : " + filePath);
            }

            nouvelles = extraireDonneesLocation(filePath, fromNumber);
        }

        // =========================================================
        // 💬 CAS TEXTE
        // =========================================================
        else if (body != null && !body.isEmpty()) {

            System.out.println("💬 Texte reçu : " + body);

            if (!body.equalsIgnoreCase("Test") && body.length() > 2) {
                nouvelles = extraireDonneesDepuisTexteDirect(body, fromNumber);
            } else {
                String reply = "Robot 🤖 : Bonjour ! "
                        + "Envoyez un vocal ou un message contenant :\n"
                        + "- votre prénom\n"
                        + "- votre nom\n"
                        + "- le modèle de voiture\n"
                        + "- la date de départ\n"
                        + "- la durée.";

                System.out.println("\n🤖 [RECONSTITUTION BOT] Réponse automatique d'aide envoyée.");
                whatsAppSenderService.sendWhatsAppResponse(fromNumber, reply);
                return ResponseEntity.ok("OK");
            }
        }

        // =========================================================
        // ❌ CAS NON GÉRÉ
        // =========================================================
        else {
            String reply = "Je gère seulement les messages texte et audio.";
            whatsAppSenderService.sendWhatsAppResponse(fromNumber, reply);
            return ResponseEntity.ok("OK");
        }

        // =========================================================
        // ✅ FUSION INTELLIGENTE ET SÉCURISÉE DES DONNÉES
        // =========================================================
        if (nouvelles != null) {

            if (nouvelles.getPrenom() != null && !nouvelles.getPrenom().isEmpty()
                    && !nouvelles.getPrenom().equalsIgnoreCase("null")) {
                data.setPrenom(nouvelles.getPrenom());
            }

            if (nouvelles.getNom() != null && !nouvelles.getNom().isEmpty()
                    && !nouvelles.getNom().equalsIgnoreCase("null")) {
                data.setNom(nouvelles.getNom());
            }

            if (nouvelles.getTypeVoiture() != null && !nouvelles.getTypeVoiture().isEmpty()
                    && !nouvelles.getTypeVoiture().equalsIgnoreCase("null")) {
                data.setTypeVoiture(nouvelles.getTypeVoiture());
            }

            if (nouvelles.getDuree() != null && !nouvelles.getDuree().isEmpty()
                    && !nouvelles.getDuree().equalsIgnoreCase("null")) {
                data.setDuree(nouvelles.getDuree());
            }

            if (nouvelles.getDateDepart() != null && !nouvelles.getDateDepart().isEmpty()
                    && !nouvelles.getDateDepart().equalsIgnoreCase("null")) {
                data.setDateDepart(nouvelles.getDateDepart());
            }
        }

        // ✅ Sauvegarde ou mise à jour de l'état de la session globale
        sessions.put(fromNumber, data);

        // Affiche l'état actuel de la mémoire accumulée dans ton terminal
        System.out.println("📊 [MÉMOIRE CONVERSATION] État actuel pour " + fromNumber + " :");
        System.out.println("   -> Prénom : " + data.getPrenom());
        System.out.println("   -> Nom    : " + data.getNom());
        System.out.println("   -> Auto   : " + data.getTypeVoiture());
        System.out.println("   -> Date   : " + data.getDateDepart());
        System.out.println("   -> Durée  : " + data.getDuree());

        // ✅ Génère la réponse finale ou la relance
        String reply = genererReponseDepuisData(data, fromNumber);

        // ✅ IMPORTANT : On ne supprime la session QUE si tout est validé de bout en
        // bout
        if (reply.startsWith("Reservation enregistree")) {
            sessions.remove(fromNumber);
            System.out.println("🗑️ Session complétée et archivée pour : " + fromNumber);
        } else {
            System.out.println("⏳ Données partielles reçues. Session préservée pour le prochain message.");
        }

        // ✅ Simulateur Local d'envoi WhatsApp
        System.out.println("\n🤖 [RECONSTITUTION BOT] Message envoyé au client :\n" + reply);

        // ✅ Envoi physique via Twilio API
        whatsAppSenderService.sendWhatsAppResponse(fromNumber, reply);

        return ResponseEntity.ok("OK");
    }

    // =========================================================
    // 🎙️ EXTRACTION AUDIO VIA WHISPER -> GEMINI
    // =========================================================
    private FormulaireAudio extraireDonneesLocation(String cheminFichier, String telephone) {
        FormulaireAudio formulaire = new FormulaireAudio(telephone);
        try {
            String transcription = openAiWhisperService.transcribeAudio(cheminFichier);

            if (transcription == null || transcription.isEmpty()) {
                return formulaire;
            }

            System.out.println("📝 Transcription : " + transcription);
            formulaire.setTexteComplet(transcription);

            // 🌟 Modification ici : Appel de la méthode Gemini nettoyée
            String jsonGptString = openAiWhisperService.analyserTexteAvecGemini(transcription);

            if (jsonGptString != null && !jsonGptString.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode json = mapper.readTree(jsonGptString);

                if (json.hasNonNull("prenom"))
                    formulaire.setPrenom(json.get("prenom").asText());
                if (json.hasNonNull("nom"))
                    formulaire.setNom(json.get("nom").asText());
                if (json.hasNonNull("typeVoiture"))
                    formulaire.setTypeVoiture(json.get("typeVoiture").asText());
                if (json.hasNonNull("duree"))
                    formulaire.setDuree(json.get("duree").asText());
                if (json.hasNonNull("dateDepart"))
                    formulaire.setDateDepart(json.get("dateDepart").asText());
            }
        } catch (Exception e) {
            System.out.println("❌ Erreur traitement IA (Audio) : " + e.getMessage());
        }
        return formulaire;
    }

    // =========================================================
    // 💬 EXTRACTION TEXTE DIRECT VIA GEMINI
    // =========================================================
    private FormulaireAudio extraireDonneesDepuisTexteDirect(String texte, String telephone) {
        FormulaireAudio formulaire = new FormulaireAudio(telephone);
        formulaire.setTexteComplet(texte);
        try {
            // 🌟 Modification ici : Appel de la méthode Gemini nettoyée
            String jsonGptString = openAiWhisperService.analyserTexteAvecGemini(texte);

            if (jsonGptString != null && !jsonGptString.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode json = mapper.readTree(jsonGptString);

                if (json.hasNonNull("prenom"))
                    formulaire.setPrenom(json.get("prenom").asText());
                if (json.hasNonNull("nom"))
                    formulaire.setNom(json.get("nom").asText());
                if (json.hasNonNull("typeVoiture"))
                    formulaire.setTypeVoiture(json.get("typeVoiture").asText());
                if (json.hasNonNull("duree"))
                    formulaire.setDuree(json.get("duree").asText());
                if (json.hasNonNull("dateDepart"))
                    formulaire.setDateDepart(json.get("dateDepart").asText());
            }
        } catch (Exception e) {
            System.out.println("❌ Erreur traitement IA (Texte) : " + e.getMessage());
        }
        return formulaire;
    }

    // =========================================================
    // 🤖 GESTIONNAIRE D'ÉTAT DES ENREGISTREMENTS
    // =========================================================
    private String genererReponseDepuisData(FormulaireAudio data, String fromNumber) {
        boolean prenomManquant = data.getPrenom() == null || data.getPrenom().isEmpty()
                || "null".equalsIgnoreCase(data.getPrenom());
        boolean nomManquant = data.getNom() == null || data.getNom().isEmpty()
                || "null".equalsIgnoreCase(data.getNom());
        boolean voitureManquante = data.getTypeVoiture() == null || data.getTypeVoiture().isEmpty()
                || "null".equalsIgnoreCase(data.getTypeVoiture());
        boolean dureeManquante = data.getDuree() == null || data.getDuree().isEmpty()
                || "null".equalsIgnoreCase(data.getDuree());
        boolean dateManquante = data.getDateDepart() == null || data.getDateDepart().isEmpty()
                || "null".equalsIgnoreCase(data.getDateDepart());

        // ✅ Si tout est là, on valide l'enregistrement !
        if (!prenomManquant && !nomManquant && !voitureManquante && !dureeManquante && !dateManquante) {
            return "Reservation enregistree ! Client : "
                    + data.getPrenom() + " " + data.getNom()
                    + " - Vehicule : " + data.getTypeVoiture()
                    + " - Date de depart : " + data.getDateDepart()
                    + " - Duree : " + data.getDuree()
                    + " - Tel : " + fromNumber
                    + ". Un agent va vous contacter !";
        }

        // ⏳ Sinon, on relance uniquement sur ce qui manque
        StringBuilder buildReply = new StringBuilder();
        buildReply.append("Robot 🤖 : Il me manque encore quelques détails pour finaliser votre demande :\n");

        if (prenomManquant)
            buildReply.append("❌ Votre prénom\n");
        if (nomManquant)
            buildReply.append("❌ Votre nom de famille\n");
        if (voitureManquante)
            buildReply.append("❌ Le modèle de véhicule souhaité\n");
        if (dateManquante)
            buildReply.append("❌ La date de départ de la location\n");
        if (dureeManquante)
            buildReply.append("❌ La durée totale de location\n");

        buildReply.append("\nVous pouvez me préciser cela par message texte ou par un nouveau message vocal ! 😊");
        return buildReply.toString();
    }
}