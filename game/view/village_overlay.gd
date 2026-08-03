class_name VillageOverlay
extends Control
## Badges de récolte au-dessus des bâtiments, et gains qui s'envolent.
##
## C'est la couche qui donne au village son côté tycoon : à tout instant on
## voit CE QUI ATTEND d'être ramassé et OÙ. Dessiné en 2D par-dessus la 3D pour
## rester net à tous les zooms, sans un nœud par bâtiment.

const BOB_SPEED := 2.4
const BOB_AMPLITUDE := 4.0
const FLY_TIME := 0.7
const BADGE_H := 34.0
const FULL_PULSE_SPEED := 5.0

var camera: IsoCamera
var _time := 0.0
var _flying: Array[Dictionary] = []
var _font: Font


func _init(iso_camera: IsoCamera) -> void:
	camera = iso_camera
	UIKit.fill_screen(self)
	mouse_filter = Control.MOUSE_FILTER_IGNORE


func _ready() -> void:
	_font = ThemeDB.fallback_font
	Game.collected.connect(_on_collected)


func _on_collected(uid: int, gains: Dictionary) -> void:
	var b := Game.state.village.get_building(uid)
	if b.is_empty():
		return
	var s := float(int(b["size"])) * 0.5
	var world := Vector3(float(int(b["x"])) + s, 1.1, float(int(b["y"])) + s)
	for res: String in gains.keys():
		_flying.append({
			"world": world, "res": res, "amount": int(gains[res]), "life": FLY_TIME,
		})
	while _flying.size() > 20:
		_flying.pop_front()


func _process(delta: float) -> void:
	_time += delta
	for i in range(_flying.size() - 1, -1, -1):
		_flying[i]["life"] = float(_flying[i]["life"]) - delta
		if float(_flying[i]["life"]) <= 0.0:
			_flying.remove_at(i)
	queue_redraw()


func _draw() -> void:
	if camera == null or camera.camera == null or Game.state == null:
		return
	var caps := Economy.storage_caps(Game.state, Game.tables)

	for b: Dictionary in Game.state.village.buildings:
		var uid := int(b["uid"])
		var slot: Dictionary = Game.state.pending.get(uid, {})
		if slot.is_empty():
			continue
		var res := ""
		var amount := 0
		for r: String in slot.keys():
			var v := int(floor(float(slot[r])))
			if v > amount:
				amount = v
				res = r
		if amount <= 0:
			continue
		_draw_badge(b, res, amount, caps)

	for f: Dictionary in _flying:
		_draw_flying(f)


func _draw_badge(b: Dictionary, res: String, amount: int, caps: Dictionary) -> void:
	var half := float(int(b["size"])) * 0.5
	var world := Vector3(float(int(b["x"])) + half, 1.05 + half * 0.55, float(int(b["y"])) + half)
	if camera.camera.is_position_behind(world):
		return
	var pos := camera.camera.unproject_position(world)
	if pos.x < -80.0 or pos.y < -40.0 or pos.x > size.x + 80.0 or pos.y > size.y + 40.0:
		return

	# Léger flottement : attire l'œil sans animation par bâtiment.
	pos.y += sin(_time * BOB_SPEED + float(int(b["uid"])) * 0.9) * BOB_AMPLITUDE

	var text := Game.format_number(amount)
	var font_size := 24
	var text_w := _font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size).x
	var w := text_w + 46.0
	var rect := Rect2(pos.x - w * 0.5, pos.y - BADGE_H * 0.5, w, BADGE_H)

	# L'entrepôt plein est signalé en rouge : sans ça, le joueur continue de
	# produire dans le vide sans comprendre pourquoi ses chiffres stagnent.
	var cap := int(caps.get(res, 0))
	var full := cap > 0 and int(Game.state.resources.get(res, 0)) >= cap
	var bg := Color(0.08, 0.09, 0.13, 0.86)
	if full:
		var pulse := 0.5 + 0.5 * sin(_time * FULL_PULSE_SPEED)
		bg = bg.lerp(Palette.UI_DANGER, 0.35 + pulse * 0.2)

	draw_rect(rect, bg, true)
	draw_rect(rect, Palette.resource_color(res), false, 2.0)

	var swatch := Rect2(rect.position.x + 7.0, rect.position.y + 8.0, 18.0, 18.0)
	draw_rect(swatch, Palette.resource_color(res), true)
	draw_string(_font, swatch.position + Vector2(4.5, 15.0), Palette.resource_icon(res),
			HORIZONTAL_ALIGNMENT_LEFT, -1, 15, Color(0.06, 0.07, 0.1))
	draw_string(_font, Vector2(rect.position.x + 32.0, rect.position.y + 25.0), text,
			HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, Palette.UI_TEXT)


func _draw_flying(f: Dictionary) -> void:
	var world: Vector3 = f["world"]
	if camera.camera.is_position_behind(world):
		return
	var life := float(f["life"])
	var t := 1.0 - life / FLY_TIME
	var pos := camera.camera.unproject_position(world)
	pos.y -= t * 62.0
	var alpha := clampf(life / (FLY_TIME * 0.5), 0.0, 1.0)
	var col: Color = Palette.resource_color(String(f["res"]))
	col.a = alpha
	var text := "+%s" % Game.format_number(int(f["amount"]))
	var w := _font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, 30).x
	draw_string(_font, pos + Vector2(-w * 0.5 + 2.0, 2.0), text,
			HORIZONTAL_ALIGNMENT_LEFT, -1, 30, Color(0, 0, 0, alpha * 0.55))
	draw_string(_font, pos + Vector2(-w * 0.5, 0.0), text,
			HORIZONTAL_ALIGNMENT_LEFT, -1, 30, col)
