package com.inigmasgames.persistentnpcs;

import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** R153 is retained only as a regression marker: its private-image architecture is prohibited. */
public final class R153AppearanceColorCardsTest {
    public static void main(String[] args) throws Exception {
        Path sourceRoot=Path.of("src/main/java/com/inigmasgames/persistentnpcs");
        String all;
        try(var files=Files.walk(sourceRoot)) {
            all=files.filter(path -> path.toString().endsWith(".java")).map(path -> {
                try{return Files.readString(path);}catch(Exception failure){throw new RuntimeException(failure);}
            }).collect(java.util.stream.Collectors.joining("\n"));
        }
        for(String retired:List.of("class AppearanceColorCards", "class AppearanceCardJobs",
                "class PrivateAppearanceCardAssets", "new AssetInitialize(",
                "new AssetPart(", "new AssetFinalize(", "new RemoveAssets(",
                "new RequestCommonAssetsRebuild(")) assert !all.contains(retired):retired;
        Path jar;
        try(var files=Files.list(Path.of("dist"))) {
            jar=files.filter(path -> path.getFileName().toString().contains("R156"))
                    .findFirst().orElseThrow();
        }
        try(JarFile archive=new JarFile(jar.toFile())) {
            List<String> entries=archive.stream().map(java.util.zip.ZipEntry::getName).toList();
            for(String name:entries) {
                assert !name.startsWith("appearance-color-sources/"):name;
                assert !name.startsWith("Common/UI/Custom/Pages/ImmersiveNpcAppearance/Thumbnails/"):name;
                assert !name.equals("Common/UI/Custom/Pages/ImmersiveNpcAppearanceThumbnails.ui"):name;
                assert !name.endsWith("AppearanceColorCards.class") && !name.endsWith("AppearanceCardJobs.class")
                        && !name.endsWith("PrivateAppearanceCardAssets.class"):name;
            }
        }
        System.out.println("R153 superseded safely: private PNG generation/upload classes and all catalog thumbnail resources are absent from production.");
    }
}
