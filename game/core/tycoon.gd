class_name Tycoon
extends RefCounted
## La couche « tycoon » greffée sur le village builder.
##
## Ce que les tycoons Roblox font mieux que les builders mobiles, et qu'on
## reprend ici :
##  * la production s'ACCUMULE visiblement au-dessus des bâtiments et se
##    ramasse au doigt — le joueur agit au lieu de regarder une barre monter ;
##  * une boutique d'améliorations à coût exponentiel, achetables en boucle,
##    qui donne un palier franchi toutes les quelques dizaines de secondes ;
##  * un rebirth qui remet le village à zéro contre un multiplicateur définitif.
##
## Ce que l'on refuse de reprendre : les murs payants et les pubs forcées.

# Réserve accumulée au-dessus d'un bâtiment. Assez généreuse pour qu'une nuit
# d'absence donne une vraie moisson au retour, assez limitée pour que le joueur
# actif ait toujours intérêt à repasser ramasser.
const PENDING_MINUTES := 120.0
const PRESTIGE_MIN_BASTION := 10
const PRESTIGE_DIVISOR := 50000.0
const PRESTIGE_BONUS_PER_POINT := 0.10

const UPGRADES := {
	"yield": {
		"name": "Rendement",
		"desc": "+12 % de production sur tout le village",
		"base_cost": 250, "growth": 1550, "max": 60,
	},
	"haste": {
		"name": "Cadence",
		"desc": "Les ressources s'accumulent plus vite entre deux récoltes",
		"base_cost": 400, "growth": 1600, "max": 40,
	},
	"auto": {
		"name": "Récolte automatique",
		"desc": "Ramasse tout seul, de plus en plus souvent",
		"base_cost": 4000, "growth": 2200, "max": 10,
	},
	"loot": {
		"name": "Pillage",
		"desc": "+6 % de butin sur tous les raids",
		"base_cost": 800, "growth": 1700, "max": 50,
	},
	"capacity": {
		"name": "Entrepôts renforcés",
		"desc": "+10 % de capacité de stockage",
		"base_cost": 600, "growth": 1650, "max": 50,
	},
	"logistics": {
		"name": "Logistique",
		"desc": "+2 places d'armée",
		"base_cost": 1200, "growth": 1750, "max": 40,
	},
}

const UPGRADE_ORDER := ["yield", "haste", "auto", "loot", "capacity", "logistics"]


# ------------------------------------------------------------------- niveaux

static func level_of(state: PlayerState, id: String) -> int:
	return int(state.upgrades.get(id, 0))


static func is_maxed(state: PlayerState, id: String) -> bool:
	var d: Dictionary = UPGRADES.get(id, {})
	if d.is_empty():
		return true
	return level_of(state, id) >= int(d["max"])


static func cost_of(state: PlayerState, id: String) -> int:
	## Coût exponentiel : c'est lui qui crée la boucle « encore un niveau ».
	var d: Dictionary = UPGRADES.get(id, {})
	if d.is_empty():
		return 0
	return DataTables.grow(int(d["base_cost"]), int(d["growth"]), level_of(state, id) + 1)


static func can_buy(state: PlayerState, id: String) -> bool:
	if is_maxed(state, id):
		return false
	return int(state.resources.get("gold", 0)) >= cost_of(state, id)


static func buy(state: PlayerState, id: String) -> bool:
	if not can_buy(state, id):
		return false
	var cost := cost_of(state, id)
	state.resources["gold"] = int(state.resources.get("gold", 0)) - cost
	state.upgrades[id] = level_of(state, id) + 1
	return true


# ------------------------------------------------------------ multiplicateurs

static func prestige_multiplier(state: PlayerState) -> float:
	return 1.0 + PRESTIGE_BONUS_PER_POINT * float(state.prestige_points)


static func production_multiplier(state: PlayerState) -> float:
	return (1.0 + 0.12 * float(level_of(state, "yield"))) * prestige_multiplier(state)


static func loot_multiplier(state: PlayerState) -> float:
	return (1.0 + 0.06 * float(level_of(state, "loot"))) * prestige_multiplier(state)


static func storage_multiplier(state: PlayerState) -> float:
	return 1.0 + 0.10 * float(level_of(state, "capacity"))


static func army_bonus(state: PlayerState) -> int:
	return 2 * level_of(state, "logistics")


static func pending_capacity_minutes(state: PlayerState) -> float:
	## « Cadence » augmente ce que peut contenir un bâtiment entre deux
	## récoltes : le joueur qui revient après une pause ramasse davantage.
	return PENDING_MINUTES * (1.0 + 0.12 * float(level_of(state, "haste")))


static func auto_collect_interval(state: PlayerState) -> float:
	## 0 = pas d'auto-récolte. Sinon, intervalle en secondes.
	var lvl := level_of(state, "auto")
	if lvl <= 0:
		return 0.0
	return maxf(1.5, 14.0 - float(lvl) * 1.25)


# ---------------------------------------------------------------- rebirth

static func prestige_points_available(state: PlayerState) -> int:
	## Racine carrée du total gagné : les premiers points arrivent vite, les
	## suivants demandent une vraie partie. C'est ce qui rend le rebirth
	## désirable sans le rendre obligatoire.
	if state.bastion_level() < PRESTIGE_MIN_BASTION:
		return 0
	var earned := float(state.stats.get("total_earned", 0))
	var pts := int(floor(sqrt(maxf(0.0, earned) / PRESTIGE_DIVISOR)))
	return maxi(0, pts - state.prestige_points)


static func can_prestige(state: PlayerState) -> bool:
	return prestige_points_available(state) > 0


static func prestige_blockers(state: PlayerState) -> Array[String]:
	var out: Array[String] = []
	if state.bastion_level() < PRESTIGE_MIN_BASTION:
		out.append("Bastion %d requis (tu es au %d)" % [PRESTIGE_MIN_BASTION, state.bastion_level()])
	elif prestige_points_available(state) <= 0:
		var earned := float(state.stats.get("total_earned", 0))
		var next_at := float((state.prestige_points + 1) * (state.prestige_points + 1)) * PRESTIGE_DIVISOR
		out.append("Encore %s de ressources à gagner" % Game.format_number(int(next_at - earned)))
	return out


static func apply_prestige(state: PlayerState, tables: DataTables) -> int:
	## Remet le village et l'économie à zéro, garde la progression de campagne,
	## et accorde des points définitifs. Retourne le nombre de points gagnés.
	var gained := prestige_points_available(state)
	if gained <= 0:
		return 0
	state.prestige_points += gained
	state.stats["prestiges"] = int(state.stats.get("prestiges", 0)) + 1

	# Ce qui est conservé : campagne, Épreuves, points de prestige, statistiques.
	# Ce qui repart de zéro : village, ressources, armée, améliorations tycoon.
	var fresh := PlayerState.create_new(tables)
	state.village = fresh.village
	state.resources = fresh.resources.duplicate()
	state.army = fresh.army.duplicate()
	state.upgrades = {}
	state.pending = {}
	state.accum = {}
	return gained
