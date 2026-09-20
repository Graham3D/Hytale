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
import com.inigmasgames.canvasui.api.editor.SkillTreeViewModel;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Native Search Mode: the same editor projection with graph mutation intentionally paused. */
public final class CanvasGraphSearchPage extends InteractiveCustomUIPage<CanvasGraphSearchPage.Data> {
    private final Supplier<SkillTreeViewModel> view;
    private final BiFunction<String,String,SkillTreeViewModel> action;
    private final Runnable dismissed;

    public CanvasGraphSearchPage(PlayerRef playerRef, Supplier<SkillTreeViewModel> view,
                                 BiFunction<String,String,SkillTreeViewModel> action, Runnable dismissed) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.view=Objects.requireNonNull(view);this.action=Objects.requireNonNull(action);
        this.dismissed=Objects.requireNonNull(dismissed);
    }

    @Override public void build(@Nonnull Ref<EntityStore> ref,@Nonnull UICommandBuilder commands,
                                @Nonnull UIEventBuilder events,@Nonnull Store<EntityStore> store) {
        commands.append("CanvasGraphEditorHud.ui");
        commands.append("CanvasGraphSearchPage.ui");
        SkillTreeViewModel frame=view.get();
        CanvasGraphEditorHud.writeFrame(commands,frame,null,null);
        commands.set("#GraphSearchInput.Value",frame.query());
        events.addEventBinding(CustomUIEventBindingType.ValueChanged,"#GraphSearchInput",
                new EventData().append("Action","change").append("@Value","#GraphSearchInput.Value"),false);
    }

    @Override public void handleDataEvent(@Nonnull Ref<EntityStore> ref,@Nonnull Store<EntityStore> store,Data data) {
        if("change".equals(data.action)){refresh(action.apply("change",data.value));return;}
        if("clear".equals(data.action)){
            SkillTreeViewModel next=action.apply("clear","");UICommandBuilder update=frame(next);
            update.set("#GraphSearchInput.Value","");sendUpdate(update,false);return;
        }
        if("skills".equals(data.action)||"passives".equals(data.action)){
            refresh(action.apply(data.action,data.value));return;
        }
        if("done".equals(data.action))close();
    }

    private void refresh(SkillTreeViewModel model){sendUpdate(frame(model),false);}
    private UICommandBuilder frame(SkillTreeViewModel model){
        UICommandBuilder update=new UICommandBuilder();
        CanvasGraphEditorHud.writeFrame(update,model,null,null);return update;
    }

    @Override public void onDismiss(@Nonnull Ref<EntityStore> ref,@Nonnull Store<EntityStore> store){dismissed.run();}
    public void closeFromService(){close();}

    public static final class Data {
        static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
                .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
                .append(new KeyedCodec<>("@Value",Codec.STRING),(d,v)->d.value=v,d->d.value).add().build();
        private String action=""; private String value="";
    }
}
