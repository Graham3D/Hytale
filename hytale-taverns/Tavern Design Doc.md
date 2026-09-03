# **Tavern Design Document**

## *Hytale Tavern Management Mod*

# **1\. Game Overview**

## **High Concept**

Build and operate a Tavern from scratch. Gather resources, prepare food and drinks, serve patrons, earn currency and Tavern XP, then use Tavern Levels, Skill Points, and earnings to grow the business. Expand from a small manually run establishment into a large, partially or fully automated Tavern that is valuable to both NPCs and real players.

The Tavern should support solo play while becoming especially interesting on persistent multiplayer servers.

## **Design Pillars**

### **Hands-On Tavern Building**

The Tavern should use Hytale's normal building freedom rather than prefabricated buildings. Players design the layout, rooms, furniture placement, kitchen, dining areas, guest rooms, and other functional spaces themselves.

### **Tavern Management**

1. Manage recipe ingredients in order to maintain food and drink supply and meet patron demand.  
2. Create and manage your own menu for patrons to choose items from.  
3. Earn Copper, Silver, and Gold by completing patron orders.  
4. Maintain a good Reputation to attract higher-quality patrons.  
5. Earn Tavern XP from successful service, which contributes toward Tavern Level.  
6. Expand and grow the Tavern as new levels, Cores, and systems become available.  
7. Hire NPC staff or other players to help run the Tavern as it becomes more difficult to manage alone.  
8. Manage service times, Tavern capacity, and patron satisfaction to prevent walkouts and Reputation loss.

### **Progression Through Growth**

Start with a small Tavern and grow it by completing patron orders and earning Tavern XP. Tavern Levels provide Skill Points and unlock new progression gates. Major milestones can require gemstones tied to Hytale Adventure progression. Skill Points and currency are invested into the Tavern Skill Tree, while Crystal Shards are used to expand Core building volumes.

### **Parallel Adventure Progression**

Tavern progression should develop alongside Hytale Adventure progression. As the Tavern advances, it begins requiring recipes, ingredients, gemstones, Crystal Shards, and other resources found in more dangerous biomes. The Tavern Host can gather these resources personally or rely on other players to obtain them.

### **Multiplayer Symbiosis**

The Tavern Host and adventuring players share a mutually beneficial relationship. Players benefit from visiting the Tavern through Relaxed bonuses, food, drinks, lodging, and other buffs that aid them while adventuring. In return, adventurers bring back valuable resources, currency, crafting recipes, gemstones, and other discoveries that help the Tavern grow and provide better services.

### **Player Freedom**

Players should be able to decide what kind of Tavern they build. A high-level Tavern could become a huge, high-volume establishment or a smaller luxury inn focused on wealthy patrons and high-quality accommodations.

## **Core Gameplay Loop**

Adventure and gather resources, prepare the Tavern, open for service, serve patrons, earn currency and Tavern XP, improve the Tavern, then close when needed to restock, remodel, repair, or return to adventuring.

## **Single-Player / Multiplayer Goals**

### **Single-Player**

The Tavern must remain fully viable when playing alone. The Host can close the Tavern at any time to adventure, gather resources, remodel, or restock. NPC staff and automation eventually help one player manage larger operations.

### **Multiplayer**

Multiplayer should make the Tavern more useful without making it mandatory. Other players can visit for buffs and lodging, bring the Host difficult-to-obtain resources, work as Tavern staff, and help complete Rare or Unique Patron requests.

# **2\. Tavern Building & Progression**

### **Building Volume**

Building and managing a Tavern begins with crafting and placing a Tavern Core. The Core establishes the Tavern's primary building volume. Cores are visible during placement and configuration, then hidden during normal gameplay so they do not clutter the Tavern.

Additional Cores, such as Kitchen Cores, Bedroom Cores, Bar Cores, and Reserved Cores, define specialized areas within the Tavern and must remain inside the Tavern Core. Containment within the Tavern Core is required and does not count as Core overlap. Specialized Core volumes may not overlap one another, and Tavern Core volumes may not overlap other Taverns. During placement or resizing, invalid space is tinted red. Tavern Management \> Zoning lists every Core and provides Show All, Hide All, and individual Show / Hide controls. Selecting a visible Core reveals its object and volume boundary for editing.

### **Core Geometry & Spatial Indexing**

All Core volumes are axis-aligned rectangular cuboids. Each Core is stored by its minimum and maximum X, Y, and Z coordinates. The Tavern Core contains all specialized Cores; specialized Cores cannot overlap one another or extend outside the Tavern Core. The Tavern Core also defines the spatial boundary used by the Comfort system: only explicitly approved Comfort assets placed inside the Tavern Core are eligible to contribute. Walls, floors, roofs, raw construction blocks, and similar structural pieces are not scanned for Comfort. Doors and Windows may contribute only through their explicit Comfort categories. Comfort density thresholds do not use cubic Core volume. They use Eligible Floor Area: validated walkable floor blocks across usable Tavern stories. Empty vertical air and ceiling height do not increase Comfort requirements, and merely expanding the Core does not increase a threshold until the expansion creates additional eligible floor area. The implementation must distinguish usable Tavern floor from raw terrain or unused expansion space; the exact finished-floor validation rule remains TBD.

Terrain Subtraction changes world terrain only and does not carve holes or irregular shapes into the Core itself. A rectangular Core remains rectangular after terrain is removed. This keeps zoning, containment, path validation, and persistence predictable.

Each Tavern and Core tracks the world chunks its volume intersects so position queries can quickly determine which Tavern or specialized Core contains a block or object without scanning every Tavern in the world.

### **Core Expansion**

The Tavern Host expands Cores through Tavern Management \> Zoning by revealing and selecting the Core, then dragging its boundaries in the Zoning Editor. Proposed expansion is validated before it can be committed. Intersections with another specialized Core, another Tavern, protected regions, or blocks the Host lacks permission to edit are invalid and shown with a red tint. Expanding a valid Core consumes Crystal Shards, with the required shard color determined by the type of Core being expanded.

The starting exchange rate is 1 Crystal Shard for 5 blocks of additional Core volume. Skill Tree Passives can improve this exchange rate, allowing the Tavern Host to expand more efficiently.

### **Crystal Shard Rebate**

When a Skill Tree Passive improves the Crystal Shard exchange rate, any Crystal Shards previously invested into Core expansion are retroactively recalculated at the improved rate. The difference is refunded to the Tavern Host so players are not penalized for expanding before unlocking the Passive.

### **Refunding Crystal Shards**

If the Tavern Host reduces a Core's volume, the Crystal Shards previously invested into that space are refunded based on the current Crystal Shard exchange rate. This allows Tavern layouts to be redesigned without permanently losing resources invested into previous expansions.

### **Volume Subtraction**

When a Core's building volume intersects existing terrain, the Tavern Host can choose to Subtract the terrain from that volume. This allows underground areas such as basements, cellars, and sunken rooms to be created without manually mining every block. Subtraction only affects blocks the Host is permitted to modify and cannot bypass player or server protection. Protected or unauthorized blocks are shown as invalid with a red tint and prevent the affected volume change from being committed.

### **Object Registration**

Functional Tavern objects use metadata that identifies their role, such as Chair, Table, Cooking Station, Storage, Dishwashing Station, Service Line, and Comfort Tier. When a compatible object is placed, destroyed, moved, loaded, or unloaded, the Tavern system updates the registry for the Tavern and Core containing that position rather than continuously scanning the entire building volume.

Each registered object stores a stable world reference or position, asset ID and type, functional type, associated Core, Comfort category and value where applicable, durability where applicable, and current Valid / Invalid state. For threshold Comfort categories, the registry also maintains the qualifying placed-instance count and, where required by that category, the distinct asset-type count. Object registration does not guarantee functionality; Open for Service performs a broader validation pass for reachability and other requirements. Invalid or unreachable objects remain placed and registered but do not contribute to functional capacity until they become valid again.

## **Core Types**

### **Tavern Core**

The Tavern Core establishes the Tavern and defines the maximum area recognized by the Tavern Management system. After placement, it is normally hidden and managed through the Zoning menu.

Starting Dimensions

* 21 × 21 × 5  
* Starting Volume: 2,205 blocks

Crafting Requirements

* 1x Green Crystal

* Found in the Emerald Wilds.

* 1x Greater Life Crystal

* Requires the Farmer Workbench.

### **Kitchen Core**

The Kitchen Core defines the space used to cook food, brew drinks, clean dishes, and support kitchen-based NPC staff and automation.

Starting Dimensions

* 13 × 10 × 5  
* Starting Volume: 650 blocks

Crafting Requirements

* 1x Cyan Crystal

* Found in the Azure Forest.

* 1x Greater Life Crystal

* Requires the Farmer Workbench.

### **Bedroom Core**

The Bedroom Core defines a rentable room that can be occupied by a patron or player tenant. NPCs use the Core volume to identify and path to their assigned room.

Starting Dimensions

* 7 × 5 × 5  
* Starting Volume: 175 blocks

Crafting Requirements

* 1x Blue Crystal

* Found in the Frostmarch Tundra.

* 1x Greater Life Crystal

* Requires the Farmer Workbench.

Room Requirements

* Bed  
* End Table  
* Personal Chest  
* Eviction Chest (can be placed anywhere in the Tavern)  
* Light Source

Occupancy

* Supports one tenant.

Tenant Benefits

* Players can use an item to teleport back to their Tavern room instantly, or type /recall.  
* Private storage is accessible only to the tenant.  
* The tenant can lock the room to keep uninvited players out.  
* Sleeping in a Bedroom rented for the night upgrades Relaxed to Rested, increasing duration by 25%.  
* An active Bedroom lease upgrades Rested to Well Rested, increasing duration by 50% relative to Relaxed.  
* If a tenant is evicted, items stored in their Personal Chest are transferred to the Tavern's Eviction Chest for later access.

### **Bar Core**

The Bar Core defines a dedicated drinking area and enables Rare Patron visits. The maximum number of Rare Patrons that can occupy the Tavern at one time is equal to the number of valid seats within the Bar Core. Any valid seating type can qualify. The Bartender profession becomes available once a Bar Core is placed and the required Drink Workbenches are inside its volume. Final dimensions and crafting requirements are still to be determined.

### **Reserved Core**

The Reserved Core defines dedicated seating for Unique Patrons. Each Reserved Core provides capacity for one Unique Patron at a time. It is intended as an endgame Core and requires a highly rare White Crystal. Final dimensions and any additional crafting requirements are still to be determined.

## **Tavern Level Progression**

Tavern XP is earned primarily by completing patron orders and Special Patron Requests. The current progression target extends through Tavern Level 40\. Each time the Tavern levels up, it awards a Skill Point. Tavern Level itself is advanced through XP; Gold and gemstones are instead used with Skill Points to purchase Passives and cross major Skill Tree progression gates. The exact Level 1–40 XP curve and Skill Tree costs are still TBD.

## **Tavern Skill Tree**

Tavern Levels award Skill Points that the Host can invest into Passives. The current Skill Tree is organized into progression bands of Levels 1–9, 10–19, 20–29, and 30–40. Individual Passives have minimum Tavern Level requirements, preventing the Host from purchasing beyond the highest progression band they have reached. Routine Passives use Skill Points and Gold, while major progression gates can require a gemstone. Tavern Skill Tree Passives belong to the establishment, so authorized staff working for that Tavern also gain access to applicable unlocked benefits.

* Cooking I–V: Improves the quality and buff duration of food and drink prepared through the Tavern.  
* Well Rested Passives: Adds survival-focused bonuses to the Well Rested state.  
* Core Efficiency: Improves the Crystal Shard to building-volume exchange rate and provides a rebate on previous expansion costs.

* Staff Capacity: Increases the number of NPC or player employees the Tavern can hire.

# **3\. Tavern Operations**

## **Tavern Management**

Tavern Management is the Tavern Host's central administrative interface and is accessed through a top-level Tavern page in Hytale's standard Tab menu. The Tavern page is visible and selectable for all players, but its management controls remain greyed out until the player owns a Tavern. An information tooltip explains how to get started by crafting and placing a Tavern Core. Once ownership is established, the landing screen combines real-time Tavern statistics with Service controls. Primary pages are Staff | Hiring | Menu | Rooms | Zoning; pages remain locked until their related systems are unlocked.

* Tavern Level / XP and current Reputation.  
* Tavern Health and Sanitation.  
* Current Patrons and Active Seats.  
* Staff Working Today / Total Employees.  
* Workstation condition and status, with Damaged or Broken equipment clearly flagged.  
* Revenue generated during the active service period.  
* Each main statistic can be pinned to a compact HUD for real-time monitoring without reopening the Tavern page.

## **Service Controls**

A Tavern can operate only between 8:00 AM and 9:00 PM. The Host sets a daily Opening Time and Closing Time within that range, allowing schedules such as 4:00 PM–9:00 PM. The Tavern Management landing screen also lets the Host choose how many valid seats are active and which employees are Working or Off Duty. Send Home ends an employee's current shift; employees can also be scheduled Off Duty for the next several Tavern days. Off Duty employees are not paid for shifts they do not work.

When the Tavern closes, no new patrons spawn. Patrons already eating or drinking finish normally, pay, and leave. Patrons still waiting on unfinished orders have those orders cancelled, leave the Tavern, and are removed from the active queue. Closing does not create a Reputation penalty for otherwise healthy pending orders, but an order whose tolerance already expired before closing still resolves as failed service. Reducing active seating or keeping staff Off Duty does not itself reduce Reputation.

## **Main Entrance & Pathing Validation**

The Host must designate one Main Entrance door through Zoning before the Tavern can Open for Service. Its position and orientation are used for patron spawning and navigation. Chairs, tables, workstations, and other functional Tavern furniture use metadata that identifies their type and, where applicable, Comfort tier.

When the Tavern prepares to open, functional objects are validated for reachability. A chair counts as Valid Seating only when it is inside the Tavern, has sufficient occupancy space, is associated with a valid table where required, and can be reached from the Main Entrance. Workstations and service objects must likewise be reachable. Unreachable objects remain placed but are treated as unavailable until a valid path exists.

## **Service Line & Order Queue**

Completed food and drink orders can be placed on a designated Service Line surface for Servers to collect and deliver to the correct patron. The Host and player employees may also deliver orders directly. Patrons display an order icon above their head so active orders can be managed visually during early gameplay.

The Order Queue is an unlockable quality-of-life feature intended for larger Taverns. It uses the Hytale Furniture\_Lumberjack\_Painting asset and consolidates active patron orders into a readable physical board. The Order Queue is unlocked through a Tavern Skill Tree Passive and is not required to operate a Tavern.

## **Food & Drink Preparation**

Patron orders require the appropriate ingredients, clean serving items, and workstations. The Host, player staff, or NPC staff can prepare eligible orders. All eligible crafting stations inside a Kitchen Core can pull ingredients and Clean Plates, Mugs, or other required serving items from storage chests within that Kitchen Core. Kitchen storage is private to the Tavern Host by default; Player Employees require explicit permission to access those containers. NPC staff and workstations can consume from authorized Kitchen storage as a shared logical ingredient pool without requiring a separate pantry system. Missing ingredients, clean serving items, or available workstations prevent completion until the Tavern can satisfy the order.

## **Inventory & Transactions**

All Tavern actions that move or create valuable items or currency are server-authoritative and atomic. Before Tavern crafting begins, the system validates the recipe, required workstation, ingredients, and clean serving item, then reserves or consumes the required resources so two simultaneous crafting jobs cannot claim the same item.

Completing a patron order validates the correct Order and delivered item before resolving payment, Tavern XP, and Reputation effects. Player purchases, employee wages, Core refunds, and similar transfers follow the same rule: either the complete transaction succeeds or no partial transfer is committed.

Prepared items created specifically for NPC service carry an internal Order ID or equivalent stable reference so Servers and automation can identify the correct patron order. If a transaction fails during validation, reserved resources are released or safely restored rather than duplicated or partially consumed.

## **Sanitation & Dishwashing**

When a patron finishes eating or drinking, they leave behind the appropriate Dirty Plate, Dirty Mug, or other Dirty Dish. The associated table remains dirty until the dish is collected and any remaining mess is cleaned. Messes can appear as small rings or spill decals on tables or floors. Messy Patrons have an increased chance to create additional messes or leave dishes on the ground.

Dirty Dishes must be taken to a Dishwashing Station in the Kitchen. The station uses the Hytale Alchemy\_Caldron asset. A player holding a Dirty Dish interacts with the station to fill a washing meter; at 100%, the dirty item is replaced by its clean version. If the player's inventory has no available space, the clean item drops to the ground. Clean Plates, Mugs, or other required serving items can be stored on shelves or in Kitchen storage and are required when crafting eligible food and drink orders; after patron use, they return to the sanitation loop as Dirty Dishes.

## **Workstation Durability**

Functional workstations, including cooking stations, Dishwashing Stations, and brewing equipment, have durability that decreases through use or damage. Damaged workstations remain usable but are clearly flagged; Broken workstations cannot be used until repaired. Durability and status are displayed on the Tavern Management landing screen. Repairs consume appropriate resources or currency; exact costs are TBD.

## **Recipes**

Recipes are part of the wider Hytale world rather than being exclusive to Tavern ownership. Once a recipe has been acquired, any player who has the required ingredients and crafting access can produce it. New food and drink recipes can be distributed through altered monster and chest loot tables, while Rare and Unique Patrons can provide exclusive recipes as highly sought-after rewards.

For Tavern-related furniture and Comfort progression, the preferred unlock model is discovery-driven rather than exposing an entire workbench catalog immediately. Discovering a new material, cultural set, or other progression trigger can reveal its associated recipes. A Discovery-tier asset does not become a lower-tier Common asset merely because its recipe has been unlocked; acquisition history and progression identity determine its Comfort tier.

Tavern progression provides a major quality advantage when producing these recipes. A recipe crafted without Tavern progression may provide a relatively short effect, such as approximately 5 minutes, while the same recipe produced through a highly developed Tavern can reach substantially longer durations such as 15–20 minutes. Exact scaling is balanced through Tavern Passives. Only acquired recipes can be added to the active Menu. Each recipe has an intrinsic Base Value used to determine Base Order XP. The Tavern Host may set the sale price independently; changing the sale price affects revenue but does not increase the recipe's Base XP.

## **Menus**

The Tavern Host builds an active Menu from acquired recipes. Menu Variety has a progression-scaled recommended range. Too few active items apply a Limited Selection XP penalty; too many apply a Choice Paralysis XP penalty. Exact menu-size bands are TBD.

The Menu UI displays each recipe's current XP Efficiency/Recipe Freshness and the Tavern's overall Menu Variety modifier so the Host can rotate items intentionally.

Menu Sets are specific food-and-drink or multi-food combinations that pair well together, such as Steak \+ Red Wine or Alfredo \+ White Wine. The complete Set must be present on the active Menu. When a patron orders the required paired items in the same visit, the completed Set grants approximately 25–50% bonus Tavern XP to those items. Set recipes still obey Recipe Freshness, count toward Menu Variety, and may belong to multiple Sets. Some pairings can remain undiscovered until the relevant recipes are acquired.

## **Patron Orders**

A patron finds appropriate seating, sits, and chooses from the active Menu. Normal patrons order one item; Patron Traits can increase the order to two or three items. Multi-order patrons prioritize compatible active Menu Sets when possible.

Each fulfilled item awards Tavern XP using: Order XP \= Recipe Base XP × Recipe Freshness × Menu Variety × Menu Set Bonus. Multi-item orders calculate each item separately and then sum the results. Recipe Base XP is derived from the recipe's intrinsic Base Value, not the owner-set sale price. NPC and player orders use the same XP rules. High-volume purchases therefore accelerate Recipe Freshness decay instead of providing an unrestricted power-leveling method.

## **Service State Machines**

Patrons, Orders, and Staff use separate runtime state machines so failure in one system does not corrupt the others. The standard Patron lifecycle is Spawn → Walk to Tavern → Find Seat → Sit → Order → Wait → Eat / Drink → Pay → Leave → Despawn. Patron failure branches include No Seat, Path Failed, Order Timed Out, Tavern Closed, and Killed.

Orders use their own lifecycle: Created → Awaiting Preparation → Preparing → Ready → Awaiting Delivery → Delivered → Consumed → Completed. An Order may instead become Failed or Cancelled. The Patron references the Order but does not own the entire preparation and delivery workflow.

NPC Staff use a separate task lifecycle: Off Duty → Idle → Claim Task → Travel to Task → Perform Task → Complete Task → Idle. A staff member can release or fail a task if its target becomes invalid, allowing another worker or the player to handle it without corrupting the Patron or Order state.

## **Service Times**

Each order enters a service queue with a tolerance timer. The Host, player staff, or NPC staff prepares and delivers the order. Longer waits reduce Patron Satisfaction and Service Readiness; Patient and Impatient Traits modify tolerance. After consumption, payment, Tavern XP, and Reputation effects are resolved and the patron leaves. The table and associated seat remain unavailable until the Dirty Dish is collected and any remaining mess is cleaned.

## **Diminishing Returns**

![][image1]

Recipe Freshness tracks Tavern-wide customer demand rather than individual patron memory. Repeatedly serving the same recipe reduces the Tavern XP efficiency of that recipe; removing it from the Menu does not reset Freshness.

The standard seven-day serving curve is Day 1: 100%, Day 2: 95%, Day 3: 80%, Day 4: 60%, Day 5: 35%, Day 6: 15%, Day 7: 5%. Continued over-serving beyond one week can drive an order toward a minimum of 1 Tavern XP.

Recovery mirrors the curve while the recipe remains off the Menu: 5%, 15%, 35%, 60%, 80%, 95%, then 100% over seven days. Order frequency also contributes to fatigue, so unusually high purchase volume accelerates the decline. Currency revenue is not reduced by Recipe Freshness.

# **4\. Patrons, Comfort & Reputation**

## **Patron Types / Quality**

Different types and qualities of patrons can visit the Tavern. Higher-quality patrons have greater expectations but provide better currency and Tavern XP rewards. While the Tavern is Open for Service, Rare and Unique Patron arrival checks occur independently each in-game day. Rare Patrons have a higher arrival chance than Unique Patrons. Available Bar seating and Reserved Cores limit simultaneous occupancy but do not guarantee arrivals.

* Common Patron: Basic patron that uses general Tavern seating and supports early Tavern progression.  
* Rare Patron: Higher-value patron that requires available seating within a functional Bar Core and offers better rewards than Common Patrons.

* Unique Patron: Top-rarity patron that requires an available Reserved Core, has the lowest arrival chance and highest expectations, and offers exceptional reward potential.

## **Patron Spawning**

Patrons use a Tavern-specific invisible spawn point generated outside the Main Entrance. The system searches roughly 45–50 blocks outward from the entrance, using the door's orientation to prioritize terrain in front of the Tavern. Spawn candidates must be on a topmost accessible walkable surface and reject underground, underwater, tree-top, rooftop, cliff, excessively steep, obstructed, or protected locations. The selected point must provide a valid walkable path back to the Main Entrance. No new patrons spawn while the Tavern is Closed. Departing patrons path back toward the spawn area and despawn once they have cleared the Tavern.

## **Patron Traits**

Patrons can have one Patron-specific Trait that modifies ordering, service expectations, payment, or Tavern impact.

## 

| Category | Trait | Description |
| :---- | :---- | :---- |
| Good | Big Spender | Orders 2 items and prioritizes compatible Menu Set pairings when available. |
| Good | Whale | Orders 3 items and can complete larger Menu Sets in one visit. |
| Good | Generous | Leaves a 25% tip after a successfully completed order. |
| Good | Patient | Has 50% more Order Tolerance before Satisfaction begins falling. |
| Good | Adventurous | Prefers recipes with high Recipe Freshness, increasing demand for under-served items. |
| Bad | Dine and Dash | Has a chance to leave after eating without paying. Successful service still awards Tavern XP. |
| Bad | Impatient | Has 50% less Order Tolerance. |
| Bad | Messy | Creates additional Sanitation mess after eating or drinking. |
| Bad | Picky | Chooses from a narrower portion of the active Menu and may refuse some recipes. |
| Bad | Rowdy | Can create disturbances, additional mess, or property damage. |
| Mixed | Foodie | Prefers high-value recipes and Menu Sets but has higher service expectations. |
| Mixed | Glutton | Orders 2–3 food items, increasing revenue and XP opportunity while consuming more ingredients. |
| Mixed | Regular | Visits more frequently, increasing repeated-order pressure on Recipe Freshness and making Menu rotation more important. |
| Mixed | Critic | A successful visit grants bonus Reputation; a poor visit causes greater Reputation loss. |
| Mixed | High Roller | Prefers expensive Menu items but has higher Comfort and service expectations. |

## **Special Patron Requests**

Rare or Unique Patrons can announce a request before they arrive. These requests may require a specific recipe or rare ingredient from a distant biome and can reward large amounts of currency, Tavern XP, Reputation, rare items, or exclusive recipes. Rare Patrons require available Bar seating, while Unique Patrons require an available Reserved Core. The patron attempts to arrive at the scheduled time. The Tavern must be Open for Service and have the required patron seating available; otherwise the request cannot be fulfilled.

In multiplayer, the Host can use these requests as server-wide opportunities by paying other players to obtain difficult ingredients or trading the Patron reward in exchange for help.

## **Comfort System**

Comfort is calculated from explicitly approved Comfort assets placed inside the Tavern Core. Eligibility should use Hytale's own furniture and decoration classifications where possible rather than broad placeable-asset heuristics. Functional benches and workbenches, crafting or processing stations, transport objects, and other utility assets do not provide Comfort unless explicitly approved.

### Comfort Categories

The current Comfort categories are:

* Containers  
* Wardrobes  
* Tables  
* Seating  
* Doors  
* Windows  
* Lighting  
* Beds  
* Shelves  
* Signs / Banners  
* Deco

Hytale's Furniture grouping is split into Wardrobes, Tables, and Seating for Comfort. Hytale's Doors grouping is split into separate Doors and Windows categories. Deco covers approved decorative assets. Building Structures is retained only as an organizational umbrella for Doors and Windows. Doors and Windows score as separate Comfort categories; walls, floors, roofs, raw blocks, stairs, trim, and similar construction pieces do not contribute Comfort.

### Category Thresholds

Each Comfort category uses a quantity requirement. Singular categories such as Beds and Containers use a Required Count of 1\. Quantity-driven categories can scale with Eligible Floor Area so larger Taverns require proportionally more furnishing without being penalized for taller ceilings. For a density-based category c: Required Count\_c \= max(Minimum\_c, ceil(Eligible Floor Area / Density\_c)). Density\_c and Minimum\_c are per-category balance values and remain TBD; a maximum cap may also be added if testing shows it is needed. Expanding the Tavern can therefore increase a threshold, but only when the expansion creates additional eligible floor area rather than empty Core volume.

Only valid, approved assets count toward a category's threshold. Each category also defines a Count Mode. Deco uses Distinct Asset Types: repeated copies of the same Deco asset count only once toward the Deco requirement, preventing one vase, painting, or other decorative model from being spammed to satisfy the category. Other categories may use placed instances where repetition is appropriate, such as Seating; Count Mode is assigned per category.

For any category with Required Count N, collect the qualifying Comfort values according to that category's Count Mode, sort them from highest to lowest, and set Category Comfort to the Nth-highest value. If fewer than N qualifying entries exist, Category Comfort \= 0\. A singular category with N \= 1 therefore uses the highest qualifying Comfort value, preserving the original highest-item rule. In a threshold category, one high-tier object surrounded by low-tier filler cannot grant the full high-tier value because all N qualifying entries must meet at least the resulting Category Comfort.

Tavern Management should eventually expose this clearly in the UI. The Comfort view should show Eligible Floor Area, each category's Current Count / Required Count, its Count Mode, whether the threshold is satisfied, and the current Category Comfort. Deco should specifically show progress in distinct eligible Deco asset types so the Tavern Host can see that duplicate placements do not advance the requirement. Exact UI placement is TBD.

### Regional Comfort Progression

Comfort values are capped at 8 in the current design and follow Adventure progression:

* Emerald Wilds: 1 Common / 2 Discovery  
* Howling Sands: 3 Common / 4 Discovery  
* Whisperfrost Frontiers: 5 Common / 6 Discovery  
* Devastated Lands: 7 Common / 8 Discovery

Common and Discovery describe progression tier, not simply whether an item currently has a crafting recipe. Common assets are ordinary, readily available regional furniture or decoration sets. Discovery assets are distinct cultural, environmental, or special sets associated with exploration and can retain the higher regional value even if they later become craftable. For example, ordinary Emerald Wilds furniture made from common local wood families is Comfort 1, while Kweebec furniture remains Comfort 2 even when it can be crafted at the Furniture Workbench.

Asset-level acquisition, region, category, and approval decisions belong in the Comfort Candidates workbook. This Design Doc defines the scoring rules rather than serving as an asset registry.

### Total Comfort & Relaxed Duration

Total Comfort \= Σ Category Comfort\_c across all Comfort categories. A category contributes 0 until its Required Count is satisfied; once satisfied, its contribution is determined by the Nth-highest rule above. With 11 current categories and a maximum value of 8 per category, the theoretical maximum remains 88 Total Comfort once all category requirements are satisfied.

Relaxed duration uses a diminishing-return curve so early Comfort improvements are meaningful while high-end Taverns do not produce excessively long buffs that remove the reason to return. Base Relaxed Duration \= round(2.46 × √Total Comfort). A Tavern with 0 Comfort provides no Relaxed benefit. At the current theoretical maximum of 88 Total Comfort, base Relaxed lasts 23 minutes.

## **Relaxed / Rested Benefits**

Players who spend enough time seated inside the Tavern become Relaxed for the calculated base Relaxed duration. Purchasing food and drinks can increase how quickly the Relaxed meter fills, with higher-quality items providing stronger multipliers. Skill Tree Passives can add survival-focused bonuses such as improved Health, Stamina, or Magic regeneration, and later duration modifiers can increase Relaxed duration by 25% to 50% after the base calculation.

Renting a Bedroom for the night and sleeping upgrades Relaxed to Rested, increasing duration by 25%. Leasing a Bedroom upgrades Rested to Well Rested, increasing duration by 50% relative to Relaxed. These bonuses and any Skill Tree duration modifiers apply after the base Relaxed calculation. Relaxed, Rested, and Well Rested durations have no hard cap.

## **Reputation**

Reputation is the Tavern's single long-term quality score. It changes gradually based on recent performance rather than swinging sharply from one poor shift. Higher Reputation attracts better patrons and improves NPC applicant quality and employee retention. Its contributing factors are:

* Patron Satisfaction: Successful orders, failed orders, walkouts, complaints, and Rare or Unique Patron experiences.

* Service Readiness: How quickly orders progress from placement through preparation and delivery.

* Sanitation: Dirty tables, dishes, spills, and how long messes remain unattended.

* Tavern Health: Physical condition of registered structures, functional furniture, and workstations.

* Hospitality: Comfort, successful room stays or leases, and the ability to accommodate patrons within the Tavern's declared service capacity.

* Staff Stability: Frequent firing, employee walkouts, and unpaid wages. Off Duty scheduling does not count against Reputation.

## **Guest Rooms / Tenants**

Bedroom Cores allow the Tavern Host to rent rooms nightly or lease them to NPCs or real players. The Host sets the rental or lease price. Long-term tenants receive private storage, room access, recall options, and stronger resting benefits.

## **Player Seating / AFK Handling**

Human players are allowed to occupy Tavern seating normally, including groups who want to sit and roleplay together. Players who remain inactive for too long can display an idle warning so Tavern staff can identify seating that may be blocked by an AFK player.

# **5\. Staff & Automation**

## **Player Staff**

The Tavern Host can hire other players through Tavern Management once employment is unlocked. Player staff receive per-shift wages and use their own profession Skill Trees for cooking, serving, and other work. Tavern-wide staff Passives still apply while they are employed. Player profession Passives should provide greater potential than NPC equivalents.

## **NPC Staff**

NPC professions include Chef, Server, Dishwasher, and Bartender. Bartenders require a functional Bar Core and Drink Workbenches. Servers bus tables, collect finished orders from the Service Line, deliver them to patrons, and clean table messes, so there is no separate Cleaner profession for now. Dishwashers operate Dishwashing Stations and return clean serving items to Kitchen storage. NPCs interact with appropriate workbenches and Core volumes to complete assigned work and cannot be manually reassigned to a different profession. NPC applicants use a human player-avatar-style model with persistent identity features such as body, face, skin tone, eyes, hair, and facial hair.

## **Dress Code**

The Staff page includes a Dress Code menu for NPC employees. The Host can standardize Shirt, Pants, and Shoes using compatible avatar cosmetics. Dress Code can apply one Tavern-wide uniform or be customized by profession for Chefs, Servers, Dishwashers, and Bartenders. Employee identity features such as body, face, skin tone, eyes, hair, and facial hair remain unchanged; only employer-controlled uniform slots are replaced while the NPC is employed.

## **Job Board**

The Job Board unlocks through Tavern Skill Tree progression rather than at a fixed late-game level; exact unlock placement is TBD. Its crafting requirements must align with its intended progression tier and are also TBD. Crafting and placing it enables the Staff and Hiring pages in Tavern Management rather than opening a separate management interface. Staff lists current employees, wages, roles, Traits, and scheduling status. Hiring shows applicants and clearly identifies NPCs and players.

NPC applicants refresh once per in-game week and are randomly generated with a fantasy name, profession, Rank I–V, one Trait, and an asking wage. Reputation affects applicant quality and employee retention. Tavern Level does not directly affect applicant quality.

When employment is unlocked, the Tavern begins with one employee slot for each profession whose prerequisites have been met. The Bartender slot remains unavailable until the Bar profession is unlocked. Skill Tree Passives can unlock additional slots. The Host can Accept, Decline, or Haggle with applicants. NPCs may accept a reasonable lower wage, while an insulting offer can cause them to withdraw their application.

## **Profession Ranks**

NPC professions use Rank I–V. Each rank improves Efficiency by 5% over the previous rank. Efficiency governs Crafting Speed and Movement Speed.

## **Employee Traits**

Each NPC employee has one immutable Trait generated with the weekly applicant pool. Traits are positive, negative, or mixed and do not change after hiring.

## 

| Category | Trait | Description |
| :---- | :---- | :---- |
| Good | Talented | \+15% Efficiency, improving both Crafting Speed and Movement Speed. |
| Good | Diligent | Reduces the delay before selecting and beginning the next available task by 25%. |
| Good | Swift | \+20% Movement Speed. |
| Good | Intelligent | \+20% Crafting Speed. |
| Good | Loyal | Less likely to quit because of poor Reputation. Unpaid wages are unaffected. |
| Bad | Lazy | \-25% Efficiency, reducing both Crafting Speed and Movement Speed. |
| Bad | Daydreamer | Periodically stops working and idles before resuming available tasks. |
| Bad | Slowpoke | \-20% Movement Speed. |
| Bad | Inefficient | \-20% Crafting Speed. |
| Bad | Greedy | Asks approximately 25% higher wages than an equivalent employee of the same profession and Rank. |
| Mixed | Quirky | Individual tasks can receive either a temporary \+25% or \-25% Crafting Speed modifier. |
| Mixed | Workaholic | \+20% Efficiency, but asks approximately 25% higher wages. |
| Mixed | Bargain Hire | \-15% Efficiency, but asks approximately 25% lower wages. |
| Mixed | Sprinter | \+30% Movement Speed but \-15% Crafting Speed. |
| Mixed | Artisan | \+25% Crafting Speed but \-15% Movement Speed. |

## **Wages & Shifts**

Staff are paid only for shifts they work. A shift begins when the Tavern is set to Open for Service and the employee is marked Working, and ends when the employee is sent home or the Tavern closes. Off Duty employees are not paid. NPC asking wages are generated during recruitment; the final wage formula is still to be determined. Staff may walk out if wages owed for a completed shift cannot be paid.

## **Working Behavior & Automation**

Staff only work while the Tavern is Open for Service and they are marked Working. While idle, they remain within the area associated with their profession: Servers in the dining area, Chefs and Dishwashers in the Kitchen, and Bartenders behind the Bar. Staff do not currently require breaks or a separate happiness simulation. Poor Reputation can still cause employees to quit. Staffing develops in stages: manual early game, limited help midgame, serious staffing late game, and high automation at endgame. Skill Tree Passives increase employee capacity and automation.

# **6\. Economy & Multiplayer**

## **Copper / Silver / Gold**

Copper, Silver, and Gold form a universal currency used by Tavern patrons, players, staff, and future business systems. Currency can enter the world through activities such as monster drops, exploration, and NPC patron payments, while successful businesses provide a more lucrative way to earn it over time.

## **Physical Currency Displays**

Existing Hytale treasure-pile props, including Deco\_Treasure\_Pile\_Small and related variants, can be reused as physical representations of accumulated wealth. Gold can use the base treasure-pile appearance, while Copper and Silver use texture/material variants. These assets can support Tavern displays, rewards, loot, and future treasury systems.

## **Tavern Revenue & Expenses**

Tavern revenue comes from patron orders, player purchases, room rentals and leases, and other services. Expenses include ingredients, wages, Core expansion, repairs, recipes, automation, and other business investments.

## **Player Spending & Staff Pay**

Players should have meaningful reasons to spend currency at a Tavern, while Tavern employees should be paid in the same universal currency so working a shift provides value outside the Tavern itself.

## **Multiplayer Incentives**

Players on multiplayer servers can visit for Relaxed benefits, food, drinks, lodging, Rare and Unique Patron opportunities, and employment. Adventurers can also sell or trade difficult-to-obtain ingredients and resources to the Tavern Host.

## **Tavern Ownership**

In the current scope, each player can own only one Tavern at a time. A player who already owns a Tavern cannot establish or receive ownership of another until the existing Tavern is relinquished or transferred. Multi-Tavern ownership can be reconsidered by future systems such as Kingdoms.

## **Permissions & Management Authority**

The Tavern Owner is the root authority for the establishment. By default, only the Owner can resize or place Cores, designate the Main Entrance, alter the Menu and prices, change operating hours, Open or Close the Tavern, hire or fire NPC employees, set wages, manage Dress Codes and Bedrooms, spend Tavern funds, receive Core refunds, or transfer ownership.

Player Employees receive only the permissions explicitly granted to them. Initial employee permissions can include accessing Kitchen storage, preparing orders, serving orders, cleaning, and using profession-appropriate workstations. Broader Manager-style permission bundles may be added later rather than being required for the first release.

Server and world protection always take precedence over Tavern permissions. Tavern ownership never grants the right to modify blocks, containers, or protected regions that the player could not otherwise modify. The authority hierarchy is Server Protection \> Tavern Protection \> Owner \> Granted Employees \> Other Players.

## **Ownership Transfer / OP Controls**

The Tavern should exist as its own persistent establishment rather than storing progression directly on the owner. Server OPs must be able to transfer ownership through an administrative UI if the current Tavern Host can no longer play. A transfer cannot complete if the recipient already owns a Tavern under the current one-Tavern-per-player rule. Tavern Level, staff, rooms, Reputation, Core data, and other persistent progress remain with the establishment.

# **7\. Tavern Persistence & Future Systems**

## **Persistence Model**

Each Tavern is stored as a persistent establishment with a unique Tavern ID independent of its current owner. Persistent business data includes the owner UUID, Tavern Level, Tavern XP, Reputation, Skill Points and unlocks, Tavern currency balance, operating schedule, Core volumes, Main Entrance, registered furniture and workstations, active Menu and prices, Kitchen storage references, employee records, Bedroom rentals and leases, Dress Codes, and structural snapshot data when that system is enabled.

Tavern data is separated into three layers. Persistent Business Data contains progression, ownership, employees, economy, Menu, rooms, Cores, and other establishment state. Persistent World References identify physical objects such as the Main Entrance, chairs, tables, workstations, and storage by stable world references and positions. Runtime Simulation contains active patrons, active orders, pathfinding requests, seat reservations, and current employee tasks.

Runtime Simulation is not treated as durable business state. After a server restart, crash, or forced simulation shutdown, active patrons and orders are discarded and the Tavern reloads Closed. Healthy pending orders removed by this recovery do not cause Reputation loss. Persistent employees retain their employment records but do not resume an interrupted runtime task.

## **Offline & Chunk-Unloaded Behavior**

In the initial scope, Tavern service only runs while the Tavern's required area is loaded and at least one authorized operator is online. An authorized operator is the Tavern Host or a Player Employee who has been granted permission to operate the Tavern. If the required area unloads or no authorized operator remains online, the Tavern gracefully closes: no new patrons spawn, healthy pending orders are cancelled without Reputation loss, runtime patrons are removed after cleanup, NPC employee simulation stops, and active shifts end. The initial release does not simulate patron traffic, production, wages, or Reputation changes while the Tavern is offline or unloaded. Future progression or Kingdom systems may explicitly add offline operation later.

## **Tavern Health**

Tavern Health summarizes the physical condition of registered structures, functional furniture, and workstations. Each registered object contributes its current durability. Broken workstations lower Tavern Health and cannot function until repaired, but they do not by themselves place the Tavern in a Destroyed state. At approximately 15% structural health or lower, the Tavern is considered practically destroyed and cannot operate normally.

## **Snapshots**

Whenever the Tavern opens for service, it saves a structural snapshot of its registered layout. The snapshot includes the Tavern structure and registered object placement, but excludes food, drinks, ingredients, currency, and other container contents that could be duplicated through restoration. Routine workstation durability is not reset by opening, closing, or snapshot restoration.

## **Repair / Demolish**

A destroyed Tavern gives the Host the option to Repair or Demolish. Repair compares the current building against the last saved snapshot and charges currency based on the amount of damage. The Host may also rebuild manually. Demolish permanently abandons the establishment and requires an explicit confirmation.

## **Failure & Recovery**

Tavern systems should fail invalid rather than fail destructively. If a registered chair, table, workstation, storage container, or other referenced object disappears, it is marked Missing or Invalid and removed from functional capacity until restored or replaced. The Tavern continues operating when possible with the remaining valid objects.

If the Main Entrance is blocked or otherwise invalid, the Tavern cannot Open for Service until a valid route is restored. During active service, NPCs use bounded repath attempts when a route fails. Patrons then enter the appropriate failure state rather than becoming permanently stuck. Stranded employees first attempt to repath to their profession area; as a final recovery measure, their runtime NPC may be repositioned to a safe valid staff location inside the Tavern.

If a Core becomes invalid because world or server protection changes, the Core is marked Requires Attention and systems dependent on it are disabled. The Core is not automatically deleted and does not automatically refund resources. Missing or invalid saved references should likewise be reported and preserved where possible rather than silently discarded.

Persisted Tavern data is versioned from the first implementation. When the save schema changes, older records are migrated to the current version. If a component cannot be migrated safely, that component should fail closed and report the problem rather than corrupting the Tavern or world.

## **Raids**

A separate future Raids mod may allow hostile groups to attack Taverns or other player structures. Raids should be announced globally before enemies converge on the target so players have time to respond. Tavern Health, snapshots, and repair systems allow destruction to matter without automatically deleting permanent Tavern progression.

## **Griefing / Patron Death**

If another player kills a Tavern patron, the Host should not receive a direct Reputation penalty for the murder. The Tavern still loses that customer, their order, and the rewards they would have provided. Crime, bounty, and jail consequences can be handled by a separate future system.

## **Future Hooks**

* Blacksmith businesses  
* Kingdoms and taxation  
* Auction House systems  
* Crime, bounties, and jail  
* Settlement protection  
* Other player-run businesses

[image1]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAnAAAAFcCAYAAABIlYNzAABPuElEQVR4Xu3dB7gU5dk+8M8S+yf2Xig2jEZREo3GvxFrNIlfYk3UWBPRxNgjRbogoGBFY0URlKIIIgekCUhRinSBQ++9Hco5nPb+eebwzs4+s33nmZ135v5d13udnXln95xzs2f3ZnZn538UAAAAABjlf/gKAAAAAAg2FDgAAAAAw6DAAQAAABgGBQ4AAADAMChwAAAAAIZBgQMAAAAwDAocgAfuvfde9T//Y/afk+k/v6RU2aSag8RatWqF3ADyhL8giBR60uDDC5IF7vTTT3f9zDfffDPfLG9SP7+EAw88UB1yyCFx6xL9e+67777qyCOPjFuXC367Tqnm0uH/rjReffVVvlnopCtwPBMalZWVfDOASEv+FwQQQvxJ45xzzlEPPfRQ3Lqg0QVOmh/fwystWrRw/bz6iX737t1x61555RXHVrnh38sp1Vw6/Lq7du2y1l144YVx68MmkwJH2ziX999//9gGAIACB9HCnzTKysri1u3YscMuAjRuuukmx9ZKDRkyJG7+vvvus9bzPXB0uUePHnHbOk2ePDlu7r///W/cvFOqAkfrTz31VOsr/QykTp06SW/7sMMOi5urVauWPUfL5557rj1H39fpyiuvTDlPpcN5207HHXdc3NyWLVus9fqJPNH16LL+nRKh+R9++MFepiL+l7/8RV177bVx22h33HFH3PcpKSmx54jzZ/zf//3fuDnn7TRu3Nha3rx5s2uO8uWc81yyOVr/wgsv2Mv08zh/9gkTJthz69evj5tz3iZf/8UXX8TNcYnWafy2Vq1aZc91797dGrRnlP8MZOfOnXHXzbbANWnSxLW98/b4v9df//pX18+r7bfffnHrN23aZK3/8ccfXd/jp59+itvbzf+2nGh53rx5cXN8m44dO7rWAeQK9ySIFP7geeutt1rFRKP5Z599Nm757bffti4vWrTIWq6urrbnv/32W+trogLnfFI56aST1Oeff25dpuJA86NHj7bnaXnDhg32slO6Ate3b197mW77iCOOsJcXLFjg+rmcxo4da1+muUGDBlmXN27c6No21fxFF11kPXlra9eutV6+JP369XPdFhVnQuvHjx9vry8tLbUv01y6AnfjjTdalwcOHKiWLVumZs+enfD3feKJJ6zLFRUV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>