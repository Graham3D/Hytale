package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.ui.*;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.setup.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;

/** Client texture footprint and rebuild churn gate, in addition to server cache limits. */
public final class R154AppearanceAtlasBudgetTest {
    public static void main(String[] args) throws Exception {
        var renderer = new AppearanceColorCards();
        var request = new AppearanceColorCards.Request(0,"OVERTOP","LongBeltedJacket","","Purple");
        var purple = renderer.render(request);
        var green = renderer.render(new AppearanceColorCards.Request(0,"OVERTOP","LongBeltedJacket","","Green"));
        List<ToClientPacket> packets = new ArrayList<>();
        var assets = new PrivateAppearanceCardAssets(packets::add);
        var hairSized = new ArrayList<AppearanceCardJobs.Card>();
        for (int slot=0;slot<112;slot++) hairSized.add(new AppearanceCardJobs.Card(slot,purple));
        var paths = assets.publish(hairSized);
        assert assets.residentNames()==112 && assets.lastUploaded()==112 && assets.lastRebuild();
        assert assets.residentTexels()==112L*92*149;
        int before = packets.size();
        for (int i=0;i<100;i++) assert assets.publish(hairSized).equals(paths);
        assert packets.size()==before && !assets.lastRebuild() && assets.lastUploaded()==0 : "Identical cards rebuilt atlas";
        var changed = new ArrayList<>(hairSized);
        changed.set(10,new AppearanceCardJobs.Card(10,green));
        assert assets.publish(changed).equals(paths) : "Color change invented a second slot bank";
        assert assets.residentNames()==112 && assets.lastUploaded()==1 && assets.lastRebuild();
        assert packets.size()==before+4 : "Single changed texture must be one asset triplet + one rebuild";
        before=packets.size();
        assets.publish(changed.subList(0,2));
        assert assets.residentNames()==2 && assets.lastRemoved()==110 && assets.lastUploaded()==0;
        assert packets.get(before) instanceof RemoveAssets && packets.get(before+1) instanceof RequestCommonAssetsRebuild;
        assert packets.size()==before+2;
        assets.publish(List.of()); assert assets.residentNames()==0;
        before=packets.size(); assets.publish(List.of()); assert packets.size()==before;
        byte[] oversizedDimensions=purple.png().clone();
        java.nio.ByteBuffer.wrap(oversizedDimensions).putInt(16,184);
        try {
            assets.publish(List.of(new AppearanceCardJobs.Card(0,new AppearanceColorCards.Rendered(oversizedDimensions,true,"Purple"))));
            assert false : "Unbudgeted dimensions accepted";
        } catch (IllegalArgumentException expected) { assert packets.size()==before; }
        assets.close();

        Path thumbs=Path.of("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearance/Thumbnails");
        long texels=0; int count=0;
        for (String line:Files.readAllLines(thumbs.resolve("index.tsv"))) {
            var png=ImageIO.read(thumbs.resolve(line.split("\t")[1]).toFile());
            assert png.getWidth()==92 && png.getHeight()==149;
            texels+=(long)png.getWidth()*png.getHeight(); count++;
        }
        assert count==590 && texels==8_087_720L;
        long maxCards=texels+128L*92*149;
        long oldCards=(590L+256)*184*298;
        assert maxCards==9_842_344L && maxCards*100/oldCards < 22 : "Insufficient client texel reduction";
        assert (590L+128)*128*256 < 24L*1024*1024 : "Conservative power-of-two rectangle budget";

        // Rapid requests before the debounce expires render only the final request.
        AtomicInteger renders=new AtomicInteger();
        BlockingQueue<Runnable> world=new LinkedBlockingQueue<>();
        List<AppearanceCardJobs.Batch> batches=new ArrayList<>();
        try(var jobs=new AppearanceCardJobs(r -> {renders.incrementAndGet(); return purple;})) {
            for(int i=0;i<100;i++) jobs.request(List.of(request),world::add,batches::add);
            Runnable done=world.poll(5,TimeUnit.SECONDS); assert done!=null; done.run();
            assert renders.get()==1 && batches.size()==1;
            jobs.request(List.of(request),world::add,batches::add); jobs.invalidate();
            assert world.poll(350,TimeUnit.MILLISECONDS)==null && renders.get()==1;
        }
        System.out.println("R154 PASS: 92x149 client textures, <22% old card texels, one bank, 100 unchanged batches/zero packets, single-change upload, category eviction, dimension guard, rapid selection coalescing.");
    }
}
