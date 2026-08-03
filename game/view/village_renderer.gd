class_name VillageRenderer
extends Node3D
## Rendu d'un village.
##
## Les bâtiments sont regroupés par (silhouette, taille, palier, couleur) dans
## des MultiMeshInstance3D : un village de 300 bâtiments tient en une vingtaine
## de draw calls au lieu de 300 (docs/07 §2). C'est ce qui permet de viser
## 60 fps sur un téléphone d'entrée de gamme.

const BUILD_ANIM_TIME := 0.45
const DROP_HEIGHT := 2.6

var village: Village
var tables: DataTables

var _groups: Dictionary = {}        # key -> {mmi, uids}
var _slot: Dictionary = {}          # uid -> {key, index}
var _base_xform: Dictionary = {}    # uid -> Transform3D au repos
var _anim: Dictionary = {}          # uid -> temps écoulé
var _hidden: Dictionary = {}        # uid -> true (détruit en combat)
var _ground: MeshInstance3D
var _selection: MeshInstance3D
var _dust: Array[MeshInstance3D] = []
var _dust_cursor := 0


func _ready() -> void:
	_build_ground_placeholder()
	_build_selection_marker()
	_build_dust_pool()


func setup(v: Village, t: DataTables) -> void:
	village = v
	tables = t
	_rebuild_ground()
	rebuild()


# ------------------------------------------------------------------- terrain

func _build_ground_placeholder() -> void:
	_ground = MeshInstance3D.new()
	_ground.material_override = Palette.material()
	_ground.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(_ground)


func _rebuild_ground() -> void:
	if village == null:
		return
	_ground.mesh = BuildingMesh.make_ground(village.width, village.height, 1337)


# ----------------------------------------------------------------- bâtiments

func rebuild() -> void:
	## Reconstruction complète des lots. Appelée quand le village change, ce
	## qui est rare (construction, amélioration, vente) — le coût est négligeable
	## comparé à une gestion incrémentale bien plus fragile.
	for key: Variant in _groups.keys():
		var mmi: MultiMeshInstance3D = _groups[key]["mmi"]
		mmi.queue_free()
	_groups.clear()
	_slot.clear()
	_base_xform.clear()
	if village == null or tables == null:
		return

	var by_key: Dictionary = {}
	for b: Dictionary in village.buildings:
		var d: Dictionary = tables.buildings.get(b["type"], {})
		if d.is_empty():
			continue
		var tier := BuildingMesh.tier_for_level(int(b["level"]))
		var key := "%s|%d|%d|%s" % [d["shape"], int(b["size"]), tier, d["color"]]
		if not by_key.has(key):
			by_key[key] = {"shape": String(d["shape"]), "size": int(b["size"]),
					"level": int(b["level"]), "color": String(d["color"]), "items": []}
		by_key[key]["items"].append(b)

	for key: String in by_key.keys():
		var g: Dictionary = by_key[key]
		var items: Array = g["items"]
		var mesh := BuildingMesh.get_mesh(String(g["shape"]), int(g["size"]),
				int(g["level"]), String(g["color"]))
		var mm := MultiMesh.new()
		mm.transform_format = MultiMesh.TRANSFORM_3D
		mm.mesh = mesh
		mm.instance_count = items.size()
		var mmi := MultiMeshInstance3D.new()
		mmi.multimesh = mm
		mmi.material_override = Palette.material()
		add_child(mmi)
		var uids: Array[int] = []
		for i in range(items.size()):
			var b: Dictionary = items[i]
			var uid := int(b["uid"])
			var xf := _transform_for(b)
			_base_xform[uid] = xf
			if _hidden.has(uid):
				mm.set_instance_transform(i, xf.scaled_local(Vector3(0.001, 0.001, 0.001)))
			else:
				mm.set_instance_transform(i, xf)
			_slot[uid] = {"key": key, "index": i}
			uids.append(uid)
		_groups[key] = {"mmi": mmi, "uids": uids}


func _transform_for(b: Dictionary) -> Transform3D:
	var s := float(int(b["size"])) * 0.5
	var pos := Vector3(float(int(b["x"])) + s, 0.0, float(int(b["y"])) + s)
	return Transform3D(Basis.IDENTITY, pos)


func world_position_of(uid: int) -> Vector3:
	if _base_xform.has(uid):
		return (_base_xform[uid] as Transform3D).origin
	return Vector3.ZERO


func _set_instance(uid: int, xf: Transform3D) -> void:
	if not _slot.has(uid):
		return
	var slot: Dictionary = _slot[uid]
	if not _groups.has(slot["key"]):
		return
	var mmi: MultiMeshInstance3D = _groups[slot["key"]]["mmi"]
	mmi.multimesh.set_instance_transform(int(slot["index"]), xf)


# ------------------------------------------------- animation de construction

func animate_build(uid: int) -> void:
	## L'effet signature du jeu : le bâtiment tombe du ciel, écrase, poussière.
	## C'est là que le joueur comprend qu'il n'y a pas de minuteur (docs/07 §5).
	if not _base_xform.has(uid):
		return
	_anim[uid] = 0.0
	var p := world_position_of(uid)
	_spawn_dust(p)


func _process(delta: float) -> void:
	if _anim.is_empty():
		return
	var done: Array[int] = []
	for uid: int in _anim.keys():
		var t: float = float(_anim[uid]) + delta
		_anim[uid] = t
		var k := clampf(t / BUILD_ANIM_TIME, 0.0, 1.0)
		var base: Transform3D = _base_xform.get(uid, Transform3D())
		var xf := base
		if k < 0.62:
			# Chute avec accélération
			var f := k / 0.62
			var h := DROP_HEIGHT * (1.0 - f * f)
			xf.origin.y = base.origin.y + h
			xf = xf.scaled_local(Vector3(0.92, 1.08, 0.92))
		else:
			# Écrasement puis retour à la normale
			var f := (k - 0.62) / 0.38
			var squash := 1.0 - 0.28 * sin(f * PI) * (1.0 - f * 0.5)
			var widen := 1.0 + 0.22 * sin(f * PI) * (1.0 - f * 0.5)
			xf = xf.scaled_local(Vector3(widen, squash, widen))
		_set_instance(uid, xf)
		if k >= 1.0:
			_set_instance(uid, base)
			done.append(uid)
	for uid: int in done:
		_anim.erase(uid)


# -------------------------------------------------------------- combat (raid)

func set_destroyed(uid: int, destroyed: bool) -> void:
	if destroyed:
		_hidden[uid] = true
		if _base_xform.has(uid):
			var xf: Transform3D = _base_xform[uid]
			_set_instance(uid, xf.scaled_local(Vector3(0.001, 0.001, 0.001)))
			_spawn_dust(xf.origin)
	else:
		_hidden.erase(uid)
		if _base_xform.has(uid):
			_set_instance(uid, _base_xform[uid])


func set_damage_ratio(uid: int, ratio: float) -> void:
	## Un bâtiment qui encaisse s'enfonce et se tasse légèrement : lisible sans
	## barre de vie, ce qui économise beaucoup d'UI en combat.
	if not _base_xform.has(uid) or _hidden.has(uid):
		return
	var base: Transform3D = _base_xform[uid]
	var k := clampf(ratio, 0.0, 1.0)
	var xf := base.scaled_local(Vector3(1.0 + 0.06 * (1.0 - k), 0.55 + 0.45 * k, 1.0 + 0.06 * (1.0 - k)))
	_set_instance(uid, xf)


func clear_damage_state() -> void:
	_hidden.clear()


# ------------------------------------------------------------------ sélection

func _build_selection_marker() -> void:
	var lp := LowPoly.new()
	lp.add_box(Vector3(0, 0.02, 0), Vector3(1.0, 0.04, 1.0), Palette.UI_ACCENT)
	_selection = MeshInstance3D.new()
	_selection.mesh = lp.build()
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(Palette.UI_ACCENT.r, Palette.UI_ACCENT.g, Palette.UI_ACCENT.b, 0.5)
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	_selection.material_override = m
	_selection.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	_selection.visible = false
	add_child(_selection)


func show_selection(b: Dictionary) -> void:
	if b.is_empty():
		hide_selection()
		return
	var s := float(int(b["size"]))
	_selection.scale = Vector3(s * 1.06, 1.0, s * 1.06)
	_selection.position = Vector3(float(int(b["x"])) + s * 0.5, 0.0, float(int(b["y"])) + s * 0.5)
	_selection.visible = true


func hide_selection() -> void:
	if _selection != null:
		_selection.visible = false


# -------------------------------------------------------------------- poussière

func _build_dust_pool() -> void:
	var lp := LowPoly.new()
	lp.add_cylinder(Vector3(0, 0.02, 0), 0.55, 0.62, 0.06, 10, Color("d8cbb0"))
	var mesh := lp.build()
	for i in range(8):
		var mi := MeshInstance3D.new()
		mi.mesh = mesh
		var m := StandardMaterial3D.new()
		m.albedo_color = Color(0.88, 0.83, 0.72, 0.7)
		m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		mi.material_override = m
		mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		mi.visible = false
		add_child(mi)
		_dust.append(mi)


func _spawn_dust(pos: Vector3) -> void:
	if _dust.is_empty():
		return
	var mi := _dust[_dust_cursor]
	_dust_cursor = (_dust_cursor + 1) % _dust.size()
	mi.position = pos
	mi.scale = Vector3(0.3, 1.0, 0.3)
	mi.visible = true
	var mat := mi.material_override as StandardMaterial3D
	mat.albedo_color.a = 0.7
	var tw := create_tween()
	tw.set_parallel(true)
	tw.tween_property(mi, "scale", Vector3(1.9, 1.0, 1.9), 0.45).set_ease(Tween.EASE_OUT)
	tw.tween_property(mat, "albedo_color:a", 0.0, 0.45)
	tw.chain().tween_callback(func() -> void: mi.visible = false)
