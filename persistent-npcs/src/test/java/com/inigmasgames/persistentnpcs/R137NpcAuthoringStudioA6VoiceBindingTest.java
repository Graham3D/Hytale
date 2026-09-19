package com.inigmasgames.persistentnpcs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Regression gate for the compact A6 recorder's client-visible event bindings. */
public final class R137NpcAuthoringStudioA6VoiceBindingTest {
    private static final Pattern SELECTOR = Pattern.compile("\\\"(#[A-Za-z0-9]+)\\\"");

    private R137NpcAuthoringStudioA6VoiceBindingTest() { }

    public static void main(String[] args) throws Exception {
        String page = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/ui/NpcProfilePage.java"));
        String ui = Files.readString(Path.of("src/main/resources/Common/UI/Custom/Pages/"
                + "ImmersiveNpcProfile.ui"));
        int start = page.indexOf("private void bindVoiceRecorderEvents");
        int end = page.indexOf("\n    private void", start + 1);
        assert start >= 0 && end > start : "Voice recorder binding method was not found";
        String bindings = page.substring(start, end);
        Matcher matcher = SELECTOR.matcher(bindings);
        int checked = 0;
        while (matcher.find()) {
            String selector = matcher.group(1);
            if (selector.equals("#VoiceEmotion")) {
                for (String emotion : List.of("REFERENCE", "AFFECTIONATE", "AMUSED",
                        "EXCITED", "ANGRY", "SAD", "SCARED")) {
                    assert ui.contains(selector + emotion + " ")
                            || ui.contains(selector + emotion + " {")
                            : "Recorder event binding targets missing emotion selector: "
                                    + selector + emotion;
                }
            } else {
                assert ui.contains(selector + " ") || ui.contains(selector + " {")
                        : "Recorder event binding targets missing UI selector: " + selector;
            }
            checked++;
        }
        assert checked == 9 : "Unexpected recorder event-binding declaration count: " + checked;
        assert !bindings.contains("#VoiceDeleteSavedButton")
                : "Removed legacy delete button must not retain an event binding";
        assert bindings.contains("#VoiceDeleteButton")
                && bindings.contains("#VoiceDeleteSavedConfirmButton")
                && bindings.contains("#VoiceDeleteSavedCancelButton")
                : "Compact delete control and confirmation bindings must remain present";
        System.out.println("R137 A6 compact recorder event-binding gate passed: selectors="
                + checked);
    }
}
