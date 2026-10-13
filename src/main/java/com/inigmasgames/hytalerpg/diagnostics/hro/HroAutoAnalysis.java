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
                if(Files.size(baseline)>1024*1024)throw new IOException("BASELINE_SIZE_LIMIT");
                JsonObject document=HroOffline.object(baseline);
                JsonObject recipe=document.getAsJsonObject("recipe");if(recipe==null)throw new IOException("BASELINE_RECIPE_MISSING");
                Files.createDirectories(output);
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
        Files.writeString(bundle.resolve("auto-analysis.json"),HroBundle.JSON.toJson(analysis)+"\n",StandardCharsets.UTF_8);
        Files.writeString(bundle.resolve("compatibility-diff.json"),HroBundle.JSON.toJson(Map.of("comparison","AUTOMATIC_APPROVED_BASELINES",
                "results",comparisons,"state",state))+"\n",StandardCharsets.UTF_8);
        String extra="\nAutomatic analysis: "+state+"; approved baselines found: "+baselines.size()+
                ". Detailed comparison results: auto-analysis.json and comparisons/. " +
                "CLIENT_PERFORMANCE_UNVERIFIED without supported client measurements.\n";
        Files.writeString(bundle.resolve("summary.md"),extra,StandardCharsets.UTF_8,StandardOpenOption.APPEND);
        HroBundle.checksums(bundle);
        HroTool.verifyChecksums(bundle);
        return state;
    }
}
