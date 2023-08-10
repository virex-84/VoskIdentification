package com.virex.voiceident.ui.home;

public class SpkUser {
    public String name;
    public Double[] spk;

    public double lastDist = 0;
    public double lastSpkFrames = 0;
    public String lastText = "";

    public boolean isNowSpeak = false;

    public SpkUser(String name, Double[] spk, Double spk_frames, String text, boolean isNowSpeak){
        this.name = name;
        this.spk = spk;
        this.lastSpkFrames = spk_frames;
        this.lastText = text;
        this.isNowSpeak = isNowSpeak;
    }
}
