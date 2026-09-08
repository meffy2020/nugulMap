#!/usr/bin/env python3
"""Offline, conservative exact-field audit. Never imports or verifies permissibility."""
import argparse
import csv
import datetime as dt
from decimal import Decimal, InvalidOperation
import hashlib
import json
from pathlib import Path
import re
import unicodedata
from collections import Counter, defaultdict


def norm(value):
    return ' '.join(unicodedata.normalize('NFC', value or '').split())


def usable_address(value):
    value = norm(value)
    return bool(value) and not value.isdecimal() and value not in {'실외', '실내', '-', '없음'}


def coordinates(row):
    try:
        pair = tuple(Decimal(norm(row.get(k, ''))) for k in ('latitude', 'longitude'))
        return pair if all(x.is_finite() for x in pair) else None
    except InvalidOperation:
        return None


def read_csv(path):
    raw = path.read_bytes()
    for encoding in ('utf-8-sig', 'cp949'):
        try:
            content = raw.decode(encoding)
            return list(csv.DictReader(content.splitlines())), encoding, hashlib.sha256(raw).hexdigest()
        except UnicodeDecodeError:
            continue
    raise ValueError(f'Unsupported encoding: {path}')


def date_from_name(name):
    match = re.search(r'(20\d{6})', name)
    return dt.datetime.strptime(match[1], '%Y%m%d').date() if match else None


def write_csv(path, rows, fields):
    with path.open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def audit(data_dir, output_dir, as_of, catalog=None):
    output_dir.mkdir(parents=True, exist_ok=True)
    sources, index = [], defaultdict(list)
    for path in sorted(data_dir.rglob('*.csv'), key=lambda p: norm(str(p))):
        if '흡연' not in norm(path.name):
            continue
        rows, encoding, digest = read_csv(path)
        source_date = date_from_name(path.name)
        headers = list(rows[0]) if rows else []
        address_columns = [k for k in headers if any(t in norm(k) for t in ('주소', '소재지', '위치')) and not any(t in norm(k) for t in ('상세', '위도', '경도'))]
        source_id = str(path.relative_to(data_dir))
        entry = dict(file=source_id, sha256=digest, encoding=encoding, row_count=len(rows), headers=headers,
                     filename_date=str(source_date) if source_date else None,
                     filename_date_is_field_verification=False,
                     stale_filename_date=bool(source_date and (as_of-source_date).days > 365),
                     location_table=bool(address_columns), coordinate_rows=0, missing_or_invalid_coordinate_rows=0,
                     outside_korea_review_bounds_rows=0,
                     exact_duplicate_source_rows=sum(n for n in Counter(tuple(r.items()) for r in rows).values() if n > 1),
                     official_catalog=(catalog or {}).get(norm(path.name)))
        sources.append(entry)
        for number, row in enumerate(rows, 2):
            normalized = {norm(k): norm(v) for k, v in row.items() if k is not None}
            lat = next((v for k,v in normalized.items() if k == '위도'), '')
            lng = next((v for k,v in normalized.items() if k == '경도'), '')
            coord = coordinates(dict(latitude=lat, longitude=lng))
            if coord is None:
                entry['missing_or_invalid_coordinate_rows'] += 1
            else:
                entry['coordinate_rows'] += 1
                if not (33 <= coord[0] <= 39.5 and 124 <= coord[1] <= 132):
                    entry['outside_korea_review_bounds_rows'] += 1
            indoor = any('실내' in k and '실외' in k and v == '실내' for k,v in normalized.items()) or any(k == '구분' and v == '실내' for k,v in normalized.items())
            for column in address_columns:
                value = norm(row.get(column, ''))
                if usable_address(value):
                    index[value].append(dict(source_file=source_id, source_row=number, address_column=column,
                        filename_date=entry['filename_date'], stale_filename_date=entry['stale_filename_date'],
                        source_reference_dates={k:v for k,v in normalized.items() if '기준일' in k},
                        coordinate=coord, indoor=indoor, values=list(normalized.values())))
    final, encoding, digest = read_csv(data_dir/'final_zones_all.csv')
    duplicate_counts = Counter(tuple(row.items()) for row in final)
    manifests, review, candidate = [], [], []
    for number, row in enumerate(final, 2):
        flags, matches = [], []
        coord = coordinates(row)
        if not usable_address(row['address']): flags.append('invalid_or_missing_address')
        if not norm(row['region']): flags.append('missing_region')
        if coord is None or not (-90 <= coord[0] <= 90 and -180 <= coord[1] <= 180): flags.append('invalid_coordinates')
        elif not (33 <= coord[0] <= 39.5 and 124 <= coord[1] <= 132): flags.append('outside_korea_review_bounds')
        if duplicate_counts[tuple(row.items())] > 1: flags.append('exact_duplicate_payload')
        seen = set()
        for match in index.get(norm(row['address']), []):
            key = (match['source_file'], match['source_row'])
            if key in seen: continue
            seen.add(key)
            matches.append({k:v for k,v in match.items() if k not in {'coordinate','values','indoor'}} | {
                'description_exact_match': bool(norm(row['description']) and norm(row['description']) in match['values']),
                'coordinate_comparison': 'source_missing' if match['coordinate'] is None else ('exact' if match['coordinate'] == coord else 'different'),
                'explicit_indoor_conflict': match['indoor'] and row['type'] == '실외'})
        if not matches: flags.append('unmatched_address')
        else:
            if len(matches)>1: flags.append('multiple_source_row_correspondences')
            if all(m['stale_filename_date'] for m in matches): flags.append('all_matching_filename_dates_older_than_365_days')
            if not any(m['coordinate_comparison']=='exact' for m in matches): flags.append('final_coordinate_lineage_unproven')
            if any(m['coordinate_comparison']=='different' for m in matches): flags.append('source_coordinate_difference')
            if any(m['explicit_indoor_conflict'] for m in matches): flags.append('source_indoor_conflict')
        item = dict(final_csv_row=number, **row, flags=flags, exact_address_correspondences=matches,
                    actual_import_lineage='unknown', field_verified=False)
        manifests.append(item)
        flat = dict(final_csv_row=number, **row, flags=';'.join(flags), source_match_count=len(matches))
        review.append(flat)
        # Strict coordinate-backed review subset. Still NOT verified or publish-ready.
        blocking = set(flags)-{'missing_region','multiple_source_row_correspondences','all_matching_filename_dates_older_than_365_days'}
        if not blocking: candidate.append(flat)
    counts = Counter(flag for row in manifests for flag in row['flags'])
    summary = dict(as_of=str(as_of), stale_threshold_days=365, final_sha256=digest, final_rows=len(final),
        source_files=len(sources), source_rows=sum(s['row_count'] for s in sources),
        rows_with_exact_address_correspondence=sum(bool(r['exact_address_correspondences']) for r in manifests),
        flags=dict(sorted(counts.items())), coordinate_backed_review_candidate_rows=len(candidate),
        type_counts=dict(Counter(r['type'] for r in final)),
        shared_coordinate_groups=sum(n > 1 for c,n in Counter(coordinates(r) for r in final).items() if c is not None),
        exact_duplicate_payload_rows=counts.get('exact_duplicate_payload',0),
        invalid_coordinate_rows=counts.get('invalid_coordinates',0),
        outside_korea_review_bounds_rows=counts.get('outside_korea_review_bounds',0),
        missing_description_rows=sum(not norm(r['description']) for r in final), verified_rows=0)
    for name, value in [('sources.json',sources),('rows.json',manifests),('summary.json',summary)]:
        (output_dir/name).write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    fields=['final_csv_row',*final[0].keys(),'flags','source_match_count']
    write_csv(output_dir/'review-queue.csv', review, fields)
    write_csv(output_dir/'coordinate-backed-review-only.csv', candidate, fields)
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data-dir', type=Path, default=Path(__file__).resolve().parents[1]/'data')
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--as-of', type=dt.date.fromisoformat, required=True)
    parser.add_argument('--catalog', type=Path)
    args=parser.parse_args()
    print(json.dumps(audit(args.data_dir,args.output_dir,args.as_of,json.loads(args.catalog.read_text()) if args.catalog else None),ensure_ascii=False,indent=2))
