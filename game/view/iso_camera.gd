class_name IsoCamera
extends Node3D
## Caméra isométrique tactile : pan à un doigt, pinch pour zoomer, rotation à
## deux doigts. Aucun collider n'est nécessaire — le picking se fait par
## intersection analytique avec le plan y = 0, ce qui est exact et gratuit.

const PITCH_DEG := 42.0
const MIN_DIST := 14.0
const MAX_DIST := 78.0
const DEFAULT_DIST := 44.0
const ROTATE_THRESHOLD := 0.06
const PAN_SENSITIVITY := 0.00105
const PINCH_SENSITIVITY := 0.55
const ROTATE_SENSITIVITY := 0.45

@export var bounds_min := Vector2(0, 0)
@export var bounds_max := Vector2(44, 44)

var camera: Camera3D
var target := Vector3(22, 0, 22)
var distance := DEFAULT_DIST
var yaw_deg := 45.0
var enabled := true

var _touches: Dictionary = {}          # index -> position écran
var _last_pinch_dist := 0.0
var _last_pinch_angle := 0.0
var _dragged := false
var _drag_start := Vector2.ZERO
var _shake_time := 0.0
var _shake_strength := 0.0


func _ready() -> void:
	camera = Camera3D.new()
	camera.fov = 34.0
	camera.near = 0.5
	camera.far = 220.0
	add_child(camera)
	_apply()


func setup_bounds(w: int, h: int) -> void:
	bounds_min = Vector2(2, 2)
	bounds_max = Vector2(float(w) - 2.0, float(h) - 2.0)
	target = Vector3(float(w) * 0.5, 0.0, float(h) * 0.5)
	# Cadrage par défaut : voir la majeure partie de son village d'un coup
	# d'oeil, pas trois bâtiments (pilier de lisibilité, docs/01 P4).
	distance = clampf(float(maxi(w, h)) * 0.95, MIN_DIST, MAX_DIST)
	_apply()


func focus_on(world_pos: Vector3, dist: float = -1.0) -> void:
	target = Vector3(world_pos.x, 0.0, world_pos.z)
	if dist > 0.0:
		distance = clampf(dist, MIN_DIST, MAX_DIST)
	_apply()


func shake(strength: float = 0.12, duration: float = 0.12) -> void:
	_shake_strength = strength
	_shake_time = duration


func _apply() -> void:
	target.x = clampf(target.x, bounds_min.x, bounds_max.x)
	target.z = clampf(target.z, bounds_min.y, bounds_max.y)
	position = target
	rotation.y = deg_to_rad(yaw_deg)
	var a := deg_to_rad(PITCH_DEG)
	camera.position = Vector3(0.0, distance * sin(a), distance * cos(a))
	camera.rotation.x = -a


func _process(delta: float) -> void:
	if _shake_time > 0.0:
		_shake_time -= delta
		var s := _shake_strength * (_shake_time / 0.12)
		camera.h_offset = randf_range(-s, s)
		camera.v_offset = randf_range(-s, s)
		if _shake_time <= 0.0:
			camera.h_offset = 0.0
			camera.v_offset = 0.0


# ------------------------------------------------------------------ entrées

func handle_input(event: InputEvent) -> bool:
	## Retourne true si la caméra a consommé l'événement (donc pas un tap de
	## sélection). Appelé par la vue, qui décide de l'ordre de priorité.
	if not enabled:
		return false

	if event is InputEventScreenTouch:
		var t := event as InputEventScreenTouch
		if t.pressed:
			_touches[t.index] = t.position
			if _touches.size() == 1:
				_dragged = false
				_drag_start = t.position
			elif _touches.size() == 2:
				_reset_pinch()
		else:
			_touches.erase(t.index)
			if _touches.is_empty():
				var was_drag := _dragged
				_dragged = false
				return was_drag
		return false

	if event is InputEventScreenDrag:
		var d := event as InputEventScreenDrag
		_touches[d.index] = d.position
		if _touches.size() == 1:
			if d.position.distance_to(_drag_start) > 6.0:
				_dragged = true
			_pan(d.relative)
			return true
		elif _touches.size() >= 2:
			_dragged = true
			_pinch_and_rotate()
			return true

	if event is InputEventMouseButton:
		var mb := event as InputEventMouseButton
		if mb.button_index == MOUSE_BUTTON_WHEEL_UP and mb.pressed:
			_zoom(-distance * 0.08)
			return true
		if mb.button_index == MOUSE_BUTTON_WHEEL_DOWN and mb.pressed:
			_zoom(distance * 0.08)
			return true
	return false


func _pan(relative: Vector2) -> void:
	# Sensibilité volontairement basse : sur mobile, un pan trop nerveux rend
	# le placement de bâtiment pénible et donne le mal de mer en session longue.
	var scale_factor := distance * PAN_SENSITIVITY
	var right := Vector3(cos(rotation.y), 0.0, -sin(rotation.y))
	var forward := Vector3(sin(rotation.y), 0.0, cos(rotation.y))
	target -= right * relative.x * scale_factor
	target -= forward * relative.y * scale_factor
	_apply()


func _zoom(amount: float) -> void:
	distance = clampf(distance + amount, MIN_DIST, MAX_DIST)
	_apply()


func _reset_pinch() -> void:
	var pts := _touch_points()
	if pts.size() < 2:
		return
	_last_pinch_dist = pts[0].distance_to(pts[1])
	_last_pinch_angle = (pts[1] - pts[0]).angle()


func _pinch_and_rotate() -> void:
	var pts := _touch_points()
	if pts.size() < 2:
		return
	var d := pts[0].distance_to(pts[1])
	var ang := (pts[1] - pts[0]).angle()
	if _last_pinch_dist > 0.0:
		var ratio := _last_pinch_dist / maxf(d, 1.0)
		# On amortit le ratio : un pincement brut fait bondir le zoom.
		distance = clampf(distance * (1.0 + (ratio - 1.0) * PINCH_SENSITIVITY),
				MIN_DIST, MAX_DIST)
		var delta_ang := angle_difference(_last_pinch_angle, ang)
		if absf(delta_ang) > ROTATE_THRESHOLD:
			yaw_deg -= rad_to_deg(delta_ang) * ROTATE_SENSITIVITY
	_last_pinch_dist = d
	_last_pinch_angle = ang
	_apply()


func _touch_points() -> Array[Vector2]:
	var keys := _touches.keys()
	keys.sort()
	var out: Array[Vector2] = []
	for k: Variant in keys:
		out.append(_touches[k])
	return out


func rotate_by(degrees: float) -> void:
	yaw_deg += degrees
	_apply()


# ------------------------------------------------------------------ picking

func screen_to_ground(screen_pos: Vector2) -> Vector3:
	## Intersection du rayon caméra avec le plan y = 0.
	if camera == null:
		return Vector3.ZERO
	var from := camera.project_ray_origin(screen_pos)
	var dir := camera.project_ray_normal(screen_pos)
	if absf(dir.y) < 0.00001:
		return Vector3.ZERO
	var t := -from.y / dir.y
	if t < 0.0:
		return Vector3.ZERO
	return from + dir * t


func screen_to_cell(screen_pos: Vector2) -> Vector2i:
	var g := screen_to_ground(screen_pos)
	return Vector2i(int(floor(g.x)), int(floor(g.z)))


func world_to_screen(world_pos: Vector3) -> Vector2:
	if camera == null:
		return Vector2.ZERO
	return camera.unproject_position(world_pos)
