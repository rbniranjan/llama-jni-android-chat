package com.sx.llama.jni;

public class ModelInfo {
    public final String id;
    public final String name;
    public final String publisher;
    public final String format;
    public final String quant;
    public final String promptFormatHint;
    public final String filename;
    public final long catalogSizeBytes;
    public final String url;

    public boolean downloaded;
    public boolean downloading;
    public int downloadProgress;
    public String localPath;
    public long downloadedAt;
    public long persistedSizeBytes;
    public long downloadRequestId;

    public ModelInfo(String id,
                     String name,
                     String publisher,
                     String format,
                     String quant,
                     String promptFormatHint,
                     String filename,
                     long catalogSizeBytes,
                     String url) {
        this.id = id;
        this.name = name;
        this.publisher = publisher;
        this.format = format;
        this.quant = quant;
        this.promptFormatHint = promptFormatHint;
        this.filename = filename;
        this.catalogSizeBytes = catalogSizeBytes;
        this.url = url;
        this.downloaded = false;
        this.downloading = false;
        this.downloadProgress = 0;
        this.localPath = "";
        this.downloadedAt = 0L;
        this.persistedSizeBytes = 0L;
        this.downloadRequestId = -1L;
    }
}
