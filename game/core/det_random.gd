class_name DetRandom
extends RefCounted
## Générateur pseudo-aléatoire déterministe (xorshift32).
##
## Volontairement séparé de `randi()` de Godot : la simulation ne doit jamais
## dépendre d'un état global partagé avec la présentation, sinon deux exécutions
## du même combat divergent.

const MASK := 0xFFFFFFFF

var _state: int


func _init(seed_value: int = 1) -> void:
	set_seed(seed_value)


func set_seed(seed_value: int) -> void:
	_state = seed_value & MASK
	if _state == 0:
		_state = 0x9E3779B9  # nombre d'or 32 bits, évite l'état absorbant


func get_state() -> int:
	return _state


func next_u32() -> int:
	var s := _state
	s = (s ^ (s << 13)) & MASK
	s = s ^ (s >> 17)
	s = (s ^ (s << 5)) & MASK
	_state = s
	return s


func next_range(min_v: int, max_v: int) -> int:
	## Entier dans [min_v, max_v).
	if max_v <= min_v:
		return min_v
	return min_v + (next_u32() % (max_v - min_v))


func next_fix_unit() -> int:
	## Valeur Q16.16 dans [0, 1).
	return next_u32() & (Fix.ONE - 1)


func chance(percent: int) -> bool:
	return int(next_u32() % 100) < percent


func pick(arr: Array) -> Variant:
	if arr.is_empty():
		return null
	return arr[next_u32() % arr.size()]


func shuffle_array(arr: Array) -> void:
	## Fisher-Yates déterministe (Array.shuffle() de Godot ne l'est pas).
	for i in range(arr.size() - 1, 0, -1):
		var j := int(next_u32() % (i + 1))
		var tmp: Variant = arr[i]
		arr[i] = arr[j]
		arr[j] = tmp
