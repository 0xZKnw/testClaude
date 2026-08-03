class_name PlayerState
extends RefCounted
## Tout ce qui appartient au joueur et qui doit survivre à la fermeture du jeu.

const SAVE_VERSION := 1

var version: int = SAVE_VERSION
var village: Village
var resources: Dictionary = {"wood": 300, "stone": 150, "iron": 0, "gold": 200, "essence": 0}
var army: Dictionary = {}                 # unit_id -> nombre en stock
var campaign_stars: Dictionary = {}       # index de niveau (int) -> étoiles (0..3)
var trials_done: Dictionary = {}          # niveau de Bastion (int) -> true
var accum: Dictionary = {}                # accumulation fractionnaire de production
var pending: Dictionary = {}              # uid -> {ressource: quantité en attente de récolte}
var upgrades: Dictionary = {}             # améliorations tycoon : id -> niveau
var quests_claimed: Dictionary = {}       # id de quête -> 1
var prestige_points: int = 0
var stats: Dictionary = {
	"raids_won": 0, "raids_played": 0, "buildings_built": 0,
	"upgrades": 0, "playtime_sec": 0, "loot_total": 0,
	"total_earned": 0, "collected": 0, "prestiges": 0,
}
var last_seen_unix: int = 0


func _init() -> void:
	village = Village.new(44, 44)


static func create_new(tables: DataTables) -> PlayerState:
	var s := PlayerState.new()
	var mid := s.village.width / 2
	s.village.place("bastion", 1, mid - 2, mid - 2, 4)
	# Un village de départ minuscule : le tutoriel fait poser le reste.
	s.village.place("sawmill", 1, mid - 6, mid - 1, 3)
	s.village.place("house", 1, mid + 3, mid, 2)
	s.village.place("warehouse", 1, mid - 1, mid + 3, 3)
	s.army = {"militia": 6}
	_scatter_decor(s, tables)
	s.last_seen_unix = int(Time.get_unix_time_from_system())
	return s


static func _scatter_decor(s: PlayerState, _tables: DataTables) -> void:
	## Quelques arbres pour que la vallée ne soit pas un damier vide.
	var rng := DetRandom.new(4242)
	var placed := 0
	var attempts := 0
	while placed < 60 and attempts < 800:
		attempts += 1
		var x := rng.next_range(1, s.village.width - 1)
		var y := rng.next_range(1, s.village.height - 1)
		if s.village.can_place(x, y, 1):
			s.village.place("tree", 1, x, y, 1)
			placed += 1


func bastion_level() -> int:
	var list := village.all_of("bastion")
	if list.is_empty():
		return 1
	return int(list[0]["level"])


func set_bastion_level(lvl: int) -> void:
	var list := village.all_of("bastion")
	if not list.is_empty():
		list[0]["level"] = lvl


func stars_for(level_index: int) -> int:
	return int(campaign_stars.get(level_index, 0))


func total_stars() -> int:
	var n := 0
	for v: Variant in campaign_stars.values():
		n += int(v)
	return n


func levels_cleared() -> int:
	var n := 0
	for v: Variant in campaign_stars.values():
		if int(v) > 0:
			n += 1
	return n


func pending_of(uid: int) -> Dictionary:
	return pending.get(uid, {})


func pending_total(uid: int) -> int:
	var n := 0
	for v: Variant in pending_of(uid).values():
		n += int(v)
	return n


func has_anything_pending() -> bool:
	for uid: Variant in pending.keys():
		if pending_total(int(uid)) > 0:
			return true
	return false


func army_size(tables: DataTables) -> int:
	var n := 0
	for id: String in army.keys():
		var d: Dictionary = tables.units.get(id, {})
		if not d.is_empty():
			n += int(army[id]) * int(d["housing"])
	return n


# ------------------------------------------------------------ sérialisation

func to_dict() -> Dictionary:
	return {
		"version": version,
		"village": village.to_dict(),
		"resources": resources,
		"army": army,
		"campaign_stars": campaign_stars,
		"trials_done": trials_done,
		"accum": accum,
		"pending": pending,
		"upgrades": upgrades,
		"quests_claimed": quests_claimed,
		"prestige_points": prestige_points,
		"stats": stats,
		"last_seen_unix": last_seen_unix,
	}


static func from_dict(d: Dictionary) -> PlayerState:
	var s := PlayerState.new()
	s.version = int(d.get("version", 1))
	s.village = Village.from_dict(d.get("village", {}))
	s.resources = _int_dict(d.get("resources", {}))
	s.army = _int_dict(d.get("army", {}))
	s.campaign_stars = _int_key_dict(d.get("campaign_stars", {}))
	s.trials_done = _int_key_dict(d.get("trials_done", {}))
	s.accum = d.get("accum", {})
	s.pending = _pending_from_dict(d.get("pending", {}))
	s.upgrades = _int_dict(d.get("upgrades", {}))
	s.quests_claimed = _int_dict(d.get("quests_claimed", {}))
	s.prestige_points = int(d.get("prestige_points", 0))
	var loaded_stats: Dictionary = d.get("stats", {})
	for k: Variant in loaded_stats.keys():
		s.stats[String(k)] = int(loaded_stats[k])
	s.last_seen_unix = int(d.get("last_seen_unix", 0))
	return s


static func _int_dict(src: Dictionary) -> Dictionary:
	## JSON ramène les nombres en float : on les remet en int.
	var out := {}
	for k: Variant in src.keys():
		out[String(k)] = int(src[k])
	return out


static func _pending_from_dict(src: Dictionary) -> Dictionary:
	## JSON transforme les clés entières en chaînes : on les restaure.
	var out := {}
	for k: Variant in src.keys():
		var inner: Dictionary = src[k]
		var conv := {}
		for res: Variant in inner.keys():
			conv[String(res)] = float(inner[res])
		out[int(String(k))] = conv
	return out


static func _int_key_dict(src: Dictionary) -> Dictionary:
	## JSON transforme aussi les clés entières en chaînes.
	var out := {}
	for k: Variant in src.keys():
		out[int(String(k))] = int(src[k]) if typeof(src[k]) != TYPE_BOOL else 1
	return out
