#!/usr/bin/env python3
"""Build APK 68: detecta o uso não qualificado de Gravity sem import explícito."""
from pathlib import Path
import re
import sys
root = Path(__file__).resolve().parents[1]
invalid = []
for path in (root/'app/src/main/java').rglob('*.kt'):
    source = path.read_text(encoding='utf-8')
    # Apenas chamadas reais em código; usos de exemplo em comentário não são suficientes.
    if re.search(r'(?<![A-Za-z0-9_.])Gravity\.[A-Z][A-Z_]+', source):
        if not re.search(r'^import\s+android\.view\.Gravity(?:\s|$)', source, re.M):
            invalid.append(str(path.relative_to(root)))
if invalid:
    print('Uso de Gravity sem import android.view.Gravity: '+', '.join(invalid), file=sys.stderr)
    sys.exit(1)
print('Imports Gravity verificados.')
