import csv
import datetime as dt
import json
from pathlib import Path
import tempfile
import unittest
from audit_smoking_sources import audit, coordinates, usable_address
from resolve_smoking_source_catalog import Links, key


class AuditTest(unittest.TestCase):
    def test_invalid_coordinates_and_addresses(self):
        self.assertIsNone(coordinates({'latitude':'NaN','longitude':'127'}))
        for value in ('', '실외', '12'):
            self.assertFalse(usable_address(value))

    def test_catalog_only_matches_exact_dataset_title(self):
        parser=Links()
        parser.feed("<a href='/data/123/fileData.do'><span>서울_흡연구역</span></a>")
        self.assertEqual(parser.links, {'123':'서울_흡연구역'})
        self.assertEqual(key('서울_흡연구역_20200101.csv'),key('서울 흡연구역'))
        self.assertNotEqual(key('서울 흡연시설'),key('서울 흡연구역'))

    def test_exact_evidence_only_and_indoor_conflict(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            with (root/'서울_흡연_20200101.csv').open('w',encoding='utf-8',newline='') as f:
                w=csv.writer(f);w.writerow(['주소','위도','경도','실외 실내']);w.writerows([['서울 길 1','37.5','127','실내'],['서울 길 1','37.5','127','실외'],['서울 길 1 2층','37.5','127','실외']])
            with (root/'final_zones_all.csv').open('w',encoding='utf-8',newline='') as f:
                w=csv.writer(f);w.writerow(['address','description','latitude','longitude','region','type','subtype']);w.writerows([['서울 길 1','','37.5','127','','실외','일반'],['서울 길 2','','37.500001','127','','실외','일반'],['서울 길 1 2층','','37.5','127','','실외','일반']])
            summary=audit(root,root/'out',dt.date(2026,9,7))
            rows=json.loads((root/'out/rows.json').read_text())
            self.assertIn('source_indoor_conflict',rows[0]['flags'])
            self.assertIn('multiple_source_row_correspondences',rows[0]['flags'])
            self.assertEqual(rows[1]['exact_address_correspondences'],[])
            self.assertNotIn('exact_duplicate_payload',rows[2]['flags'])
            self.assertEqual(summary['coordinate_backed_review_candidate_rows'],1)
            first=(root/'out/rows.json').read_bytes()
            audit(root,root/'out',dt.date(2026,9,7))
            self.assertEqual(first,(root/'out/rows.json').read_bytes())

if __name__ == '__main__': unittest.main()
