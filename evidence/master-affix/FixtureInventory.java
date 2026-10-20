import com.inigmasgames.hytalerpg.gear.*;
import com.google.gson.GsonBuilder;
import java.util.*;
class FixtureInventory {
  public static void main(String[] args) {
    var qa = new GearAffixQaSuite(GearCatalog.load());
    var rows = qa.fixtures().stream().map(f -> Map.of(
      "id", f.fixtureId(), "base", f.itemBaseId(), "affixes", f.affixIds(),
      "spawnable", qa.spawnable(f))).toList();
    System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
      "total", rows.size(), "spawnable", qa.fixtures().stream().filter(qa::spawnable).count(),
      "pairs", qa.fixtures().stream().filter(f -> f.fixtureId().endsWith("-affixed")).count(),
      "combined", qa.fixtures().stream().filter(f -> f.fixtureId().startsWith("combo-")).count(),
      "fixtures", rows)));
  }
}
