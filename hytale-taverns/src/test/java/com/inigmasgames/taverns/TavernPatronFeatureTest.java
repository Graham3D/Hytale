package com.inigmasgames.taverns;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Contract coverage for the first server-authoritative patron service loop. */
public final class TavernPatronFeatureTest {
    private TavernPatronFeatureTest() {
    }

    public static void main(String[] args) throws Exception {
        patronStateAndTimersAreStable();
        roleUsesTheNativeHumanoidNpcPipeline();
        ordersUseOnlyRegisteredPreparedFoods();
        serviceLoopReusesTablesFoodAndPlates();
        failureSuccessAndCleanupPathsExist();
        patronPresentationAndLogoutCleanupAreSafe();
        humanAppearanceIsRestrictedToVanillaHumanPalettes();
        cosmeticVariantsKeepTheirVariantWhenRecolored();
        patronTeardownAndLocomotionAreIdempotent();
        patronWorldSpaceUiUsesAttachedBillboardParticles();
        patronTicksNeverLoadChunksSynchronously();
        manifestDeclaresRequiredNativeModules();
        System.out.println("TavernPatronFeatureTest passed");
    }

    private static void patronStateAndTimersAreStable() {
        UUID tavernId = UUID.randomUUID();
        TavernPatronComponent original = new TavernPatronComponent(tavernId);
        TavernPatronComponent copied = (TavernPatronComponent) original.clone();
        require(tavernId.equals(copied.tavernId()), "Patron Tavern identity was not cloned");
        require("1:00".equals(TavernPatronManager.formatTimer(60)),
                "One-minute order display is incorrect");
        require("0:00".equals(TavernPatronManager.formatTimer(-1)),
                "Expired order display is not clamped");
    }

    private static void roleUsesTheNativeHumanoidNpcPipeline() throws Exception {
        String role = resource("/Server/NPC/Roles/Taverns/Tavern_Patron.json");
        require(role.contains("\"Appearance\": \"Player\""),
                "Patron role is not a player-shaped humanoid");
        require(role.contains("\"Type\": \"Seek\"")
                        && role.contains("\"UsePathfinder\": true"),
                "Patron role does not use native pathfinding");
        require(role.contains("\"TargetSlot\": \"GreetingTarget\"")
                        && role.contains("\"HeadMotion\":")
                        && role.contains("\"Type\": \"Watch\""),
                "Seated patrons do not use native head tracking for greetings");
        require(role.contains("\"Type\": \"Nav\"")
                        && role.contains("\"Type\": \"RecomputePath\""),
                "Patron role cannot recover blocked/deferred navigation");

        String manager = source("TavernPatronManager.java");
        require(manager.contains("NPCPlugin.get().spawnNPC("),
                "Patrons are not spawned through NPCPlugin");
        require(manager.contains("generateRandomSkin(random)"),
                "Patron clothing/appearance is not randomized");
        require(manager.contains("npc.setLeashPoint("),
                "Patron navigation does not feed native A* targets");
        require(manager.contains("BlockMountAPI.mountOnBlock("),
                "Patrons do not use native chair mounting");
        require(manager.contains("AnimationSlot.Status")
                        && manager.contains("SIT_ANIMATION"),
                "Mounted patrons are not given the native sitting pose");
        require(manager.contains("doorOperator.openIfClosed("),
                "Patrons do not use the Tavern vanilla Door bridge");
    }

    private static void ordersUseOnlyRegisteredPreparedFoods() throws IOException {
        String manager = source("TavernPatronManager.java");
        require(manager.contains("preparedFoods.definitions().stream()"),
                "Patron orders do not use the Prepared food registry");
        require(manager.contains("PreparedMeal.inspect(snapshot.foodStack(), preparedFoods)"),
                "Patron delivery does not require Prepared meal metadata");
        require(manager.contains("prepared.get().isFresh(System.currentTimeMillis())"),
                "Patron delivery does not require a fresh meal");
        require(!manager.contains("itemId.contains(\"Food\")"),
                "Patron orders use an item-name heuristic");
        require(manager.contains("ORDER_SECONDS = 60.0f"),
                "Patron order window is not one minute");
    }

    private static void serviceLoopReusesTablesFoodAndPlates() throws IOException {
        String manager = source("TavernPatronManager.java");
        String servings = source("TableServingManager.java");
        require(manager.contains("servingManager.isRegisteredTable(block)"),
                "Patrons do not use Tavern's Table registry");
        require(manager.contains("servingManager.isRegisteredSeat(block)"),
                "Patrons do not use Tavern's Seating registry");
        require(manager.contains("servingManager.servingAt("),
                "Patrons do not observe existing tabletop servings");
        require(manager.contains("servingManager.consumeForPatron("),
                "Completed meals do not use the serving subsystem");
        require(servings.contains("state.food = null"),
                "Meal completion does not remove only the food");
        require(servings.contains("if (state.plate == null || !state.plate.isValid())")
                        && servings.contains("spawnMissingPlate(key, state.food, commandBuffer)"),
                "Patron meal completion does not guarantee its tracked plate");
        require(servings.contains("PLATE_ITEM_ID = \"Deco_Plate\""),
                "Table serving subsystem no longer tracks Deco_Plate");
        require(servings.contains("DIRTY_PLATE_ITEM_ID = \"Dirty_Plate\"")
                        && servings.contains("replacePlate(key, DIRTY_PLATE_ITEM_ID"),
                "Patron meal completion does not leave a Dirty Plate");
    }

    private static void failureSuccessAndCleanupPathsExist() throws Exception {
        String manager = source("TavernPatronManager.java");
        require(manager.contains("PARTICLE_STUNNED = \"Taverns_Stunned\""),
                "Wrong-food feedback is missing");
        String stunned = resource(
                "/Server/Particles/Taverns/Emotions/Taverns_Stunned.particlesystem");
        require(stunned.contains("\"LifeSpan\": 2.5"),
                "Wrong-food feedback does not clean up its attached emitter");
        require(manager.contains("PARTICLE_ANGRY = \"Angry\""),
                "Failed-order feedback is missing");
        require(manager.contains("PARTICLE_HEARTS = \"Hearts\""),
                "Completed-meal feedback is missing");
        require(manager.contains("EXIT_DESPAWN_SECONDS = 20.0f"),
                "Forced exit despawn is not 20 seconds");
        require(manager.contains("releaseReservation(session)"),
                "Seat/Table reservations are not cleaned up");
        require(manager.contains("restoreFoodVisual("),
                "Interrupted meals do not restore their food to the table");
        require(manager.contains("isReservationValid(session, world)"),
                "Destroyed Table/chair reservations are not invalidated");
    }

    private static void patronPresentationAndLogoutCleanupAreSafe() throws IOException {
        String manager = source("TavernPatronManager.java");
        String plugin = source("TavernsPlugin.java");
        String doors = source("TavernDoorOperator.java");
        require(!manager.contains("spawnOrderVisual")
                        && !manager.contains("orderVisual"),
                "Detached 3D order props can still drift away from patrons");
        require(!manager.contains("\"Order: \"")
                        && manager.contains("formatTimer(second)"),
                "Order nameplate still displays Order/item-name text");
        require(manager.contains("GREETINGS[random.nextInt(GREETINGS.length)]")
                        && manager.contains("ORDER_FOLLOW_UPS[random.nextInt(ORDER_FOLLOW_UPS.length)]")
                        && manager.contains("GREETING_COOLDOWN_SECONDS"),
                "Proximity greeting does not lead into the existing order display safely");
        require(!manager.contains("\"I'll have....\"")
                        && manager.contains("\"I'll have...\"")
                        && manager.contains("\"Make me this, now!!!\""),
                "Intro and follow-up dialogue pools are not separated correctly");
        require(manager.contains("getMarkedEntityRef(GREETING_TARGET_SLOT)")
                        && manager.contains("getHeadSteering()")
                        && manager.contains("HeadRotation.getComponentType()")
                        && manager.contains("restoreHeadForward(session, commandBuffer)")
                        && manager.contains("PhysicsMath.headingFromDirection")
                        && manager.contains("PhysicsMath.pitchFromDirection"),
                "Greeting target is not bound and applied to the native head rotation");
        require(plugin.contains("Order.AFTER, SteeringSystem.class")
                        && plugin.contains("TransformSystems.EntityTrackerUpdate.class"),
                "Patron head updates can be overwritten before transform replication");
        require(manager.contains("CORRECT_MEAL_LINES[random.nextInt(CORRECT_MEAL_LINES.length)]")
                        && manager.contains("FAILED_ORDER_LINES[random.nextInt(FAILED_ORDER_LINES.length)]")
                        && manager.contains("SATISFIED_MEAL_LINES[random.nextInt(SATISFIED_MEAL_LINES.length)]"),
                "Meal acknowledgement/departure dialogue is incomplete");
        require(plugin.contains("patronManager.ownerDisconnected(playerRef.getUuid())"),
                "Owner disconnect does not close patron service");
        require(manager.contains("tavern.withStatus(TavernStatus.CLOSED)")
                        && manager.contains("forceCleanup(session, commandBuffer)"),
                "Owner logout is not persisted or does not clean active patrons");
        require(doors.contains("extends DoorInteraction")
                        && !doors.contains("setBlock("),
                "Patron Door use bypasses vanilla DoorInteraction/state handling");
    }

    private static void humanAppearanceIsRestrictedToVanillaHumanPalettes()
            throws IOException {
        String manager = source("TavernPatronManager.java");
        require(manager.contains("\"06\", \"05\", \"11\", \"04\", \"15\", \"02\""),
                "Patrons do not use the approved six vanilla human skin gradients");
        require(manager.contains("skin.ears = \"Default\"")
                        && manager.contains("skin.cape = null")
                        && manager.contains("skin.headAccessory = null"),
                "Fantasy ears/capes/head traits are not excluded");
        require(manager.contains("skin.haircut = withGradient")
                        && manager.contains("skin.eyebrows = withGradient")
                        && manager.contains("skin.facialHair = withGradient"),
                "Hair, eyebrows and facial hair do not share one hair gradient");
        require(manager.contains("cosmetics.validateSkin(skin)")
                        && manager.indexOf("cosmetics.validateSkin(skin)")
                        < manager.indexOf("new ModelComponent(model)"),
                "A patron model can be installed before its cosmetics validate");
        require(manager.contains("if (model == null)"),
                "A null patron model can still reach ModelComponent");
    }

    private static void cosmeticVariantsKeepTheirVariantWhenRecolored() {
        require("Dreadlocks.Red.Dreadlocks02".equals(
                        TavernPatronManager.withGradient(
                                "Dreadlocks.BrownSemiDark.Dreadlocks02", "Red")),
                "Hair recoloring corrupts variant cosmetic keys");
        require("Morning.Red".equals(
                        TavernPatronManager.withGradient("Morning.Black", "Red")),
                "Hair recoloring does not replace the gradient segment");
    }

    private static void patronTeardownAndLocomotionAreIdempotent()
            throws IOException {
        String manager = source("TavernPatronManager.java");
        require(manager.contains("AnimationUtils.playAnimation(patronRef, slot, null, commandBuffer)")
                        && !manager.contains("IDLE_ANIMATION"),
                "Leaving patrons still mask locomotion with persistent Idle");
        require(manager.contains("AnimationUtils.playAnimation(patronRef, slot, null, commandBuffer)")
                        && manager.contains("stopTrackedAnimation(session.patronRef, AnimationSlot.Status")
                        && manager.contains("stopTrackedAnimation(session.patronRef, AnimationSlot.Movement"),
                "Dismount can leave a cached Status/Movement sitting pose active");
        require(manager.contains("private boolean completeSession(")
                        && manager.contains("if (session.removed)"),
                "Patron cleanup is not guarded against duplicate execution");
        require(manager.contains("sessions.remove(session.patronRef, session)"),
                "Patron cleanup can remove a replacement session by reference alone");
    }

    private static void patronWorldSpaceUiUsesAttachedBillboardParticles()
            throws Exception {
        String manager = source("TavernPatronManager.java");
        String particles = source("PatronParticleController.java");
        String cheeseSystem = resource(
                "/Server/Particles/Taverns/PatronOrders/"
                        + "Taverns_Order_Food_Cheese.particlesystem");
        String cheeseIcon = resource(
                "/Server/Particles/Taverns/PatronOrders/Spawners/"
                        + "Taverns_Order_Food_Cheese_Icon.particlespawner");
        String orderFrame = resource(
                "/Server/Particles/Taverns/PatronOrders/Spawners/"
                        + "Taverns_Order_Frame.particlespawner");
        require(manager.contains("particles.pulseOrder(")
                        && manager.contains("particles.spawnEmotion("),
                "Patron order/emotion feedback bypasses the attached particle controller");
        require(!manager.contains("ParticleUtil.spawnParticleEffect("),
                "Patron emotions can still be left behind at world coordinates");
        require(particles.contains("new SpawnModelParticles(")
                        && particles.contains("EntityPart.Entity")
                        && particles.contains("TARGET_NODE = \"Head\"")
                        && particles.contains("false);"),
                "Patron particles are not attached to the NPC Head model node");
        require(particles.contains("ORDER_HEIGHT = 1.10f"),
                "Order icon is not raised clearly above the patron's head");
        require(cheeseIcon.contains("\"ParticleRotationInfluence\": \"Billboard\"")
                        && cheeseIcon.contains("Particles/Taverns/PatronOrders/Textures/"
                                + "Taverns_Order_Food_Cheese_Icon.png")
                        && cheeseIcon.contains("\"Animation\":")
                        && cheeseIcon.contains("\"100\":"),
                "Order frame/icon particles are not explicit camera-facing billboards");
        require(cheeseSystem.contains("Taverns_Order_Frame")
                        && cheeseSystem.contains("Taverns_Order_Food_Cheese_Icon"),
                "Order frame and native item icon are not composed in one particle system");
        require(orderFrame.contains("\"Min\": 0.16")
                        && cheeseIcon.contains("\"Min\": 0.38")
                        && orderFrame.contains("\"Min\": 1.0")
                        && cheeseIcon.contains("\"Min\": 1.0"),
                "Order frame/icon particle scales are not compact with neutral animation scale");
        require(!particles.contains("UpdateParticleSpawners")
                        && !particles.contains("UpdateParticleSystems")
                        && !particles.contains("AddOrUpdate"),
                "Patron orders still mutate client particle assets during play");
        require("Icons/ItemsGenerated/Food_Cheese.png".equals(
                        PatronParticleController.normalizeIconTexture(
                                "Common/Icons/ItemsGenerated/Food_Cheese.png")),
                "Common-relative item icon paths are not particle compatible");
        require(particles.contains("ORDER_PARTICLE_LIFETIME_SECONDS")
                        && !particles.contains("ScheduledExecutor"),
                "Order indicator lifetime depends on an entity-retaining callback");
        require(manager.contains("equipMealInHand(session, serving.get().foodStack()")
                        && manager.contains("AnimationSlot.Action")
                        && manager.contains("\"Consume\"")
                        && manager.contains("servingManager.hideFoodVisual("),
                "Eating does not use native hand equipment and consume animation");
        require(manager.contains("BITE_EQUIPMENT_LEAD_SECONDS = 0.20f")
                        && manager.contains("biteEquipmentLeadRemaining")
                        && manager.contains("if (!session.biteAnimationStarted)"),
                "Consume can start before the hand equipment reaches the client");
        require(manager.contains("MealVisualState.EATING")
                        && manager.contains("MealVisualState.RESTING")
                        && manager.contains("MIN_REST_DURATION_SECONDS = 3.0f")
                        && manager.contains("MAX_REST_DURATION_SECONDS = 5.0f")
                        && manager.contains("MIN_EATING_DURATION_SECONDS = 4.0f")
                        && manager.contains("MAX_EATING_DURATION_SECONDS = 8.0f")
                        && manager.contains("randomRestDuration()")
                        && manager.contains("randomEatingDuration()")
                        && manager.contains("finishBite(session, commandBuffer)")
                        && manager.contains("servingManager.restoreFoodVisual("),
                "Eating does not alternate hand bites with food resting on its Table slot");
        require(manager.contains("active.setPlayingAnimation(slot, null)")
                        && manager.contains("AnimationUtils.playAnimation(patronRef, slot, null, commandBuffer)")
                        && manager.contains("AnimationSlot.Action, commandBuffer")
                        && manager.contains("stopEatingPresentation(session, commandBuffer)"),
                "Tracked Consume state or meal attachment can survive meal completion/leave");
    }

    private static void patronTicksNeverLoadChunksSynchronously() throws IOException {
        String manager = source("TavernPatronManager.java");
        require(!manager.contains("world.getBlockType("),
                "Patron ECS tick can still synchronously load a chunk");
        require(manager.contains("world.getChunkIfLoaded("),
                "Patron block scans are not restricted to loaded chunks");
        require(manager.contains("loadedBlockType(world, x, y, z)"),
                "Entrance/Table scans bypass the safe loaded-chunk lookup");

        String servings = source("TableServingManager.java");
        require(servings.contains("private static BlockType loadedBlockType("),
                "Interrupted meal restoration can still load a chunk");
        require(!servings.contains(
                        "BlockType.getAssetMap().getAsset(\n"
                                + "                world.getBlock(key.x(), key.y(), key.z()))"),
                "Serving restoration still performs a synchronous world lookup");
    }

    private static void manifestDeclaresRequiredNativeModules() throws Exception {
        String manifest = resource("/manifest.json");
        require(manifest.contains("\"Hytale:NPC\": \"*\""),
                "Native NPC module dependency is missing");
        require(manifest.contains("\"Hytale:Mounts\": \"*\""),
                "Native Mounts module dependency is missing");
        require(manifest.contains("\"Hytale:CosmeticsModule\": \"*\""),
                "Native Cosmetics dependency is missing");
    }

    private static String source(String name) throws IOException {
        return Files.readString(Path.of(
                "src", "main", "java", "com", "inigmasgames", "taverns", name));
    }

    private static String resource(String path) throws Exception {
        try (InputStream stream = TavernPatronFeatureTest.class.getResourceAsStream(path)) {
            require(stream != null, "Missing resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
