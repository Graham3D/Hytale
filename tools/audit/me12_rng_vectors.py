"""Independent AD1 uniformBelow oracle. No Java, server, save or production RNG access."""
import hashlib
import json
import struct
import sys
from pathlib import Path


def draws(domain, seed, bound, count):
    a, b = domain.encode('utf-8'), seed.encode('utf-8')
    prefix = struct.pack('>I', len(a)) + a + struct.pack('>I', len(b)) + b
    counter = 0
    output = []
    bits = (bound - 1).bit_length()
    size = (bits + 7) // 8
    while len(output) < count:
        if bound == 1:
            output.append('0')
            continue
        candidate = bytearray()
        while len(candidate) < size:
            candidate.extend(hashlib.sha256(prefix + struct.pack('>Q', counter)).digest())
            counter += 1
        candidate = candidate[:size]
        candidate[0] &= 255 >> (size * 8 - bits)
        value = int.from_bytes(candidate, 'big')
        if value < bound:
            output.append(str(value))
    return output


def vectors():
    return [dict(domain=domain, seed='ME12-S04/golden', bound=str(bound),
                 draws=draws(domain, 'ME12-S04/golden', bound, 8))
            for domain in ('me12.tier-pair/v1', 'me12.name-pair/v1', 'me12.affix-set/v1')
            for bound in (1, 31, 10000, 2**128 + 17)]


if __name__ == '__main__':
    path = Path(__file__).resolve().parents[2] / 'src/test/resources/enemies/me12-rng-vectors.json'
    if sys.argv[1:] == ['--write']:
        path.write_text(json.dumps(vectors(), indent=2) + '\n', encoding='utf-8')
    else:
        assert json.loads(path.read_text(encoding='utf-8')) == vectors()
        print('ME12 independent RNG oracle: 12 vectors / 96 draws verified')
