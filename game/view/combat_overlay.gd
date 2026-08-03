class_name CombatOverlay
extends Control
## Barres de vie et nombres de dégâts pendant un raid.
##
## Dessiné en 2D par-dessus la 3D plutôt qu'avec des panneaux billboardés :
## les barres restent nettes et de taille constante quel que soit le zoom, et
## ça coûte un seul appel de dessin au lieu d'une centaine de nœuds.
##
## Sans ces barres, un raid est illisible : on ne sait pas ce qui est en train
## de tomber, ni si l'on est en train de gagner.

const BUILDING_BAR_W := 54.0
const BUILDING_BAR_H := 9.0
const UNIT_BAR_W := 26.0
const UNIT_BAR_H := 5.0
const DAMAGE_SAMPLE := 0.28      # regroupement des dégâts avant d'afficher un nombre
const POPUP_LIFE := 0.85
const MIN_POPUP_DAMAGE := 12.0

var view: RaidView
var camera: IsoCamera

var _prev_b_hp: Array[float] = []
var _prev_u_hp: Array[float] = []
var _accum_b: Array[float] = []
var _sample_timer := 0.0
var _popups: Array[Dictionary] = []
var _font: Font


func _init(raid_view: RaidView, iso_camera: IsoCamera) -> void:
	view = raid_view
	camera = iso_camera
	UIKit.fill_screen(self)
	mouse_filter = Control.MOUSE_FILTER_IGNORE


func _ready() -> void:
	_font = ThemeDB.fallback_font


func _process(delta: float) -> void:
	if view == null or view.sim == null:
		return
	_sync_arrays()
	_sample_timer += delta
	if _sample_timer >= DAMAGE_SAMPLE:
		_collect_damage()
		_sample_timer = 0.0

	for i in range(_popups.size() - 1, -1, -1):
		_popups[i]["life"] = float(_popups[i]["life"]) - delta
		if float(_popups[i]["life"]) <= 0.0:
			_popups.remove_at(i)
	queue_redraw()


func _sync_arrays() -> void:
	var sim := view.sim
	while _prev_b_hp.size() < sim.b_hp.size():
		var idx := _prev_b_hp.size()
		_prev_b_hp.append(float(sim.b_hp[idx]))
		_accum_b.append(0.0)
	while _prev_u_hp.size() < sim.u_hp.size():
		_prev_u_hp.append(float(sim.u_hp[_prev_u_hp.size()]))


func _collect_damage() -> void:
	## Les dégâts sont continus (dps × dt) : afficher un nombre par tick
	## produirait une bouillie. On regroupe sur ~0,3 s puis on affiche le total.
	var sim := view.sim
	for j in range(sim.b_hp.size()):
		var cur := float(sim.b_hp[j])
		var lost := _prev_b_hp[j] - cur
		_prev_b_hp[j] = cur
		if lost <= 0.0:
			continue
		var dmg := lost / float(Fix.ONE)
		if dmg < MIN_POPUP_DAMAGE:
			continue
		var world := Vector3(Fix.to_float(sim.b_x[j]), 1.4, Fix.to_float(sim.b_y[j]))
		_popups.append({
			"world": world, "text": "-%d" % int(round(dmg)),
			"life": POPUP_LIFE, "color": Palette.UI_DANGER,
		})
	for i in range(sim.u_hp.size()):
		_prev_u_hp[i] = float(sim.u_hp[i])
	# On borde le nombre de nombres flottants : au-delà, c'est du bruit.
	while _popups.size() > 14:
		_popups.pop_front()


func _draw() -> void:
	if view == null or view.sim == null or camera == null or camera.camera == null:
		return
	var sim := view.sim

	# --- bâtiments : seulement ceux qui ont pris des coups
	for j in range(sim.b_alive.size()):
		if not sim.b_alive[j] or sim.b_is_deco[j] or sim.b_maxhp[j] <= 0:
			continue
		var ratio := float(sim.b_hp[j]) / float(sim.b_maxhp[j])
		if ratio >= 0.999:
			continue
		var half := Fix.to_float(sim.b_half[j])
		var world := Vector3(Fix.to_float(sim.b_x[j]), 0.9 + half * 0.8, Fix.to_float(sim.b_y[j]))
		_draw_bar(world, ratio, BUILDING_BAR_W, BUILDING_BAR_H, sim.b_is_def[j])

	# --- unités : toutes, dès qu'elles sont blessées
	for i in range(sim.u_alive.size()):
		if not sim.u_alive[i] or sim.u_maxhp[i] <= 0:
			continue
		var ratio := float(sim.u_hp[i]) / float(sim.u_maxhp[i])
		if ratio >= 0.999:
			continue
		var world := Vector3(Fix.to_float(sim.u_x[i]), 0.62, Fix.to_float(sim.u_y[i]))
		_draw_bar(world, ratio, UNIT_BAR_W, UNIT_BAR_H, false, true)

	for p: Dictionary in _popups:
		_draw_popup(p)


func _draw_bar(world: Vector3, ratio: float, w: float, h: float,
		is_defense: bool, is_ally: bool = false) -> void:
	if camera.camera.is_position_behind(world):
		return
	var pos := camera.camera.unproject_position(world)
	if pos.x < -w or pos.y < -h or pos.x > size.x + w or pos.y > size.y + h:
		return
	var r := clampf(ratio, 0.0, 1.0)
	var origin := Vector2(pos.x - w * 0.5, pos.y - h * 0.5)

	# Cadre sombre : indispensable pour rester lisible sur l'herbe claire.
	draw_rect(Rect2(origin - Vector2(1.5, 1.5), Vector2(w + 3.0, h + 3.0)),
			Color(0.05, 0.06, 0.09, 0.75))
	draw_rect(Rect2(origin, Vector2(w, h)), Color(0.16, 0.18, 0.22, 0.9))

	var col: Color
	if is_ally:
		# Les alliés sont toujours bleus : la couleur dit le camp, la longueur
		# dit l'état. Sinon on confond « mon unité va mourir » et « je suis en
		# train de gagner ».
		col = Palette.UI_XP
	elif r > 0.55:
		col = Palette.UI_OK
	elif r > 0.28:
		col = Palette.UI_ACCENT
	else:
		col = Palette.UI_DANGER
	if is_defense and not is_ally:
		col = col.lerp(Palette.UI_DANGER, 0.35)
	draw_rect(Rect2(origin, Vector2(w * r, h)), col)


func _draw_popup(p: Dictionary) -> void:
	var world: Vector3 = p["world"]
	if camera.camera.is_position_behind(world):
		return
	var life := float(p["life"])
	var t := 1.0 - life / POPUP_LIFE
	var pos := camera.camera.unproject_position(world)
	pos.y -= t * 34.0
	var alpha := clampf(life / (POPUP_LIFE * 0.45), 0.0, 1.0)
	var col: Color = p["color"]
	col.a = alpha
	var text := String(p["text"])
	var font_size := 26
	var w := _font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size).x
	draw_string(_font, pos + Vector2(-w * 0.5 + 1.5, 1.5), text,
			HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, Color(0, 0, 0, alpha * 0.6))
	draw_string(_font, pos + Vector2(-w * 0.5, 0), text,
			HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, col)
