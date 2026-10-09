package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;

/** Player-owned WA-155 projection. State follows the actor and is never persisted. */
public final class NativeAffixLightSystem extends EntityTickingSystem<EntityStore> {
    private static ComponentType<EntityStore, State> stateType;
    private final NativeAffixLightProjection.Calibration calibration;

    public static void bind(ComponentType<EntityStore, State> type) { stateType=Objects.requireNonNull(type); }
    public static ComponentType<EntityStore, State> stateType(){return stateType;}
    public NativeAffixLightSystem(NativeAffixLightProjection.Calibration calibration) {
        this.calibration=Objects.requireNonNull(calibration);
    }
    @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }

    @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,
                               Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
        var actor=chunk.getReferenceTo(index);
        var state=stateType==null?null:store.getComponent(actor,stateType);
        if(stateType==null)throw new IllegalStateException("NativeAffixLightSystem state type not bound");
        var light=store.getComponent(actor,DynamicLight.getComponentType());
        var snapshot=GearNativeItems.effects(actor,store).snapshot();
        var update=update(light,state,heldLight(actor,store),snapshot,calibration);
        if(update.removeLight())buffer.removeComponent(actor,DynamicLight.getComponentType());
        else if(light==null && update.light()!=null)buffer.putComponent(actor,DynamicLight.getComponentType(),update.light());
        if(update.state()==null) {
            if(state!=null)buffer.removeComponent(actor,stateType);
        } else if(state==null)buffer.putComponent(actor,stateType,update.state());
    }

    record Update(DynamicLight light,State state,boolean removeLight) {}
    static Update update(DynamicLight light,State state,ColorLight held,GearEffectSnapshot snapshot,
                         NativeAffixLightProjection.Calibration calibration) {
        double bonus=snapshot.total(GearEffectSnapshot.Operator.LIGHT_RADIUS);
        if(bonus<=0) {
            if(state!=null) {
                if(light!=null && state.last!=null && state.last.equals(light.getColorLight())) {
                    if(state.created) {
                        if(held==null || Byte.toUnsignedInt(held.radius)==0)return new Update(null,null,true);
                        light.setColorLight(new ColorLight(held));
                        state.last=new ColorLight(held);
                        state.targetMetres=0;state.effectiveNativeRadius=Byte.toUnsignedInt(held.radius);
                        return new Update(light,state,false);
                    }
                    light.setColorLight(compose(state.baseline,held));
                }
            }
            return new Update(light,null,false);
        }
        ColorLight current=light==null?null:light.getColorLight();
        if(state==null) {
            // A pre-existing actor light is another native contributor, not our property.
            // The held item may already be mirrored into DynamicLight. Do not retain it as
            // an independent contributor when the player puts that item away.
            ColorLight nativeBase=current!=null && held!=null && current.equals(held)?null:current;
            ColorLight baseline=compose(nativeBase,held);
            state=new State(nativeBase,light==null);
            if(light==null) {
                light=new DynamicLight(new ColorLight(baseline));
            }
        } else {
            // Another native writer may change the component (for example on a held-item swap).
            if(current!=null && state.last!=null && !state.last.equals(current)) {
                state.baseline=held!=null && current.equals(held)?null:new ColorLight(current);
                state.created=false;
            }
            if(light==null) {
                state.baseline=null;state.created=true;
                light=new DynamicLight(compose(null,held));
            }
        }
        var result=NativeAffixLightProjection.project(light,compose(state.baseline,held),snapshot,calibration);
        state.last=new ColorLight(light.getColorLight());
        state.targetMetres=result.targetMetres();
        state.effectiveNativeRadius=result.effectiveNativeRadius();
        return new Update(light,state,false);
    }

    static ColorLight heldLight(Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor) {
        var main=InventoryComponent.getItemInHand(accessor,actor);
        var utility=accessor.getComponent(actor,InventoryComponent.Utility.getComponentType());
        return compose(itemLight(main),itemLight(utility==null?null:utility.getActiveItem()));
    }
    private static ColorLight itemLight(ItemStack stack) {
        return ItemStack.isEmpty(stack)||stack.getItem()==null?null:stack.getItem().getLight();
    }
    static ColorLight compose(ColorLight existing,ColorLight held) {
        if(existing==null && held==null)return new ColorLight((byte)0,(byte)255,(byte)255,(byte)255);
        if(existing==null)return new ColorLight(held);
        if(held==null)return new ColorLight(existing);
        return new ColorLight(Byte.toUnsignedInt(held.radius)>Byte.toUnsignedInt(existing.radius)?held:existing);
    }

    public static final class State implements Component<EntityStore> {
        private ColorLight baseline,last;
        private boolean created;
        private double targetMetres;
        private int effectiveNativeRadius;
        public State(){this(null,false);}
        private State(ColorLight baseline,boolean created) {
            this.baseline=baseline==null?null:new ColorLight(baseline);this.created=created;
        }
        private State(State original) {
            this(original.baseline,original.created);
            last=original.last==null?null:new ColorLight(original.last);
            targetMetres=original.targetMetres;effectiveNativeRadius=original.effectiveNativeRadius;
        }
        public double targetMetres(){return targetMetres;}
        public int effectiveNativeRadius(){return effectiveNativeRadius;}
        @Override public State clone(){return new State(this);}
    }
}
