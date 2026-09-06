package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.content.CatalogResolution;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.LinkEdge;
import com.inigmasgames.hytalerpg.domain.PassiveDefinition;
import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.domain.SkillDefinition;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.progress.MutationResult;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutOperations;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.attribute.DerivedStats;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService;
import com.inigmasgames.hytalerpg.combat.damage.CriticalRoller;
import com.inigmasgames.hytalerpg.combat.damage.SkillScalingService;
import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace;
import com.inigmasgames.hytalerpg.combat.hytale.DerivedStatEntityAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.EntityStatResourcePort;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata;
import com.inigmasgames.hytalerpg.combat.resource.ResourceCost;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.combat.power.BasePowerResolver;
import com.inigmasgames.hytalerpg.combat.power.BasePowerSource;
import com.inigmasgames.hytalerpg.combat.status.ControlProfile;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.links.CompatibilityService;
import com.inigmasgames.hytalerpg.links.LinkCompiler;
import com.inigmasgames.hytalerpg.links.RpgLinkGraphService;
import com.inigmasgames.hytalerpg.progress.RpgPlayerState;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Temporary command editing frontend. Authority remains in RpgLoadoutOperations. */
public final class RpgCommand extends AbstractCommandCollection {
    public RpgCommand(RpgCatalog catalog, RpgLoadoutOperations loadouts,
                      RpgCombatKernel kernel, CombatTrace combatTrace) {
        super("rpg", "Configure and inspect the server-authoritative RPG Link Tree.");
        addSubCommand(new EquipCommand(catalog, loadouts));
        addSubCommand(new UnequipCommand(loadouts));
        addSubCommand(new LinkCommand(loadouts));
        addSubCommand(new UnlinkCommand(loadouts));
        addSubCommand(new LoadoutCommand(catalog, loadouts));
        addSubCommand(new CompileCommand(loadouts));
        addSubCommand(new StatsCommand(loadouts, kernel, combatTrace));
        addSubCommand(new DevCommand(catalog, loadouts, kernel, combatTrace));
    }

    private abstract static class PlayerSubcommand extends AbstractPlayerCommand {
        final RpgLoadoutOperations loadouts;
        PlayerSubcommand(String name, String description, RpgLoadoutOperations loadouts) {
            super(name, description); this.loadouts = loadouts; setPermissionGroup(GameMode.Adventure);
        }
        void send(CommandContext context, MutationResult result) { context.sendMessage(Message.raw(result.message())); }
        void error(CommandContext context, RuntimeException error) { context.sendMessage(Message.raw(error.getMessage())); }
    }

    private static final class EquipCommand extends PlayerSubcommand {
        private final RpgCatalog catalog;
        private final RequiredArg<String> slot;
        private final RequiredArg<String> definition;
        EquipCommand(RpgCatalog catalog, RpgLoadoutOperations loadouts) {
            super("equip", "Equip a Skill or Passive definition into a permanent slot.", loadouts);
            this.catalog = catalog;
            slot = withRequiredArg("slot", "skill01..skill04 or passive01..passive06", ArgTypes.STRING);
            definition = withRequiredArg("definition", "canonical name or ID", ArgTypes.GREEDY_STRING);
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            try {
                String slotValue = context.get(slot);
                String query = clean(context.get(definition));
                if (slotValue.toLowerCase(java.util.Locale.ROOT).startsWith("skill")) {
                    CatalogResolution<SkillDefinition> resolved = catalog.resolveSkill(query);
                    if (resolved.status() != CatalogResolution.Status.RESOLVED) {
                        context.sendMessage(Message.raw(resolved.message())); return;
                    }
                    send(context, loadouts.equipSkill(playerRef.getUuid(), SkillSlot.parse(slotValue), resolved.value().id()));
                } else if (slotValue.toLowerCase(java.util.Locale.ROOT).startsWith("passive")) {
                    CatalogResolution<PassiveDefinition> resolved = catalog.resolvePassive(query);
                    if (resolved.status() != CatalogResolution.Status.RESOLVED) {
                        context.sendMessage(Message.raw(resolved.message())); return;
                    }
                    send(context, loadouts.equipPassive(playerRef.getUuid(), PassiveSlot.parse(slotValue), resolved.value().id()));
                } else throw new IllegalArgumentException("Equip slot must be skill01..skill04 or passive01..passive06.");
            } catch (RuntimeException error) { error(context, error); }
        }
    }

    private static final class UnequipCommand extends PlayerSubcommand {
        private final RequiredArg<String> slot;
        UnequipCommand(RpgLoadoutOperations loadouts) {
            super("unequip", "Unequip a Skill or Passive and safely remove affected links.", loadouts);
            slot = withRequiredArg("slot", "skill01..skill04 or passive01..passive06", ArgTypes.STRING);
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            try {
                String value = context.get(slot);
                if (value.toLowerCase(java.util.Locale.ROOT).startsWith("skill"))
                    send(context, loadouts.unequipSkill(playerRef.getUuid(), SkillSlot.parse(value)));
                else send(context, loadouts.unequipPassive(playerRef.getUuid(), PassiveSlot.parse(value)));
            } catch (RuntimeException error) { error(context, error); }
        }
    }

    private static final class LinkCommand extends PlayerSubcommand {
        private final RequiredArg<String> source;
        private final RequiredArg<String> target;
        LinkCommand(RpgLoadoutOperations loadouts) {
            super("link", "Create or replace one validated Link route edge.", loadouts);
            source = withRequiredArg("source", "passive or joint node", ArgTypes.STRING);
            target = withRequiredArg("target", "skill or joint node", ArgTypes.STRING);
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            try { send(context, loadouts.link(playerRef.getUuid(), LinkNodeId.parse(context.get(source)), LinkNodeId.parse(context.get(target)))); }
            catch (RuntimeException error) { error(context, error); }
        }
    }

    private static final class UnlinkCommand extends PlayerSubcommand {
        private final RequiredArg<String> source;
        UnlinkCommand(RpgLoadoutOperations loadouts) {
            super("unlink", "Remove the outgoing edge from a Passive or Joint node.", loadouts);
            source = withRequiredArg("source", "passive or joint node", ArgTypes.STRING);
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            try { send(context, loadouts.unlinkSource(playerRef.getUuid(), LinkNodeId.parse(context.get(source)))); }
            catch (RuntimeException error) { error(context, error); }
        }
    }

    private static final class LoadoutCommand extends PlayerSubcommand {
        private final RpgCatalog catalog;
        LoadoutCommand(RpgCatalog catalog, RpgLoadoutOperations loadouts) {
            super("loadout", "Print the authoritative equipped graph and compiled plan summary.", loadouts); this.catalog = catalog;
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            context.sendMessage(Message.raw(loadouts.getLoadout(playerRef.getUuid()).format(catalog)));
        }
    }

    private static final class CompileCommand extends PlayerSubcommand {
        CompileCommand(RpgLoadoutOperations loadouts) { super("compile", "Compile the current Link Tree without mutating it.", loadouts); }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            var result = loadouts.compile(playerRef.getUuid());
            context.sendMessage(Message.raw(result.success() ? "Compile: PASS. Plans=" + result.plans().size()
                    : "Compile: FAIL " + result.code() + ": " + result.message()));
        }
    }

    private static final class StatsCommand extends PlayerSubcommand {
        private final RpgCombatKernel kernel;
        private final CombatTrace trace;
        StatsCommand(RpgLoadoutOperations loadouts, RpgCombatKernel kernel, CombatTrace trace) {
            super("stats", "Show Stage 02 raw/effective attributes, derived values, and native resource pools.", loadouts);
            this.kernel = kernel; this.trace = trace;
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            try {
                DerivedStats derived = derive(loadouts, playerRef.getUuid(), kernel);
                EntityStatMap stats = requireStats(store, ref);
                new DerivedStatEntityAdapter().apply(stats, derived);
                String correlation = shortId();
                trace.emit(playerRef.getUuid(), RpgTraceEventType.ATTRIBUTE_SNAPSHOT,
                        new CombatTrace.Context("stats", "stats", correlation),
                        Map.of("raw", derived.rawAttributes(), "effective", derived.effectiveAttributes()));
                trace.emit(playerRef.getUuid(), RpgTraceEventType.DERIVED_STATS,
                        new CombatTrace.Context("stats", "stats", correlation), derivedDetails(derived));
                context.sendMessage(Message.raw(formatStats(derived, stats, kernel.balance().profileId)));
            } catch (RuntimeException error) { error(context, error); }
        }
    }

    private static final class DevCommand extends AbstractCommandCollection {
        DevCommand(RpgCatalog catalog, RpgLoadoutOperations loadouts, RpgCombatKernel kernel, CombatTrace trace) {
            super("dev", "Development-only Stage 02 kernel fixtures.");
            addSubCommand(new AttributeCommand(loadouts));
            addSubCommand(new ResetCommand(loadouts));
            addSubCommand(new ResourceCommand(loadouts, kernel, trace));
            addSubCommand(new RecoveryCommand(loadouts, kernel, trace));
            addSubCommand(new RecoveryProofCommand(loadouts, kernel, trace));
            addSubCommand(new PotencyProofCommand(catalog, loadouts, kernel, trace));
            addSubCommand(new DamageCommand(loadouts, kernel, trace));
            addSubCommand(new StatusCommand(loadouts, kernel, trace));
        }
    }

    private static final class AttributeCommand extends PlayerSubcommand {
        private final RequiredArg<String> attribute;
        private final RequiredArg<String> value;
        AttributeCommand(RpgLoadoutOperations loadouts) {
            super("attribute", "Development-only raw attribute override.", loadouts);
            attribute = withRequiredArg("attribute", "str, dex, int, wis, or luck", ArgTypes.STRING);
            value = withRequiredArg("raw", "non-negative raw value", ArgTypes.STRING);
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            try { send(context, loadouts.setDevelopmentAttribute(playerRef.getUuid(),
                    RpgAttribute.parse(context.get(attribute)), Integer.parseInt(context.get(value)))); }
            catch (RuntimeException error) { error(context, error); }
        }
    }

    private static final class ResetCommand extends PlayerSubcommand {
        ResetCommand(RpgLoadoutOperations loadouts) { super("reset", "Reset development attributes to 10.", loadouts); }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) { send(context, loadouts.resetDevelopmentAttributes(playerRef.getUuid())); }
    }

    private abstract static class KernelCommand extends PlayerSubcommand {
        final RpgCombatKernel kernel; final CombatTrace trace;
        KernelCommand(String name, String description, RpgLoadoutOperations loadouts,
                      RpgCombatKernel kernel, CombatTrace trace) {
            super(name, description, loadouts); this.kernel = kernel; this.trace = trace;
        }
        EntityStatResourcePort resources(Store<EntityStore> store, Ref<EntityStore> ref, UUID actor) {
            EntityStatMap stats = requireStats(store, ref);
            new DerivedStatEntityAdapter().apply(stats, derive(loadouts, actor, kernel));
            return new EntityStatResourcePort(stats);
        }
    }

    private static final class ResourceCommand extends KernelCommand {
        private final RequiredArg<String> type, action, amount;
        ResourceCommand(RpgLoadoutOperations loadouts, RpgCombatKernel kernel, CombatTrace trace) {
            super("resource", "Spend or regenerate a native Stage 02 resource.", loadouts, kernel, trace);
            type = withRequiredArg("type", "mana or stamina", ArgTypes.STRING);
            action = withRequiredArg("action", "spend or regen", ArgTypes.STRING);
            amount = withRequiredArg("amount", "cost, or regen seconds", ArgTypes.STRING);
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            try {
                ResourceType resourceType = ResourceType.valueOf(context.get(type).toUpperCase(java.util.Locale.ROOT));
                double numeric = Double.parseDouble(context.get(amount));
                EntityStatResourcePort port = resources(store, ref, playerRef.getUuid());
                String correlation = shortId();
                var ids = new CombatTrace.Context("dev-resource", "dev-resource", correlation);
                if (context.get(action).equalsIgnoreCase("spend")) {
                    ResourceCost cost = new ResourceCost(resourceType, numeric);
                    boolean affordable = kernel.resources().canAfford(playerRef.getUuid(), cost, port);
                    trace.emit(playerRef.getUuid(), RpgTraceEventType.RESOURCE_CHECK, ids,
                            Map.of("resource", resourceType, "cost", numeric, "current", port.current(resourceType), "affordable", affordable));
                    if (!affordable) {
                        trace.emit(playerRef.getUuid(), RpgTraceEventType.RESOURCE_REJECTED, ids, Map.of("reason", "INSUFFICIENT"));
                        context.sendMessage(Message.raw("Rejected: insufficient " + resourceType + "; no resource consumed.")); return;
                    }
                    var token = kernel.resources().reserveCost(playerRef.getUuid(), cost, port);
                    kernel.resources().commitCost(token, port); kernel.resources().finish(token);
                    trace.emit(playerRef.getUuid(), RpgTraceEventType.RESOURCE_COMMIT, ids,
                            Map.of("resource", resourceType, "cost", numeric, "current", port.current(resourceType)));
                } else if (context.get(action).equalsIgnoreCase("regen")) {
                    double recovered = kernel.resources().regenerate(playerRef.getUuid(), resourceType, numeric, port);
                    trace.emit(playerRef.getUuid(), RpgTraceEventType.RESOURCE_RECOVERY, ids,
                            Map.of("resource", resourceType, "seconds", numeric, "recovered", recovered, "current", port.current(resourceType)));
                } else throw new IllegalArgumentException("Action must be spend or regen.");
                context.sendMessage(Message.raw(resourceType + "=" + port.current(resourceType) + "/" + port.maximum(resourceType)));
            } catch (RuntimeException error) { error(context, error); }
        }
    }

    private static final class RecoveryCommand extends KernelCommand {
        private final RequiredArg<String> mode, rootId;
        RecoveryCommand(RpgLoadoutOperations loadouts, RpgCombatKernel kernel, CombatTrace trace) {
            super("recovery", "Apply one deduplicated normal/charged hostile-hit recovery fixture.", loadouts, kernel, trace);
            mode = withRequiredArg("mode", "normal or charged", ArgTypes.STRING);
            rootId = withRequiredArg("rootId", "deduplication ID", ArgTypes.STRING);
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            try {
                boolean charged = switch (context.get(mode).toLowerCase(java.util.Locale.ROOT)) {
                    case "normal" -> false; case "charged" -> true;
                    default -> throw new IllegalArgumentException("Mode must be normal or charged.");
                };
                var port = resources(store, ref, playerRef.getUuid());
                var result = kernel.resources().recoverHostileWeaponHit(playerRef.getUuid(), context.get(rootId), charged, port);
                trace.emit(playerRef.getUuid(), RpgTraceEventType.RESOURCE_RECOVERY,
                        new CombatTrace.Context(context.get(rootId), "dev-recovery", shortId()),
                        Map.of("charged", charged, "deduplicated", !result.applied(),
                                "manaRecovered", result.manaRecovered(), "staminaRecovered", result.staminaRecovered()));
                context.sendMessage(Message.raw("Recovery applied=" + result.applied() + " Mana=" + port.current(ResourceType.MANA)
                        + " Stamina=" + port.current(ResourceType.STAMINA)));
            } catch (RuntimeException error) { error(context, error); }
        }
    }

    private static final class RecoveryProofCommand extends KernelCommand {
        RecoveryProofCommand(RpgLoadoutOperations loadouts, RpgCombatKernel kernel, CombatTrace trace) {
            super("recovery-proof", "Deterministically prove normal/charged recovery and root-hit deduplication.",
                    loadouts, kernel, trace);
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            try {
                UUID actor = playerRef.getUuid();
                EntityStatResourcePort port = resources(store, ref, actor);
                port.setCurrent(ResourceType.MANA, 0.0);
                port.setCurrent(ResourceType.STAMINA, 0.0);
                String correlation = shortId();
                String normalRoot = "r011-normal-" + shortId();
                String chargedRoot = "r011-charged-" + shortId();
                trace.emit(actor, RpgTraceEventType.RESOURCE_PROOF_SETUP,
                        new CombatTrace.Context("r011-recovery-proof", "dev-recovery-proof", correlation),
                        Map.of("mana", port.current(ResourceType.MANA), "stamina", port.current(ResourceType.STAMINA),
                                "maxMana", port.maximum(ResourceType.MANA), "maxStamina", port.maximum(ResourceType.STAMINA),
                                "normalFraction", kernel.balance().normalHostileHitRecovery,
                                "chargedFraction", kernel.balance().chargedHostileHitRecovery));
                var normal = recoveryProofStep(actor, normalRoot, false, correlation, port);
                var normalDuplicate = recoveryProofStep(actor, normalRoot, false, correlation, port);
                var charged = recoveryProofStep(actor, chargedRoot, true, correlation, port);
                var chargedDuplicate = recoveryProofStep(actor, chargedRoot, true, correlation, port);
                context.sendMessage(Message.raw("Recovery proof: normal=" + f2(normal.manaRecovered()) + "/"
                        + f2(normal.staminaRecovered()) + ", normal duplicate rejected=" + !normalDuplicate.applied()
                        + ", charged=" + f2(charged.manaRecovered()) + "/" + f2(charged.staminaRecovered())
                        + ", charged duplicate rejected=" + !chargedDuplicate.applied()
                        + ". Final Mana/Stamina=" + f2(port.current(ResourceType.MANA)) + "/"
                        + f2(port.current(ResourceType.STAMINA))));
            } catch (RuntimeException error) { error(context, error); }
        }
        private com.inigmasgames.hytalerpg.combat.resource.RpgResourceService.RecoveryResult recoveryProofStep(
                UUID actor, String root, boolean charged, String correlation, EntityStatResourcePort port) {
            double beforeMana = port.current(ResourceType.MANA);
            double beforeStamina = port.current(ResourceType.STAMINA);
            var result = kernel.resources().recoverHostileWeaponHit(actor, root, charged, port);
            trace.emit(actor, RpgTraceEventType.RESOURCE_RECOVERY,
                    new CombatTrace.Context(root, "dev-recovery-proof", correlation),
                    Map.of("mode", charged ? "CHARGED" : "NORMAL",
                            "expectedFraction", charged ? kernel.balance().chargedHostileHitRecovery
                                    : kernel.balance().normalHostileHitRecovery,
                            "applied", result.applied(), "deduplicated", !result.applied(),
                            "beforeMana", beforeMana, "afterMana", port.current(ResourceType.MANA),
                            "beforeStamina", beforeStamina, "afterStamina", port.current(ResourceType.STAMINA),
                            "manaRecovered", result.manaRecovered(), "staminaRecovered", result.staminaRecovered()));
            return result;
        }
    }

    private static final class PotencyProofCommand extends KernelCommand {
        private final RpgCatalog catalog;
        PotencyProofCommand(RpgCatalog catalog, RpgLoadoutOperations loadouts, RpgCombatKernel kernel,
                            CombatTrace trace) {
            super("potency-proof", "Compile an isolated canonical Potency fixture without changing the loadout.",
                    loadouts, kernel, trace);
            this.catalog = catalog;
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            try {
                RpgPlayerState fixture = RpgPlayerState.create(playerRef.getUuid());
                fixture.skill(SkillSlot.SKILL01, new SkillId("fire_bolt"));
                fixture.passive(PassiveSlot.PASSIVE01, new PassiveId("potency"));
                fixture.linkEdges(java.util.List.of(LinkEdge.create(LinkNodeId.PASSIVE01, LinkNodeId.SKILL01)));
                CompatibilityService compatibility = new CompatibilityService();
                RpgLinkGraphService graph = new RpgLinkGraphService(catalog, compatibility);
                var compilation = new LinkCompiler(catalog, graph, compatibility, kernel.balance()).compile(fixture);
                if (!compilation.success())
                    throw new IllegalStateException("Potency proof compile failed: " + compilation.code() + " "
                            + compilation.message());
                double increased = compilation.plans().get(SkillSlot.SKILL01).kernelModifiers()
                        .scalablePayloadIncreased();
                String correlation = shortId();
                trace.emit(playerRef.getUuid(), RpgTraceEventType.POTENCY_PROOF,
                        new CombatTrace.Context("r011-potency-proof", "fire-bolt-potency", correlation),
                        Map.of("skill", "fire_bolt", "passive", "potency", "scalablePayloadIncreased", increased,
                                "expected", kernel.balance().potencyIncreased, "catalogPassiveCount", catalog.passives().size(),
                                "compileResult", "PASS"));
                context.sendMessage(Message.raw("Potency proof: compiler scalable magnitude Increased="
                        + pct(increased) + " (expected " + pct(kernel.balance().potencyIncreased)
                        + "); canonical passives=" + catalog.passives().size() + ". Loadout unchanged."));
            } catch (RuntimeException error) { error(context, error); }
        }
    }

    private static final class DamageCommand extends KernelCommand {
        private final RequiredArg<String> mode, basePower;
        DamageCommand(RpgLoadoutOperations loadouts, RpgCombatKernel kernel, CombatTrace trace) {
            super("damage", "Submit bounded damage to the aimed non-player entity through Hytale's native pipeline.",
                    loadouts, kernel, trace);
            mode = withRequiredArg("mode", "never, force, or seeded", ArgTypes.STRING);
            basePower = withRequiredArg("basePower", "non-negative test base power", ArgTypes.STRING);
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            try {
                Ref<EntityStore> target = TargetUtil.getTargetEntity(ref, 20.0f, store);
                if (target == null || !target.isValid())
                    throw new IllegalStateException("Aim at a living, damageable NPC within 20 blocks.");
                if (target == ref || target.equals(ref))
                    throw new IllegalStateException("The Stage 02 damage diagnostic rejects self targets.");
                NPCEntity npc = store.getComponent(target, NPCEntity.getComponentType());
                if (npc == null)
                    throw new IllegalStateException("The aimed entity is not a controlled non-player damage target.");
                EntityStatMap targetStats = store.getComponent(target, EntityStatMap.getComponentType());
                if (targetStats == null || targetStats.get(DefaultEntityStatTypes.getHealth()) == null
                        || targetStats.get(DefaultEntityStatTypes.getHealth()).get()
                        <= targetStats.get(DefaultEntityStatTypes.getHealth()).getMin())
                    throw new IllegalStateException("The aimed NPC has no positive native Health to damage.");
                double power = Double.parseDouble(context.get(basePower));
                String modeValue = context.get(mode).toLowerCase(java.util.Locale.ROOT);
                double chance = switch (modeValue) { case "never" -> 0.0; case "force" -> 1.0; case "seeded" -> 0.5;
                    default -> throw new IllegalArgumentException("Mode must be never, force, or seeded."); };
                DerivedStats derived = derive(loadouts, playerRef.getUuid(), kernel);
                var powerResolution = kernel.basePower().resolve(
                        new BasePowerResolver.Request(BasePowerSource.INNATE, null, power));
                DamageCalculationService calculator = modeValue.equals("seeded")
                        ? new DamageCalculationService(new SkillScalingService(kernel.balance()), CriticalRoller.seeded(20260906L))
                        : kernel.damage();
                var result = calculator.calculate(DamageCalculationService.Request.direct(powerResolution.basePower(),
                        derived.effective(RpgAttribute.STR), 1.0, ModifierBuckets.NONE, chance, derived.criticalMultiplier()));
                String root = "dev-damage-" + shortId(); String correlation = shortId();
                CombatTrace.Context ids = new CombatTrace.Context(root, root + "-1", correlation);
                trace.emit(playerRef.getUuid(), RpgTraceEventType.DAMAGE_CALC_BEGIN, ids,
                        Map.of("basePower", power, "effectiveAttribute", derived.effective(RpgAttribute.STR), "coefficient", 1.0));
                trace.emit(playerRef.getUuid(), RpgTraceEventType.BASE_POWER_RESOLVED, ids,
                        Map.of("source", powerResolution.source(), "weaponClass", powerResolution.weaponClass(),
                                "basePower", powerResolution.basePower()));
                trace.emit(playerRef.getUuid(), RpgTraceEventType.SCALING_APPLIED, ids,
                        Map.of("attributeMultiplier", result.attributeMultiplier(), "scaledBasePower", result.scaledBasePower()));
                trace.emit(playerRef.getUuid(), RpgTraceEventType.MODIFIERS_APPLIED, ids,
                        Map.of("modifierFactor", result.modifierFactor(), "preCritDamage", result.preCritDamage()));
                trace.emit(playerRef.getUuid(), RpgTraceEventType.CRIT_ROLL, ids,
                        Map.of("chance", chance, "critical", result.critical(), "multiplier", derived.criticalMultiplier()));
                new HytaleDamageAdapter().apply(target, store, ref, DamageCause.COMMAND,
                        new HytaleDamageMetadata(playerRef.getUuid(), root, root + "-1", correlation,
                                result.preMitigationDamage(), Double.NaN), result);
                context.sendMessage(Message.raw("Submitted " + result.preMitigationDamage() + " pre-mitigation damage to NPC "
                        + npc.getRoleName() + "; critical=" + result.critical()
                        + ". Inspect automatic trace for native Health loss."));
            } catch (RuntimeException error) { error(context, error); }
        }
    }

    private static final class StatusCommand extends KernelCommand {
        private final RequiredArg<String> type;
        StatusCommand(RpgLoadoutOperations loadouts, RpgCombatKernel kernel, CombatTrace trace) {
            super("status", "Apply one Stage 02 status lifecycle step to the diagnostic target.", loadouts, kernel, trace);
            type = withRequiredArg("type", "chill, burn, poison, root, fear, taunt, or stagger", ArgTypes.STRING);
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            try {
                RpgStatusType status = RpgStatusType.valueOf(context.get(type).toUpperCase(java.util.Locale.ROOT));
                var result = kernel.statuses().apply(playerRef.getUuid(), status, ControlProfile.NORMAL);
                RpgTraceEventType event = switch (result.outcome()) {
                    case APPLIED -> RpgTraceEventType.STATUS_APPLIED; case REFRESHED -> RpgTraceEventType.STATUS_REFRESHED;
                    case THRESHOLD -> RpgTraceEventType.STATUS_THRESHOLD; case REJECTED -> RpgTraceEventType.STATUS_REJECTED;
                };
                String correlation = shortId();
                var ids = new CombatTrace.Context("dev-status", "dev-status", correlation);
                trace.emit(playerRef.getUuid(), RpgTraceEventType.STATUS_REQUEST, ids, Map.of("requested", status));
                trace.emit(playerRef.getUuid(), event,
                        ids,
                        Map.of("result", result.type(), "stacks", result.stacks(), "seconds", result.remainingSeconds(), "detail", result.detail()));
                context.sendMessage(Message.raw(result.outcome() + " " + result.type() + " stacks=" + result.stacks()
                        + " seconds=" + result.remainingSeconds() + " " + result.detail()));
            } catch (RuntimeException error) { error(context, error); }
        }
    }

    private static DerivedStats derive(RpgLoadoutOperations loadouts, UUID actor, RpgCombatKernel kernel) {
        Map<RpgAttribute, Integer> raw = new EnumMap<>(RpgAttribute.class);
        var persisted = loadouts.getLoadout(actor).state().attributes;
        for (RpgAttribute attribute : RpgAttribute.values()) raw.put(attribute, persisted.getOrDefault(attribute.name(), 10));
        return kernel.derivedStats().derive(raw);
    }
    private static EntityStatMap requireStats(Store<EntityStore> store, Ref<EntityStore> ref) {
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) throw new IllegalStateException("Hytale EntityStatMap is unavailable for this player.");
        return stats;
    }
    private static Map<String, Object> derivedDetails(DerivedStats value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maxHealth", value.maxHealth()); result.put("maxStamina", value.maxStamina()); result.put("maxMana", value.maxMana());
        result.put("heavyMultiplier", value.heavyDamageMultiplier()); result.put("lightMultiplier", value.lightDamageMultiplier());
        result.put("magicMultiplier", value.magicDamageMultiplier()); result.put("healingMultiplier", value.healingMultiplier());
        result.put("critChance", value.criticalChance()); result.put("cooldownRecovery", value.cooldownRecovery());
        return result;
    }
    private static String formatStats(DerivedStats d, EntityStatMap stats, String balanceProfile) {
        StringBuilder out = new StringBuilder("RPG Stage 02 stats (balance ").append(balanceProfile).append("):");
        for (RpgAttribute attribute : RpgAttribute.values()) out.append("\n").append(attribute).append(" raw=")
                .append(d.rawAttributes().get(attribute)).append(" effective=").append(f2(d.effective(attribute)));
        out.append("\nHealth=").append(nativeStat(stats, DefaultEntityStatTypes.getHealth()));
        out.append(" Mana=").append(nativeStat(stats, DefaultEntityStatTypes.getMana()));
        out.append(" Stamina=").append(nativeStat(stats, DefaultEntityStatTypes.getStamina()));
        out.append("\nHeavy=").append(f2(d.heavyDamageMultiplier())).append("x Light=").append(f2(d.lightDamageMultiplier()))
                .append("x Magic=").append(f2(d.magicDamageMultiplier())).append("x Healing=").append(f2(d.healingMultiplier())).append('x');
        out.append("\nCrit=").append(pct(d.criticalChance())).append(" Cooldown recovery=").append(pct(d.cooldownRecovery()))
                .append(" Learn=").append(pct(d.learnRate())).append(" Upgrade=").append(pct(d.upgradeSuccess()))
                .append(" Magic Find=").append(pct(d.magicFind()));
        return out.toString();
    }
    private static String nativeStat(EntityStatMap stats, int index) {
        var stat = stats.get(index); return stat == null ? "missing" : f2(stat.get()) + "/" + f2(stat.getMax());
    }
    private static String pct(double value) { return f2(value * 100.0) + "%"; }
    private static String f2(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
    private static String shortId() { return UUID.randomUUID().toString().substring(0, 12); }

    private static String clean(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() >= 2 && ((clean.startsWith("\"") && clean.endsWith("\""))
                || (clean.startsWith("'") && clean.endsWith("'")))) return clean.substring(1, clean.length() - 1);
        return clean;
    }
}
