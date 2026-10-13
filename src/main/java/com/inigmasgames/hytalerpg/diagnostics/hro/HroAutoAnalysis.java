package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Worker-only integrity and optional approved-baseline pass. Never approves a new baseline. */
final class HroAutoAnalysis {
    static final long BUDGET_NANOS=30_000_000_000L;
    private HroAutoAnalysis() { }
    static String finish(Path root,Path bundle,LongSupplier clock)throws IOException {
        long began=clock.getAsLong();
        HroTool.verifyChecksums(bundle);
        Path approved=root.resolve("approved-baselines");
        List<Path> baselines;
        if(Files.isDirectory(approved)&&!Files.isSymbolicLink(approved))try(var stream=Files.list(approved)) {
            baselines=stream.filter(p->p.getFileName().toString().endsWith(".json")&&Files.isRegularFile(p)&&!Files.isSymbolicLink(p))
                    .sorted().limit(18).toList();
        }else baselines=List.of();
        List<Map<String,String>> comparisons=new ArrayList<>();
        String state="COMPLETE";
        if(baselines.size()>17){state="INCOMPLETE_BASELINE_LIMIT";baselines=baselines.subList(0,17);}
        Path output=bundle.resolve("comparisons");
        for(Path baseline:baselines) {
            if(clock.getAsLong()-began>=BUDGET_NANOS){state="PENDING_ANALYSIS_BUDGET";break;}
            String name=Objects.requireNonNull(baseline.getFileName()).toString();
            try {
                if(!name.matches("[A-Za-z0-9_.-]{1,80}\\.json"))throw new IOException("BASELINE_NAME_INVALID");
                if(Files.size(baseline)>1024*1024)throw new IOException("BASELINE_SIZE_LIMIT");
                JsonObject document=HroOffline.object(baseline);
                JsonObject recipe=document.getAsJsonObject("recipe");if(recipe==null)throw new IOException("BASELINE_RECIPE_MISSING");
                if(Files.isSymbolicLink(output))throw new IOException("ANALYSIS_OUTPUT_LINK");
                Files.createDirectories(output);
                if(!output.toRealPath().startsWith(bundle.toRealPath()))throw new IOException("ANALYSIS_OUTPUT_ESCAPE");
                Path report=output.resolve(name);
                HroComparison.compare(bundle,baseline,recipe,report,clock,began+BUDGET_NANOS);
                JsonObject result=HroOffline.object(report);
                comparisons.add(Map.of("baseline",name,"contract",HroOffline.value(result,"contract"),
                        "classification",HroOffline.value(result,"comparison")));
            }catch(HroComparison.AnalysisBudgetExceeded deadline){state="PENDING_ANALYSIS_BUDGET";break;
            }catch(IOException|RuntimeException failure){state="INCOMPLETE_COMPARISON";
                comparisons.add(Map.of("baseline",name,"classification","INCOMPARABLE","reason",failure.getClass().getSimpleName()));}
        }
        var analysis=new LinkedHashMap<String,Object>();analysis.put("state",state);analysis.put("baselineCount",baselines.size());
        analysis.put("comparisons",comparisons);analysis.put("integrity","CHECKSUMS_VERIFIED");
        analysis.put("elapsedNanos",Math.max(0,clock.getAsLong()-began));
        Files.writeString(bundle.resolve("auto-analysis.json"),HroBundle.JSON.toJson(analysis)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        writeSidecarChecksums(bundle);
        verifySidecarChecksums(bundle);
        HroTool.verifyChecksums(bundle);
        return state;
    }
    private static void writeSidecarChecksums(Path bundle)throws IOException {
        var files=new ArrayList<Path>();files.add(bundle.resolve("auto-analysis.json"));
        Path comparisons=bundle.resolve("comparisons");
        if(Files.isSymbolicLink(comparisons))throw new IOException("ANALYSIS_OUTPUT_LINK");
        if(Files.isDirectory(comparisons))try(var listed=Files.list(comparisons)){
            files.addAll(listed.filter(Files::isRegularFile).sorted().limit(18).toList());
        }
        if(files.size()>18)throw new IOException("ANALYSIS_REPORT_LIMIT");
        var lines=new StringBuilder();
        for(Path file:files){if(Files.isSymbolicLink(file))throw new IOException("ANALYSIS_LINK_REJECTED");
            lines.append(HroBundle.sha256(file)).append("  ").append(bundle.relativize(file).toString().replace('\\','/')).append('\n');}
        Files.writeString(bundle.resolve("analysis-checksums.sha256"),lines,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    static void verifySidecarChecksums(Path bundle)throws IOException {
        Path index=bundle.resolve("analysis-checksums.sha256");
        if(Files.size(index)>8192)throw new IOException("ANALYSIS_CHECKSUM_LIMIT");
        var lines=Files.readAllLines(index,StandardCharsets.UTF_8);
        if(lines.isEmpty()||lines.size()>18)throw new IOException("ANALYSIS_CHECKSUM_LIMIT");
        var verified=new HashSet<Path>();
        for(String line:lines){String[] fields=line.split("  ",2);
            if(fields.length!=2||!fields[0].matches("[0-9a-f]{64}")||
                    !(fields[1].equals("auto-analysis.json")||fields[1].matches("comparisons/[A-Za-z0-9_.-]{1,80}\\.json")))
                throw new IOException("ANALYSIS_CHECKSUM_FORMAT");
            Path file=bundle.resolve(fields[1]).normalize();
            if(!file.startsWith(bundle)||Files.isSymbolicLink(file)||Files.size(file)>1024*1024||!verified.add(file)
                    ||!fields[0].equals(HroBundle.sha256(file)))throw new IOException("ANALYSIS_CHECKSUM_MISMATCH");
        }
        if(!verified.contains(bundle.resolve("auto-analysis.json")))throw new IOException("ANALYSIS_STATUS_UNCHECKED");
    }
}
