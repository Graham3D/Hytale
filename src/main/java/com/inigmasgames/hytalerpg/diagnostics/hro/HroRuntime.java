package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.inigmasgames.hytalerpg.diagnostics.RpgTraceRecord;
import java.nio.file.Path;
import java.util.*;

/** Small observation bridge; no gameplay authority or retained native objects. */
public final class HroRuntime {
    static final String BOOT_ID=UUID.randomUUID().toString();
    private static volatile HroRecorder recorder;
    private static final Set<String> WITNESSES=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Context> CONTEXT=new ThreadLocal<>();
    private record Context(UUID player,UUID world,long connection,long tick) { }
    public static final class Scope implements AutoCloseable {
        private static final Scope OFF=new Scope(null,false);
        private final Context previous;private final boolean enabled;
        private Scope(Context previous,boolean enabled){this.previous=previous;this.enabled=enabled;}
        @Override public void close(){if(enabled){if(previous==null)CONTEXT.remove();else CONTEXT.set(previous);}}
    }
    public static Scope owner(UUID player,UUID world,long connection,long tick) {
        if(!active(player))return Scope.OFF;
        Context previous=CONTEXT.get();CONTEXT.set(new Context(player,world,connection,tick));return new Scope(previous,true);
    }
    private HroRuntime() { }
    public static void install(Path root) {
        WITNESSES.clear();
        try{recorder=new HroRecorder(root);}catch(RuntimeException unavailable){recorder=null;}
    }
    public static void witness(String registration){WITNESSES.add(registration);}
    static Set<String> witnesses(){return Set.copyOf(WITNESSES);}
    public static void registered(String contract) { var r=recorder;if(r!=null)r.registered(contract); }
    public static boolean active(UUID player) { var r=recorder;return r!=null&&r.active(player); }
    public static String start(UUID player,UUID world){var r=recorder;return r==null?"HRO unavailable":r.start(player,world);}
    public static String stop(){var r=recorder;return r==null?"HRO unavailable":r.stop();}
    public static String status(){var r=recorder;return r==null?"HRO unavailable":r.status();}
    public static void disconnect(UUID player){var r=recorder;if(r!=null)r.disconnect(player);}
    public static void shutdown(){var r=recorder;if(r!=null)r.close();recorder=null;}
    public static void observe(UUID player,String contract,String provider,String boundary,String operation,UUID world,
                               Long connection,Long tick,Long revision,Map<String,?> facts) {
        var r=recorder;if(r==null||!r.active(player))return;
        Context context=CONTEXT.get();
        if(context!=null&&context.player.equals(player)) {
            if(world==null)world=context.world;if(connection==null)connection=context.connection;if(tick==null)tick=context.tick;
            if("admission".equals(operation))operation="admission-"+context.connection;
        }
        r.observe(player,contract,provider,boundary,operation,world,connection,tick,revision,facts);
    }
    public static boolean waiting(UUID player,String reason) {
        if(active(player))observe(player,"PLAYER.ADMISSION","HytaleSupportSystem","WAIT","admission",null,null,null,null,Map.of("reason",reason));
        return false;
    }
    public static void admission(UUID player,UUID world,String boundary) {
        if(active(player))observe(player,"PLAYER.ADMISSION","HytalePlayerPersistenceReady",boundary,"admission",world,null,null,null,Map.of());
    }
    /** Existing authority already owns this immutable published state; never retained past this call. */
    public static void loadout(UUID player,long revision,String[] slots) {
        var r=recorder;if(r==null||!r.firstLoadout(player,revision))return;
        for(int i=0;i<Math.min(4,slots.length);i++) {
            String slot="skill0"+(i+1);
            observe(player,"SKILL.PROJECTION","RpgLoadoutService","SAVED_SLOT_OBSERVED",slot,null,null,null,revision,
                    Map.of("logicalSlot",slot,"skillId",slots[i]==null?"":slots[i]));
        }
    }
    public static void skill(RpgTraceRecord event) {
        if(event.eventType()==com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType.NATIVE_RPG_TICK_SAMPLE) {
            var r=recorder;if(r!=null)r.worldSample(event.details());return;
        }
        if(!active(event.playerUuid()))return;
        String contract=switch(event.eventType()) {
            case EQUIP_SKILL_REQUEST,EQUIP_SKILL_COMMITTED,UNEQUIP_SKILL_REQUEST,UNEQUIP_SKILL_COMMITTED,
                 COMPILE_BEGIN,COMPILE_SUCCESS,COMPILE_FAILURE,SAVE -> "SKILL.ASSIGN";
            case NATIVE_ABILITY_SLOT_PROJECTED,NATIVE_ABILITY_SLOT_CLEARED,NATIVE_ABILITY_SLOT_CONFLICT,
                 NATIVE_ABILITY4_UNAVAILABLE,NATIVE_ABILITY_INPUT_UNAVAILABLE -> "SKILL.PROJECTION";
            case SKILL_ACTIVATION_REQUEST,SKILL_ACTIVATION_REJECTED,SKILL_VALIDATION_PASS,SKILL_VALIDATION_REJECTED,
                 SKILL_COMMITTED,EXECUTOR_DISPATCH,SKILL_COMMIT_FAILED,SKILL_EXECUTION_FAILED,RESOURCE_COMMIT -> "SKILL.EXECUTION";
            default -> null;
        };
        if(contract!=null)observe(event.playerUuid(),contract,"RpgSkillTraceService",event.eventType().name(),event.correlationId(),
                null,null,null,number(event.details().get("RPG revision")),event.details());
    }
    private static Long number(Object value){return value instanceof Number n?n.longValue():null;}
    public static void ui(UUID player,String event,String correlation,Map<String,?> facts) {
        if(!active(player))return;
        String contract=switch(event) {
            case "HUD_LAYOUT_READY","HUD_REFRESH_FAILED" -> "HUD.PUBLICATION";
            case "PAGE_OPEN","PAGE_CLOSE","HUD_TEARDOWN" -> "UI.LIFECYCLE";
            case "DOMAIN_OPERATION" -> "INVENTORY.TRANSFER";
            default -> null;
        };
        if(contract!=null)observe(player,contract,"UiInteractionTrace",event,correlation,null,null,null,null,facts);
    }
    public static void custody(UUID player,UUID world,boolean componentPresent,String mode,long revision) {
        if(active(player))observe(player,"INVENTORY.OWNER","SpatialBagComponent","CUSTODY_SNAPSHOT","custody",world,
                null,null,revision<0?null:revision,Map.of("componentPresent",componentPresent,"ownershipMode",mode));
    }
}
