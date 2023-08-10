package com.virex.voiceident.models;

import com.google.gson.annotations.SerializedName;

public class ModelInfo {
    @SerializedName("version")
    public String version;
    @SerializedName("type")
    public String type;
    @SerializedName("uuid")
    public String uuid;

    //присваивается в момент синхронизации assets -> externalstorage
    public String absolutePath;
}
