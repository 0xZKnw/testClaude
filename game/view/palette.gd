extends Node
## Palette et materials globaux (autoload `Palette`).
##
## Tout le rendu 3D tient dans ces teintes, écrites dans les couleurs de
## sommets, et dans deux shaders : un cel-shading et un contour. Aucune texture,
## un seul material pour tout le village.
##
## Changer l'identité visuelle du jeu = changer ce fichier.

const COLORS := {
	# --- terrain
	"grass": Color("6ea852"),
	"grass_dark": Color("5d9245"),
	"grass_deep": Color("4d7c39"),
	"sand": Color("e8d59b"),
	"sand_dark": Color("cbb478"),
	"rock": Color("8c8a86"),
	"rock_dark": Color("6b6a67"),
	"water": Color("4aa8c8"),
	"dirt": Color("a3794f"),
	"path": Color("c2a878"),

	# --- matériaux de construction
	"wood": Color("c98f52"),
	"wood_dark": Color("8f5f34"),
	"stone": Color("cfd4d8"),
	"stone_dark": Color("9aa1a8"),
	"iron": Color("7d8a97"),
	"gold": Color("f2c14e"),
	"copper": Color("d08a5a"),

	# --- toitures : c'est ce qui donne sa couleur au village vu de haut
	"roof_red": Color("d4573f"),
	"roof_blue": Color("4d80c4"),
	"roof_green": Color("57a85c"),
	"roof_purple": Color("8f6bb5"),
	"roof_teal": Color("3fa9a0"),
	"roof_orange": Color("e08a3c"),

	# --- végétation
	"leaf": Color("46893f"),
	"leaf_dark": Color("3c7a3b"),
	"leaf_autumn": Color("d99a3c"),

	# --- divers
	"essence": Color("7fe0ea"),
	"white": Color("f5efe2"),
	"dark": Color("2b2f38"),
	"danger": Color("e05a4a"),
	"ok": Color("6cc36a"),
}

# --- interface : chaudes, contrastées, alignées sur la 3D
const UI_BG := Color("171a21")
const UI_PANEL := Color("232833")
const UI_PANEL_LIGHT := Color("2f3644")
const UI_TEXT := Color("f2ece0")
const UI_TEXT_DIM := Color("97a0af")
const UI_ACCENT := Color("f2c14e")
const UI_ACCENT_DARK := Color("c99a2f")
const UI_OK := Color("6cc36a")
const UI_DANGER := Color("e05a4a")
const UI_XP := Color("7fc4ea")

const TOON_SHADER := "res://view/shaders/toon.gdshader"
const OUTLINE_SHADER := "res://view/shaders/outline.gdshader"

var _material: ShaderMaterial
var _material_flat: ShaderMaterial
var _water_material: ShaderMaterial
var _outline_material: ShaderMaterial


func get_color(name: String) -> Color:
	return COLORS.get(name, COLORS["white"])


func shade(name: String, amount: float) -> Color:
	var c: Color = get_color(name)
	if amount >= 0.0:
		return c.lerp(Color.WHITE, amount)
	return c.lerp(Color.BLACK, -amount)


func material() -> ShaderMaterial:
	## Material des bâtiments et unités. Le contour est un MultiMeshInstance
	## séparé (voir village_renderer) et non un `next_pass` : dans le renderer
	## Compatibility, next_pass ne suit pas les instances.
	if _material == null:
		_material = ShaderMaterial.new()
		_material.shader = load(TOON_SHADER)
		_material.set_shader_parameter("bands", 3.0)
		_material.set_shader_parameter("shadow_lift", 0.30)
		_material.set_shader_parameter("rim_strength", 0.18)
	return _material


func outline_material() -> ShaderMaterial:
	if _outline_material == null:
		_outline_material = ShaderMaterial.new()
		_outline_material.shader = load(OUTLINE_SHADER)
		_outline_material.set_shader_parameter("width", 0.028)
		_outline_material.set_shader_parameter("outline_color", Vector3(0.09, 0.10, 0.14))
	return _outline_material


func material_flat() -> ShaderMaterial:
	## Terrain : même cel-shading, mais SANS contour — un trait noir autour de
	## chaque tuile d'herbe transformerait le sol en papier millimétré.
	if _material_flat == null:
		_material_flat = ShaderMaterial.new()
		_material_flat.shader = load(TOON_SHADER)
		_material_flat.set_shader_parameter("bands", 2.0)
		_material_flat.set_shader_parameter("shadow_lift", 0.30)
		_material_flat.set_shader_parameter("rim_strength", 0.0)
	return _material_flat


func water_material() -> ShaderMaterial:
	if _water_material == null:
		_water_material = ShaderMaterial.new()
		_water_material.shader = load(TOON_SHADER)
		_water_material.set_shader_parameter("bands", 2.0)
		_water_material.set_shader_parameter("shadow_lift", 0.7)
		_water_material.set_shader_parameter("rim_strength", 0.5)
	return _water_material


func ghost_material(valid: bool) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = (UI_OK if valid else UI_DANGER)
	m.albedo_color.a = 0.5
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	m.cull_mode = BaseMaterial3D.CULL_DISABLED
	return m


func unshaded_material(color: Color, alpha: float = 1.0) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(color.r, color.g, color.b, alpha)
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	if alpha < 1.0:
		m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.billboard_mode = BaseMaterial3D.BILLBOARD_DISABLED
	return m


func resource_color(res: String) -> Color:
	match res:
		"wood": return get_color("wood")
		"stone": return get_color("stone")
		"iron": return get_color("iron")
		"gold": return get_color("gold")
		"essence": return get_color("essence")
	return get_color("white")


func resource_icon(res: String) -> String:
	## Une lettre par ressource. Pas d'emoji : la police de secours de Godot
	## n'en contient pas et afficherait des carrés. La lettre double la couleur
	## pour les joueurs daltoniens.
	match res:
		"wood": return "B"
		"stone": return "P"
		"iron": return "F"
		"gold": return "O"
		"essence": return "E"
	return "?"


func resource_name(res: String) -> String:
	return DataTables.RESOURCE_NAMES.get(res, res)
