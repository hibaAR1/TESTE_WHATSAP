package com.location.whatsappbot.dto;

import java.util.List;

public class WhatsAppWebhookPayload {

    private List<WhatsAppResult> results;

    public List<WhatsAppResult> getResults() {
        return results;
    }

    public void setResults(List<WhatsAppResult> results) {
        this.results = results;
    }

    public static class WhatsAppResult {
        private String from;
        private WhatsAppMessage message;

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public WhatsAppMessage getMessage() {
            return message;
        }

        public void setMessage(WhatsAppMessage message) {
            this.message = message;
        }
    }

    public static class WhatsAppMessage {
        private String text;
        private String type;
        private WhatsAppMedia media; // ✅ "media" correspond au JSON Infobip

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public WhatsAppMedia getMedia() {
            return media;
        }

        public void setMedia(WhatsAppMedia media) {
            this.media = media;
        }
    }

    public static class WhatsAppMedia {
        private String id;
        private String fileUrl;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getFileUrl() {
            return fileUrl;
        }

        public void setFileUrl(String fileUrl) {
            this.fileUrl = fileUrl;
        }
    }
}