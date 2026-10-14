package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.ui.hud.EnemyTargetLayout;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Me12TargetLayoutTest {
    @Test void P08EightActualAffixesRenderWithoutClippingAcrossResolutionAndScaleFixtures()throws Exception{
        var fixture=Me12PersistenceTest.fixture(true);var birth=fixture.root().plan();var actor=birth.actors().getFirst();var pack=birth.pack();
        var dto=EnemyDisplayDto.project(actor,fixture.policies().registry(),EnemyAffixSnapshot.resolve(actor,fixture.policies().balance(),pack,0,true,false),pack,0,
                actor.v2().plan().displayName(),"Trork Warrior",Set.of("STUN_STAGGER","SLOW_MOVEMENT"),false,100);
        assertEquals(8,dto.ownAffixTags().size());assertEquals(2,dto.ownAffixTags().stream().filter(t->t.labelParameters().containsKey("signature")).count());
        // Presentation stress fixture only: add a second immunity indicator without granting it to the actor.
        // The legal eight-affix plan above has one immunity; this is not a combat/certification fixture.
        var defenses=new ArrayList<>(dto.activeDefenseTags());
        defenses.add(new EnemyDisplayDto.EnemyTag("damage-immunity/WATER",EnemyDisplayDto.SourceKind.DAMAGE_IMMUNITY,"WATER","fixture.water",Map.of(),"Water Immune",EnemyDisplayDto.Style.IMMUNITY,220,"fixture.water"));
        dto=new EnemyDisplayDto(dto.worldId(),dto.logicalActorId(),dto.generation(),dto.descriptorRevision(),dto.stateRevision(),dto.name(),dto.baseRoleDisplayName(),dto.combatLevel(),dto.rarityLabel(),dto.packRoleLabel(),dto.ownAffixTags(),dto.inheritedEffectTags(),defenses,dto.stateTags(),dto.remainingGuardCount(),dto.paletteVariantId(),dto.currentHealthRef(),dto.shieldRef());
        assertEquals(2,dto.activeDefenseTags().stream().filter(t->t.sourceKind()==EnemyDisplayDto.SourceKind.DAMAGE_IMMUNITY).count());
        assertTrue(dto.activeDefenseTags().stream().anyMatch(t->t.sourceKind()==EnemyDisplayDto.SourceKind.PROTECTION));
        var layout=EnemyTargetLayout.of(dto);var folder=Path.of("build/reports/me12/hud");Files.createDirectories(folder);
        var evidence=new ArrayList<Map<String,Object>>();
        for(double[] dimensions:new double[][]{{1280,720,1},{1280,720,1.5},{1920,1080,1.25},{1920,1080,2},{2560,1440,1.5}}){
            int width=(int)dimensions[0],height=(int)dimensions[1];double scale=dimensions[2];
            assertTrue((layout.height()+16)*scale<=height,"Target text exceeds virtual height at "+Arrays.toString(dimensions));
            var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();
            try{
                graphics.setColor(new Color(28,32,36));graphics.fillRect(0,0,width,height);graphics.scale(scale,scale);
                int left=(int)(width/scale)-EnemyTargetLayout.WIDTH-16;graphics.translate(left,16);
                graphics.setColor(new Color(10,14,18));graphics.fillRect(0,0,EnemyTargetLayout.WIDTH,layout.height());
                graphics.setFont(new Font(Font.SANS_SERIF,Font.BOLD,14));graphics.setColor(new Color(255,210,74));graphics.drawString(layout.name(),10,17);
                assertTrue(graphics.getFontMetrics().stringWidth(layout.name())<=EnemyTargetLayout.WIDTH-20);
                graphics.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,EnemyTargetLayout.FONT));graphics.setColor(Color.WHITE);graphics.drawString(layout.identity(),10,37);
                graphics.setColor(new Color(140,30,30));graphics.fillRect(10,44,460,12);graphics.setColor(Color.WHITE);
                int y=64+EnemyTargetLayout.FONT;
                for(String line:layout.lines()){assertTrue(graphics.getFontMetrics().stringWidth(line)<=EnemyTargetLayout.WIDTH-20,line);graphics.drawString(line,10,y);y+=EnemyTargetLayout.LINE;}
                assertTrue(y-EnemyTargetLayout.LINE+graphics.getFontMetrics().getDescent()<=layout.height());
            }finally{graphics.dispose();}
            String file=width+"x"+height+"-scale-"+scale+".png";ImageIO.write(image,"png",folder.resolve(file).toFile());
            evidence.add(Map.of("file",file,"width",width,"height",height,"uiScale",scale,"rows",layout.lines().size(),"virtualHeight",layout.height(),"ownAffixes",8,"nativeRenderingVerified",false));
        }
        Files.writeString(folder.resolve("fixtures.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "evidence","SYNTHETIC_OFFLINE_JAVA2D; legal eight-affix text plus synthetic second immunity indicator; not a certified combat combination or native Hytale rendering", "fixtures",evidence)));
        String text=String.join(" ",layout.lines());for(var tag:dto.ownAffixTags())assertTrue(text.contains(tag.fallbackText()));
        assertFalse(Files.readString(Path.of("src/main/resources/Common/UI/Custom/RpgEnemyTarget.ui")).contains("WrapMaxLines: 2"));
    }
}
