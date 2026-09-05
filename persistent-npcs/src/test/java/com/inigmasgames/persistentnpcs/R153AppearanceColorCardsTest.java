package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.ui.*;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.setup.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;

public final class R153AppearanceColorCardsTest {
    public static void main(String[] args) throws Exception {
        AppearanceColorCards renderer = new AppearanceColorCards();
        assert renderer.catalogSize() == 590;
        var purple = new AppearanceColorCards.Request(0,"OVERTOP","LongBeltedJacket","","Purple");
        var green = new AppearanceColorCards.Request(0,"OVERTOP","LongBeltedJacket","","Green");
        var p = renderer.render(purple); var g = renderer.render(green);
        assert p.selectedColor() && g.selectedColor() && !Arrays.equals(p.png(),g.png());
        assert Arrays.equals(p.png(),renderer.render(purple).png()) : "Non-deterministic cache";
        var fallback = renderer.render(new AppearanceColorCards.Request(0,"OVERTOP","LongBeltedJacket","","NotAColor"));
        assert !fallback.selectedColor();
        assert renderer.render(new AppearanceColorCards.Request(0,"OVERTOP","NotAnOption","","Purple")) == null;
        assert renderer.render(new AppearanceColorCards.Request(0,"OVERTOP","LongBeltedJacket","BadVariant","Purple")) == null;
        assert renderer.render(new AppearanceColorCards.Request(0,"../", "../", "", "")) == null;
        var image = ImageIO.read(new ByteArrayInputStream(p.png()));
        assert image.getWidth() == 184 && image.getHeight() == 298;
        // Renderer cache pressure with actual catalog choices, never arbitrary generated RGB.
        var index = com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/appearance-color-sources/index.json"))).getAsJsonObject();
        int rendered = 0;
        for (String key : index.getAsJsonObject("entries").keySet()) {
            String[] parts = key.split(":",2);
            assert renderer.render(new AppearanceColorCards.Request(0,parts[0],parts[1],"","")) != null : key;
            rendered++;
        }
        assert rendered == 590;
        assert renderer.cachedSources() <= 32 && renderer.cachedImages() <= 128 && renderer.cachedBytes() <= 12*1024*1024;

        List<ToClientPacket> packets = new ArrayList<>(), otherPackets = new ArrayList<>();
        var assets = new PrivateAppearanceCardAssets(packets::add);
        var other = new PrivateAppearanceCardAssets(otherPackets::add);
        var paths = assets.publish(List.of(new AppearanceCardJobs.Card(0,p)));
        assert packets.size() == 4 && packets.get(0) instanceof AssetInitialize
                && packets.get(1) instanceof AssetPart && packets.get(2) instanceof AssetFinalize
                && packets.get(3) instanceof RequestCommonAssetsRebuild;
        var initialize = (AssetInitialize)packets.get(0);
        assert initialize.asset.name.equals("UI/Custom/"+paths.get(0));
        assert initialize.size == p.png().length && initialize.asset.hash.length() == 64;
        assert otherPackets.isEmpty() : "Cross-viewer delivery";
        assert !other.publish(List.of(new AppearanceCardJobs.Card(0,p))).get(0).equals(paths.get(0));
        other.release(); assert other.residentNames() == 0;
        assert other.publish(List.of(new AppearanceCardJobs.Card(0,g))).size() == 1 : "Reopen after Back";
        for (int i=0;i<500;i++) assets.publish(List.of(new AppearanceCardJobs.Card(i%128, i%2==0?p:g)));
        assert assets.residentNames() <= 256;
        int before = packets.size();
        assets.close(); assets.close();
        assert assets.residentNames() == 0 && packets.size() == before+2;
        assert packets.get(before) instanceof RemoveAssets;
        assert assets.publish(List.of(new AppearanceCardJobs.Card(0,p))).isEmpty();
        other.close();

        // Invalid batches never partly send or consume private slot names.
        List<ToClientPacket> invalidPackets = new ArrayList<>();
        try (var invalid = new PrivateAppearanceCardAssets(invalidPackets::add)) {
            try { invalid.publish(List.of(new AppearanceCardJobs.Card(128,p))); assert false; }
            catch (IllegalArgumentException expected) { assert invalidPackets.isEmpty(); }
        }

        // Deliberately block a renderer: rapid changes coalesce, old generation never delivers.
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        BlockingQueue<Runnable> world = new LinkedBlockingQueue<>();
        List<AppearanceCardJobs.Batch> delivered = new ArrayList<>();
        try (var jobs = new AppearanceCardJobs(request -> {
            if (calls.incrementAndGet()==1) { entered.countDown(); try { release.await(5,TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
            return request.color().equals("Purple") ? p : g;
        })) {
            jobs.request(List.of(purple),world::add,delivered::add);
            assert entered.await(5,TimeUnit.SECONDS);
            for (int i=0;i<200;i++) jobs.request(List.of(green),world::add,delivered::add);
            release.countDown();
            Runnable queued = world.poll(5,TimeUnit.SECONDS); assert queued != null; queued.run();
            assert calls.get()==2 && delivered.size()==1 && delivered.getFirst().cards().getFirst().image()==g;
            jobs.request(List.of(purple),world::add,delivered::add);
            queued=world.poll(5,TimeUnit.SECONDS); assert queued!=null;
            jobs.invalidate(); queued.run();
            assert delivered.size()==1 : "Category/search/Back stale delivery";
            jobs.request(List.of(purple),world::add,delivered::add);
            queued=world.poll(5,TimeUnit.SECONDS); assert queued!=null;
            jobs.close(); queued.run();
            assert delivered.size()==1 : "Close/disconnect stale delivery";
        }
        String page = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        assert page.contains("appearanceCardJobs.invalidate();") && page.contains("this::closeAppearanceCards");
        assert page.contains("appearanceCardAssets.publish(batch.cards())") && page.contains("setTexturePath(");
        assert page.contains("NpcSkinCodecAdapter.colorId(selected)") && page.contains("NpcSkinCodecAdapter.variantId(selected)");
        for (String file : List.of("AppearanceColorCards", "AppearanceCardJobs", "PrivateAppearanceCardAssets")) {
            String code = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/ui/"+file+".java"));
            for (String forbidden : List.of("EquipmentUpdate", "PlayerSkinUpdate", "InventoryComponent", "ProfileRepository", "sendAssets(", "addCommonAsset(", "sendRemoveAssets("))
                assert !code.contains(forbidden) : file+" has unauthorized dependency "+forbidden;
        }
        System.out.println("R153 PASS: native material cards, 590 catalog, bounded caches/jobs/assets, private packet order/isolation, stale category/Back/close rejection. Client refresh still requires connected approval.");
    }
}
