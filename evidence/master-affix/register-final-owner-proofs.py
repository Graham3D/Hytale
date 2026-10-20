"""One-time reviewed R165 final owner evidence registration (no runtime edits)."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
path=ROOT/'tools/affix-proof-map-v1.json'
document=json.loads(path.read_text(encoding='utf-8')); proofs=document['proofs']
PREFIX='com.inigmasgames.hytalerpg.'
def owner(ids,test,required,note,**anchor):
    for ident in ids:
        p=proofs[ident]; key='finalOwnerTest'
        p[key]=PREFIX+test
        p['additionalOwnerTests']=[key]
        p['sourceAnchors'][key]={'test':PREFIX+test,'required':required,**anchor}
        p.setdefault('resolvedQualificationHoles',[]).extend(p.get('qualificationHoles',[]))
        p['qualificationHoles']=[]
        p['qualificationResolution']=note
def ids(first,last):return [f'WA-{n:03d}' for n in range(first,last+1)]
owner(['WA-013'],'combat.status.GearStatusRuntimeTest#wa013IncreasesAllPeriodicTicksWithoutChangingDirectHitOrSourceCaps',
      ['GearStatusRuntime.applyPeriodic','assertTrue(ticks.getFirst()>ordinary.getFirst()',
       'assertEquals(0,GearStatusRuntime.increasedDps','assertEquals(100,'],
      'All periodic kinds measure changed recipient ticks, unchanged expiry/direct hit and wrong-source exclusion.')
owner(ids(17,22),'gear.NativeAffixFinalChannelTest#wa017Through022WrongActiveSourceAndUnsupportedCarrierSubmitNoElementalChannel',
      ['assertTrue(rolledHit.amount(channel)>0,id)','assertEquals(0,idleHit.amount(channel)',
       'assertNull(ManagedGearDamageInteraction.claim','assertSame(accepted,ManagedGearDamageInteraction.claim'],
      'Accepted native attack rejects wrong active item and unsupported native cause; matching contact claims once.',channelLoop=17)
owner(ids(29,34),'gear.NativeAffixFinalChannelTest#wa029Through034NativeDamagePenetratesOnlyMatchingDirectCauseWithoutChangingTarget',
      ['assertTrue(piercing>0,id)','assertEquals(originalResistance,target.resistance(),id)',
       'assertEquals(0,HytaleDifficultyCombat.penetration(wrong)','assertEquals(0,immune.resolve'],
      'Six native causes measure penetration amount, wrong cause, periodic/environmental and immunity controls without changing target resistance.',channelLoop=29)
owner(['WA-065'],'combat.status.GearStatusRuntimeTest#wa065BurnOnlyReplacesWeakerSourceWithoutRaisingCapOrOtherStatus',
      ['runtime.tick(actor,1,port)','assertEquals(1,retained.sourceCap())','assertEquals(replaced.coefficientPerSecond(),retained.coefficientPerSecond()',
       'assertEquals(0,GearStatusRuntime.increasedDps(plain'],
      'Burn refresh uses production periodic owner; stronger source retained, other source/status unchanged.')
owner(['WA-068'],'combat.status.GearStatusRuntimeTest#wa068PoisonAndBleedRefreshExpiryAndTickMagnitudeKeepSourceCaps',
      ['assertEquals(plainTicks,longerTicks.subList(0,plainTicks.size())','assertEquals(cap*10*(extendedSeconds-4),longerTicks.getLast()',
       'assertEquals(cap,extended.sourceView(source,1).orElseThrow().sourceCap()'],
      'Poison/Bleed extended expiry yields the exact additional tick without changing cap or base tick magnitude.')
owner(['WA-078'],'gear.NativeAffixRecipientFilterTest#wa078NativeFilterWritesEachElementOnceAndLeavesHazardsImmunityAndOtherOwnersAlone',
      ['HytaleDamageAdapter.GearResistanceFilter.applyResolved','assertEquals(100*(1-defense.percent("WA-078")),positive.getAmount()',
       'assertEquals(once,positive.getAmount())','assertEquals(100,excluded.getAmount())'],
      'Actual native FilterDamage writes each matching elemental amount once with direct-origin, immunity, NPC and Physical controls.')
owner(['WA-082'],'gear.MasterAffixCombatCompletionTest#criticalDotDurationAllResistanceAndDisplacementHaveUnrolledAndInvalidConditions',
      ['NativeAffixHostileDisplacement.applyNpcDestination(position,reduce)','NativeAffixHostileDisplacement.applyNpcDestination(position,blocked)',
       'assertEquals('],
      'Production NPC displacement application writes native TransformComponent position; blocked plan cannot move recipient.')
for n in range(94,98):
    method={94:'wa094AcceptedPostMitigationHitCreditsHealthOnce',95:'wa095AcceptedPostMitigationHitCreditsManaOnce',
            96:'wa096AcceptedPostMitigationHitCreditsLifeLeechOnce',97:'wa097AcceptedPostMitigationHitCreditsManaLeechOnce'}[n]
    owner(ids(n,n),'combat.hytale.GearAppliedHitRecoveryOwnerTest#'+method,
          ['GearAppliedHitRecovery.accepted','assertEquals(expected,runtime.pay','assertEquals(0,blank.pay','assertEquals(0,rejected.pay'],
          'Default recovery owner consumes accepted post-mitigation loss receipt; exact once-only Health/Mana credit and unrolled/cancelled controls.',helpers=['hit'])
owner(['WA-098'],'execution.hytale.GearRecoveryRewardDispatchTest#wa098AcceptedSharePaysDefaultOwnerOnceWithinLiveCreditWindow',
      ['assertEquals(900+expected,port.health','assertFalse(runtime.hasPending(uncredited'],
      'Accepted encounter share feeds frozen death gear to default kill recovery owner; no duplicate or uncredited payment.')
owner(['WA-111'],'execution.hytale.GearReceivedHealingOwnerTest#wa111AdmittedPlayerRecipientChangesActualRequestAndClipsNativeCredit',
      ['assertEquals(base,HytaleSupportSystem.receivedHealing(base,unrolled)','assertEquals(request,gain',
       'assertEquals(100,capped.value'],
      'Player healing owner changes actual request and native resource credit, with unrolled/broken/cap controls.',helpers=['recipient'])
for ident in ids(121,133):
    p=proofs[ident]
    # Already registered direct GearSkillRanks invocation and exact CASES entry.
    for field in ['positiveTest','controlTest','negativeTest']:
        p['sourceAnchors'][field]['required'].extend([
            'assertTrue(positive.applied>control.applied',
            'assertEquals(control.applied,negative.applied',
            'assertEquals(control.applied*1.02,positive.applied'])
    p['resolvedQualificationHoles']=p['qualificationHoles'];p['qualificationHoles']=[]
    p['qualificationResolution']='Canonical dispatch now measures existing damage/heal/summon owner recipient output at authored mastery step; saved rank unchanged.'
owner(['WA-134'],'gear.MasterAffixCombatCompletionTest#wa134BleedAdmissionHasUnrolledWrongChannelAndNoProcControls',
      ['assertEquals(5,suppressed.regenerate','assertEquals(10,normal.regenerate'],
      'Native RegeneratingValue delegate measures exact regeneration reduction after canonical Bleed admission and unrolled restoration.')
owner(['WA-135','WA-137'],'gear.SignatureConsumerProofTest#crushingAndBarbedReachRecipientWithIcdExpiryAndAdmissionControls',
      ['assertEquals(960,victim.hp(TARGET)','assertEquals(492,recipient.hp(TARGET)','assertEquals(500,recipient.hp(OTHER)',
       'assertEquals(0,deniedPort.drain'],
      'Signature queue drains native Damage through controlled recipient calculation; source, ICD, expiry, NoProc and target isolation measured.')
owner(['WA-138','WA-140'],'gear.SignatureConsumerProofTest#armorBreakAndFortifyMutateSubmittedNativeHitsAndExpire',
      ['assertTrue(brokenRecipient.hp(TARGET)<normalRecipient.hp(TARGET))','assertEquals(405,fortifiedRecipient.hp(OWNER))',
       'assertEquals(ordinary.getAmount(),expired.getAmount())'],
      'Production native defense factors change submitted hit and recipient Health; unrolled, expiry, wrong recipient and NoProc controls.')
owner(['WA-139'],'gear.SignatureConsumerProofTest#cullDrainsToDeathOnceAndRejectsThresholdBossProtectedAndDuplicate',
      ['assertEquals(0,survivor.hp(TARGET))','assertEquals(1,survivor.deaths.get(TARGET))','assertEquals(0,control.drain'],
      'Cull production child drains into one recipient death; threshold, boss, protected, unrolled/broken and duplicate exclusions.')
owner(['WA-142','WA-143'],'gear.SignatureConsumerProofTest#retributionAndKillBurstDeliverNoProcReflectedAndCreditedRecipients',
      ['assertEquals(500-shield.affixes().getFirst().value(),recipient.hp(TARGET)',
       'assertEquals(58,deaths.hp(OTHER)','assertEquals(0,noCredit.drain','assertEquals(0,noRoll.drain'],
      'Native reflected and credited kill children change controlled recipient Health once; origin, NoProc, credit and unrolled controls.')
owner(['WA-144'],'BorrowedSkillOwnerTest#wa144AcceptedBorrowedRequestHitsCanonicalOwnerWithoutLearningAndWithdrawalRejectsSameRequest',
      ['assertTrue(port.damage>0||port.health>0','assertFalse(loadouts.getPresentationView(actor).state().learnedSkills.contains(skill))',
       'assertEquals(SkillExecutionResult.Status.REJECTED,execution.request(request,port).status())'],
      'Normal ownership rules grant canonical paid rank-one execution from admitted item, measure damage/heal, reject withdrawal and leave learning unchanged.')
owner(['WA-145','WA-146'],'ItemSkillChildExecutionTest#wa145And146QueuedCanonicalBoltsHitOneProjectileRecipientWithSourceControls',
      ['assertTrue(','assertEquals('],
      'Canonical Fire/Frost projectile planning/contact yields one kernel recipient damage result; source invalidation, NoProc and duplicate controls.')
owner(['WA-147'],'ItemSkillChildExecutionTest#wa147SuccessfulBlockChildCreditsActualSelfHealthOnceWithCap',
      ['assertEquals(','assertTrue('],
      'Successful block child enters SupportRuntime and credits capped self Health once; failed block/invalid source/duplicate controls.')
owner(ids(148,150),'ItemAuraRuntimeTest#wa148To150EquipmentResolutionActivatesWithdrawsAndNeverLayersRecipientEffects',
      ['assertTrue(magnitude>0,id)','assertEquals(0,effect(h,id),1e-9,id+" unrolled control")',
       'assertEquals(magnitude,effect(h,id),1e-9,id+" no second layer")',
       'assertEquals(0,effect(h,id),1e-9,id+" withdrawn recipient")'],
      'Admitted equipment activates actual support recipient effect once; unrolled, backpack, broken/wrong-slot and removal restore baseline.')
owner(['WA-156'],'gear.NativeAffixPickupTransferTest#wa156ExtendedAdmissionCommitsExactlyOneProtectedStockTransferAndPreservesNoFit',
      ['assertTrue(NativeAffixMaterialPickup.admit','assertFalse(NativeAffixMaterialPickup.admit',
       'assertEquals(7,saved.entries().stream().mapToInt','assertEquals("FINALIZED",loot.finishSpatialStock',
       'assertEquals(SpatialBagAggregate.Outcome.NO_FIT,denied.receipt().outcome())'],
      'Extended admission routes through protected stock receipt, real bag insertion and BSON persistence once; full bag preserves source and receipt count.')
# These existing registered tests now contain the required final measurement.
for ident,note in {
    'WA-085':'Existing per-ID AdvancedStatsAffixProjectionTest compares displayed raw STR to derived owner and exact accepted delta, then restoration.',
    'WA-112':'User scope correction requires generic successful-cleanse receipt, not a new authored cleanse skill. Existing registered simulator removes actual status then consumes receipt into measured barrier; no-status/unrolled/duplicate/expiry controls pass.',
    'WA-141':'NativeTwinCommitTest includes same-root unrolled gate rejection and invalid source controls; actual offhand Damage child distinct, 0.4 power, NoProc. Native selector codec is qualified; live collision belongs to connected QA.',
    'WA-151':'Existing registered test now invokes actual magicFindBreakdown from admitted non-QA gear, freezes death snapshot, proves later removal cannot change selected loot.',
    'WA-152':'Existing registered test now freezes HytaleGearEquipment.goldFind into DeathPlan before equipment removal; persisted contribution-share Gold and duplicate control exact.'
}.items():
    p=proofs[ident];p['resolvedQualificationHoles']=p['qualificationHoles'];p['qualificationHoles']=[];p['qualificationResolution']=note
document['note']='R165 reviewed offline production-owner evidence. FUNCTIONAL requires fresh passing assertions, legal admission, recipient/adapter measurement, UI and Sentinel proofs. WA-155 renderer calibration remains explicitly gated for owner connected QA.'
path.write_text(json.dumps(document,indent=2)+'\n',encoding='utf-8')
