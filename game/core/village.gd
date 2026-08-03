class_name Village
extends RefCounted
## L'état d'un village : une grille d'occupation + une liste de bâtiments.
##
## Purement logique, aucune dépendance à la scène 3D. C'est cette structure qui
## est sérialisée dans la sauvegarde, publiée comme snapshot PvP et donnée en
## entrée à la simulation de combat.

var width: int = 44
var height: int = 44
var buildings: Array[Dictionary] = []   # {uid, type, level, x, y, size}
var _cells: Dictionary = {}             # cell_key -> uid
var _by_uid: Dictionary = {}            # uid -> index dans `buildings`
var _next_uid: int = 1


func _init(w: int = 44, h: int = 44) -> void:
	width = w
	height = h


static func cell_key(x: int, y: int) -> int:
	return y * 4096 + x


func in_bounds(x: int, y: int, size: int) -> bool:
	return x >= 0 and y >= 0 and x + size <= width and y + size <= height


func is_area_free(x: int, y: int, size: int, ignore_uid: int = -1) -> bool:
	for dy in range(size):
		for dx in range(size):
			var k := cell_key(x + dx, y + dy)
			if _cells.has(k) and _cells[k] != ignore_uid:
				return false
	return true


func can_place(x: int, y: int, size: int, ignore_uid: int = -1) -> bool:
	return in_bounds(x, y, size) and is_area_free(x, y, size, ignore_uid)


func place(type_id: String, level: int, x: int, y: int, size: int) -> int:
	## Retourne l'uid du bâtiment créé, ou -1 si le placement est invalide.
	if not can_place(x, y, size):
		return -1
	var uid := _next_uid
	_next_uid += 1
	var b := {"uid": uid, "type": type_id, "level": level, "x": x, "y": y, "size": size}
	buildings.append(b)
	_by_uid[uid] = buildings.size() - 1
	_occupy(x, y, size, uid)
	return uid


func move(uid: int, nx: int, ny: int) -> bool:
	var b := get_building(uid)
	if b.is_empty():
		return false
	var size: int = b["size"]
	if not can_place(nx, ny, size, uid):
		return false
	_free(b["x"], b["y"], size)
	b["x"] = nx
	b["y"] = ny
	_occupy(nx, ny, size, uid)
	return true


func remove(uid: int) -> bool:
	var b := get_building(uid)
	if b.is_empty():
		return false
	_free(b["x"], b["y"], b["size"])
	var idx: int = _by_uid[uid]
	buildings.remove_at(idx)
	_reindex()
	return true


func get_building(uid: int) -> Dictionary:
	if not _by_uid.has(uid):
		return {}
	return buildings[_by_uid[uid]]


func building_at(x: int, y: int) -> Dictionary:
	var k := cell_key(x, y)
	if not _cells.has(k):
		return {}
	return get_building(_cells[k])


func count_of(type_id: String) -> int:
	var n := 0
	for b: Dictionary in buildings:
		if b["type"] == type_id:
			n += 1
	return n


func all_of(type_id: String) -> Array[Dictionary]:
	var out: Array[Dictionary] = []
	for b: Dictionary in buildings:
		if b["type"] == type_id:
			out.append(b)
	return out


func center_of(b: Dictionary) -> Vector2:
	var s: float = float(b["size"]) * 0.5
	return Vector2(float(b["x"]) + s, float(b["y"]) + s)


func _occupy(x: int, y: int, size: int, uid: int) -> void:
	for dy in range(size):
		for dx in range(size):
			_cells[cell_key(x + dx, y + dy)] = uid


func _free(x: int, y: int, size: int) -> void:
	for dy in range(size):
		for dx in range(size):
			_cells.erase(cell_key(x + dx, y + dy))


func _reindex() -> void:
	_by_uid.clear()
	for i in range(buildings.size()):
		_by_uid[buildings[i]["uid"]] = i


func rebuild_index() -> void:
	_cells.clear()
	_reindex()
	for b: Dictionary in buildings:
		_occupy(b["x"], b["y"], b["size"], b["uid"])


# ------------------------------------------------------------ sérialisation

func to_dict() -> Dictionary:
	return {"w": width, "h": height, "next_uid": _next_uid, "buildings": buildings}


static func from_dict(d: Dictionary) -> Village:
	var v := Village.new(int(d.get("w", 44)), int(d.get("h", 44)))
	v._next_uid = int(d.get("next_uid", 1))
	var raw: Array = d.get("buildings", [])
	for item: Variant in raw:
		var b: Dictionary = item
		v.buildings.append({
			"uid": int(b["uid"]), "type": String(b["type"]), "level": int(b["level"]),
			"x": int(b["x"]), "y": int(b["y"]), "size": int(b["size"]),
		})
	v.rebuild_index()
	return v
