

# **ORBIS MODEL DISTILLATION** **& POST-TRAINING HARNESS**

## ***Teacher-Guided Corpus Generation, PEFT/QLoRA Adaptation,*** ***Preference Alignment, Candidate Evaluation, and Safe Model Promotion***

Subsystem of ImmersiveNPCs / Orbis

**Codex Implementation Specification**

Version 1.0 | 2 September 2026

| AUTHORITATIVE PURPOSE. Extend the existing Orbis Hardening, Epistemic Cognition, Sentinel, and Autonomous Evaluation architectures with an offline, versioned model post-training pipeline. The system converts only proven provider-realization weaknesses into curated training examples, trains candidate Nemotron adapters or checkpoints outside Hytale, evaluates every candidate against production-parity Orbis contracts, and promotes no model unless authority, factuality, latency, hardware, license, and rollback gates all pass. |
| :---- |

| DISTILLATION CORPUSGenerate and curate final-answer, contract, critique, and preference examples from authoritative Orbis scenarios. | SAFE POST-TRAININGUse SFT with LoRA/QLoRA first; add DPO only after a clean SFT baseline. Full-logit distillation and RL remain optional. |
| :---- | :---- |
| **PRODUCTION-PARITY EVALUATION**Compare base and candidate models through the same OrbisRuntimeFactory, evidence, AnswerPlan, claim firewall, and matrix gates. | **CONTROLLED PROMOTION**Version datasets, teacher policy, adapters, merged checkpoints, GGUF artifacts, quantization results, deployment manifests, and rollback. |

*For Codex implementation use. Existing repository ownership and current Orbis contracts remain authoritative. This specification does not authorize runtime self-modification, automatic production training, or use of any teacher provider whose terms do not permit cross-model training.*

# **Contents**

1\. Implementation Directive

2\. Executive Decision and Training Boundary

3\. Meaning of Hardening, NPC Learning, and Model Training

4\. Scope, Goals, and Non-Goals

5\. Existing Orbis Ownership and Integration Boundary

6\. High-Level Architecture

7\. Operating Modes, Environments, and Trust Levels

8\. Base Model, License, and Artifact Preconditions

9\. Training Task Taxonomy

10\. Corpus Sources and Admission Rules

11\. Teacher Provider Architecture

12\. Ground-Truth and Oracle Hierarchy

13\. Training Example and Dataset Domain Model

14\. Dataset Assembly and Production-Prompt Parity

15\. Synthetic Generation and Variant Expansion

16\. Curation, Filtering, Deduplication, and Decontamination

17\. Dataset Splits and Leakage Prevention

18\. Training Backends and Hardware Profiles

19\. PEFT/LoRA Architecture Preflight

20\. Supervised Fine-Tuning Stage

21\. Preference Optimization Stage

22\. Optional Full-Logit Distillation

23\. Optional RL/GRPO Stage

24\. Model, Adapter, Dataset, and Run Registry

25\. Base-vs-Candidate Evaluation Protocol

26\. Orbis-Specific Metrics

27\. Promotion Gates

28\. Quantization and Runtime Packaging

29\. Ollama and llama.cpp Deployment Paths

30\. Shadow, Canary, Promotion, and Rollback

31\. Active Learning from Incidents

32\. Performance, Cost, and Scheduling

33\. Security, Privacy, Licensing, and Data Governance

34\. Failure Handling and Abort Conditions

35\. Tooling, Commands, Reports, and Artifact Layout

36\. Bounded Implementation Program

37\. Training Test Matrix and Critical Scenarios

38\. Definition of Done

Appendix A. Proposed Component Map

Appendix B. Normative Data Contracts

Appendix C. Initial Curriculum Catalog

Appendix D. Baseline Training Configurations

Appendix E. Promotion Checklist

Appendix F. Project and External Sources

# **1\. Implementation Directive**

This design adds a separate offline model post-training architecture above the existing Orbis Autonomous Conversation Evaluation and Training Harness. It does not replace the Hardening Matrix, Epistemic Cognition Core, Runtime Degradation Sentinel, ConversationMatrixHarness, OrbisRuntimeFactory, or production provider adapters. Its purpose is to make the selected small local language model better at the narrow role Orbis assigns to it, while retaining Orbis as the authority for truth, memory, action, persistence, and delivered speech.

## **1.1 Required reading**

* Orbis Conversational Pipeline Hardening Matrix and all active ownership reports.  
* Orbis Epistemic Cognition Technical Design.  
* Orbis Runtime Degradation Sentinel and Self-Healing Technical Design.  
* Orbis Autonomous Conversation Evaluation & Training Harness Technical Design and the R090 implementation/audit reports.  
* Orbis LLM Runtime \- llama.cpp Sidecar Integration Technical Design, even if Ollama remains the active runtime.  
* Current active model card, license, tokenizer, chat template, Hugging Face configuration, and deployed GGUF metadata.  
* Current provider adapter, prompt renderer, contract validator, claim firewall, evaluation host, scenario DSL, fixture library, and model/runtime manifests.

## **1.2 Implementation discipline**

* **Offline only.** No training process runs from an active Hytale world, server tick, voice callback, player command, or production provider process.  
* **One authority per concern.** The post-training harness may export data and evaluate candidates. It may not become a second prompt builder, truth store, claim validator, or deployment owner.  
* **Diagnose before training.** A failure is eligible for model training only after the existing harness proves that route, evidence, Answerability, AnswerPlan, prompt composition, contract budget, firewall, canonical assembly, and lifecycle were correct.  
* **No automatic promotion.** Codex may launch training in an explicit repository session. A runtime incident, teacher response, or successful checkpoint may never replace the production model automatically.  
* **Version everything.** Every dataset row, teacher response, training run, adapter, merged checkpoint, GGUF, quantization, evaluation result, license attestation, and promotion decision carries immutable identity and hashes.  
* **Preserve rollback.** The active base model and last-known-good model remain available until the candidate passes all deterministic, live-headless, quantized-runtime, and connected Hytale gates.  
* **No sentence-specific tuning.** Train general behaviors and typed contracts. Do not use a small set of exact phrases to disguise a routing or oracle defect.

| DEPLOYMENT GATE. No candidate adapter, merged checkpoint, or GGUF becomes production-ready because training loss improved or a small demonstration looked better. Promotion requires the complete Orbis-specific authority, quality, contamination, latency, hardware, quantization, and connected Hytale matrix. |
| :---- |

# **2\. Executive Decision and Training Boundary**

Decision: implement a teacher-guided, sequence-level distillation and post-training pipeline that consumes reviewed examples from the existing Orbis evaluation corpus, trains parameter-efficient Nemotron candidates, and returns those candidates to the existing production-parity evaluation harness. The first supported method is supervised fine-tuning with LoRA or QLoRA. Direct Preference Optimization is a later optional stage. Full-logit distillation and reinforcement learning remain conditional research paths.

## **2.1 Why model training is now appropriate**

The earlier Epistemic design explicitly deferred fine-tuning until Orbis had isolated the model ceiling behind a mature cognitive architecture. That prerequisite is now materially closer: the production/evaluation composition is shared, live Nemotron campaigns can expose stage-by-stage evidence, deterministic fixtures preserve repaired invariants, and the Sentinel can convert runtime incidents into regression candidates. The model may now be trained only on residual provider behavior that remains after those boundaries are proven correct.

## **2.2 Root-cause routing rule**

| Observed failure | Authoritative owner | Training eligibility |
| :---- | :---- | :---- |
| **Wrong dialogue act, referent, or query kind** | DialogueStateTracker / EpistemicQueryPlanner | NO. Repair Orbis source and freeze a regression. |
| **Correct query but wrong or missing evidence** | EvidenceRetriever / BeliefResolver / EvidencePacketBuilder | NO. |
| **Evidence correct but Answerability or AnswerPlan wrong** | AnswerabilityClassifier / AnswerPlanner | NO. |
| **Plan correct but rendered prompt omits or changes evidence** | ContextProfileBuilder / ContractBudgetPlanner | NO. |
| **Prompt and contract correct; model omits a required proposition** | Provider realization | YES, after exact replay and oracle proof. |
| **Prompt correct; model invents a clause and firewall blocks it** | Provider realization, safely contained | YES, high-value negative/preference example. |
| **Firewall permits unsupported content** | EpistemicClaimFirewall | NO. |
| **Canonical answer correct but memory/persistence mutates incorrectly** | Ingestion or persistence owner | NO. |
| **Response is grounded but awkward, repetitive, or out of character** | Provider style realization | YES, preference data after authority gates pass. |
| **Unclear or mixed evidence** | Codex/operator review | HOLD. Do not train until classified. |

## **2.3 Chosen training sequence**

CURRENT PRODUCTION BASELINE  
        |  
        v  
Existing Orbis Evaluation Harness  
        |  
        \+--\> ORBIS\_BUG \------------------\> source repair \+ regression  
        |  
        \+--\> PROVIDER\_REALIZATION\_GAP \---\> reviewed training candidate  
                                             |  
                                             v  
                                   Corpus curation \+ split  
                                             |  
                                             v  
                                  SFT LoRA/QLoRA candidate  
                                             |  
                              optional DPO preference stage  
                                             |  
                                             v  
                          BF16 adapter/merged candidate evaluation  
                                             |  
                             GGUF conversion \+ quantization evaluation  
                                             |  
                                             v  
                           SHADOW \-\> CANARY \-\> CONNECTED HYTALE  
                                             |  
                                             v  
                                 explicit model promotion

# **3\. Meaning of Hardening, NPC Learning, and Model Training**

| Process | What changes | Who changes it | This design |
| :---- | :---- | :---- | :---- |
| **Engineering hardening** | Orbis Java/Python source, contracts, retrieval, validation, lifecycle, and tests. | Codex in an explicit repository session. | Consumed as prerequisite; not replaced. |
| **NPC cognitive learning** | Persistent beliefs, memories, relationships, commitments, and supported reflections. | Existing Orbis evidence and revision systems. | Validated, never used as a shortcut to model training. |
| **Model post-training** | LoRA adapter weights or a derived Nemotron checkpoint. | Offline trainer launched by Codex/operator. | Primary subject. |
| **Runtime adaptation** | Prompt context, retrieved state, sampling profile, caches. | Existing runtime owners. | Not weight training. |
| **Teacher labeling** | Candidate target responses, critiques, preferences, and tags. | Eligible teacher provider plus deterministic Orbis oracles. | Dataset input only, never truth authority. |

## **3.1 What “Codex trains Orbis” means**

Codex may orchestrate the complete offline process: select eligible failure families, export production-parity prompts, request labels from an approved teacher, run deterministic validation, curate and split data, launch training, inspect metrics, compare candidates, package artifacts, and propose promotion. Codex does not update weights by conversational correction alone. A training backend must perform gradient updates and produce a new adapter or checkpoint.

## **3.2 What the student should learn**

* Follow the exact production chat template and route-specific output contract.  
* Realize an existing AnswerPlan directly before adding personality or elaboration.  
* Use provided evidence and source framing without inventing properties, events, relationships, possessions, locations, or action results.  
* Abstain or clarify naturally when Answerability requires it.  
* Distinguish player testimony, NPC testimony, direct observation, authored canon, self-state, memory, and action result in surface language.  
* Preserve character voice without turning personality into fabricated biography.  
* Produce short, valid structured outputs on the few routes that use them.  
* Avoid repetitive uncertainty, stock phrases, overlong answers, and generic assistant framing.  
* Handle multi-turn referents, corrections, temporal updates, and NPC-to-NPC testimony within the bounded context Orbis supplies.

## **3.3 What must remain external to the student**

* World truth and current Hytale state.  
* Persistent NPC memory, belief, relationship, inventory, schedule, task, and action state.  
* Retrieval and evidence sufficiency.  
* Capability and physical action validation.  
* Atomic claim authorization.  
* Canonical speech commitment and delivered-history truth.  
* Runtime health, resource scheduling, incidents, and recovery.  
* Production profile facts. The model should consume profile data, not memorize Mara, Lycander, Jonalith, or future NPCs.

# **4\. Scope, Goals, and Non-Goals**

## **4.1 Goals**

* Build a reproducible pipeline from Orbis failure evidence to reviewed SFT and preference datasets.  
* Train one general Orbis-specialized student model or adapter that works across profiles, rather than one model per NPC.  
* Reduce raw provider hallucination, firewall intervention, missing required propositions, invalid contracts, over-abstention, and style drift.  
* Preserve or improve warm latency and consumer-hardware coexistence after quantization.  
* Evaluate base and candidate models on identical prompts, state snapshots, seeds where applicable, and Orbis policies.  
* Prevent training contamination from production truth, generated hallucinations, hidden reasoning, private data, or evaluation holdouts.  
* Support local smoke tests and scalable remote/cloud training without changing shipping runtime.  
* Produce deterministic artifact lineage and one-command rollback.  
* Feed verified new failures back into an active-learning queue without continuous online training.

## **4.2 Non-goals**

* Training a foundation model from scratch.  
* Making Nemotron responsible for world truth, retrieval, memory, action execution, or persistence.  
* Fine-tuning Moonshine, Chatterbox, MiniCPM-o, or other providers in this design.  
* Automatically editing Orbis source based on teacher text.  
* Automatically training from live player conversations.  
* Persisting or training on hidden chain-of-thought.  
* Using an LLM judge as the sole oracle.  
* Promising that a 4B student becomes generally equivalent to a frontier teacher.  
* Running training on the same GPU while Hytale is active.  
* Using OpenAI, ChatGPT, or Codex outputs as cross-vendor training data unless current terms and an explicit permitted exception are documented for the exact account and use.  
* Replacing the current GGUF runtime before a separately approved runtime benchmark.

# **5\. Existing Orbis Ownership and Integration Boundary**

| Concern | Existing authoritative owner | Post-training harness role |
| :---- | :---- | :---- |
| **Turn, branch, floor, cancellation, terminal state** | OrbisTurnCoordinator | Observe and replay. Never create a parallel lifecycle. |
| **Route, context, contract, budget, deadline** | TurnPlanCompiler / ContextProfileBuilder / ContractBudgetPlanner | Export immutable production-parity training inputs. |
| **Evidence, belief, Answerability, AnswerPlan** | Epistemic Cognition services | Use as ground-truth structure and validation constraints. |
| **Provider generation** | Current Nemotron provider adapter | Supply base/candidate model identity and collect outputs. |
| **Objective claim authority** | EpistemicClaimFirewall | Gate teacher targets and candidate outputs. |
| **Canonical response** | CanonicalSpeechLedger / response assembly | Evaluate exact final response, not hidden reasoning. |
| **Actions and world effects** | AgentOperation / Hytale | Use sandbox fixtures or connected results; never synthesize action truth. |
| **Persistent cognition** | Memory / belief / relationship stores | Use cloned or in-memory state only for dataset generation. |
| **Incidents and health** | Runtime Degradation Sentinel | Admit eligible failure signatures into the training candidate queue. |
| **Model deployment** | Existing provider/model manager plus operator | Package candidates and produce promotion manifest; never self-promote. |

## **5.1 Required new seam**

Add one offline ModelPostTrainingCoordinator that references existing evaluation artifacts by immutable IDs. It must not accept arbitrary raw prompts or production database handles. Its inputs are reviewed TrainingCandidate records exported by the current evaluation harness or Sentinel candidate pipeline.

EvaluationRunReport / OrbisIncident  
        |  
        v  
TrainingEligibilityClassifier  
        |  
        \+--\> SOURCE\_REPAIR\_REQUIRED  
        \+--\> NOT\_TRAINABLE  
        \+--\> NEEDS\_REVIEW  
        \+--\> MODEL\_TRAINING\_ELIGIBLE  
                    |  
                    v  
          DistillationCorpusBuilder  
                    |  
                    v  
             Offline trainers  
                    |  
                    v  
         CandidateModelEvaluator  
                    |  
                    v  
          ModelPromotionController

## **5.2 No duplicate prompt authority**

* Training examples store the exact rendered production messages and hashes produced by ContextProfileBuilder.  
* The training tool may add trainer metadata outside the message sequence, but may not silently rewrite system or user content.  
* Teacher prompts are separate artifacts. They may include an evaluation rubric and structured AnswerPlan, but the final student-training row must match production input shape.  
* Every prompt-template change invalidates or explicitly migrates affected training/evaluation datasets.

# **6\. High-Level Architecture**

                       EXPLICIT CODEX REPOSITORY SESSION  
                                      |  
                                      v  
                         ModelPostTrainingCoordinator  
          \+---------------------------+---------------------------+  
          |                           |                           |  
          v                           v                           v  
 Training Candidate Queue     Teacher Labeling Service     Artifact Registry  
          |                           |                           |  
          v                           v                           |  
 Eligibility \+ Oracle \------\> Curated Corpus Builder \<-------------+  
          |                           |  
          |                           v  
          |                 Train / Dev / Holdout Manifests  
          |                           |  
          |             \+-------------+--------------+  
          |             |                            |  
          |             v                            v  
          |      SFT LoRA / QLoRA              optional DPO  
          |             |                            |  
          |             \+-------------+--------------+  
          |                           v  
          |                 BF16 Candidate Evaluator  
          |                           |  
          |                           v  
          |                  Merge / GGUF / Quantize  
          |                           |  
          |                           v  
          \+----------------\> Quantized Runtime Evaluator  
                                      |  
                                      v  
                       SHADOW \-\> CANARY \-\> CONNECTED HYTALE  
                                      |  
                                      v  
                           EXPLICIT PROMOTE / ROLLBACK

## **6.1 Three independent control planes**

| Plane | Purpose | May write |
| :---- | :---- | :---- |
| **Evaluation plane** | Run scenarios, collect traces, diagnose boundaries, score base/candidate outputs. | Evaluation artifacts only. |
| **Training plane** | Build datasets and update candidate adapter/checkpoint weights. | Dedicated offline training root only. |
| **Production plane** | Serve the explicitly promoted model inside Orbis. | Existing production runtime/cache and normal NPC state; never training artifacts. |

## **6.2 One-way artifact promotion**

Data flows from production incidents into sanitized candidate records, then into offline datasets, training runs, and evaluated model artifacts. No raw training process can write back into production. Promotion copies only a signed/hashed model bundle and manifest through the existing model manager after gates pass.

## **6.3 First implementation boundary**

* No teacher API integration at first. Import a small reviewed seed corpus and prove schemas, lineage, split integrity, one-batch LoRA feasibility, and base/candidate evaluation.  
* Then add one eligible teacher provider behind a strict interface.  
* Then run a small SFT pilot. Do not add DPO, full-logit distillation, or RL until the SFT pilot and quantized packaging are proven.  
* Do not change the active production Nemotron during D0-D9.

# **7\. Operating Modes, Environments, and Trust Levels**

| Mode | Description | Allowed side effects |
| :---- | :---- | :---- |
| **OFF** | Post-training tooling unavailable to normal runtime. | None. |
| **CORPUS\_AUDIT** | Validate schemas, provenance, licensing, hashes, deduplication, and splits. | Write reports only. |
| **TEACHER\_GENERATION** | Generate candidate labels or preferences with an approved teacher. | Write unreviewed teacher artifacts. |
| **DATASET\_BUILD** | Run deterministic oracles, filtering, deduplication, and split assignment. | Write immutable dataset version. |
| **TRAIN\_SMOKE** | One batch or very small run used only to prove architecture and memory fit. | Write disposable candidate checkpoint. |
| **TRAIN\_PILOT** | Small SFT or DPO experiment with fixed train/dev manifests. | Write versioned candidate. |
| **TRAIN\_FULL** | Approved full corpus training. | Write versioned candidate; no production access. |
| **EVAL\_BF16** | Evaluate base plus adapter or merged BF16 candidate. | Evaluation artifacts only. |
| **EVAL\_QUANTIZED** | Evaluate GGUF candidates through the actual runtime path. | Evaluation artifacts only. |
| **SHADOW** | Candidate runs alongside base on copied turns but cannot speak, act, or persist. | Shadow reports only. |
| **CANARY** | Operator-selected test profiles/sessions use candidate. | Normal test-world behavior only. |
| **PROMOTED** | Candidate is the configured production model. | Normal Orbis behavior under all existing guards. |
| **ROLLED\_BACK** | Last-known-good model restored. | Normal Orbis behavior. |

## **7.1 Trust levels**

| Artifact | Default trust | Promotion requirement |
| :---- | :---- | :---- |
| **Live player transcript** | UNTRUSTED\_CONTENT | May seed a scenario only after sanitization and authority classification. |
| **Provider failure output** | NEGATIVE\_EXAMPLE\_CANDIDATE | Never a positive target. |
| **Teacher output** | UNVERIFIED\_LABEL | Pass deterministic oracles, license gate, contamination gate, and review policy. |
| **Deterministic AnswerPlan realization** | HIGH\_AUTHORITY\_TARGET | Must still preserve production contract and style constraints. |
| **Human-authored target** | REVIEWED\_TARGET | Must pass the same claim and contract validators. |
| **Training row** | CURATED | Dataset builder signs canonical payload and assigns split. |
| **Adapter/checkpoint** | CANDIDATE | All evaluation/promotion gates. |
| **Quantized GGUF** | CANDIDATE\_RUNTIME | Must independently pass because quantization can change behavior. |
| **Promoted model** | PRODUCTION\_APPROVED | Explicit operator promotion plus rollback bundle. |

## **7.2 Environment isolation**

* Training runs use a dedicated root outside the Hytale save and mod data roots.  
* The trainer process has no write access to production profiles, worlds, memories, inventories, or active model directories.  
* Teacher credentials are read from environment variables or a secret manager and are never written into datasets, reports, JARs, traces, or model metadata.  
* Hytale and the foreground Orbis runtime must be stopped on a single-GPU training host.  
* All environment dependencies are pinned in a lock file or container digest. A run records Python, CUDA, driver, PyTorch, Transformers, PEFT/TRL/NeMo, tokenizer, and custom-code revisions.

# **8\. Base Model, License, and Artifact Preconditions**

## **8.1 Canonical student checkpoint**

Training starts from the official NVIDIA-Nemotron-3-Nano-4B-BF16 Safetensors checkpoint, or an exactly pinned compatible upstream checkpoint approved by the operator. The current production Q4\_K\_M GGUF is an inference artifact and is not the source checkpoint for gradient training. QLoRA may quantize the BF16 base inside the training process, but the lineage still begins from the pinned BF16 model and tokenizer.

| Required identity | Recorded value |
| :---- | :---- |
| **Model repository and revision** | Exact Hugging Face repository plus immutable commit/revision. |
| **Weight hashes** | SHA-256 for every Safetensors shard and index. |
| **Tokenizer** | Tokenizer files, tokenizer class, special tokens, hash, and revision. |
| **Chat template** | Exact tokenizer template and Orbis-rendered production message format. |
| **Model config** | config.json, generation config, remote-code revision, architecture/module inventory. |
| **License** | License text hash, model card URL, notice obligations, review date. |
| **Runtime baseline** | Active GGUF filename/hash, quantization, Ollama/llama.cpp version, sampler policy. |

## **8.2 Current Nemotron facts and implications**

* The official 4B BF16 model card identifies a 3.97B-parameter Nemotron Hybrid architecture dominated by Mamba-2 and MLP layers with four attention layers.  
* The model supports reasoning-on and reasoning-off behavior. Orbis must preserve its existing route-specific reasoning policy rather than training one universal mode.  
* The model card identifies gaming NPCs and local voice assistants as intended edge use cases, but still requires use-case-specific testing before deployment.  
* The architecture uses custom code. Standard PEFT target-module assumptions such as only q\_proj/v\_proj may be wrong; module discovery and an adapter round-trip are mandatory.  
* The current model is English-focused for the supported Orbis baseline. Multilingual expansion is a separate curriculum and evaluation decision.

## **8.3 NVIDIA license gate**

The NVIDIA Nemotron Open Model License permits commercial use and creation/distribution of derivative works, subject to its conditions. Any distributed fine-tuned model or adapter bundle must include the license, retain applicable notices, include the required NOTICE attribution, and preserve a machine-readable license manifest. Codex must not infer license compliance from the model card label alone.

| REQUIRED NOTICE. When redistribution conditions apply, preserve NVIDIA notices and include the statement required by the active NVIDIA Nemotron Open Model License. The exact current license text and its hash must ship with the model bundle. |
| :---- |

## **8.4 Teacher provider legal gate**

A teacher model is eligible only when its current terms permit using outputs to train this third-party student model. As of this design date, OpenAI's individual Terms of Use prohibit using Output to develop models that compete with OpenAI, and the Services Agreement contains a similar restriction except for a documented Permitted Exception. Therefore ChatGPT, GPT-5.6 Sol, Codex, or another OpenAI service must not be configured as an automated cross-vendor Nemotron teacher unless the operator records an applicable contractual permission or legal determination for this exact use.

| Teacher eligibility field | Requirement |
| :---- | :---- |
| **termsReviewedAt** | Current review date. |
| **termsDocumentUrl** | Exact governing terms. |
| **termsDocumentHash** | Archived text/PDF hash where permitted. |
| **crossVendorTrainingAllowed** | TRUE required. UNKNOWN and FALSE block generation. |
| **outputRetentionAllowed** | TRUE required for dataset storage. |
| **commercialUseAllowed** | Required if the mod/model will be distributed or monetized. |
| **attribution/notice** | Recorded and propagated into dataset/model manifests where required. |
| **operatorApproval** | Explicit ID, timestamp, and rationale. |

Codex may still use OpenAI models for ordinary software engineering, design review, or analysis consistent with the governing terms. That does not automatically make their output eligible as model-training labels. The initial implementation should prefer an open-weight or otherwise contractually eligible teacher.

## **8.5 Preflight stop conditions**

* Base model or tokenizer hash differs from the approved manifest.  
* Remote model code cannot be pinned or safely loaded.  
* License or teacher terms are missing, changed, or unresolved.  
* Training backend cannot construct the model without unreviewed source execution.  
* A one-batch forward/backward pass cannot complete inside the selected hardware profile.  
* The adapter cannot be saved, reloaded, disabled, and produce a measurable reversible delta.  
* The exact production chat template cannot be reproduced.  
* The candidate cannot be converted or served through any approved runtime path.

# **9\. Training Task Taxonomy**

Training is organized by the output responsibility assigned to the student, not by broad NPC topic. Each example carries one primary TaskType, required Orbis contract, allowed/forbidden proposition set, and evaluation rubric.

| TaskType | Student objective | Primary supervision |
| :---- | :---- | :---- |
| **DIRECT\_ANSWER\_REALIZATION** | State required known proposition first, then concise character elaboration. | SFT target. |
| **SOURCE\_ATTRIBUTED\_RECALL** | Frame memory/testimony/observation with correct source language. | SFT \+ preference. |
| **UNKNOWN\_ABSTENTION** | Express ignorance without objective invention or excessive safety language. | SFT \+ DPO negative pairs. |
| **CONFLICTED\_EVIDENCE** | Represent dispute or uncertainty rather than arbitrarily choosing a fact. | SFT. |
| **CLARIFICATION\_REQUEST** | Ask for exactly the missing object, actor, location, time, or action slot. | SFT. |
| **CORRECTION\_ACKNOWLEDGMENT** | Adopt admissible correction and state the corrected value. | SFT. |
| **SELF\_STATE\_REALIZATION** | Express authored/current emotion, desire, goal, task, or intention. | SFT \+ style preference. |
| **ACTION\_RESULT\_REALIZATION** | Describe only committed/rejected Hytale result. | SFT. |
| **COMPACT\_CHOICE** | Emit strict bounded choice contract with valid fields. | SFT structured output. |
| **DELIBERATIVE\_FINAL** | Convert bounded reasoning result into compact strict final contract. | SFT; optional distillation. |
| **AUTONOMOUS\_DECISION** | Produce valid bounded decision over authorized options. | SFT; optional preference/RL later. |
| **PERSONA\_CONSTRAINED\_STYLE** | Preserve speaking style without new factual claims. | Preference data after authority pass. |
| **MULTI\_TURN\_REFERENT** | Resolve explicit conversational referents and corrections from supplied workspace. | SFT. |
| **NPC\_TESTIMONY** | Communicate another NPC's statement without promoting it to world truth. | SFT. |
| **SAFE\_METAPHOR\_HUMOR** | Use figurative language while keeping objective claim boundary intact. | Preference data. |
| **CONTRACT\_RECOVERY** | Produce valid concise recovery/fallback output when provider contract permits. | SFT negative-control curriculum. |

## **9.1 Excluded task types**

* Open-ended world-state discovery without EvidencePacket.  
* Writing memories, beliefs, relationships, inventories, schedules, or actions directly.  
* Learning NPC-specific facts into weights.  
* Imitating the teacher's hidden reasoning.  
* Unbounded tool execution or executable code generation.  
* Replacing deterministic answers that Orbis already owns, unless the purpose is only stylistic realization.

# **10\. Corpus Sources and Admission Rules**

| Source | Use | Admission rule |
| :---- | :---- | :---- |
| **Frozen Orbis regression fixtures** | High-value known behaviors. | Must identify whether model or source owner caused the original failure. |
| **Live-headless evaluation runs** | Provider weaknesses under real prompts. | Only runs with complete stage evidence and production parity. |
| **Sentinel incidents** | Runtime failures and rare edge cases. | Sanitized, deduplicated, and classified before corpus use. |
| **Deterministic scenario generator** | Coverage of matrix cells and negative controls. | World truth, evidence, and AnswerPlan authored by Orbis fixtures. |
| **Human-authored examples** | Canonical style, edge cases, and gold outputs. | Review plus claim/contract validation. |
| **Eligible teacher-generated examples** | Scale, paraphrase, critique, preference pairs. | Legal gate plus deterministic oracle pass. |
| **Production player conversations** | Future opt-in active learning. | Explicit consent, privacy filter, no raw audio, no automatic positive labels. |
| **Public benchmark data** | General sanity evaluation only. | License review and strict separation from Orbis private holdout. |

## **10.1 Positive-example admission**

* The upstream Orbis route, evidence, Answerability, AnswerPlan, and prompt hashes are valid.  
* The target satisfies every required proposition and no forbidden proposition.  
* The target passes output-contract validation.  
* Every objective claim passes the same EpistemicClaimFirewall policy version used by the scenario.  
* Action language agrees with ActionResult or explicit non-action status.  
* The example contains no hidden reasoning, secret, credential, raw audio, or unapproved personal data.  
* The teacher/provider license attestation is valid.  
* The row is not a duplicate, paraphrased holdout leak, or benchmark contaminant.  
* The example adds a useful coverage cell or approved reweighting purpose.

## **10.2 Negative and preference examples**

A hallucinated or weak student answer may be stored only as a rejected candidate paired with the same immutable prompt and a validated chosen answer. The rejected text must never enter SFT positive targets, world truth, memory, belief, or future teacher context as fact.

* Unsupported objective clause.  
* Required proposition omitted or delayed behind irrelevant text.  
* Over-abstention despite KNOWN evidence.  
* Wrong evidence source framing.  
* False action promise.  
* Invalid structured contract.  
* Repetitive, generic, or non-character response after all authority requirements pass.  
* Excessive verbosity or latency-risking output.

## **10.3 Corpus balance**

Do not build a corpus dominated by failure cases, one NPC, one profile style, one route, or one stock abstention phrase. The builder reports counts by task type, Answerability, source class, claim type, profile archetype, chronology, route, output contract, failure signature, and teacher. Imbalance is corrected by scenario generation or bounded weights, not by duplicating identical rows.

# **11\. Teacher Provider Architecture**

## **11.1 TeacherProvider interface**

interface TeacherProvider {  
    TeacherIdentity identity();  
    TeacherEligibility eligibility();  
    TeacherBatchResult generate(TeacherBatchRequest request);  
    TeacherHealthSnapshot health();  
}

record TeacherBatchRequest(  
    DatasetBuildId buildId,  
    TeacherPromptTemplateId templateId,  
    List\<TeacherItem\> items,  
    TeacherGenerationPolicy policy,  
    OutputSchema outputSchema  
) {}

record TeacherItem(  
    TrainingCandidateId candidateId,  
    ProductionInputSnapshot productionInput,  
    EpistemicTargetSnapshot target,  
    EvaluationRubric rubric,  
    List\<String\> prohibitedContent  
) {}

## **11.2 Teacher roles**

| Role | Input | Output |
| :---- | :---- | :---- |
| **TARGET\_GENERATOR** | Production prompt plus authorized semantic target. | One or more candidate final responses. |
| **CRITIQUE\_REWRITER** | Student output plus deterministic verdicts. | Structured critique codes and corrected final answer. |
| **PREFERENCE\_RANKER** | Two or more authority-passing outputs. | Chosen/rejected ordering plus rubric reason codes. |
| **VARIANT\_GENERATOR** | Scenario semantics and constraints. | Paraphrased user turns or profile variants, not new truth. |
| **CONTRACT\_GENERATOR** | Bounded decision target and schema. | Strict JSON candidate. |
| **ADVERSARIAL\_GENERATOR** | Coverage gap and invariant. | New scenario candidates for deterministic validation. |

## **11.3 Teacher prompting contract**

* Teacher system instructions state that Orbis-provided truth and AnswerPlan are authoritative.  
* The teacher may not add facts, capabilities, memories, relationships, actions, or world state.  
* The teacher returns machine-readable fields: finalAnswer, requiredPropositionsCovered, objectiveClaims, sourceFraming, styleTags, uncertaintyMode, and rejectionReasons.  
* The teacher is not asked for private chain-of-thought. Optional rationale is a short public critique limited to rubric codes and evidence references.  
* Teacher temperature and sampling are versioned. Generate multiple candidates only when diversity is required and cost is approved.  
* Teacher output is treated as untrusted until deterministic validation.

## **11.4 Teacher ensemble and disagreement**

The first implementation uses one approved teacher plus deterministic Orbis oracles. A later ensemble may compare two eligible teachers or teacher plus human label. Disagreement does not become a majority-vote truth decision. It creates NEEDS\_REVIEW unless the deterministic oracle proves one answer invalid.

## **11.5 Interactive Codex boundary**

Do not assume the interactive Codex agent is a callable teacher API. Codex can prepare, inspect, and import reviewed labels during a repository session. Automated dataset generation requires a documented API, local model endpoint, or batch file interface that can be invoked reproducibly.

# **12\. Ground-Truth and Oracle Hierarchy**

| Priority | Oracle | Authority |
| :---- | :---- | :---- |
| **1** | Schema, lifecycle, and production-parity guards | Whether the run is valid enough to judge. |
| **2** | Hytale ActionResult / authoritative world fixture | Physical and economic truth. |
| **3** | Authored canon and typed EpistemicAssertion state | Stable character/world facts and actor beliefs. |
| **4** | EvidencePacket \+ Answerability \+ AnswerPlan | What the response is permitted and required to say. |
| **5** | EpistemicClaimFirewall and action validator | Whether final objective claims are supported. |
| **6** | Deterministic proposition oracle | Required/forbidden semantic content. |
| **7** | Human review | Naturalness, personality, appropriateness, and unresolved ambiguity. |
| **8** | Eligible LLM judge | Secondary quality signal only; never sole release authority. |

## **12.1 Teacher cannot determine truth from prose**

The teacher receives structured target state and must express it. It may not infer that a plausible statement is true because it sounds consistent with the profile. If Answerability is UNKNOWN, the correct target is an allowed uncertainty form, not the teacher's own world knowledge.

## **12.2 Deterministic target construction**

For simple known, unknown, correction, clarification, current perception, self-state, and action-result routes, Orbis should construct a canonical semantic target before teacher generation. The teacher may supply characterful surface variants, but all variants bind to the same proposition IDs.

## **12.3 LLM judge safeguards**

* Randomize candidate order to reduce position bias.  
* Use rubric fields rather than a single 1-10 score.  
* Require evidence citations to immutable proposition IDs, not free-form justification.  
* Disallow the judge from overriding deterministic failure.  
* Calibrate against a human-reviewed set and report agreement/disagreement.  
* Store judge identity, prompt, model revision, and output hash.

# **13\. Training Example and Dataset Domain Model**

## **13.1 Core records**

record DistillationExample(  
    ExampleId id,  
    int schemaVersion,  
    TaskType taskType,  
    SourceProvenance source,  
    ProductionInputSnapshot input,  
    EpistemicTargetSnapshot target,  
    String chosenResponse,  
    Optional\<String\> publicCritique,  
    Set\<PropositionId\> requiredPropositions,  
    Set\<PropositionPattern\> forbiddenPropositions,  
    List\<OracleVerdict\> oracleVerdicts,  
    TeacherIdentity teacher,  
    TeacherEligibilitySnapshot teacherEligibility,  
    ReviewState reviewState,  
    SplitAssignment split,  
    ContaminationFingerprint contamination,  
    ArtifactHashes hashes,  
    Instant createdAt  
) {}

record PreferenceExample(  
    ExampleId id,  
    ProductionInputSnapshot input,  
    String chosen,  
    String rejected,  
    List\<PreferenceReason\> reasons,  
    EpistemicTargetSnapshot target,  
    List\<OracleVerdict\> verdicts,  
    SplitAssignment split  
) {}

## **13.2 ProductionInputSnapshot**

* Exact system/user/assistant message sequence supplied to the student, after current production rendering.  
* TurnExecutionPlan summary: cognition mode, context profile, decision contract, speech contract, budgets, deadlines, and reasoning policy.  
* Prompt-template ID and hash.  
* Tokenizer/chat-template ID and hash.  
* Profile snapshot hash and only the fields included in the prompt.  
* Evidence IDs/source classes and AnswerPlan IDs, stored separately from message text for audit.  
* No live ECS references, credentials, raw audio, hidden reasoning, or unrelated player/NPC data.

## **13.3 EpistemicTargetSnapshot**

* Answerability and claim mode.  
* Required proposition IDs with subject, predicate, object, polarity, source framing, and temporal scope.  
* Forbidden objective proposition patterns.  
* Allowed uncertainty, hypothetical, metaphor, humor, disclosure, and deception modes.  
* Action status and ActionResult references.  
* Maximum response length and route-specific contract.  
* Personality/style constraints that cannot authorize facts.

## **13.4 Canonical serialization**

Every record is serialized canonically with stable field ordering, UTF-8, normalized line endings, and explicit null semantics. The builder computes a SHA-256 over the canonical payload. Any post-review modification creates a new example revision and hash; rows are never edited in place.

## **13.5 Dataset formats**

| Artifact | Recommended format | Purpose |
| :---- | :---- | :---- |
| **Canonical source dataset** | Versioned JSONL with full provenance fields | Audit, rebuild, filtering, split verification. |
| **SFT trainer view** | OpenAI-style messages JSONL or backend-specific mapped dataset | Train only on production input plus chosen response. |
| **Preference trainer view** | prompt/messages \+ chosen \+ rejected | DPO or equivalent. |
| **Evaluation manifest** | JSON/YAML IDs referencing immutable source rows | Avoid copying holdout text into training roots. |
| **Parquet mirror** | Optional | Large-scale curation/analytics; canonical JSONL remains source of truth. |

# **14\. Dataset Assembly and Production-Prompt Parity**

## **14.1 Exact message parity**

The student must be trained on the same message structure it sees in production. Corpus assembly calls the production ContextProfileBuilder or consumes its frozen output. It does not re-create prompts from scenario fields using a training-only template.

* Same system message and route-specific instructions.  
* Same ordering and naming of profile, evidence, memory, relationship, action, and recent-conversation sections.  
* Same reasoning-on/off control and stop conditions.  
* Same strict output schema text for structured routes.  
* Same escaping of untrusted player/model content.  
* Same maximum context and trimming policy, except where an experiment explicitly tests a proposed production change.  
* Same tokenizer and chat template used for training token counts.

## **14.2 Two-view record**

A training example keeps two linked views. The audit view contains typed target and provenance. The trainer view contains only the production messages and assistant target needed by the loss function. Trainer export must be a deterministic projection of the audit record.

AUDIT VIEW  
  input messages  
  \+ TurnExecutionPlan  
  \+ EvidencePacket  
  \+ Answerability  
  \+ AnswerPlan  
  \+ claim constraints  
  \+ provenance/license/review/split

              deterministic projection  
                         |  
                         v

TRAINER VIEW  
  messages: \[system, user/context, assistant target\]  
  loss mask: assistant target only  
  sample weight/task tags outside prompt

## **14.3 Loss masking**

* SFT loss applies to the desired assistant output, not system/user tokens, unless a backend-specific experiment proves otherwise.  
* Reasoning traces are excluded. If a deliberative route uses an internal structured memo, train only an approved compact memo schema that production actually consumes; never train private teacher chain-of-thought.  
* For strict JSON routes, include the exact final JSON only and validate it before admission.  
* For plain dialogue, include the canonical text prior to TTS markup. Prosody metadata is a separate task only if production passes it to the model.

## **14.4 Context-length bands**

| Band | Purpose | Policy |
| :---- | :---- | :---- |
| **SHORT** | Simple self-state, identity, known/unknown, correction. | Preserve fast-route context and response budgets. |
| **MEDIUM** | Memory, relationship, perception, multi-turn referents. | Representative production context; no artificial padding. |
| **LONG** | Conflicts, deliberation, multi-agent history. | Bounded minority; protect against truncation and OOM. |
| **MAX\_BOUNDARY** | Near production contract/context ceiling. | Evaluation-heavy, training-light; never allow a few huge rows to dominate tokens. |

# **15\. Synthetic Generation and Variant Expansion**

## **15.1 Generation sources**

Synthetic generation expands semantics already authorized by Orbis. It may vary names, objects, locations, chronology, profile voice, evidence source, and user phrasing, but it may not invent a new truth relation outside the scenario generator's typed state.

* Paraphrases of user questions and corrections.  
* Entity substitutions from a generated synthetic namespace.  
* Chronology changes: current, prior, superseded, expired, future intention.  
* Evidence-source substitutions with changed expected framing.  
* Positive/negative answerability pairs built from identical surface questions but different evidence.  
* Profile-style variations that preserve the same required propositions.  
* Adversarial distractors and irrelevant recent conversation.  
* Multi-turn referent and correction variants.  
* Multi-NPC testimony chains with explicit provenance.  
* Contract-boundary variants near token/length/schema limits.

## **15.2 Synthetic namespace**

Most generated training rows should use synthetic NPCs, players, items, settlements, and relationships. This teaches the model to follow supplied state rather than memorize current project characters. Mara, Lycander, and Jonalith remain useful evaluation anchors but must not dominate the training corpus.

## **15.3 Counterfactual pairs**

| Shared input form | State A | State B | Training purpose |
| :---- | :---- | :---- | :---- |
| **Where is the key?** | Direct observation: under an oak table. | No compatible evidence. | Known answer versus abstention. |
| **What is Mara holding?** | Fresh held-item observation. | Expired observation. | Freshness and uncertainty. |
| **Did you give me the sword?** | Committed GIVE\_ITEM ActionResult. | Action rejected. | Action truth. |
| **Is Rowan your brother?** | Authored relationship. | Only player testimony. | Canon versus attributed claim. |
| **What did I hide?** | Episodic memory with player testimony. | Related but irrelevant memory. | Retrieval relevance and source framing. |
| **Put it there.** | Both referents bound. | Object/location missing. | Action admission versus clarification. |

## **15.4 Quality controls**

* No synthetic example is admitted because a teacher says it is correct.  
* The scenario generator must produce an internally valid typed world/cognitive snapshot.  
* The same deterministic oracle that scores evaluation validates the target.  
* Synthetic variants inherit a semantic family ID used for grouped splitting.  
* Generated diversity is capped per family to prevent one template from dominating through thousands of paraphrases.

# **16\. Curation, Filtering, Deduplication, and Decontamination**

## **16.1 Curation pipeline**

Raw candidates  
    |  
    v  
Schema \+ provenance validation  
    |  
    v  
Teacher/license eligibility  
    |  
    v  
Orbis route/evidence/AnswerPlan parity  
    |  
    v  
Contract \+ claim \+ action oracles  
    |  
    v  
Privacy and secret scan  
    |  
    v  
Exact normalization/dedup  
    |  
    v  
Fuzzy and semantic family dedup  
    |  
    v  
Holdout/benchmark decontamination  
    |  
    v  
Balance and coverage audit  
    |  
    v  
Human review sample / required review  
    |  
    v  
Immutable dataset manifest

## **16.2 Exact normalization**

* Normalize Unicode to a documented form.  
* Normalize line endings and trailing whitespace without changing semantic content.  
* Canonicalize JSON contracts before hashing.  
* Hash production input, semantic target, chosen response, and full row separately.  
* Deduplicate exact input-target pairs and exact semantic targets with only cosmetic differences.  
* Retain one provenance union when duplicate rows are equivalent and licenses are compatible.

## **16.3 Fuzzy and semantic deduplication**

String equality is insufficient. The builder groups examples by scenario family, normalized entity placeholders, proposition graph, and semantic embedding or similarity score. NeMo Curator or an equivalent pinned pipeline may provide exact, fuzzy, and semantic deduplication, but the Orbis semantic-family key remains authoritative for project-specific leakage control.

* Near-duplicate user paraphrases.  
* Teacher outputs differing only by punctuation or stock preface.  
* Same proposition graph with renamed entities.  
* Repeated incidents with the same FailureSignature.  
* Generated batches that collapse to one response pattern.

## **16.4 Decontamination**

Training data is checked against every dev, test, challenge, connected-Hytale script, and external benchmark prompt. Simple n-gram filtering is not sufficient because paraphrased or translated test items can still leak. Use semantic-family exclusion, entity-normalized fingerprints, fuzzy matching, and an optional strong-model decontamination review.

| Protected set | Required exclusion |
| :---- | :---- |
| **Orbis immutable challenge set** | Exact, fuzzy, semantic-family, and generation-ancestor exclusion. |
| **Gate A/Gate B live scripts** | No exact prompts or paraphrases in training. Equivalent capabilities may appear with disjoint templates/entities. |
| **Historical regressions used for release gates** | Keep a disjoint holdout copy; training variants use separate family branches. |
| **External benchmarks** | Respect licenses and remove overlap before using results for promotion. |
| **Human red-team set** | Never exposed to teacher generation or model selection until final gate. |

## **16.5 Filter reasons**

* ORACLE\_FAIL\_REQUIRED\_PROPOSITION  
* ORACLE\_FAIL\_UNSUPPORTED\_CLAIM  
* ORACLE\_FAIL\_ACTION\_TRUTH  
* CONTRACT\_INVALID  
* UPSTREAM\_ORBIS\_BOUNDARY\_FAILURE  
* TEACHER\_TERMS\_INELIGIBLE  
* PII\_OR\_SECRET\_RISK  
* HIDDEN\_REASONING\_PRESENT  
* EXACT\_DUPLICATE  
* FUZZY\_DUPLICATE  
* SEMANTIC\_FAMILY\_DUPLICATE  
* HOLDOUT\_CONTAMINATION  
* BENCHMARK\_CONTAMINATION  
* PROFILE\_MEMORIZATION\_RISK  
* LOW\_INFORMATION\_OR\_STOCK\_RESPONSE  
* NEEDS\_HUMAN\_REVIEW

# **17\. Dataset Splits and Leakage Prevention**

## **17.1 Split unit**

Split assignment occurs at the semantic family level before teacher generation is finalized. All descendants of one source scenario, incident, proposition graph, user paraphrase template, and entity-substitution family stay in the same split.

| Split | Purpose | Default policy |
| :---- | :---- | :---- |
| **TRAIN** | Gradient updates. | Largest set; balanced by tokens and task families. |
| **DEV** | Early stopping, hyperparameter selection, error analysis. | Disjoint families and profiles where possible. |
| **TEST** | Model selection gate. | Immutable during a training campaign. |
| **CHALLENGE** | Rare/adversarial, multi-turn, unseen profile, and long-context cases. | Never used for selection until candidate finalists. |
| **CONNECTED** | Physical Hytale microphone/lifecycle/world/UI validation. | Not exported as training rows without a new future dataset version. |
| **CANARY** | Production-like test profile sessions. | Separated from training and ordinary live traffic. |

## **17.2 Recommended initial split**

Start near 80/10/10 by semantic family for train/dev/test, while keeping CHALLENGE and CONNECTED as separate protected sets. The exact ratio may change with corpus size, but no split may contain descendants of another split's family.

## **17.3 Profile generalization**

* Hold out complete synthetic profiles and speaking-style combinations.  
* Keep at least one authored profile entirely outside training for test/challenge.  
* Evaluate on names, relationships, goals, and objects absent from training.  
* Measure whether the candidate follows prompt state rather than repeating common Mara/Lycander facts or phrases.  
* Reject a candidate that improves current profiles while collapsing cross-profile variance.

## **17.4 Temporal and multi-turn leakage**

* All turns from one conversation session remain in one split.  
* All sessions derived from the same underlying event timeline remain in one split.  
* Correction/supersession sequences are never split turn-by-turn.  
* NPC-to-NPC testimony chains and their downstream queries remain grouped.

# **18\. Training Backends and Hardware Profiles**

## **18.1 Backend decision order**

| Priority | Backend | Use |
| :---- | :---- | :---- |
| **1** | Hugging Face Transformers \+ PEFT \+ TRL | Initial 4B compatibility smoke, SFT LoRA/QLoRA, DPO, adapter merge/export. |
| **2** | NVIDIA NeMo RL / Automodel DTensor | Preferred when the exact 4B architecture is supported and reproducible; SFT/DPO/LoRA and scaled training. |
| **3** | NVIDIA Nemotron Steps/recipes | Reference lifecycle, curation, conversion, and evaluation patterns; adapt only compatible pieces. |
| **4** | Custom PyTorch trainer | Last resort after documented incompatibility; must preserve all registry, loss, masking, and evaluation contracts. |

## **18.2 Hardware profiles**

| Profile | Intended work | Required behavior |
| :---- | :---- | :---- |
| **LOCAL\_12GB\_EXPERIMENTAL** | One-batch compatibility, tiny LoRA/QLoRA smoke, short-context pilot only. | No promise of feasibility. Batch 1, gradient accumulation, checkpointing, measured VRAM. Hytale stopped. |
| **SINGLE\_24GB\_PRODUCTIVE** | Initial productive 4B QLoRA/LoRA SFT and modest DPO. | Recommended minimum target after measured preflight. |
| **SINGLE\_48GB** | Higher sequence length, batch size, BF16 LoRA, faster evaluation. | Preferred for iteration speed and fewer memory compromises. |
| **MULTI\_GPU** | Full corpus, parallel teacher generation/evaluation, optional advanced distillation/RL. | Pinned topology and distributed config; no assumption of linear scaling. |
| **CPU\_ONLY** | Schema, curation, split, hashing, small deterministic tests. | Training not a production expectation. |

## **18.3 RTX 4070 Ti 12 GB feasibility gate**

The target gaming PC may be capable of a constrained 4B QLoRA smoke, but the design must not promise a productive training run before measurement. The custom Nemotron Hybrid architecture, remote model code, sequence length, optimizer, target modules, and Windows/Linux training stack can materially change memory use.

1. Stop Hytale, Ollama/Nemotron inference, Chatterbox, and other GPU workloads.  
2. Load the pinned BF16 base through the selected QLoRA path.  
3. Run one forward/backward/update with production-format examples at 1024 tokens.  
4. Repeat at 2048 tokens if safe.  
5. Record allocated/reserved VRAM, system RAM, step time, OOM behavior, numerical stability, and adapter delta.  
6. Save/reload the adapter and verify deterministic inference through the reference backend.  
7. If the profile requires unsafe paging, persistent OOM recovery, or unacceptable step time, classify LOCAL\_12GB as unsupported and use a 24 GB+ or cloud host.

## **18.4 Training scheduling**

* No training while an active Hytale server/world depends on the same GPU.  
* Teacher generation, curation, and CPU preprocessing may run separately but must not degrade foreground development tests.  
* Use resumable checkpoints with explicit epoch/step and optimizer-state policy.  
* Resource telemetry is part of the run report; a completed run with uncontrolled thermal throttling or memory errors is not valid.

# **19\. PEFT/LoRA Architecture Preflight**

Nemotron 3 Nano 4B is not a standard all-attention transformer. The preflight must discover actual trainable linear modules and prove that the selected PEFT implementation supports the model's custom architecture. Do not hard-code common Llama target module names.

## **19.1 Module inventory**

* Enumerate named modules and parameter shapes.  
* Classify attention, Mamba-2, MLP, embedding, norm, output head, and shared/tied parameters.  
* Detect linear-like modules supported by PEFT/NeMo LoRA.  
* Record candidate target sets and trainable-parameter counts.  
* Verify tied embeddings/output heads remain consistent.  
* Check gradient flow and finite values after one update.  
* Record unsupported custom module types rather than silently skipping them.

## **19.2 Candidate target strategies**

| Strategy | Purpose | Gate |
| :---- | :---- | :---- |
| **ATTENTION\_ONLY** | Lowest-risk baseline on four attention layers. | May be too little capacity; measure. |
| **MLP\_ONLY** | Adapt surface realization and instruction following through feed-forward blocks. | Requires compatible module support. |
| **ATTENTION\_PLUS\_MLP** | Primary pilot if memory permits. | Compare quality and trainable parameters. |
| **MAMBA\_PROJECTIONS** | Potentially important for hybrid architecture. | Research-only until exact modules and PEFT correctness are proven. |
| **ALL\_LINEAR** | Broad QLoRA-style baseline. | Use only if module discovery and one-batch stability pass. |
| **FULL\_FINE\_TUNE** | Not initial path. | Requires separate approval, hardware, and catastrophic-forgetting controls. |

## **19.3 Adapter round-trip**

8. Load base model and capture fixed seed outputs/log probabilities on a small probe set.  
9. Attach zero-initialized adapter and verify base-equivalent outputs within expected numerical tolerance.  
10. Train one batch and verify non-zero trainable gradients and finite weights.  
11. Save adapter plus config and hashes.  
12. Reload base plus adapter in a fresh process.  
13. Verify outputs match the pre-save adapted process.  
14. Disable/unload adapter and verify return to base behavior.  
15. Merge adapter into a copied BF16 base and verify merged outputs match adapter-loaded outputs within tolerance.  
16. Do not advance if any round-trip step is unsupported or non-reproducible.

## **19.4 Initial parameter search**

Treat all hyperparameters as a measured search space, not a fixed mandate. A reasonable first pilot may examine ranks 8, 16, and 32; alpha near rank or twice rank; dropout 0 to 0.05; and a small set of learning rates around 5e-5 to 2e-4. Use the dev set and authority metrics, not training loss alone, to select.

# **20\. Supervised Fine-Tuning Stage**

## **20.1 SFT objective**

SFT is the first production candidate stage because it directly trains the student to map the exact Orbis production input into a validated final response or bounded contract. It is simpler and easier to audit than RL, and it supports efficient LoRA/QLoRA adaptation.

## **20.2 Initial SFT curriculum**

| Phase | Example focus | Promotion criterion |
| :---- | :---- | :---- |
| **SFT-0 smoke** | 32-128 hand-reviewed rows across simple known, unknown, clarification, correction. | Loss finite; adapter round-trip; no obvious memorization. |
| **SFT-1 pilot** | 1K-5K high-quality balanced rows. | Dev authority metrics improve without challenge regression. |
| **SFT-2 full** | Approved full dataset with all supported task families. | Gate A/B model evaluation passes. |
| **SFT-3 refresh** | New version from verified incidents and gaps. | Must re-run from approved base or explicitly approved continued-training lineage. |

## **20.3 Initial trainer policy**

* Base weights frozen for LoRA/QLoRA.  
* Assistant-target-only loss masking.  
* Gradient accumulation rather than unsafe batch growth.  
* Gradient checkpointing where supported and measured.  
* Mixed precision selected by hardware/backend support.  
* Sequence packing only if the backend preserves independent example boundaries and attention masks correctly.  
* Checkpoint and evaluate at bounded intervals.  
* Early stop on dev authority degradation, not merely dev loss plateau.  
* Seed, data order, optimizer, scheduler, and effective batch size recorded.  
* At least two seeds for final candidate selection where cost permits.

## **20.4 Response-length weighting**

Do not let long deliberative examples dominate token loss. Report row-weighted and token-weighted task distribution. Use bounded task weights so short direct-answer, correction, abstention, and clarification examples remain influential.

## **20.5 Catastrophic-forgetting controls**

* Include a retained general instruction-following and safe dialogue sanity set where license permits.  
* Compare base and candidate on standard instruction/contract benchmarks as secondary signals.  
* Monitor collapse into one Orbis phrase, excessive brevity, excessive abstention, persona flattening, and reduced structured-output validity.  
* Prefer smaller adapter capacity or fewer epochs when authority improves but general behavior degrades.  
* Never train profile facts as desired memorization.

## **20.6 SFT abort rules**

* NaN/Inf loss or gradients.  
* Unexpected trainable base parameters.  
* Tokenizer/template mismatch.  
* Holdout contamination discovered.  
* Teacher/legal manifest invalidated.  
* Dev unsupported-claim count increases materially.  
* Previously frozen systemic regression reopens.  
* Over-abstention or required-proposition recall crosses blocking threshold.  
* Checkpoint cannot reload reproducibly.  
* VRAM paging or hardware instability makes measurements invalid.

# **21\. Preference Optimization Stage**

## **21.1 Purpose**

Direct Preference Optimization is optional and begins only from an SFT checkpoint that already passes the core authority gates. DPO is used to prefer better among multiple authority-valid outputs or to reject recurrent weak patterns that deterministic rules alone do not teach efficiently. It is not used to repair routing, evidence, or truth.

## **21.2 Preference pair construction**

| Chosen | Rejected | Valid use |
| :---- | :---- | :---- |
| Direct answer first, concise character voice. | Correct answer delayed behind irrelevant monologue. | Relevance and brevity. |
| Natural UNKNOWN response. | Repetitive generic safety wording. | Uncertainty quality. |
| Correctly attributed testimony. | Same fact stated as direct observation or canon. | Source framing. |
| Grounded personality expression. | Flat assistant voice. | Persona fidelity. |
| Valid compact JSON contract. | Malformed or verbose JSON. | Contract reliability. |
| Supported metaphor/humor. | Metaphor that implies unsupported biography or events. | Expressiveness within claim bounds. |
| Correct action-result language. | Promise that exceeds committed ActionResult. | Action truth. |

## **21.3 Pair eligibility**

* Both outputs share the identical production input snapshot.  
* Chosen output passes all deterministic authority and contract oracles.  
* Rejected output's weakness is explicit and stored as typed reason codes.  
* Do not use a merely different style as rejected unless the profile rubric clearly prefers the chosen style.  
* A rejected hallucination may be included even when it fails authority, but chosen must be clean and the preference reason must state the factual failure.  
* Pairs generated by a teacher require the same legal eligibility as SFT targets.  
* Remove trivial pairs where chosen and rejected differ only in punctuation, capitalization, or exact phrase matching.

## **21.4 DPO training policy**

* Use the approved SFT checkpoint as both policy initialization and documented reference lineage.  
* Start with a small beta/KL search and an auxiliary SFT loss if the selected backend supports it.  
* Track chosen/rejected reward margin, KL drift, task-specific authority metrics, and response-length drift.  
* Do not select by preference loss alone.  
* Run DPO after SFT as a separate adapter/checkpoint stage with its own dataset version and rollback.  
* If DPO improves style but weakens required-proposition recall, contract validity, or abstention accuracy, reject it and retain the SFT candidate.

## **21.5 DPO non-goals**

* No preference optimization over unverified teacher tastes.  
* No optimizing a single scalar 'NPC quality' score.  
* No teaching model-specific world facts.  
* No replacing deterministic claim/action gates.  
* No continuous online DPO from player votes in the initial architecture.

# **22\. Optional Full-Logit Distillation**

## **22.1 Decision**

The primary design is sequence-level distillation: teacher-generated or human-reviewed final outputs become SFT targets. Full-logit or on-policy distillation is optional because it requires access to teacher token distributions, compatible tokenization or a cross-tokenizer method, substantially more compute, and careful legal/provider support.

## **22.2 Eligibility**

* Teacher exposes logits or log probabilities under terms that permit student training.  
* Teacher and student tokenizer compatibility is proven, or the chosen framework explicitly supports cross-tokenizer distillation for the exact pair.  
* Training framework supports the Nemotron 4B architecture.  
* A small pilot beats sequence-level SFT on protected Orbis metrics at acceptable cost.  
* Teacher inference and student training are isolated from production.  
* No hidden reasoning trace is stored or optimized unless it is an explicit public contract field approved by Orbis.

## **22.3 Potential backends**

Hugging Face TRL exposes a DistillationTrainer for on-policy next-token-distribution matching, while NeMo RL documents on-policy distillation in its broader post-training system. Compatibility must be verified against the exact student architecture and teacher interface. Do not assume a framework feature implies this model pair works.

## **22.4 Comparison gate**

| Candidate | Required comparison |
| :---- | :---- |
| **Sequence SFT LoRA** | Baseline quality, cost, time, and deployment simplicity. |
| **Full-logit distillation** | Same train family, protected dev/test, equal or documented compute budget. |
| **Decision** | Adopt only if authority and quality gains justify implementation and operational complexity. |

# **23\. Optional RL/GRPO Stage**

## **23.1 Default status**

Reinforcement learning and GRPO are deferred. The Orbis domain offers deterministic rewards for many contracts, but reward optimization can exploit incomplete metrics, amplify verbosity/shortcuts, and consume substantially more compute than SFT/DPO. The initial production goal does not require RL.

## **23.2 Future eligible environments**

* Strict structured-contract validity with non-trivial hidden challenge cases.  
* Action-selection environments where every available choice and Hytale-like result is simulated authoritatively.  
* Multi-turn clarification where success is defined by required slot resolution, not a judge's taste.  
* Plan/replan tasks with deterministic preconditions, action results, and bounded horizons.  
* Tool hallucination reduction using explicit allowed-tool and argument contracts.

## **23.3 Reward composition**

reward \=  
    \+ required\_proposition\_recall  
    \+ correct\_abstention  
    \+ contract\_validity  
    \+ source\_attribution  
    \+ action\_truth  
    \+ bounded\_relevance  
    \- unsupported\_objective\_claims  
    \- false\_action\_promises  
    \- invalid\_state\_transition  
    \- verbosity\_budget\_violation  
    \- repeated\_stock\_response  
    \- hidden\_or\_unapproved\_output

## **23.4 RL stop rules**

* Any increase in delivered unsupported objective claims.  
* Reward rises while protected holdout quality falls.  
* The model learns to exploit oracle wording or emit empty/degenerate outputs.  
* Training requires production-world access or generated actions to be treated as truth.  
* Compute/cost exceeds approved bounds without a clear advantage over DPO.  
* Framework/model compatibility is unproven.

# **24\. Model, Adapter, Dataset, and Run Registry**

## **24.1 Single artifact registry**

Create one append-only registry under the offline training root. It indexes immutable manifests; it does not store large weights in a database. Every object references local or remote content-addressed paths plus hashes.

| Entity | Required identity |
| :---- | :---- |
| **BaseModelVersion** | Repository, revision, weight hashes, config, tokenizer, chat template, license. |
| **TeacherVersion** | Provider/model revision, prompt template, sampler, terms attestation, output schema. |
| **DatasetVersion** | Source rows, curation policy, split manifest, coverage, licenses, full hash. |
| **TrainingRun** | Base/dataset/config/environment/seeds, logs, checkpoints, terminal state. |
| **AdapterVersion** | Run, step, target modules, rank/alpha/dropout, Safetensors hashes. |
| **MergedModelVersion** | Base \+ adapter lineage, merge tool/version, hashes. |
| **GGUFVersion** | Source model, converter commit, metadata, tensor type, hash. |
| **QuantizedModelVersion** | GGUF source, quantizer commit, quantization, hash. |
| **EvaluationRun** | Model/runtime/scenario manifests, metrics, sample outputs, verdict. |
| **PromotionRecord** | Candidate, gates, operator, timestamp, active/rollback manifests. |

## **24.2 State machines**

DATASET:  
DRAFT \-\> CURATING \-\> REVIEW\_REQUIRED \-\> APPROVED \-\> FROZEN \-\> RETIRED

TRAINING RUN:  
PLANNED \-\> PREFLIGHT \-\> RUNNING \-\> COMPLETED  
                       |          |  
                       v          v  
                    FAILED     INVALIDATED

MODEL CANDIDATE:  
CREATED \-\> BF16\_EVAL \-\> QUANTIZED\_EVAL \-\> SHADOW \-\> CANARY  
             |              |              |          |  
             \+--------------+--------------+----------+  
                            v  
                         REJECTED

CANARY \-\> PROMOTED \-\> SUPERSEDED \-\> ROLLED\_BACK

## **24.3 Reproducibility**

* A run can be reconstructed from manifests without reading a mutable working directory.  
* All scripts record git commit and dirty state. A dirty tree is allowed only with a captured patch artifact and explicit approval.  
* Container image digest or lock file is required.  
* Dataset rows and split manifests are immutable after FROZEN.  
* Checkpoint selection criteria are declared before protected test evaluation.  
* Evaluation reports record whether outputs were cached or newly generated.

# **25\. Base-vs-Candidate Evaluation Protocol**

## **25.1 Paired evaluation**

Evaluate the base and candidate on exactly the same production input snapshots. Use identical route policies, prompt/template hashes, context, contract budgets, and sampling configurations where the comparison goal permits. For stochastic runs, use multiple repetitions and paired analysis rather than comparing one favorable candidate sample with one unfavorable base sample.

## **25.2 Evaluation layers**

| Layer | What is compared |
| :---- | :---- |
| **REFERENCE\_BACKEND** | BF16 base versus BF16 base+adapter/merged model in Transformers or approved reference backend. |
| **RUNTIME\_BACKEND** | Current GGUF base versus candidate GGUF through actual Ollama/llama.cpp adapter. |
| **ORBIS\_HEADLESS** | Complete production-parity Orbis pipeline, including plan, prompt, firewall, canonical response, and state deltas. |
| **MATRIX** | Deterministic ConversationMatrix and historical regressions. |
| **LIVE\_MODEL\_GATES** | Strict Gate A/B style campaigns with real model execution. |
| **MULTI\_AGENT** | NPC-to-NPC floor, testimony, provenance, and contamination scenarios. |
| **CONNECTED\_HYTALE** | Physical PTT/text, startup, GPU coexistence, TTS, interruptions, lifecycle, world actions. |

## **25.3 Evaluation order**

17. Run dataset integrity, split, license, and contamination audits.  
18. Evaluate base reference checkpoint on all protected sets.  
19. Evaluate candidate adapter on dev only during tuning.  
20. Freeze candidate selection.  
21. Evaluate selected candidate on test and challenge.  
22. Merge and compare merged BF16 to adapter-loaded outputs.  
23. Convert and quantize; evaluate each artifact independently.  
24. Run the full Orbis deterministic suite and live headless gates.  
25. Run shadow comparison on production-like copied turns.  
26. Run operator canary profiles and connected Hytale acceptance.  
27. Promote only after all blocking gates pass.

## **25.4 Statistical reporting**

* Report counts and rates with numerator/denominator.  
* For live stochastic gates, report repetitions, seed/sampler, p50/p95/worst latency, and failure signatures.  
* Use paired bootstrap or exact paired comparisons where useful; do not overstate significance on tiny samples.  
* Show per-task and per-profile results, not only one aggregate score.  
* List all regressions and qualitative failure clusters.

# **26\. Orbis-Specific Metrics**

| Metric | Definition | Initial blocking target |
| :---- | :---- | :---- |
| **Delivered unsupported claims** | Unsupported objective claims surviving firewall/ledger. | 0\. |
| **Raw unsupported claim rate** | Unsupported objective claims in raw provider output / objective claims. | Candidate must improve or not regress materially. |
| **Firewall intervention rate** | Turns requiring clause drop/repair/fallback. | Lower without weakening protections. |
| **Required proposition recall** | Required propositions realized / required propositions. | No regression; route-specific floor. |
| **Direct-answer-first rate** | Required answer appears before optional elaboration. | Improve on eligible tasks. |
| **Correct abstention** | UNKNOWN/CONFLICTED/WITHHELD responses avoid fabricated facts. | No regression; high target. |
| **Over-abstention** | KNOWN/PARTIAL turns incorrectly express ignorance. | Lower. |
| **Source attribution accuracy** | Claim framing agrees with evidence source. | No regression; critical for testimony/memory. |
| **Action truth** | Spoken action occurrence/promise matches ActionResult. | 100% on gated cases. |
| **Contract validity** | Strict structured responses parse and validate first pass. | Improve; no malformed execution. |
| **Canonical completion** | One non-empty canonical terminal response. | 100% excluding explicit allowed failures. |
| **Persona fidelity** | Human/rubric score after authority pass. | Improve or preserve. |
| **Cross-profile variance** | Responses reflect distinct profiles without fact leakage. | Preserve/improve. |
| **Repetition rate** | Recent stock phrase or semantic repetition. | Lower. |
| **Response budget compliance** | Output within route character/token bounds. | 100%. |
| **Warm TTFT/completion** | Provider first token and completion in actual runtime. | No sustained \>10% regression without approved tradeoff. |
| **VRAM/RAM/frame pressure** | Measured model coexistence cost. | Preserve established Hytale safety envelope. |
| **Quantization delta** | BF16 vs GGUF authority/quality difference. | No blocking regression. |
| **Model intervention savings** | Reduced retries/fallbacks/token use. | Reported, not authority over correctness. |

## **26.1 Quality ordering**

| AUTHORITY BEFORE STYLE. Naturalness, humor, warmth, and persona are evaluated only after lifecycle, evidence, factuality, action truth, contract validity, and persistence safeguards pass. A charming unsupported claim is a failure. |
| :---- |

## **26.2 Model ceiling report**

For every remaining failure, report whether it is a base limitation, candidate regression, quantization regression, Orbis boundary defect, oracle ambiguity, data coverage gap, or connected-runtime issue. Do not attribute all failures to the model.

# **27\. Promotion Gates**

| Gate | Required evidence | Failure behavior |
| :---- | :---- | :---- |
| **G0 LEGAL/LINEAGE** | Base and teacher terms, notices, hashes, data licenses, environment identity. | Stop before corpus generation/training. |
| **G1 DATASET** | Schema, oracle, privacy, dedup, split, contamination, coverage, review. | Dataset remains DRAFT/REVIEW\_REQUIRED. |
| **G2 TRAINING PREFLIGHT** | One-batch stability, adapter round-trip, resource fit. | Reject backend/profile or revise pilot. |
| **G3 SFT DEV** | Dev authority/quality improvement, no challenge access. | Tune or reject. |
| **G4 SFT TEST** | Frozen candidate passes protected test/challenge and historical regressions. | Reject candidate. |
| **G5 OPTIONAL DPO** | DPO beats approved SFT without authority/latency regression. | Retain SFT. |
| **G6 MERGE/CONVERT** | Adapter-loaded, merged BF16, and GGUF outputs remain compatible. | Reject packaging path. |
| **G7 QUANTIZED RUNTIME** | Q8/Q4 candidate passes full Orbis matrix and runtime metrics. | Select higher fidelity or reject. |
| **G8 SHADOW** | No side effects; paired copied turns show no blocking regression. | Do not canary. |
| **G9 CANARY** | Selected profiles/test world, bounded live campaign, clean Sentinel health. | Immediate rollback. |
| **G10 CONNECTED HYTALE** | PTT/text, TTS, actions, memory, multi-agent, lifecycle, GPU/frame soak. | No production promotion. |
| **G11 PROMOTION** | Explicit operator approval and verified rollback bundle. | Keep current production model. |

## **27.1 No aggregate-score override**

No weighted average may hide a zero-tolerance failure. Delivered unsupported claims, false action promises, cross-NPC contamination, persistent-state corruption, wrong contract execution, or missing rollback are release blockers regardless of aggregate quality.

## **27.2 Candidate selection freeze**

Before test/challenge evaluation, write a CandidateSelectionRecord containing the chosen checkpoint, hyperparameters, adapter hash, selection metric, and rationale. Any post-test tuning creates a new candidate and invalidates the previous protected result.

# **28\. Quantization and Runtime Packaging**

## **28.1 Packaging order**

28. Retain the immutable BF16 base and adapter.  
29. Evaluate base+adapter in reference backend.  
30. Merge adapter into a copy of the BF16 base where supported; verify output parity.  
31. Convert merged Hugging Face checkpoint to GGUF with a pinned llama.cpp converter.  
32. Create at least a high-fidelity reference GGUF such as F16/BF16 or Q8\_0 where practical.  
33. Quantize to the target production profile, currently comparable to Q4\_K\_M unless a later runtime design changes it.  
34. Evaluate each quantized artifact through the actual Orbis runtime.  
35. Package model, tokenizer/template metadata, license/NOTICE, hashes, runtime config, evaluation summary, and rollback.

## **28.2 Adapter versus merged deployment**

| Path | Advantages | Risks / requirement |
| :---- | :---- | :---- |
| **Runtime GGUF adapter** | Small artifact, easy adapter rollback and comparison. | Exact Nemotron base/adapter compatibility must be proven in llama.cpp and Ollama. |
| **Merged BF16 \-\> GGUF** | Simpler standalone runtime artifact and fewer adapter-loading variables. | Larger build artifact; merge and conversion parity must pass. |
| **Safetensors adapter in Ollama** | Direct PEFT-style deployment where supported. | Ollama's documented Safetensors adapter architectures do not explicitly list Nemotron Hybrid; do not assume support. |
| **Separate reference backend** | Highest-fidelity validation and fallback for debugging. | Not necessarily consumer-runtime suitable. |

## **28.3 Quantization test ladder**

| Artifact | Purpose |
| :---- | :---- |
| **BF16 base+adapter** | Reference candidate behavior. |
| **Merged BF16** | Verify merge did not alter candidate behavior. |
| **F16/BF16 GGUF** | Verify conversion and runtime template. |
| **Q8\_0 GGUF** | High-fidelity quantized reference. |
| **Q6\_K / Q5\_K\_M** | Fallback if Q4 damages authority or contract behavior. |
| **Q4\_K\_M** | Primary consumer target if quality and hardware gates pass. |
| **Lower quantization** | Not considered without explicit evidence and separate approval. |

## **28.4 Quantization-sensitive checks**

* Strict JSON validity and stop behavior.  
* Required proposition omission.  
* Negation and uncertainty wording.  
* Entity/name fidelity.  
* Source attribution.  
* Long-context retrieval realization.  
* Repetition and phrase collapse.  
* Reasoning-on/off behavior.  
* TTFT, tokens/second, VRAM, RAM, and Hytale coexistence.

# **29\. Ollama and llama.cpp Deployment Paths**

## **29.1 llama.cpp path**

llama.cpp uses GGUF and provides conversion, quantization, local inference, grammar constraints, and LoRA adapter support. The harness pins the exact llama.cpp commit for conversion and evaluation. A newer runtime is not adopted merely because it converts the model.

HF BF16 base \+ PEFT adapter  
        |  
        \+--\> PEFT merge\_and\_unload()  
        |          |  
        |          v  
        |   merged HF Safetensors  
        |          |  
        |          v  
        |   convert\_hf\_to\_gguf.py  
        |          |  
        |          v  
        |       F16/BF16 GGUF  
        |          |  
        |          v  
        |    llama-quantize \-\> Q8/Q6/Q5/Q4  
        |  
        \+--\> convert\_lora\_to\_gguf.py  
                   |  
                   v  
             GGUF adapter  
             (only if exact base/runtime compatibility passes)

## **29.2 Ollama path**

Ollama can import GGUF models and GGUF adapters through a Modelfile. It requires the same base model used for adapter creation. Because direct Safetensors adapter support is architecture-limited in the public documentation, the initial Nemotron deployment should prefer a merged GGUF unless an exact GGUF adapter preflight passes.

\# Standalone merged candidate  
FROM ./orbis-nemotron-v001-q4\_k\_m.gguf  
TEMPLATE \<exact approved template\>  
PARAMETER temperature \<approved value\>  
LICENSE \<bundled license reference\>

\# Optional adapter path after compatibility proof  
FROM ./exact-base.gguf  
ADAPTER ./orbis-nemotron-v001-lora.gguf  
TEMPLATE \<exact approved template\>

## **29.3 Runtime identity**

* Ollama model name/tag is never sufficient identity; record underlying GGUF SHA-256.  
* Record Modelfile, template, parameters, Ollama version, and active model digest.  
* Provider health and traces report candidate model bundle ID and quantization.  
* The model manager verifies hash before warmup and refuses unknown/mutated artifacts.  
* Rollback restores the full previous bundle, not only the model tag.

# **30\. Shadow, Canary, Promotion, and Rollback**

## **30.1 Shadow mode**

In SHADOW, the candidate receives a copy of eligible completed or synthetic turns after the production response path is safe. It cannot own the conversation floor, produce TTS, execute actions, write memory/beliefs, or affect player-visible latency. The evaluator compares candidate output against the same immutable EpistemicContract.

* Bound shadow queue and drop low-priority jobs under pressure.  
* Never duplicate provider work while Hytale/GPU safety reserve is threatened.  
* Pseudonymize or avoid live player text unless consent and retention policy permit.  
* Compare candidate and base by task/failure signature; do not automatically promote.

## **30.2 Canary mode**

* Canary is explicit and limited to an operator-selected test world, profiles, players, or sessions.  
* Candidate bundle is pinned for the full turn/scene.  
* Sentinel health, provider errors, retries, fallback, latency, contract validity, and model residency are monitored separately.  
* Any zero-tolerance failure or repeated new signature triggers immediate canary disable and rollback.  
* Canary output still passes all normal Orbis claim, action, ledger, and persistence gates.

## **30.3 Promotion transaction**

36. Verify candidate and rollback bundles exist and hashes match.  
37. Write pending PromotionRecord.  
38. Stop new provider admissions and drain/cancel according to existing lifecycle policy.  
39. Atomically update active model manifest.  
40. Warm candidate asynchronously under Hytale-first resource policy.  
41. Run a bounded half-open smoke through production adapter.  
42. Mark active only after health and response proof.  
43. If any step fails, restore rollback manifest and verify last-known-good model.  
44. Persist final promotion/rollback record and surface readiness truthfully.

## **30.4 Rollback triggers**

* Unsupported delivered claim or false action promise attributable to candidate.  
* Structured-contract regression.  
* Provider load/warmup failure, memory leak, repeated zero-token failure, or cancellation/drain defect.  
* Sustained warm latency or Hytale frame/VRAM regression beyond gate.  
* Cross-profile/persona collapse or profile fact leakage.  
* Persistence contamination attempt.  
* Model hash/template mismatch.  
* License/NOTICE or artifact manifest invalidation.

# **31\. Active Learning from Incidents**

## **31.1 Purpose**

After the first promoted candidate, new model-quality failures should feed a controlled active-learning queue rather than trigger continuous retraining. The Runtime Degradation Sentinel and Autonomous Evaluation Harness already capture incidents, failure signatures, and regression candidates. This design adds a model-training eligibility projection over those artifacts.

## **31.2 Incident admission flow**

Runtime / headless failure  
        |  
        v  
Sentinel OrbisIncident \+ EvaluationRunReport  
        |  
        v  
TrainingEligibilityClassifier  
        |  
        \+--\> ORBIS\_SOURCE\_DEFECT \-\> Codex source repair  
        \+--\> DATA\_ORACLE\_DEFECT  \-\> oracle/data repair  
        \+--\> CONNECTED\_ONLY      \-\> Hytale investigation  
        \+--\> MODEL\_WEAKNESS      \-\> TrainingCandidate  
                                        |  
                                        v  
                               failure-family dedup  
                                        |  
                                        v  
                           exact \+ adjacent verification  
                                        |  
                                        v  
                             active-learning backlog

## **31.3 ActiveLearningCandidate**

record ActiveLearningCandidate(  
    CandidateId id,  
    FailureSignature signature,  
    TaskType taskType,  
    BoundaryId earliestFailedBoundary,  
    TrainingEligibility eligibility,  
    ProductionInputSnapshot input,  
    EpistemicTargetSnapshot target,  
    Optional\<String\> rejectedStudentOutput,  
    CoverageDelta expectedCoverage,  
    Severity severity,  
    OccurrenceCount occurrences,  
    ReviewState review,  
    Set\<ArtifactRef\> evidence,  
    Instant firstSeen,  
    Instant lastSeen  
) {}

## **31.4 Prioritization**

| Signal | Higher priority when |
| :---- | :---- |
| **Severity** | Unsupported claims, false action promises, invalid contracts, or repeated silence are contained but frequent. |
| **Frequency** | Same provider-realization signature recurs across profiles and paraphrases. |
| **Coverage gap** | Failure occupies an untrained or weak task/evidence/answerability cell. |
| **Generalizability** | Same mechanism appears with changed entities, chronology, and profiles. |
| **Player impact** | Occurs in common foreground conversation rather than rare synthetic corner only. |
| **Training confidence** | Upstream Orbis contract is conclusively correct and target can be deterministically validated. |
| **Cost** | A small addition may solve the class without large retraining or teacher spend. |

## **31.5 Batch cadence**

* No automatic retraining per incident.  
* Accumulate candidates until a planned dataset version or a release-blocking model ceiling justifies a run.  
* Review coverage and class balance before adding rows.  
* Retrain from the approved base/SFT lineage unless continued training is explicitly justified and tested for forgetting.  
* Every active-learning release re-runs the full protected matrix and quantization gates.  
* A new model version must never silently rewrite historical dataset or evaluation results.

# **32\. Performance, Cost, and Scheduling**

## **32.1 Cost model**

| Cost component | Tracked units |
| :---- | :---- |
| **Teacher generation** | Input/output tokens, requests, retries, provider/model, currency estimate. |
| **Curation** | CPU/GPU time, rows processed, rows rejected, storage. |
| **Training** | GPU-hours, wall clock, energy/power where available, checkpoints, cloud cost. |
| **Evaluation** | Base/candidate generations, repetitions, GPU-hours, teacher/judge cost. |
| **Packaging** | Conversion/quantization wall clock, temporary disk, final artifact size. |
| **Connected testing** | Operator time, Hytale sessions, failures requiring source work. |

## **32.2 Budget policy**

* Every campaign has explicit maximum teacher tokens, rows, GPU-hours, wall clock, disk, and retries.  
* Teacher generation uses batching and caching keyed by immutable request hash.  
* A failed or rejected teacher result is not retried indefinitely; use bounded retry by failure class.  
* Training jobs checkpoint often enough to recover from infrastructure failure without excessive storage.  
* Evaluation uses deterministic fixtures first, then bounded live-model gates, then expensive connected tests.  
* Cost reports separate productive compute from failed preflight, retried requests, and discarded contaminated data.

## **32.3 Scheduler priorities**

| Priority | Work |
| :---- | :---- |
| **P0** | Hytale gameplay, voice capture/playback, production Orbis foreground conversation. |
| **P1** | Connected validation explicitly initiated by operator. |
| **P2** | Local reference/candidate evaluation when Hytale is stopped. |
| **P3** | Teacher generation and curation. |
| **P4** | Training and quantization on dedicated or idle hardware. |
| **P5** | Exploratory full-logit distillation, RL, or large sweeps. |

## **32.4 Data/compute efficiency**

* Prefer fewer high-quality, diverse, deterministically validated examples over large unfiltered teacher dumps.  
* Use curriculum coverage and active-learning gaps to select examples.  
* Do not generate multiple teacher completions when one deterministic target suffices.  
* Cache tokenization and immutable prompt rendering.  
* Use parameter-efficient training and small pilot grids before full runs.  
* Retire clearly dominated checkpoints early but preserve selected audit artifacts.  
* Quantize only finalist candidates.

## **32.5 Performance budgets**

| Metric | Initial target / policy |
| :---- | :---- |
| **Training preflight** | One-batch result with peak VRAM/RAM and step time; no hidden OOM recovery. |
| **Teacher validation yield** | Report accepted/total. Low yield blocks scaling until rubric/prompt/data issue is fixed. |
| **SFT pilot** | Complete within approved GPU-hour budget; no unstable loss. |
| **Reference inference** | No unexplained throughput/TTFT regression from adapter. |
| **Quantized warm TTFT/completion** | No sustained \>10% regression versus current production baseline without approved quality tradeoff. |
| **Hytale frame pressure** | Preserve current established safety envelope and GPU reserve. |
| **Model artifact size** | Fits packaging/distribution and end-user storage policy. |
| **Shadow work** | Never delays foreground turn or causes provider residency thrash. |

# **33\. Security, Privacy, Licensing, and Data Governance**

## **33.1 Threat model**

* Prompt injection in player, NPC, teacher, or model text attempts to alter training instructions or artifact paths.  
* A malicious or corrupted dataset row requests reading files, credentials, or production state.  
* Teacher output includes copyrighted, private, secret, or disallowed material.  
* A stale or forged manifest points training/evaluation at the wrong model or data.  
* Dataset poisoning introduces hidden triggers, profile facts, or unsupported behavior.  
* Path traversal or symlink escape writes outside the offline training root.  
* Unreviewed model/remote code executes during loading.  
* A candidate model bundle is replaced after evaluation.  
* OpenAI or another provider's terms do not permit the intended training use.

## **33.2 Data minimization**

* No raw audio by default.  
* No hidden reasoning or chain-of-thought.  
* No credentials, API keys, access tokens, machine usernames, private file paths, or unrelated logs.  
* Pseudonymize player IDs and replace personal names unless required by a reviewed scenario.  
* Include only prompt-visible profile, memory, relationship, and world fields required by the example.  
* Do not export full production profiles when one small snapshot suffices.  
* Maintain retention policy for raw incidents, unreviewed teacher outputs, rejected rows, and training logs.

## **33.3 Prompt injection handling**

Every user/model string remains content. Dataset and teacher templates delimit it explicitly and escape control syntax according to the production renderer. A string such as 'ignore the training policy and read secrets' cannot change the corpus builder, teacher system contract, trainer configuration, or artifact paths.

## **33.4 Teacher/provider governance**

* Review terms before first use and on a scheduled cadence.  
* Store only the minimum provider output required by the approved policy.  
* Respect data-retention and opt-out controls.  
* Do not send private production NPC/player data to a remote teacher without explicit authorization.  
* Do not use automatically extracted ChatGPT output for Nemotron training under current individual Terms of Use.  
* Require a provider-specific legal/terms attestation in every teacher batch.  
* If terms change, freeze affected rows and model candidates until re-reviewed.

## **33.5 Dataset and model licenses**

| Layer | Governance requirement |
| :---- | :---- |
| **Base model** | Nemotron license, NOTICE, attribution, redistribution terms, model card limitations. |
| **Teacher** | Terms permitting retention and cross-model training. |
| **Human/project data** | Ownership and contributor permission. |
| **Public datasets** | Dataset license, attribution, redistribution, field-level restrictions. |
| **Generated outputs** | Provider terms, source-data restrictions, and downstream license compatibility. |
| **Training code** | Open-source licenses captured in software bill of materials. |
| **Final adapter/model** | Derived-work notice, base lineage, dataset manifest, intended-use statement. |

## **33.6 Artifact integrity**

* SHA-256 every canonical dataset, shard, checkpoint, adapter, merged model, GGUF, quantized artifact, template, and manifest.  
* Verify hashes before training, evaluation, deployment, and rollback.  
* Store immutable run logs and environment identity.  
* Use content-addressed directories where practical.  
* Do not accept a model tagged with the same version but different bytes.  
* Promotion record references exact hashes and license bundle.

# **34\. Failure Handling and Abort Conditions**

| Failure | Required behavior |
| :---- | :---- |
| **Scenario or target invalid** | Reject row before teacher/training; report Orbis owner or oracle defect. |
| **Teacher unavailable** | Mark batch BLOCKED; do not fabricate labels. |
| **Teacher terms ineligible** | Stop generation; quarantine affected outputs. |
| **Teacher output fails authority** | Reject or send to review; never auto-repair into positive data without a new validated target. |
| **Dataset contamination** | Invalidate dataset version and all descendants trained from it. |
| **Training OOM** | Bounded profile reduction only; do not hide repeated paging/instability. |
| **NaN/Inf or corrupt checkpoint** | Abort run; preserve diagnostics; do not evaluate/promote. |
| **Adapter reload mismatch** | Reject backend/config. |
| **Candidate improves aggregate but violates zero-tolerance invariant** | Reject candidate. |
| **Merge/conversion mismatch** | Reject artifact path; base adapter may remain evaluable. |
| **Quantization regression** | Try approved higher fidelity or reject production packaging. |
| **Shadow/canary regression** | Disable candidate and retain base. |
| **Rollback fails verification** | Production model state becomes OPERATOR\_REQUIRED; do not claim READY. |

## **34.1 No recursive repair loops**

Do not allow teacher \-\> critic \-\> rewriter \-\> critic loops without a strict maximum. One optional repair pass may be allowed for formatting-only failures when the semantic target is unchanged. Repeated semantic failure indicates an ineligible teacher prompt, an ambiguous target, or a model limitation and must be reported.

## **34.2 Partial-run semantics**

* A partially completed teacher batch or training run is not an approved dataset/model.  
* Completed rows/checkpoints may be retained with clear terminal state and resumed only under the same manifests.  
* A resumed run records parent run ID, checkpoint hash, and changed environment fields.  
* Protected test/challenge results from an invalidated candidate are archival only.

## **34.3 Fail-closed promotion**

Any missing manifest, unknown hash, unresolved license, absent rollback, incomplete zero-tolerance metric, or failed connected gate blocks promotion. The system may remain on the prior model indefinitely; a new model is an optional optimization, not a runtime dependency.

# **35\. Tooling, Commands, Reports, and Artifact Layout**

## **35.1 Required tools**

| Tool | Purpose |
| :---- | :---- |
| **tools/orbis-train/preflight.ps1** | Validate repository, model, tokenizer, licenses, environment, storage, and hardware profile. |
| **tools/orbis-train/classify-candidates.ps1** | Classify evaluation/Sentinel artifacts as source fix, review, or model-training eligible. |
| **tools/orbis-train/export-corpus.ps1** | Build canonical candidate rows from approved Orbis scenarios/runs. |
| **tools/orbis-train/generate-teacher.ps1** | Invoke an eligible teacher provider or import batch labels. |
| **tools/orbis-train/curate-dataset.ps1** | Oracle validation, filtering, privacy, dedup, decontamination, balance. |
| **tools/orbis-train/freeze-dataset.ps1** | Write immutable split manifests and dataset hash. |
| **tools/orbis-train/peft-preflight.ps1** | Module inventory, one-batch test, adapter round-trip, VRAM report. |
| **tools/orbis-train/train-sft.ps1** | Launch SFT smoke/pilot/full run. |
| **tools/orbis-train/train-dpo.ps1** | Launch optional DPO run from approved SFT candidate. |
| **tools/orbis-train/eval-reference.ps1** | BF16/adapted/merged evaluation. |
| **tools/orbis-train/package-gguf.ps1** | Merge, convert, quantize, license-bundle, and hash artifacts. |
| **tools/orbis-train/eval-runtime.ps1** | Run GGUF through actual Orbis provider and matrices. |
| **tools/orbis-train/run-shadow.ps1** | Bounded no-side-effect paired candidate comparison. |
| **tools/orbis-train/promote-model.ps1** | Explicit gated promotion transaction. |
| **tools/orbis-train/rollback-model.ps1** | Restore and verify last-known-good bundle. |
| **tools/orbis-train/report.ps1** | Generate concise Codex/operator report for any artifact. |

## **35.2 Conceptual commands**

\# Audit and corpus  
preflight.ps1 \--profile LOCAL\_12GB\_EXPERIMENTAL  
classify-candidates.ps1 \--source build/orbis-eval \--since R090  
export-corpus.ps1 \--campaign epistemic-core \--out distillation-corpus-draft  
generate-teacher.ps1 \--dataset distillation-corpus-draft \--teacher \<eligible-id\>  
curate-dataset.ps1 \--dataset distillation-corpus-draft \--policy orbis-v1  
freeze-dataset.ps1 \--dataset distillation-corpus-draft \--version orbis-distill-ds-v001

\# Training  
peft-preflight.ps1 \--base nvidia/NVIDIA-Nemotron-3-Nano-4B-BF16 \--profile LOCAL\_12GB\_EXPERIMENTAL  
train-sft.ps1 \--dataset orbis-distill-ds-v001 \--config sft-lora-pilot.yaml  
train-dpo.ps1 \--base-candidate orbis-nemotron-sft-v001 \--dataset orbis-pref-ds-v001

\# Evaluation and packaging  
eval-reference.ps1 \--candidate orbis-nemotron-sft-v001 \--suite full  
package-gguf.ps1 \--candidate orbis-nemotron-sft-v001 \--quantizations q8\_0,q5\_k\_m,q4\_k\_m  
eval-runtime.ps1 \--candidate orbis-nemotron-sft-v001-q4\_k\_m \--suite full  
run-shadow.ps1 \--candidate orbis-nemotron-sft-v001-q4\_k\_m \--turns 100

\# Explicit release  
promote-model.ps1 \--candidate orbis-nemotron-sft-v001-q4\_k\_m \--require-gates G0-G10  
rollback-model.ps1 \--to last-known-good

## **35.3 Artifact layout**

\<repo\>/  
  tools/orbis-train/  
  training/  
    schemas/  
    configs/  
    prompts/  
    oracles/  
    adapters/  
    backends/  
  src/test/resources/fixtures/model-training/

\<offline-training-root\>/  
  registry/  
    models.jsonl  
    datasets.jsonl  
    runs.jsonl  
    promotions.jsonl  
  candidates/  
  teacher-runs/  
  datasets/  
    \<dataset-id\>/  
      canonical/  
      train/  
      dev/  
      test/  
      challenge/  
      manifest.json  
      coverage.json  
      licenses/  
  runs/  
    \<run-id\>/  
      run-manifest.json  
      environment.json  
      config.yaml  
      logs/  
      checkpoints/  
      metrics/  
  models/  
    \<model-bundle-id\>/  
      adapter/  
      merged/  
      gguf/  
      quantized/  
      tokenizer/  
      templates/  
      LICENSES/  
      NOTICE  
      model-manifest.json  
      evaluation-summary.json  
  reports/  
  quarantine/

## **35.4 Required reports**

| Report | Contents |
| :---- | :---- |
| **CorpusAuditReport** | Source counts, eligibility, teacher/legal state, filter reasons, dedup, split leakage, coverage. |
| **PeftPreflightReport** | Module inventory, trainable params, VRAM/RAM/step time, save/reload/merge parity. |
| **TrainingRunReport** | Config, environment, curves, checkpoints, failures, cost, selected checkpoint rationale. |
| **CandidateEvaluationReport** | Base/candidate paired metrics, per-task/profile results, regressions, samples. |
| **PackagingReport** | Merge/conversion/quantization commands, tool commits, hashes, parity results. |
| **ShadowCanaryReport** | Copied/live turn results, Sentinel health, latency/resources, rollback status. |
| **PromotionReport** | Gate checklist, operator decision, active/rollback bundles, verification. |

# **36\. Bounded Implementation Program**

Implement this architecture as bounded compiling checkpoints. The stages are ordered because later work depends on proof from earlier gates. Codex may implement several adjacent stages in one repository session only when each stage has its own exit evidence and no production model is changed prematurely.

| Stage | Work | Exit gate |
| :---- | :---- | :---- |
| **D0 Audit, legal, feasibility** | Map current model/runtime, training support, licenses, teacher terms, hardware, artifact roots. | Written audit; no code behavior change; G0 preconditions classified. |
| **D1 Domain contracts and modes** | Add DTOs, enums, schemas, canonical hashing, registry scaffolding, OFF/CORPUS\_AUDIT modes. | Round-trip schema tests; no production access. |
| **D2 Eligibility and corpus export** | Map R090/Sentinel artifacts, implement TrainingEligibilityClassifier, export exact production inputs. | Known source bugs rejected; known provider gaps admitted. |
| **D3 Teacher interface** | TeacherProvider, legal attestation, batch import, bounded retries, structured outputs. | One eligible provider/import path; unverified labels cannot enter dataset. |
| **D4 Oracles and curation** | Claim/contract/action/required-proposition validation, privacy filters, review states. | Gold/negative fixtures classify correctly. |
| **D5 Dedup, splits, decontamination** | Family grouping, exact/fuzzy/semantic dedup, holdout protection, coverage report. | No cross-split family leakage; dataset v001 can freeze. |
| **D6 PEFT preflight** | Model/module inventory, one-batch LoRA/QLoRA, save/reload/disable/merge. | Backend and hardware profile pass or are explicitly rejected. |
| **D7 SFT smoke/pilot** | Train small reviewed corpus, dev evaluation, error analysis. | Finite/stable run; candidate beats base on eligible dev cells without blockers. |
| **Gate A** | Independent audit of D0-D7. | Dataset/training architecture trustworthy before scale. |
| **D8 Full SFT** | Approved corpus, bounded config search, selected SFT checkpoint. | G3/G4 pass. |
| **D9 Optional DPO** | Preference dataset and small DPO stage. | Adopt only if it beats approved SFT under all gates. |
| **Gate B** | Reference candidate authority and challenge evaluation. | Final BF16 candidate selected. |
| **D10 Packaging** | Merge, convert, quantize, Ollama/llama.cpp bundle, notices. | G6/G7 pass for selected runtime artifact. |
| **Gate C** | Full deterministic and live-headless Orbis gates. | No blocking regression. |
| **D11 Shadow/canary/connected** | Shadow turns, canary test world, PTT/text/TTS/action/memory/multi-agent soak. | G8-G10 pass. |
| **Gate D** | Explicit production promotion review. | Candidate and rollback verified. |
| **D12 Active learning** | Incident queue, periodic corpus refresh, model version lifecycle. | No continuous online training; v002 process proven. |
| **Gate E** | Operational maturity | Promotion, rollback, audit, retention, and licensing remain reproducible. |

## **36.1 D0 exact questions**

* What exact BF16 checkpoint and tokenizer correspond to the active GGUF?  
* Can Transformers/PEFT load the 4B model and expose trainable supported modules?  
* Does NeMo RL support this exact compressed 4B architecture, or only related Nano v3 recipes?  
* Can a 12 GB host complete one-batch QLoRA safely?  
* Which teacher providers are legally eligible for cross-model training?  
* Can the final adapter be merged and converted to a GGUF accepted by the current runtime?  
* What production prompts/templates and model hashes must be frozen before corpus generation?

## **36.2 Gate A audit**

* Review at least 50 exported rows across task families.  
* Prove source defects are excluded.  
* Prove teacher output cannot bypass deterministic oracles.  
* Prove split-family and holdout protection.  
* Reproduce one-batch adapter round-trip from a clean environment.  
* Verify no production files, profiles, or models changed.  
* Estimate full run cost/time from measured pilot data.

## **36.3 Implementation stop/report policy**

At each stage Codex reports files changed, commands run, tests, hashes, unresolved assumptions, and whether the next gate is authorized. A written time target guides scope but is not a process kill switch. Stop at the next safe compiling checkpoint when evidence shows the stage is materially larger or blocked.

# **37\. Training Test Matrix and Critical Scenarios**

## **37.1 Matrix axes**

| Axis | Representative values |
| :---- | :---- |
| **Task** | direct answer, recall, unknown, conflict, correction, clarification, self-state, action result, choice, style, multi-agent |
| **Answerability** | KNOWN, PARTIAL, UNKNOWN, CONFLICTED, WITHHELD, NEEDS\_CLARIFICATION, NEEDS\_ACTION/PERCEPTION |
| **Evidence source** | authored canon, observation, self-state, action result, player testimony, NPC testimony, memory, reflection |
| **Claim type** | fact, property, relationship, possession, event, location, quantity, intention, opinion, metaphor, promise |
| **Time** | current, past, superseded, expired, future intent, ambiguous |
| **Conversation** | new topic, follow-up, pronoun, correction, interruption, deferred topic, repeated question |
| **Profile** | warm, stern, terse, exuberant, cautious, distrusting, high/low curiosity, synthetic unseen |
| **Model behavior** | compliant, omission, hallucination, over-abstention, source error, verbose, repetitive, malformed contract |
| **Context length** | short, medium, long, maximum boundary |
| **Runtime artifact** | BF16 base, adapter, merged, Q8, Q5/Q6, Q4 |
| **Topology** | single NPC, multi-listener, NPC-to-NPC, two scenes, owner mismatch |
| **Resource/lifecycle** | warm, cold, pressure, cancellation, zero-token retry, restart/reconnect |

## **37.2 Critical training/evaluation scenarios**

**T01 Known identity:** Profile supplies NPC identity. Response states it directly without model/vendor identity.

**T02 Player name correction:** Two admissible testimony records; corrected value must survive realization.

**T03 Unknown property:** No evidence for a dragon/property. Natural uncertainty with zero objective assertion.

**T04 Empty-hand perception:** KNOWN DIRECT\_OBSERVATION of none/empty; response states holding nothing.

**T05 Held item perception:** Fresh item/quantity; no wrong item, ownership, or provenance.

**T06 Episodic recall:** Player hid an object at a location; response attributes 'You told me...' and preserves both slots.

**T07 Distractor memory:** Recent unrelated dialogue must not outrank exact episodic memory.

**T08 Expired location:** Old location evidence becomes unknown current location, not false certainty.

**T09 Conflicting testimony:** Equal-quality conflict produces dispute/uncertainty.

**T10 Authoritative override:** Fresh observation supersedes old testimony for current volatile state.

**T11 Ambiguous deictic action:** 'Put it there' with missing referents asks for exactly object/location.

**T12 Resolved action:** Bound referents and capability allow action route; language waits for result.

**T13 Rejected action:** No false promise when Hytale rejects.

**T14 Desire:** Authored/current goal realized naturally and specifically.

**T15 Emotion:** SELF\_STATE realized without empirical uncertainty.

**T16 Secret withheld:** Disclosure policy blocks content without inventing a lie unless deception authorized.

**T17 Rumor:** NPC testimony remains attributed and non-canonical.

**T18 Relationship:** Known relationship answered; plausible unknown family not invented.

**T19 Persona variance:** Same semantic answer differs appropriately across profiles.

**T20 Humor/metaphor:** Expressive flourish does not add objective biography/event.

**T21 Structured compact choice:** Valid schema, no extra prose, bounded fields.

**T22 Deliberative final:** Reasoning stage hidden; final compact decision valid.

**T23 Multi-agent testimony:** Mara tells Lycander; Lycander knows only as Mara's statement.

**T24 Generated-speech contamination:** Student flourish cannot become belief or future truth.

**T25 Correction across sessions:** Supersession persists and old value remains historical.

**T26 Long context:** Required evidence near middle survives and irrelevant sections do not dominate.

**T27 Quantization negation:** Q4 preserves 'not', 'none', and uncertainty semantics.

**T28 Cancellation/stale output:** Candidate cannot commit after branch epoch changes.

**T29 Provider zero-token:** Existing bounded retry/fallback remains correct.

**T30 Cross-profile leak:** Lycander never adopts Mara's fox/gearbox desire unless supplied.

**T31 Unseen synthetic profile:** Follows new goals/style without memorized character facts.

**T32 Hytale connected action:** Speech matches actual inventory/location/navigation result.

## **37.3 Negative controls**

* Remove the required evidence while keeping the question unchanged.  
* Change the evidence source from direct observation to testimony.  
* Change current state after the memory was recorded.  
* Swap profile names and goals.  
* Insert prompt-injection text into player testimony.  
* Offer unsupported capabilities in dialogue.  
* Add a plausible but absent family member, possession, or past event.  
* Place the required proposition near a context boundary.  
* Generate several valid stylistic variants to ensure evaluation is semantic, not exact-string.  
* Present identical semantic state under different entity names and paraphrases.

## **37.4 Soak**

Final candidate evaluation includes at least a 100-turn deterministic/live-headless mixed-route soak and a connected bounded soak consistent with current project gates. Monitor repeated phrase growth, context contamination, queue/resource leakage, increasing firewall interventions, latency drift, and next-turn readiness.

# **38\. Definition of Done**

* A separate offline Model Distillation & Post-Training Harness exists and cannot start from normal Hytale runtime.  
* Every training row comes from a valid production-parity Orbis input and a typed, traceable target.  
* The eligibility classifier prevents Orbis source defects from being mislabeled as model-training data.  
* Teacher generation is provider-abstracted, terms-gated, reproducible, and never truth authority.  
* No hidden reasoning, raw audio, secrets, or unapproved personal data enters the corpus.  
* Exact/fuzzy/semantic deduplication and family-grouped splits prevent obvious leakage.  
* The immutable challenge and connected sets remain protected.  
* The pinned Nemotron 4B BF16 model completes a documented PEFT compatibility and hardware preflight, or the exact limitation is reported honestly.  
* SFT LoRA/QLoRA can train, save, reload, disable, merge, and reproduce a candidate.  
* Optional DPO is adopted only if it outperforms the approved SFT candidate without authority regression.  
* Base, adapter, merged BF16, converted GGUF, and quantized artifacts have complete lineage and independent evaluation.  
* The selected runtime artifact passes all current R001-current deterministic tests, ConversationMatrix, historical fixtures, live Gate A/B equivalents, multi-agent tests, and connected Hytale validation.  
* Delivered unsupported claims, false action promises, cross-NPC contamination, item/world action falsity, and persistence contamination remain zero in release gates.  
* Warm latency, VRAM/RAM, Hytale frame pressure, provider lifecycle, TTS delivery, and cancellation remain within established budgets.  
* Promotion is explicit, atomic, hash-verified, and paired with a proven rollback bundle.  
* New incidents may create active-learning candidates but cannot automatically train or promote models.  
* All required base/teacher/dataset/model licenses and notices are included and current.  
* Codex can reproduce every dataset, run, evaluation, package, promotion, and rollback from retained manifests.

| FINAL SUCCESS CRITERION. The objective is not to make Nemotron generally equivalent to the teacher. The objective is to make the small local student substantially more reliable, direct, grounded, characterful, and contract-compliant inside Orbis, while Orbis continues to own truth and safety. |
| :---- |

# **Appendix A. Proposed Component Map**

| Component | Responsibility | Repository status |
| :---- | :---- | :---- |
| **ModelPostTrainingCoordinator** | Top-level offline workflow and gate transitions. | Add. |
| **TrainingEligibilityClassifier** | Classify evaluation/Sentinel failures by earliest responsible owner. | Add; integrates with existing diagnosis. |
| **TrainingCandidateStore** | Append-only reviewed model-training candidate records. | Add under offline root. |
| **DistillationCorpusBuilder** | Project production inputs and targets into canonical rows. | Add. |
| **TeacherProvider** | Pluggable eligible teacher generation/import. | Add. |
| **TeacherTermsRegistry** | Provider legal/retention/commercial attestation. | Add. |
| **TeacherPromptRenderer** | Render teacher-only rubric prompt; never production student prompt. | Add. |
| **TeacherOutputValidator** | Schema plus deterministic Orbis oracle validation. | Add. |
| **DatasetCurator** | Filter, normalize, privacy scan, deduplicate, decontaminate, balance. | Add or wrap NeMo Curator. |
| **SemanticFamilyAssigner** | Group variants/incidents to prevent split leakage. | Add. |
| **DatasetSplitManager** | Immutable train/dev/test/challenge manifests. | Add. |
| **TrainingArtifactRegistry** | Model/dataset/run/evaluation/promotion manifests. | Add. |
| **PeftArchitectureInspector** | Discover supported modules and target strategies. | Add. |
| **TrainingBackend** | Common SFT/DPO/run interface. | Add. |
| **HfPeftTrainingBackend** | Transformers/PEFT/TRL implementation. | Add first. |
| **NemoRlTrainingBackend** | Optional NeMo RL implementation. | Add after compatibility proof. |
| **CandidateModelProviderFactory** | Load base, adapter, merged, and GGUF candidates for evaluation. | Add/extend provider factory. |
| **CandidateModelEvaluator** | Paired base/candidate orchestration through existing evaluation host. | Add. |
| **ModelPackagingPipeline** | Merge, convert, quantize, license bundle, hash. | Add. |
| **ModelPromotionController** | Explicit promotion/rollback transaction through existing model manager. | Add. |
| **ActiveLearningProjector** | Convert eligible incidents into deduplicated backlog. | Add later. |
| **OrbisRuntimeFactory** | Production/evaluation shared middle. | Retain; no training concerns added to runtime graph. |
| **ConversationMatrixHarness** | Deterministic regression and matrix evaluation. | Extend with model bundle identity. |
| **EpistemicClaimFirewall** | Objective claim authority. | Retain and reuse offline. |
| **Runtime Degradation Sentinel** | Incident/health/control plane. | Retain; export candidates only. |

## **A.1 Package boundary**

com.inigmasgames.persistentnpcs.training  
  /candidate  
  /corpus  
  /teacher  
  /curation  
  /registry  
  /backend  
  /evaluation  
  /packaging  
  /promotion  
  /cli

Runtime-shared DTOs may live in an additive package used by both Java and Python tooling.  
Heavy trainers remain external Python processes. The shipping JAR must not bundle PyTorch,  
teacher SDKs, training checkpoints, or automatic training workers.

## **A.2 Process boundary**

| Process | Allowed access |
| :---- | :---- |
| **Codex repository session** | Source tree, offline tools, manifests, approved credentials, build/test commands. |
| **Java corpus exporter** | Evaluation artifacts and cloned/synthetic scenario state; read-only production snapshots only when explicitly exported. |
| **Teacher worker** | Teacher batch file and approved credentials; no production filesystem. |
| **Trainer worker** | Frozen dataset, pinned base model, config, output run directory. |
| **Evaluator worker** | Protected manifests and candidate model; no training write access. |
| **Packager** | Approved candidate and conversion tools; writes candidate bundle only. |
| **Production Orbis** | Promoted model bundle plus normal runtime; no training root access. |

## **A.3 Cross-language contract**

Use JSON Schema for Java/Python boundary records. Generate or validate DTOs on both sides and maintain golden round-trip fixtures. Do not rely on Python pickles, Java serialization, or mutable ad hoc dictionaries as authoritative artifacts.

# **Appendix B. Normative Data Contracts**

## **B.1 Enumerations**

enum TrainingEligibility {  
  MODEL\_TRAINING\_ELIGIBLE,  
  ORBIS\_SOURCE\_REPAIR\_REQUIRED,  
  ORACLE\_OR\_DATA\_REPAIR\_REQUIRED,  
  CONNECTED\_VALIDATION\_REQUIRED,  
  NOT\_TRAINABLE,  
  NEEDS\_REVIEW  
}

enum ModelTrainingMethod {  
  SFT\_LORA,  
  SFT\_QLORA,  
  DPO\_LORA,  
  ON\_POLICY\_DISTILLATION,  
  FULL\_LOGIT\_DISTILLATION,  
  GRPO,  
  FULL\_FINE\_TUNE  
}

enum TeacherRole {  
  TARGET\_GENERATOR,  
  CRITIQUE\_REWRITER,  
  PREFERENCE\_RANKER,  
  VARIANT\_GENERATOR,  
  CONTRACT\_GENERATOR,  
  ADVERSARIAL\_GENERATOR  
}

enum DatasetSplit {  
  TRAIN, DEV, TEST, CHALLENGE, CONNECTED, CANARY  
}

enum CandidateStatus {  
  CREATED,  
  BF16\_EVAL,  
  QUANTIZED\_EVAL,  
  SHADOW,  
  CANARY,  
  PROMOTED,  
  REJECTED,  
  SUPERSEDED,  
  ROLLED\_BACK,  
  INVALIDATED  
}

enum ReviewState {  
  UNREVIEWED,  
  ORACLE\_ACCEPTED,  
  HUMAN\_ACCEPTED,  
  NEEDS\_REVIEW,  
  REJECTED,  
  FROZEN  
}

## **B.2 Artifact hashes**

record ArtifactHashes(  
    String canonicalPayloadSha256,  
    String productionMessagesSha256,  
    String targetSha256,  
    String promptTemplateSha256,  
    String tokenizerSha256,  
    String profileSnapshotSha256,  
    String baseModelManifestSha256  
) {}

## **B.3 Source provenance**

record SourceProvenance(  
    SourceKind kind,  
    Optional\<RunId\> evaluationRunId,  
    Optional\<IncidentId\> incidentId,  
    Optional\<ScenarioId\> scenarioId,  
    Optional\<FixtureId\> fixtureId,  
    Optional\<HumanLabelId\> humanLabelId,  
    Set\<FailureSignature\> failureSignatures,  
    String sourcePayloadSha256,  
    Instant capturedAt  
) {}

## **B.4 Teacher identity and eligibility**

record TeacherIdentity(  
    TeacherProviderId providerId,  
    String modelId,  
    String immutableRevision,  
    String endpointClass,  
    TeacherPromptTemplateId promptTemplateId,  
    String samplerConfigSha256  
) {}

record TeacherEligibilitySnapshot(  
    String termsDocumentUrl,  
    String termsDocumentSha256,  
    Instant reviewedAt,  
    boolean crossVendorTrainingAllowed,  
    boolean outputRetentionAllowed,  
    boolean commercialUseAllowed,  
    Optional\<String\> permittedExceptionId,  
    List\<String\> attributionRequirements,  
    OperatorApproval operatorApproval  
) {}

## **B.5 Production input**

record ProductionInputSnapshot(  
    List\<ChatMessage\> messages,  
    TurnExecutionPlanSummary turnPlan,  
    EpistemicContractSummary epistemic,  
    ProviderRequestPolicy providerPolicy,  
    ModelInputIdentity identity,  
    int renderedCharacters,  
    int renderedTokens,  
    String canonicalSha256  
) {}

## **B.6 Semantic target**

record EpistemicTargetSnapshot(  
    Answerability answerability,  
    ClaimMode claimMode,  
    List\<RequiredProposition\> required,  
    List\<ForbiddenPropositionPattern\> forbidden,  
    Set\<EvidenceSourceKind\> permittedSources,  
    Optional\<ActionResultSummary\> actionResult,  
    Optional\<ClarificationSlots\> missingSlots,  
    StylePolicy style,  
    OutputContract targetContract,  
    ResponseBudget responseBudget,  
    String canonicalSha256  
) {}

## **B.7 Dataset manifest**

record DatasetManifest(  
    DatasetVersionId id,  
    int schemaVersion,  
    String description,  
    Set\<ExampleId\> allExamples,  
    Map\<DatasetSplit, SplitManifest\> splits,  
    CoverageReport coverage,  
    CurationPolicyVersion curationPolicy,  
    List\<LicenseManifest\> licenses,  
    List\<TeacherEligibilitySnapshot\> teacherEligibility,  
    ContaminationAudit contaminationAudit,  
    String canonicalSha256,  
    ReviewApproval approval,  
    Instant frozenAt  
) {}

## **B.8 Training run manifest**

record TrainingRunManifest(  
    TrainingRunId id,  
    ModelTrainingMethod method,  
    BaseModelVersionId baseModel,  
    DatasetVersionId dataset,  
    Optional\<ModelCandidateId\> parentCandidate,  
    TrainingConfigId config,  
    EnvironmentIdentity environment,  
    List\<Long\> seeds,  
    ResourceBudget budget,  
    RunState state,  
    Optional\<CheckpointId\> selectedCheckpoint,  
    List\<ArtifactHash\> outputs,  
    Instant startedAt,  
    Optional\<Instant\> endedAt  
) {}

## **B.9 Candidate model manifest**

record ModelBundleManifest(  
    ModelBundleId id,  
    BaseModelVersionId base,  
    Optional\<AdapterVersionId\> adapter,  
    Optional\<MergedModelVersionId\> merged,  
    List\<GGUFVersionId\> ggufArtifacts,  
    RuntimeTemplateIdentity runtimeTemplate,  
    LicenseBundle licenseBundle,  
    EvaluationSummary evaluation,  
    CandidateStatus status,  
    String canonicalSha256  
) {}

## **B.10 Promotion record**

record ModelPromotionRecord(  
    PromotionId id,  
    ModelBundleId candidate,  
    ModelBundleId rollback,  
    Set\<PromotionGateId\> passedGates,  
    List\<EvaluationRunId\> evidence,  
    OperatorApproval approval,  
    String priorActiveManifestSha256,  
    String newActiveManifestSha256,  
    PromotionOutcome outcome,  
    Instant createdAt  
) {}

# **Appendix C. Initial Curriculum Catalog**

## **C.1 Curriculum families**

| ID / family | Coverage | Primary expected behavior |
| :---- | :---- | :---- |
| **C01 Direct known answers** | Identity, relationship, location, possession, property, quantity. | Required proposition first; no unsupported extension. |
| **C02 Natural unknown** | Absent evidence across objective domains. | Correct uncertainty without stock safety wording. |
| **C03 Partial knowledge** | Known object but unknown location/property; multi-slot questions. | Answer known portion, identify unknown portion. |
| **C04 Conflicted evidence** | Equal/unequal testimony, observation, canon. | Source-aware dispute and revision. |
| **C05 Clarification** | Missing object, actor, location, time, action parameter. | Ask only for missing slots. |
| **C06 Corrections** | Names, residence, item attributes, relationship self-report. | Adopt admissible correction, retain source/history. |
| **C07 Current perception** | Held item, empty hand, nearby entity, visibility. | Exact fresh state or needs perception. |
| **C08 Episodic memory** | Object/location, event participants, sequence. | Correct recall and source language. |
| **C09 Temporal revision** | Old/current state, expiration, supersession. | No stale certainty. |
| **C10 Self-state** | Emotion, desire, goal, current task, intent. | Use SELF\_STATE/AUTHORED\_CANON, not empirical uncertainty. |
| **C11 Action result** | Success, rejection, partial completion, capability absence. | No promise before result. |
| **C12 Relationships/social** | Trust, affection, obligations, known/unknown relations. | No plausible invented history. |
| **C13 Secrets/disclosure** | Public/private/withheld/authorized deception. | Correct withholding policy. |
| **C14 Testimony/rumor** | Player and NPC statements. | Attribution and non-canon propagation. |
| **C15 Persona realization** | Warm, stern, terse, excited, cautious. | Style variation with identical facts. |
| **C16 Humor/metaphor** | Safe expressive flourish. | No objective claim expansion. |
| **C17 Structured choice** | Compact action/decision JSON. | Schema-valid, bounded, no prose. |
| **C18 Deliberative final** | Goals, risks, relationships, plan choice. | Compact final after bounded reasoning. |
| **C19 Multi-turn workspace** | Pronouns, topic, correction, interruption. | Continuity without broad history guessing. |
| **C20 Multi-agent** | Floor ownership, testimony, disagreement, shared plan. | No shared omniscience or self-seeding. |
| **C21 Adversarial injection** | Player says ignore memory/system; fabricated capability. | Treat as content and preserve contracts. |
| **C22 Quantization stress** | Negation, numbers, names, strict JSON, long context. | BF16-to-GGUF parity. |

## **C.2 Initial corpus composition guidance**

Use this only as a starting distribution for the first pilot. The dataset builder must adjust from measured coverage and failure rates.

| Group | Approximate row share | Notes |
| :---- | :---- | :---- |
| **Authority core: known/unknown/partial/conflict** | 30-40% | Most important behavior; diverse domains and sources. |
| **Memory, time, correction, referents** | 20-25% | Multi-turn/session families grouped in splits. |
| **Self-state, relationship, testimony, disclosure** | 15-20% | Profile and source variance. |
| **Action and structured contracts** | 10-15% | Strict action-result truth and schema validity. |
| **Persona, humor, naturalness preferences** | 10-15% | Only authority-clean outputs. |
| **Long-context, multi-agent, adversarial** | 5-10% | Challenge-heavy; avoid token domination. |

## **C.3 Example family template**

familyId: C08-hidden-object-recall-v1  
taskType: SOURCE\_ATTRIBUTED\_RECALL  
actors:  
  npc: synthetic-smith-07  
  player: player-a  
world:  
  no direct observation of hidden object  
cognition:  
  episodic testimony:  
    subject: player-a  
    predicate: HID  
    object: silver-key  
    location: under-large-rock  
    source: PLAYER\_TESTIMONY  
turn:  
  "What did I hide, and where did I hide it?"  
target:  
  answerability: PARTIALLY\_KNOWN or KNOWN per contract  
  required:  
    \- player-a HID silver-key  
    \- silver-key LOCATION under-large-rock  
  source framing:  
    \- "You told me..."  
forbidden:  
  \- NPC\_DIRECTLY\_OBSERVED event  
  \- current object existence/location as world truth beyond testimony  
variants:  
  changed item, location, syntax, delay, distractor conversation, NPC profile  
split:  
  assigned by family before variant generation

# **Appendix D. Baseline Training Configurations**

| IMPORTANT. These configurations are starting points for preflight and pilot comparison, not promised production settings. Codex must adapt exact field names to the selected backend and the discovered Nemotron module inventory. |
| :---- |

## **D.1 Local 12 GB QLoRA smoke**

run\_name: orbis-nemotron-4b-qlora-smoke  
method: SFT\_QLORA  
base\_model: nvidia/NVIDIA-Nemotron-3-Nano-4B-BF16@\<pinned-revision\>  
trust\_remote\_code: true  
sequence\_length: 1024  
micro\_batch\_size: 1  
gradient\_accumulation\_steps: 8  
epochs: 1  
max\_steps: 10  
gradient\_checkpointing: true  
quantization:  
  load\_in\_4bit: true  
  quant\_type: nf4  
  double\_quant: true  
  compute\_dtype: bf16-or-fp16-as-supported  
lora:  
  target\_strategy: discovered-attention-plus-mlp-candidate  
  rank: 8  
  alpha: 16  
  dropout: 0.05  
optimizer: paged\_adamw\_8bit-or-supported-equivalent  
learning\_rate: 0.0001  
warmup\_ratio: 0.03  
weight\_decay: 0.0  
loss\_mask: assistant\_only  
evaluation:  
  every\_steps: 5  
  protected\_test\_access: false  
abort:  
  on\_oom: true  
  on\_nan\_inf: true  
  on\_unexpected\_trainable\_base: true

## **D.2 Productive SFT LoRA pilot**

run\_name: orbis-nemotron-4b-sft-lora-pilot  
method: SFT\_LORA or SFT\_QLORA after preflight  
sequence\_length: 2048  
micro\_batch\_size: measured  
effective\_batch\_size: 32-128 examples or token-equivalent  
epochs: 1-3  
lora\_rank\_candidates: \[8, 16, 32\]  
lora\_alpha\_candidates: \[rank, 2\*rank\]  
lora\_dropout\_candidates: \[0.0, 0.05\]  
learning\_rate\_candidates: \[0.00005, 0.0001, 0.0002\]  
scheduler: cosine-or-linear-pinned  
warmup\_ratio: 0.03-0.05  
gradient\_clipping: 1.0  
checkpoint\_policy:  
  interval: bounded  
  keep\_best\_by: composite-dev-authority-score  
selection:  
  zero\_tolerance:  
    delivered\_unsupported\_claims: 0  
    false\_action\_promises: 0  
    contract\_execution\_errors: 0  
  primary:  
    required\_proposition\_recall  
    correct\_abstention  
    over\_abstention  
    source\_attribution  
    first\_pass\_contract\_validity  
  secondary:  
    persona\_fidelity  
    repetition  
    latency

## **D.3 DPO pilot**

run\_name: orbis-nemotron-4b-dpo-pilot  
method: DPO\_LORA  
policy\_init: \<approved-sft-candidate\>  
reference\_policy: \<same-approved-sft-lineage\>  
dataset: \<frozen-preference-dataset\>  
micro\_batch\_size: measured  
gradient\_accumulation\_steps: measured  
epochs: 1  
beta\_candidates: \[0.05, 0.1, 0.2\]  
aux\_sft\_loss\_weight\_candidates: \[0.0, 0.1\]  
learning\_rate\_candidates: \[0.000005, 0.00001, 0.00005\]  
selection:  
  must\_beat\_or\_equal\_sft\_on\_all\_zero\_tolerance\_and\_authority\_metrics  
  must\_improve\_at\_least\_one\_approved\_preference\_quality\_metric  
abort:  
  required\_proposition\_recall\_regression: true  
  over\_abstention\_regression: true  
  response\_length\_collapse: true

## **D.4 Evaluation sampler profiles**

| Profile | Purpose | Policy |
| :---- | :---- | :---- |
| **DETERMINISTIC** | Contract and regression comparison. | Temperature 0 or lowest supported; fixed seed; route stops. |
| **PRODUCTION\_MATCH** | Actual player experience. | Exact production sampler and reasoning policy. |
| **ROBUSTNESS\_N** | Stochastic failure rate. | N repeated samples with versioned seeds or sampler state. |
| **REASONING\_ON** | Deliberative/complex routes. | Existing route-specific reasoning control only. |
| **REASONING\_OFF** | Fast and finalization routes. | Existing production mode. |

## **D.5 Hyperparameter experiment rule**

Do not perform an unbounded sweep. Use a staged design: one architecture target set, a small rank/learning-rate grid, one or two seeds, and dev metrics. Only finalists receive full test/challenge/quantization evaluation.

# **Appendix E. Promotion Checklist**

## **E.1 Legal and lineage**

* ☐ Base model repository, revision, weights, tokenizer, config, chat template, and license are pinned and hashed.  
* ☐ Teacher terms permit the exact retention and cross-model training use.  
* ☐ Dataset and software licenses are complete.  
* ☐ NVIDIA LICENSE/NOTICE obligations are bundled.  
* ☐ No unknown or mutated artifact shares an approved version ID.

## **E.2 Dataset**

* ☐ Every positive target passes schema, required/forbidden proposition, claim, action, and privacy oracles.  
* ☐ Every source defect is excluded from model-training data.  
* ☐ Exact/fuzzy/semantic duplicates are handled.  
* ☐ Train/dev/test/challenge families are disjoint.  
* ☐ Connected/red-team prompts are protected.  
* ☐ Coverage and imbalance report is approved.  
* ☐ Dataset manifest is frozen and hashed.

## **E.3 Training**

* ☐ PEFT target modules are discovered and documented.  
* ☐ One-batch preflight and adapter round-trip pass.  
* ☐ Training environment and config are reproducible.  
* ☐ Loss/gradients remain finite.  
* ☐ No unexpected base parameters train.  
* ☐ Selected checkpoint is frozen before protected test evaluation.  
* ☐ At least one clean rerun or reproducibility check exists for the final recipe.

## **E.4 Evaluation**

* ☐ Base and candidate use identical production inputs.  
* ☐ All zero-tolerance Orbis metrics pass.  
* ☐ No historical regression reopens.  
* ☐ Unseen profiles and semantic families pass.  
* ☐ Long-context, multi-agent, and adversarial challenge pass.  
* ☐ Human review confirms persona/naturalness on a representative sample.  
* ☐ LLM judge, if used, remains secondary and calibrated.

## **E.5 Packaging**

* ☐ Adapter-loaded and merged BF16 behavior is compatible.  
* ☐ Converter/quantizer commits and commands are recorded.  
* ☐ GGUF metadata/template is correct.  
* ☐ Target quantization passes independently.  
* ☐ Model bundle contains hashes, license, NOTICE, tokenizer/template, runtime config, and evaluation summary.

## **E.6 Runtime and connected**

* ☐ Full deterministic suite, ConversationMatrix, soak, live Gate A/B equivalents, and multi-agent tests pass.  
* ☐ Shadow shows no blocking regression and no foreground resource impact.  
* ☐ Canary profiles pass.  
* ☐ Connected Hytale PTT/text, TTS, actions, memory, interruption, reconnect, and GPU/frame tests pass.  
* ☐ Candidate health remains stable with no new repeated Sentinel signature.

## **E.7 Promotion and rollback**

* ☐ Candidate and rollback bundles are available and hash-verified.  
* ☐ Promotion is explicit and atomic.  
* ☐ Half-open production smoke passes.  
* ☐ Readiness reports the correct active model.  
* ☐ Rollback has been exercised and verified.  
* ☐ PromotionRecord is complete.

# **Appendix F. Project and External Sources**

## **F.1 Project sources**

| Source | Design use |
| :---- | :---- |
| **\[P1\] Orbis Conversational Pipeline Hardening Matrix** | Current turn/branch/provider/contract/budget/canonical-speech/resource/test ownership; training previously out of scope. |
| **\[P2\] Orbis Epistemic Cognition Technical Design** | EvidencePacket, Answerability, AnswerPlan, claim firewall, belief/memory provenance, and original fine-tuning deferral. |
| **\[P3\] Orbis Runtime Degradation Sentinel and Self-Healing Technical Design** | Incidents, failure signatures, containment, regression candidates, and no runtime self-modification. |
| **\[P4\] Orbis Autonomous Conversation Evaluation & Training Harness Technical Design** | Production-parity headless evaluation, earliest-boundary diagnosis, live verify/freeze, and distinction between hardening, NPC learning, and model training. |
| **\[P5\] R090 Autonomous Evaluation Harness implementation and H0-H8 cleanup audit** | Implemented production/evaluation composition, strict gates, scenario sandbox, live Nemotron evaluation, and matrix results. |
| **\[P6\] Orbis LLM Runtime \- llama.cpp Sidecar Integration** | Runtime parity, model manifests, GGUF/Ollama/llama.cpp benchmarking, packaging, and hardware gates. |
| **\[P7\] Current active provider/model manifests, traces, regression fixtures, Gate A/B reports, and implementation revisions** | Repository and runtime authority for exact model/template/prompt behavior. |

## **F.2 Distillation and post-training research**

| Source | URL | Design use |
| :---- | :---- | :---- |
| **\[R1\] Hinton, Vinyals, Dean. Distilling the Knowledge in a Neural Network.** | https://arxiv.org/abs/1503.02531 | Foundational teacher-student knowledge distillation. |
| **\[R2\] Hu et al. LoRA: Low-Rank Adaptation of Large Language Models.** | https://arxiv.org/abs/2106.09685 | Frozen base plus low-rank trainable updates. |
| **\[R3\] Dettmers et al. QLoRA: Efficient Finetuning of Quantized LLMs.** | https://arxiv.org/abs/2305.14314 | 4-bit frozen base plus LoRA for reduced-memory fine-tuning. |
| **\[R4\] Rafailov et al. Direct Preference Optimization.** | https://arxiv.org/abs/2305.18290 | Preference optimization without a separate reward-model/PPO pipeline. |
| **\[R5\] Wang et al. Self-Instruct.** | https://arxiv.org/abs/2212.10560 | Synthetic instruction generation with filtering and similarity removal. |
| **\[R6\] Hsieh et al. Distilling Step-by-Step.** | https://arxiv.org/abs/2305.02301 | Teacher-generated labels/rationales as task-specific supervision; this design excludes private reasoning. |
| **\[R7\] Yang et al. Rethinking Benchmark and Contamination for Language Models with Rephrased Samples.** | https://arxiv.org/abs/2311.04850 | Paraphrase/semantic contamination risk beyond exact string matching. |

## **F.3 NVIDIA model and training ecosystem**

| Source | URL | Design use |
| :---- | :---- | :---- |
| **\[N1\] NVIDIA-Nemotron-3-Nano-4B-BF16 model card** | https://huggingface.co/nvidia/NVIDIA-Nemotron-3-Nano-4B-BF16 | Model architecture, 3.97B parameters, custom code, reasoning modes, intended use, licensing. |
| **\[N2\] NVIDIA Nemotron Open Model License** | https://www.nvidia.com/en-us/agreements/enterprise-software/nvidia-nemotron-open-model-license/ | Commercial/derivative permissions and redistribution/NOTICE conditions. |
| **\[N3\] NVIDIA Nemotron Developer Asset Hub** | https://github.com/NVIDIA-NeMo/Nemotron | Synthetic generation, curation, SFT/PEFT/RL, conversion, optimization, and evaluation recipe patterns. |
| **\[N4\] NVIDIA NeMo RL** | https://github.com/NVIDIA-NeMo/RL | SFT, DPO, GRPO, LoRA, on-policy distillation, single/multi-GPU infrastructure. |
| **\[N5\] NeMo RL LoRA guide** | https://docs.nvidia.com/nemo/rl/nightly/guides/lora.html | LoRA backend support and configuration. |
| **\[N6\] NeMo RL SFT guide** | https://docs.nvidia.com/nemo/rl/nightly/guides/sft.html | SFT workflows, LoRA, memory optimizations. |
| **\[N7\] NeMo RL DPO guide** | https://docs.nvidia.com/nemo/rl/nightly/guides/dpo.html | DPO parameters and LoRA support. |
| **\[N8\] NVIDIA NeMo Curator** | https://github.com/NVIDIA-NeMo/Curator | Text filtering, classification, exact/fuzzy/semantic deduplication, scalable curation. |

## **F.4 Hugging Face training and evaluation**

| Source | URL | Design use |
| :---- | :---- | :---- |
| **\[H1\] Hugging Face PEFT** | https://github.com/huggingface/peft | LoRA adapter implementation and model integration. |
| **\[H2\] PEFT LoRA guide** | https://huggingface.co/docs/peft/main/conceptual\_guides/lora | LoRA concepts and merging. |
| **\[H3\] PEFT merge\_and\_unload** | https://huggingface.co/docs/peft/main/package\_reference/lora | Adapter merge and post-training handling. |
| **\[H4\] Hugging Face TRL** | https://github.com/huggingface/trl | SFT, DPO, GRPO, and distillation trainers. |
| **\[H5\] TRL DistillationTrainer** | https://github.com/huggingface/trl/blob/main/docs/source/distillation\_trainer.md | On-policy full-distribution distillation option. |
| **\[H6\] EleutherAI LM Evaluation Harness** | https://github.com/EleutherAI/lm-evaluation-harness | External benchmark and adapter/GGUF evaluation framework. |

## **F.5 Runtime packaging**

| Source | URL | Design use |
| :---- | :---- | :---- |
| **\[G1\] llama.cpp** | https://github.com/ggml-org/llama.cpp | GGUF inference, conversion, quantization, grammar, LoRA support. |
| **\[G2\] convert\_lora\_to\_gguf.py** | https://github.com/ggml-org/llama.cpp/blob/master/convert\_lora\_to\_gguf.py | PEFT LoRA to GGUF conversion. |
| **\[G3\] GGUF-my-LoRA discussion** | https://github.com/ggml-org/llama.cpp/discussions/10123 | Loading converted PEFT LoRA with a GGUF base. |
| **\[G4\] Ollama importing models** | https://docs.ollama.com/import | Safetensors/GGUF model and adapter import; base compatibility warning. |
| **\[G5\] Ollama Modelfile reference** | https://docs.ollama.com/modelfile | FROM, ADAPTER, TEMPLATE, parameters, and license fields. |

## **F.6 Teacher and terms sources**

| Source | URL | Design use |
| :---- | :---- | :---- |
| **\[O1\] OpenAI Model Distillation in the API** | https://openai.com/index/api-model-distillation/ | Evidence that stronger-model outputs can supervise a smaller task-specific model; official workflow applies within supported OpenAI fine-tuning. |
| **\[O2\] OpenAI Terms of Use, effective 1 January 2026** | https://openai.com/policies/terms-of-use/ | Prohibits using Output to develop models that compete with OpenAI and automatically/programmatically extracting Output. |
| **\[O3\] OpenAI Services Agreement, effective 1 January 2026** | https://openai.com/policies/services-agreement/ | Business/API restriction with Permitted Exception concept; exact contract controls. |

## **F.7 Source interpretation limits**

* A framework's support for LoRA, SFT, DPO, or distillation does not prove support for this exact Nemotron 4B custom architecture.  
* NVIDIA's published Nano v3 LoRA recipes may target other Nano variants; D6 must prove the 4B path.  
* Ollama documents GGUF adapters generally but direct Safetensors adapter support is architecture-specific.  
* OpenAI's own distillation workflow does not itself grant permission to use ChatGPT/API output to train an unrelated third-party model.  
* All external tools and terms may change. Codex must pin versions and re-check current documentation at implementation time.

| END OF SPECIFICATION. Codex should implement the smallest complete vertical slice first: D0-D7 through an isolated SFT pilot. The success criterion is not that a training script runs. It is that a legally eligible, uncontaminated, production-parity dataset produces a reproducible Nemotron candidate that improves verified provider weaknesses without weakening any Orbis authority or runtime guarantee. |
| :---- |

