class_name UIKit
extends RefCounted
## Fabrique de contrôles : un seul endroit où vit le style de l'interface.
##
## Contraintes mobile respectées ici (docs/07 §6) : cible tactile de 48 px
## minimum, actions importantes dans le tiers bas de l'écran, texte jamais
## sous 24 px dans un viewport 1080×1920.

const TOUCH_MIN := 96          # ~48 dp dans un viewport 1080 de large
const FONT_SM := 26
const FONT_MD := 32
const FONT_LG := 42
const FONT_XL := 58


static func stylebox(color: Color, radius: int = 14, margin: int = 16) -> StyleBoxFlat:
	var sb := StyleBoxFlat.new()
	sb.bg_color = color
	sb.corner_radius_top_left = radius
	sb.corner_radius_top_right = radius
	sb.corner_radius_bottom_left = radius
	sb.corner_radius_bottom_right = radius
	sb.content_margin_left = margin
	sb.content_margin_right = margin
	sb.content_margin_top = margin
	sb.content_margin_bottom = margin
	return sb


static func panel(color: Color = Palette.UI_PANEL, radius: int = 18, margin: int = 20) -> PanelContainer:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", stylebox(color, radius, margin))
	return p


static func label(text: String, size: int = FONT_MD, color: Color = Palette.UI_TEXT) -> Label:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", color)
	return l


static func title(text: String) -> Label:
	var l := label(text, FONT_LG, Palette.UI_ACCENT)
	return l


static func button(text: String, on_press: Callable = Callable(),
		accent: bool = false, min_h: int = TOUCH_MIN) -> Button:
	var b := Button.new()
	b.text = text
	b.custom_minimum_size = Vector2(0, min_h)
	b.add_theme_font_size_override("font_size", FONT_MD)
	var base := Palette.UI_ACCENT if accent else Palette.UI_PANEL_LIGHT
	var text_col := Palette.UI_BG if accent else Palette.UI_TEXT
	b.add_theme_stylebox_override("normal", stylebox(base, 12, 14))
	b.add_theme_stylebox_override("hover", stylebox(base.lightened(0.08), 12, 14))
	b.add_theme_stylebox_override("pressed", stylebox(base.darkened(0.18), 12, 14))
	b.add_theme_stylebox_override("disabled", stylebox(Palette.UI_PANEL.darkened(0.1), 12, 14))
	b.add_theme_color_override("font_color", text_col)
	b.add_theme_color_override("font_hover_color", text_col)
	b.add_theme_color_override("font_pressed_color", text_col)
	b.add_theme_color_override("font_disabled_color", Palette.UI_TEXT_DIM)
	if on_press.is_valid():
		b.pressed.connect(on_press)
	return b


static func icon_swatch(color: Color, size: int = 26) -> ColorRect:
	var c := ColorRect.new()
	c.color = color
	c.custom_minimum_size = Vector2(size, size)
	c.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	return c


static func hbox(separation: int = 12) -> HBoxContainer:
	var h := HBoxContainer.new()
	h.add_theme_constant_override("separation", separation)
	return h


static func vbox(separation: int = 12) -> VBoxContainer:
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", separation)
	return v


static func spacer() -> Control:
	var c := Control.new()
	c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	c.size_flags_vertical = Control.SIZE_EXPAND_FILL
	return c


static func scroll(content: Control) -> ScrollContainer:
	var s := ScrollContainer.new()
	s.size_flags_vertical = Control.SIZE_EXPAND_FILL
	s.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	content.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	s.add_child(content)
	return s


static func separator() -> HSeparator:
	var s := HSeparator.new()
	var sb := StyleBoxFlat.new()
	sb.bg_color = Palette.UI_PANEL_LIGHT
	sb.content_margin_top = 1
	sb.content_margin_bottom = 1
	s.add_theme_stylebox_override("separator", sb)
	return s


static func cost_line(cost: Dictionary, affordable_check: Callable = Callable()) -> HBoxContainer:
	## Ligne de coût avec pastille de couleur par ressource, rouge si le joueur
	## ne peut pas payer. Lisible d'un coup d'œil, sans lire les chiffres.
	var box := hbox(14)
	if cost.is_empty():
		box.add_child(label("gratuit", FONT_SM, Palette.UI_TEXT_DIM))
		return box
	for res: String in cost.keys():
		var amount := int(cost[res])
		if amount <= 0:
			continue
		var ok := true
		if affordable_check.is_valid():
			ok = bool(affordable_check.call(res, amount))
		var item := hbox(6)
		item.add_child(icon_swatch(Palette.resource_color(res), 22))
		item.add_child(label(Game.format_number(amount), FONT_SM,
				Palette.UI_TEXT if ok else Palette.UI_DANGER))
		box.add_child(item)
	return box


static func stars_row(count: int, size: int = FONT_SM) -> HBoxContainer:
	var box := hbox(2)
	for i in range(3):
		var filled := i < count
		box.add_child(label("★" if filled else "☆", size,
				Palette.UI_ACCENT if filled else Palette.UI_TEXT_DIM))
	return box


static func fill_screen(c: Control) -> void:
	c.set_anchors_preset(Control.PRESET_FULL_RECT)
	c.offset_left = 0
	c.offset_top = 0
	c.offset_right = 0
	c.offset_bottom = 0
