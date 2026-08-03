extends Node
## Palette globale (autoload `Palette`).
##
## Toute la 3D du jeu se peint avec ces ~20 teintes, écrites dans les couleurs
## de sommets. Un seul material pour tout le village -> quelques draw calls au
## total (docs/07 §1). Changer l'identité visuelle du jeu = changer ce fichier.

const COLORS := {
	"wood": Color("c08a4e"),
	"wood_dark": Color("8a5a32"),
	"stone": Color("a9b0b8"),
	"stone_dark": Color("6e767f"),
	"iron": Color("7e8c99"),
	"gold": Color("e8b84b"),
	"roof_red": Color("c4553f"),
	"roof_blue": Color("4e7fb8"),
	"roof_green": Color("5e9e5a"),
	"leaf": Color("4e8c4a"),
	"leaf_dark": Color("3a6b38"),
	"dirt": Color("8b6f4e"),
	"grass": Color("6fa85f"),
	"grass_dark": Color("57894b"),
	"essence": Color("7fd9e8"),
	"white": Color("ede7dc"),
	"dark": Color("2e3238"),
	"water": Color("3e7fa8"),
	"danger": Color("d8564a"),
	"ok": Color("6cc36a"),
}

# Teintes de l'interface, alignées sur la 3D.
const UI_BG := Color("1b1f26")
const UI_PANEL := Color("252b34")
const UI_PANEL_LIGHT := Color("2f3742")
const UI_TEXT := Color("ece7de")
const UI_TEXT_DIM := Color("9aa3ad")
const UI_ACCENT := Color("e8b84b")
const UI_OK := Color("6cc36a")
const UI_DANGER := Color("d8564a")

var _material: StandardMaterial3D
var _material_ghost: StandardMaterial3D


func get_color(name: String) -> Color:
	return COLORS.get(name, COLORS["white"])


func shade(name: String, amount: float) -> Color:
	var c: Color = get_color(name)
	if amount >= 0.0:
		return c.lerp(Color.WHITE, amount)
	return c.lerp(Color.BLACK, -amount)


func material() -> StandardMaterial3D:
	## LE material partagé. Les couleurs viennent des sommets, donc une seule
	## instance suffit pour tout le jeu.
	if _material == null:
		_material = StandardMaterial3D.new()
		_material.vertex_color_use_as_albedo = true
		_material.roughness = 0.95
		_material.metallic = 0.0
		_material.specular_mode = BaseMaterial3D.SPECULAR_DISABLED
		_material.shading_mode = BaseMaterial3D.SHADING_MODE_PER_PIXEL
	return _material


func ghost_material(valid: bool) -> StandardMaterial3D:
	## Material de prévisualisation lors du placement.
	var m := StandardMaterial3D.new()
	m.vertex_color_use_as_albedo = false
	m.albedo_color = (UI_OK if valid else UI_DANGER)
	m.albedo_color.a = 0.55
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	m.cull_mode = BaseMaterial3D.CULL_DISABLED
	return m


func resource_color(res: String) -> Color:
	match res:
		"wood": return get_color("wood")
		"stone": return get_color("stone")
		"iron": return get_color("iron")
		"gold": return get_color("gold")
		"essence": return get_color("essence")
	return get_color("white")
