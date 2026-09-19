package com.inigmasgames.canvasui.rendering;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/** Focused native text entry layered over the passive graph HUD. */
public final class CanvasGraphSearchPage extends InteractiveCustomUIPage<CanvasGraphSearchPage.Data> {
    private final String initial;
    private final Consumer<String> changed;
    private final Runnable dismissed;

    public CanvasGraphSearchPage(PlayerRef playerRef,String initial,Consumer<String> changed,Runnable dismissed){
        super(playerRef,CustomPageLifetime.CanDismiss,Data.CODEC);
        this.initial=initial==null?"":initial;this.changed=changed;this.dismissed=dismissed;
    }

    @Override public void build(@Nonnull Ref<EntityStore> ref,@Nonnull UICommandBuilder commands,
                                @Nonnull UIEventBuilder events,@Nonnull Store<EntityStore> store){
        commands.append("CanvasGraphSearchPage.ui");
        commands.set("#GraphSearchInput.Value",initial);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged,"#GraphSearchInput",
                new EventData().append("Action","change").append("@Value","#GraphSearchInput.Value"),false);
        events.addEventBinding(CustomUIEventBindingType.Activating,"#GraphSearchDone",
                new EventData().append("Action","done"),false);
        events.addEventBinding(CustomUIEventBindingType.Activating,"#GraphSearchClear",
                new EventData().append("Action","clear"),false);
    }

    @Override public void handleDataEvent(@Nonnull Ref<EntityStore> ref,@Nonnull Store<EntityStore> store,Data data){
        if("change".equals(data.action)){changed.accept(data.value);return;}
        if("clear".equals(data.action)){changed.accept("");UICommandBuilder update=new UICommandBuilder();update.set("#GraphSearchInput.Value","");sendUpdate(update,false);return;}
        if("done".equals(data.action))close();
    }

    @Override public void onDismiss(@Nonnull Ref<EntityStore> ref,@Nonnull Store<EntityStore> store){dismissed.run();}
    public void closeFromService(){close();}

    public static final class Data{
        static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
                .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
                .append(new KeyedCodec<>("@Value",Codec.STRING),(d,v)->d.value=v,d->d.value).add().build();
        private String action=""; private String value="";
    }
}
