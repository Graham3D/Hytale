package com.inigmasgames.hytalerpg.diagnostics.hro;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class HroLegacyAndParityTest {
    @TempDir Path root;
    @ParameterizedTest @ValueSource(strings={"skill-v1","ui-v1","gear-v1","combat-v1","spawn-v1"})
    void explicitParsersPreserveLocatorsAndOmitPrivateData(String format)throws Exception {
        String field=switch(format){case "skill-v1","ui-v1"->"eventType";case "gear-v1"->"event";case "combat-v1"->"hitId";default->"stage";};
        Path source=root.resolve("old.jsonl.gz"),output=root.resolve("import.json");
        try(var stream=new GZIPOutputStream(Files.newOutputStream(source))) {stream.write(("{\""+field+"\":\"TEST\",\"password\":\"private-secret\",\"correlationId\":\"uuid\",\"details\":{\"skillId\":\"test\",\"chat\":\"private-secret\"}}\n{partial").getBytes(StandardCharsets.UTF_8));}
        String hash=HroBundle.sha256(source);HroLegacy.importFile(source,format,output,null);
        String result=Files.readString(output);assertFalse(result.contains("private-secret"));assertEquals(hash,HroBundle.sha256(source));
        assertTrue(result.contains("\"sourceOffset\":0"));assertTrue(result.contains("\"partialTail\":true"));assertTrue(result.contains("INCONCLUSIVE"));
        assertThrows(FileAlreadyExistsException.class,()->HroLegacy.importFile(source,format,output,null));
    }
    @Test void recoveredVerifiedManifestCannotClaimOriginalBuild()throws Exception {
        Path source=root.resolve("old.jsonl"),manifest=root.resolve("manifest.json"),output=root.resolve("result.json");
        Files.writeString(source,"{\"eventType\":\"SKILL_COMMITTED\",\"buildVersion\":\"old\"}\n{\"eventType\":\"OTHER\",\"buildVersion\":\"new\"}\n");
        Files.writeString(manifest,HroBundle.JSON.toJson(Map.of("schemaVersion",1,"archiveState","VERIFIED","buildVersion","recovery-binary","uncompressedSha256",HroBundle.sha256(source))));
        HroLegacy.importFile(source,"skill-v1",output,manifest);var result=HroOffline.object(output);
        assertTrue(HroOffline.value(result,"captureBuild").startsWith("UNKNOWN"));assertTrue(result.get("mixedBuilds").getAsBoolean());
        Files.writeString(source,"changed");assertThrows(IOException.class,()->HroLegacy.importFile(source,"skill-v1",root.resolve("bad.json"),manifest));
    }
    @Test void rejectsNestedAndOverlongAndUnsupportedInputsAndSaveOutputs()throws Exception {
        Path source=root.resolve("input.jsonl");Files.writeString(source,"{\"eventType\":\"x\",\"deep\":"+"[".repeat(18)+"0"+"]".repeat(18)+"}\n");
        assertThrows(IOException.class,()->HroLegacy.importFile(source,"ui-v1",root.resolve("nested.json"),null));
        Files.writeString(source,"x".repeat(65537));assertThrows(IOException.class,()->HroLegacy.importFile(source,"ui-v1",root.resolve("long.json"),null));
        assertThrows(IOException.class,()->HroLegacy.importFile(source,"guess",root.resolve("guess.json"),null));
        Path saves=Files.createDirectory(root.resolve("Saves"));assertThrows(IOException.class,()->HroOffline.create(saves.resolve("new.json"),Map.of()));
    }
    @Test void importCapsRowsWithVisibleOmissions()throws Exception {
        Path source=root.resolve("many.jsonl");Files.writeString(source,"{\"event\":\"EQUIPPED\"}\n".repeat(300));
        HroLegacy.importFile(source,"gear-v1",root.resolve("result.json"),null);
        var result=HroOffline.object(root.resolve("result.json"));assertEquals(256,result.getAsJsonArray("events").size());assertEquals(44,result.get("omittedEvents").getAsInt());
    }
    @Test void uiFixturesAreImmutableAndDetectCommandsValidationStateAndInformationDrift() {
        var fixtures=HroUiParity.fixtures();assertEquals(15,fixtures.size());
        assertThrows(UnsupportedOperationException.class,()->fixtures.getFirst().intent().put("mutate","no"));
        assertTrue(HroUiParity.compare(fixtures,HroUiParity.Fixture::expected,HroUiParity.Fixture::expected).stream().allMatch(p->p.outcome().equals("PARITY")));
        for(int changed=0;changed<4;changed++) {
            final int dimension=changed;
            var parity=HroUiParity.compare(fixtures,HroUiParity.Fixture::expected,fixture->{var expected=fixture.expected();return new HroUiParity.Result(
                    dimension==0?List.of("extra-authoritative-command"):expected.submittedCommands(),dimension==1?"INCORRECT":expected.validation(),
                    dimension==2?Map.of("wrong","state"):expected.stateDelta(),dimension==3?Map.of("missing","view"):expected.projection());});
            assertTrue(parity.stream().allMatch(p->p.outcome().equals("DIVERGENCE")));
        }
        var wrong=HroUiParity.compare(fixtures,f->new HroUiParity.Result(List.of(),"bad",Map.of(),Map.of()),HroUiParity.Fixture::expected);
        assertTrue(wrong.stream().allMatch(p->p.outcome().equals("REFERENCE_FIXTURE_MISMATCH")));
    }
    @Test void sourceMapAnchorsExistAndCoverAllContracts()throws Exception {
        assertEquals(Set.copyOf(HroContracts.IDS),HroMaps.implementation().stream().map(row->row.get("contract")).collect(java.util.stream.Collectors.toSet()));
        for(var row:HroMaps.implementation())assertTrue(Files.readString(Path.of(row.get("path"))).contains(row.get("symbol")),row.toString());
    }
    @Test void nativeFingerprintsPreserveDescriptorsAndSemanticJsonChanges()throws Exception {
        byte[] code;try(var stream=HroUiParity.class.getResourceAsStream("HroUiParity.class")){code=Objects.requireNonNull(stream).readAllBytes();}
        assertTrue(HroFingerprint.signatures(code).stream().anyMatch(s->s.contains("compare:")));
        assertThrows(IOException.class,()->HroFingerprint.signatures(new byte[8]));
        Path spec=root.resolve("bindings.json");Files.writeString(spec,HroBundle.JSON.toJson(Map.of("schemaVersion",1,"bindings",List.of(
                Map.of("entry","native.class","contract","UI.LIFECYCLE","owner","Hywind"),Map.of("entry","asset.json","contract","SKILL.PROJECTION","owner","Hywind"),
                Map.of("entry","missing.class","contract","PLAYER.ADMISSION","owner","Hywind")))));
        for(int i=0;i<3;i++) {
            Path archive=root.resolve("native"+i+".zip");try(var zip=new ZipOutputStream(Files.newOutputStream(archive))) {
                zip.putNextEntry(new ZipEntry("native.class"));zip.write(code);zip.closeEntry();zip.putNextEntry(new ZipEntry("asset.json"));
                zip.write((i==0?"{\"Parent\":\"base\",\"Value\":1}":i==1?"{\"Value\":1,\"Parent\":\"base\"}":"{\"Value\":2,\"Parent\":\"base\"}").getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }HroFingerprint.capture(archive,spec,root.resolve("fingerprint"+i+".json"));
        }
        HroFingerprint.compare(root.resolve("fingerprint0.json"),root.resolve("fingerprint1.json"),root.resolve("equal.json"));
        assertFalse(Files.readString(root.resolve("equal.json")).contains("ADAPT_NATIVE_BOUNDARY"));
        HroFingerprint.compare(root.resolve("fingerprint1.json"),root.resolve("fingerprint2.json"),root.resolve("changed.json"));
        assertTrue(Files.readString(root.resolve("changed.json")).contains("ADAPT_NATIVE_BOUNDARY"));
    }
}
