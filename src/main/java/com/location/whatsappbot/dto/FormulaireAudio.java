package com.location.whatsappbot.dto;

public class FormulaireAudio {
    private String nom;
    private String prenom;
    private String typeVoiture;
    private String duree;
    private String telephone;
    private String texteComplet;
    private String statutAnalyse;
    private String dateDepart;

    // 1. Constructeur par défaut (Obligatoire pour certaines librairies de parsing)
    public FormulaireAudio() {
    }

    // 2. Constructeur avec téléphone
    public FormulaireAudio(String telephone) {
        this.telephone = telephone;
    }

    // ==========================================
    // GETTERS ET SETTERS (Tous corrigés et complets)
    // ==========================================

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getTypeVoiture() {
        return typeVoiture;
    }

    public void setTypeVoiture(String typeVoiture) {
        this.typeVoiture = typeVoiture;
    }

    public String getDuree() {
        return duree;
    }

    public void setDuree(String duree) {
        this.duree = duree;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getTexteComplet() {
        return texteComplet;
    }

    public void setTexteComplet(String texteComplet) {
        this.texteComplet = texteComplet;
    }

    public String getStatutAnalyse() {
        return statutAnalyse;
    }

    public void setStatutAnalyse(String statutAnalyse) {
        this.statutAnalyse = statutAnalyse;
    }

    // 🔥 Correction ici : Affectation correcte du paramètre à la variable de classe
    public void setDateDepart(String dateDepart) {
        this.dateDepart = dateDepart;
    }

    // 🔥 Correction ici : Retourne la vraie valeur stockée
    public String getDateDepart() {
        return dateDepart;
    }
}