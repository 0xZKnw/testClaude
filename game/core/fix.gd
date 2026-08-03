class_name Fix
extends RefCounted
## Arithmétique en virgule fixe Q16.16.
##
## Toute la simulation de combat utilise ces entiers et JAMAIS de float : c'est
## la condition pour qu'un même (seed + inputs) produise le même résultat bit à
## bit sur n'importe quel appareil, et donc pour que le serveur puisse rejouer
## et valider un combat PvP (voir docs/05 §4).
##
## Un entier Fix représente une valeur réelle v telle que raw = v * 65536.

const SHIFT := 16
const ONE := 1 << SHIFT      # 65536
const HALF := ONE >> 1
const MAX := 0x7FFFFFFFFFFF


static func from_int(v: int) -> int:
	return v << SHIFT


static func to_int(v: int) -> int:
	## Troncature vers le bas (décalage arithmétique), y compris pour les négatifs.
	return v >> SHIFT


static func round_to_int(v: int) -> int:
	return (v + HALF) >> SHIFT


static func from_float(v: float) -> int:
	## Utilisé UNIQUEMENT au chargement des données (mêmes chaînes CSV ->
	## mêmes floats -> mêmes entiers). Jamais pendant la simulation.
	return int(round(v * float(ONE)))


static func to_float(v: int) -> float:
	return float(v) / float(ONE)


static func mul(a: int, b: int) -> int:
	return (a * b) >> SHIFT


static func div(a: int, b: int) -> int:
	if b == 0:
		return 0
	return (a << SHIFT) / b


static func abs_fix(v: int) -> int:
	return -v if v < 0 else v


static func min_fix(a: int, b: int) -> int:
	return a if a < b else b


static func max_fix(a: int, b: int) -> int:
	return a if a > b else b


static func clamp_fix(v: int, lo: int, hi: int) -> int:
	if v < lo:
		return lo
	if v > hi:
		return hi
	return v


static func isqrt(n: int) -> int:
	## Racine carrée entière par méthode de Newton. Purement entière, donc
	## identique sur toutes les plateformes.
	if n <= 0:
		return 0
	if n < 4:
		return 1
	var x := n
	var y := (x + 1) >> 1
	while y < x:
		x = y
		y = (x + n / x) >> 1
	return x


static func sqrt_fix(v: int) -> int:
	## sqrt d'une valeur Q16.16 : sqrt(v/ONE)*ONE == isqrt(v*ONE).
	if v <= 0:
		return 0
	return isqrt(v << SHIFT)


static func dist(ax: int, ay: int, bx: int, by: int) -> int:
	var dx := ax - bx
	var dy := ay - by
	return sqrt_fix(mul(dx, dx) + mul(dy, dy))


static func dist_sq(ax: int, ay: int, bx: int, by: int) -> int:
	var dx := ax - bx
	var dy := ay - by
	return mul(dx, dx) + mul(dy, dy)
