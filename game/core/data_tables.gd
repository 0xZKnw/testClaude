class_name DataTables
extends RefCounted
## Chargement et interrogation des données de jeu (CSV -> tables).
##
## Toutes les courbes de progression sont calculées en **arithmétique entière**
## (multiplication/division répétée), jamais avec pow() en float : les valeurs
## dérivées alimentent la simulation de combat, qui doit rester déterministe.
##
## Rééquilibrer le jeu = éditer les CSV. Aucune recompilation, aucun code.

const RESOURCES := ["wood", "stone", "iron", "gold", "essence"]
const RESOURCE_NAMES := {
	"wood": "Bois", "stone": "Pierre", "iron": "Fer",
	"gold": "Or", "essence": "Essence",
}

# Facteurs de croissance, exprimés en millièmes (voir docs/03 §2).
const GROW_COST := 1340      # 1.34^(n-1)
const GROW_PROD := 1220      # 1.22^(n-1)  — volontairement < GROW_COST
const GROW_STORE := 1400     # 1.40^(n-1)
const GROW_HP := 1180        # 1.18^(n-1)
const GROW_DPS := 1150       # 1.15^(n-1)

# Multiplicateur de coût par catégorie, en centièmes.
const CATEGORY_COST_MULT := {
	"production": 100, "storage": 110, "military": 115,
	"defense": 125, "special": 160, "wall": 100, "deco": 100,
}

enum TargetPref { ANY, DEFENSE, BUILDING, WALL, ALLY }

const TARGET_PREF_FROM_STRING := {
	"any": TargetPref.ANY,
	"defense": TargetPref.DEFENSE,
	"building": TargetPref.BUILDING,
	"wall": TargetPref.WALL,
	"ally": TargetPref.ALLY,
}

var buildings: Dictionary = {}      # id -> Dictionary
var building_order: Array[String] = []
var units: Dictionary = {}          # id -> Dictionary
var unit_order: Array[String] = []

var _loaded := false


func load_all(base_path: String = "res://data") -> bool:
	if _loaded:
		return true
	var ok := _load_buildings(base_path.path_join("buildings.csv"))
	ok = _load_units(base_path.path_join("units.csv")) and ok
	_loaded = ok
	return ok


func is_loaded() -> bool:
	return _loaded


# ---------------------------------------------------------------- chargement

func _read_csv(path: String) -> Array:
	var f := FileAccess.open(path, FileAccess.READ)
	if f == null:
		push_error("Valdris: CSV introuvable: %s" % path)
		return []
	var header := f.get_csv_line()
	var rows: Array = []
	while not f.eof_reached():
		var line := f.get_csv_line()
		if line.size() < 2 or line[0].strip_edges().is_empty():
			continue
		var row := {}
		for i in range(header.size()):
			row[header[i].strip_edges()] = line[i].strip_edges() if i < line.size() else ""
		rows.append(row)
	f.close()
	return rows


func _load_buildings(path: String) -> bool:
	var rows := _read_csv(path)
	if rows.is_empty():
		return false
	for row: Dictionary in rows:
		var store_res: Array[String] = []
		if row["store_res"] != "-" and not String(row["store_res"]).is_empty():
			for r in String(row["store_res"]).split("|"):
				store_res.append(r)
		var d := {
			"id": row["id"],
			"name": row["name"],
			"category": row["category"],
			"size": int(row["size"]),
			"max_level": int(row["max_level"]),
			"base_cost": {
				"wood": int(row["c_wood"]), "stone": int(row["c_stone"]),
				"iron": int(row["c_iron"]), "gold": int(row["c_gold"]),
				"essence": 0,
			},
			"prod_res": "" if row["prod_res"] == "-" else row["prod_res"],
			"base_prod": int(row["prod_rate"]),
			"store_res": store_res,
			"base_store": int(row["store_amount"]),
			"base_hp": int(row["hp"]),
			"base_dps": int(row["dps"]),
			"range": float(row["range"]),
			"unlock": int(row["unlock"]),
			"shape": row["shape"],
			"color": row["color"],
			"count_base": int(row["count_base"]),
			"count_growth_x10": int(row["count_growth_x10"]),
			"count_cap": int(row["count_cap"]),
		}
		d["is_defense"] = d["base_dps"] > 0
		d["is_wall"] = d["category"] == "wall"
		buildings[d["id"]] = d
		building_order.append(d["id"])
	return true


func _load_units(path: String) -> bool:
	var rows := _read_csv(path)
	if rows.is_empty():
		return false
	for row: Dictionary in rows:
		var d := {
			"id": row["id"],
			"name": row["name"],
			"housing": int(row["housing"]),
			"cost": {"gold": int(row["c_gold"]), "iron": int(row["c_iron"])},
			"hp": int(row["hp"]),
			"dps": int(row["dps"]),
			"speed": float(row["speed"]),
			"range": float(row["range"]),
			"target_pref": TARGET_PREF_FROM_STRING.get(row["target_pref"], TargetPref.ANY),
			"unlock": int(row["unlock"]),
			"shape": row["shape"],
			"color": row["color"],
		}
		d["is_healer"] = d["dps"] < 0
		units[d["id"]] = d
		unit_order.append(d["id"])
	return true


# ------------------------------------------------------------------ courbes

static func grow(base: int, mult_x1000: int, level: int) -> int:
	## base * (mult/1000)^(level-1), en entiers uniquement.
	if base == 0:
		return 0
	var v := base
	for _i in range(max(0, level - 1)):
		v = (v * mult_x1000) / 1000
	return v


func building_cost(id: String, level: int) -> Dictionary:
	## Coût pour AMÉLIORER vers `level` (level 1 = coût de construction).
	var d: Dictionary = buildings.get(id, {})
	if d.is_empty():
		return {}
	var mult: int = CATEGORY_COST_MULT.get(d["category"], 100)
	var out := {}
	for res: String in RESOURCES:
		var base: int = d["base_cost"].get(res, 0)
		if base == 0:
			continue
		out[res] = (grow(base, GROW_COST, level) * mult) / 100
	return out


func building_production(id: String, level: int) -> int:
	var d: Dictionary = buildings.get(id, {})
	if d.is_empty():
		return 0
	return grow(d["base_prod"], GROW_PROD, level)


func building_storage(id: String, level: int) -> int:
	var d: Dictionary = buildings.get(id, {})
	if d.is_empty():
		return 0
	return grow(d["base_store"], GROW_STORE, level)


func building_hp(id: String, level: int) -> int:
	var d: Dictionary = buildings.get(id, {})
	if d.is_empty():
		return 0
	return grow(d["base_hp"], GROW_HP, level)


func building_dps(id: String, level: int) -> int:
	var d: Dictionary = buildings.get(id, {})
	if d.is_empty():
		return 0
	return grow(d["base_dps"], GROW_DPS, level)


func building_max_count(id: String, bastion_level: int) -> int:
	var d: Dictionary = buildings.get(id, {})
	if d.is_empty():
		return 0
	if bastion_level < int(d["unlock"]):
		return 0
	var n: int = int(d["count_base"]) + ((bastion_level - 1) * int(d["count_growth_x10"])) / 10
	return min(n, int(d["count_cap"]))


func unlocked_buildings(bastion_level: int) -> Array[String]:
	var out: Array[String] = []
	for id: String in building_order:
		if id == "bastion":
			continue
		if int(buildings[id]["unlock"]) <= bastion_level:
			out.append(id)
	return out


func unlocked_units(bastion_level: int) -> Array[String]:
	var out: Array[String] = []
	for id: String in unit_order:
		if int(units[id]["unlock"]) <= bastion_level:
			out.append(id)
	return out


func building_name(id: String) -> String:
	return String(buildings.get(id, {}).get("name", id))


func unit_name(id: String) -> String:
	return String(units.get(id, {}).get("name", id))
