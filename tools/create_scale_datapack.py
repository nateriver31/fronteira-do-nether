#!/usr/bin/env python3
"""Build a dimension-type override from the server's actual registry data.

No vanilla properties or Minecraft pack-version numbers are guessed here.
Requires Python 3.10+ and uses only the standard library.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import sys
from typing import Any


MAX_INPUT_BYTES = 8 * 1024 * 1024
DIMENSION_RESOURCE = "data/minecraft/dimension_type/the_nether.json"


class InputError(ValueError):
    """Invalid or unsafe generator input."""


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise InputError(f"Chave JSON duplicada: {key}")
        result[key] = value
    return result


def _reject_constant(value: str) -> None:
    raise InputError(f"Constante JSON não finita: {value}")


def _check_finite(value: Any) -> None:
    if isinstance(value, float) and not math.isfinite(value):
        raise InputError("JSON contém número não finito.")
    if isinstance(value, dict):
        for child in value.values():
            _check_finite(child)
    elif isinstance(value, list):
        for child in value:
            _check_finite(child)


def _no_symlink_components(path: Path) -> Path:
    absolute = Path(os.path.abspath(path))
    for part in (absolute, *absolute.parents):
        if part.is_symlink():
            raise InputError(f"Links simbólicos não são aceitos: {part}")
    return absolute


def read_json_file(path: Path) -> tuple[bytes, dict[str, Any]]:
    path = _no_symlink_components(path)
    if not path.is_file():
        raise InputError(f"Arquivo regular não encontrado: {path}")
    if path.stat().st_size > MAX_INPUT_BYTES:
        raise InputError(f"Arquivo excede {MAX_INPUT_BYTES} bytes: {path}")
    raw = path.read_bytes()
    try:
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=_unique_object,
                           parse_constant=_reject_constant)
    except (UnicodeError, json.JSONDecodeError, RecursionError) as exc:
        raise InputError(f"JSON UTF-8 inválido: {path.name}") from exc
    if not isinstance(value, dict):
        raise InputError(f"A raiz JSON deve ser um objeto: {path.name}")
    _check_finite(value)
    return raw, value


def _positive_scale(value: Any) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise InputError("coordinate_scale/scale deve ser um número positivo finito.")
    try:
        number = float(value)
    except (OverflowError, ValueError) as exc:
        raise InputError("Escala fora do intervalo numérico suportado.") from exc
    if not math.isfinite(number) or number <= 0:
        raise InputError("coordinate_scale/scale deve ser um número positivo finito.")
    return number


def _format_version(value: Any) -> tuple[int, int]:
    if isinstance(value, int) and not isinstance(value, bool) and value > 0:
        return value, 0
    if (isinstance(value, list) and len(value) == 2
            and all(isinstance(part, int) and not isinstance(part, bool)
                    and part >= 0 for part in value) and value[0] > 0):
        return value[0], value[1]
    raise InputError("Versão de formato deve ser inteiro positivo ou [major, minor].")


def validate_mcmeta(value: dict[str, Any]) -> None:
    """Check basic metadata structure; this cannot infer server compatibility."""
    if set(value) != {"pack"}:
        raise InputError("Use metadados mínimos contendo somente 'pack'; features, filtros e overlays não são aceitos.")
    pack = value.get("pack")
    if not isinstance(pack, dict):
        raise InputError("pack.mcmeta deve conter o objeto 'pack'.")
    if not isinstance(pack.get("description"), (str, dict, list)):
        raise InputError("pack.mcmeta deve conter 'pack.description'.")
    has_old = "pack_format" in pack
    has_range = "min_format" in pack or "max_format" in pack
    if not has_old and not has_range:
        raise InputError("pack.mcmeta não declara formato do datapack.")
    if has_old:
        # Legacy pack_format is an integer; ranges belong in separate fields.
        if not isinstance(pack["pack_format"], int) or isinstance(pack["pack_format"], bool):
            raise InputError("pack_format deve ser inteiro positivo.")
        _format_version(pack["pack_format"])
    if has_range:
        if "min_format" not in pack or "max_format" not in pack:
            raise InputError("Informe ambos min_format e max_format.")
        if _format_version(pack["min_format"]) > _format_version(pack["max_format"]):
            raise InputError("min_format deve ser menor ou igual a max_format.")
    # Preserve unknown metadata; Minecraft validates its version-specific schema.


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def generate(dimension_type: Path, pack_mcmeta: Path, output: Path,
             scale: float = 6.0) -> dict[str, Any]:
    target_scale = _positive_scale(scale)
    source_raw, dimension = read_json_file(dimension_type)
    metadata_raw, metadata = read_json_file(pack_mcmeta)
    validate_mcmeta(metadata)
    if "coordinate_scale" not in dimension:
        raise InputError("O dimension_type efetivo deve conter coordinate_scale.")
    source_scale = _positive_scale(dimension["coordinate_scale"])
    dimension["coordinate_scale"] = target_scale
    rendered = (json.dumps(dimension, ensure_ascii=False, indent=2, allow_nan=False)
                + "\n").encode("utf-8")

    output = _no_symlink_components(output)
    if output.exists():
        raise InputError("O diretório de saída já existe; nada foi sobrescrito.")
    if not output.parent.is_dir():
        raise InputError("O diretório pai da saída deve existir.")

    report = {
        "generator": "SurvivalPlusCore create_scale_datapack.py",
        "dimension_type": "minecraft:the_nether",
        "only_semantic_change": "coordinate_scale",
        "source_coordinate_scale": source_scale,
        "target_coordinate_scale": target_scale,
        "source_dimension_sha256": _sha256(source_raw),
        "output_dimension_sha256": _sha256(rendered),
        "pack_mcmeta_sha256": _sha256(metadata_raw),
        "minecraft_version_compatibility": "not_verified_by_generator",
        "warning": "Confirme o tipo efetivo da dimensão, a escala do Overworld e a prioridade dos packs.",
    }
    # Exclusive creation: never merge into or replace an existing pack.
    output.mkdir()
    target = output / DIMENSION_RESOURCE
    target.parent.mkdir(parents=True)
    with target.open("xb") as stream:
        stream.write(rendered)
    with (output / "pack.mcmeta").open("xb") as stream:
        stream.write(metadata_raw)
    with (output / "scale-provenance.json").open("x", encoding="utf-8") as stream:
        json.dump(report, stream, ensure_ascii=False, indent=2, allow_nan=False)
        stream.write("\n")
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dimension-type", required=True, type=Path,
                        help="JSON do tipo EFETIVO minecraft:the_nether do servidor")
    parser.add_argument("--pack-mcmeta", required=True, type=Path,
                        help="pack.mcmeta comprovadamente válido na versão exata do servidor")
    parser.add_argument("--output", required=True, type=Path,
                        help="Novo diretório do pack (não pode existir)")
    parser.add_argument("--scale", type=float, default=6.0)
    args = parser.parse_args(argv)
    try:
        report = generate(args.dimension_type, args.pack_mcmeta, args.output, args.scale)
    except (InputError, OSError, RecursionError) as exc:
        print(f"Erro: {exc}", file=sys.stderr)
        return 1
    print(f"Datapack criado: {args.output}")
    print(f"coordinate_scale: {report['source_coordinate_scale']} → {report['target_coordinate_scale']}")
    print("Compatibilidade de versão e prioridade dos datapacks exigem validação no servidor.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
