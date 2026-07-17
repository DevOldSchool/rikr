package org.de.rikr.service;

public record SearchQuery(String text, boolean matchCase, boolean matchWord) {
    public boolean isUsable() {
        return text != null && text.length() >= 2;
    }
}
