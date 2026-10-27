import unittest

import geocode_centres as geo

KERALA, KARNATAKA = "13", "19"


def gazetteer():
    places = [
        # (state, names, lat, lon, population, district code, feature)
        (KARNATAKA, {"bengaluru"}, 12.9719, 77.5937, 8_000_000, "572", "PPLA"),
        (KARNATAKA, {"koramangala"}, 12.9279, 77.6271, 0, "572", "PPLX"),
        (KARNATAKA, {"agara"}, 12.9246, 77.6400, 0, "572", "PPLX"),
        (KARNATAKA, {"koramangala"}, 13.2928, 77.7516, 0, "581", "PPL"),   # a same-named village far away
        (KERALA, {"attingal"}, 8.6964, 76.8150, 37_000, "601", "PPL"),
        (KERALA, {"santhi"}, 11.2000, 75.8000, 0, "603", "PPL"),            # a village sharing a centre-name word
        (KERALA, {"santhi"}, 9.9000, 76.3000, 0, "598", "PPL"),
    ]
    districts = [(KERALA, {"thiruvananthapuram"}, 8.52, 76.94, "601"), (KARNATAKA, {"bengaluru"}, 13.0, 77.58, "572")]
    offices = [("560034", "Koramangala", KARNATAKA, 13.2257, 77.575), ("560034", "Agara", KARNATAKA, 13.2257, 77.575),
               ("560034", "St. John's Medical College", KARNATAKA, 13.2257, 77.575)]
    return geo.Gazetteer(places, districts, offices, {"kerala": KERALA, "karnataka": KARNATAKA})


def centre(address, state="Karnataka", district="", **extra):
    return {"id": "x", "name": "x", "address": address, "state": state, "district": district, "city": "", **extra}


class LocateTest(unittest.TestCase):
    def setUp(self):
        self.gaz = gazetteer()

    def test_existing_exact_coordinates_are_kept(self):
        row = centre("Anything, Bengaluru 560034", latitude=13.05, longitude=77.59)
        self.assertEqual(geo.locate(row, self.gaz), [13.05, 77.59, "exact"])

    def test_pin_uses_its_post_offices_in_the_gazetteer_not_the_postal_coordinates(self):
        lat, lon, precision = geo.locate(centre("Clinic, Sarjapur Road, Bengaluru 560 034"), self.gaz)
        self.assertEqual(precision, "pincode")
        self.assertLess(geo.km((lat, lon), (12.927, 77.633)), 2)   # Koramangala/Agara, not the far village

    def test_town_named_in_address_when_there_is_no_pin(self):
        self.assertEqual(geo.locate(centre("Clinic, CSI Mission Hospital, Attingal, Trivandrum Kerala", "Kerala"), self.gaz),
                         [8.6964, 76.815, "city"])

    def test_generic_parts_are_not_read_as_a_town(self):
        # "Bengaluru Road" names a road, so it must not place the centre in Bengaluru.
        self.assertIsNone(geo.locate(centre("Clinic, Bengaluru Road, Somewhere", "Karnataka"), self.gaz))

    def test_small_place_from_the_centre_name_is_ignored(self):
        self.assertIsNone(geo.locate(centre("Santhi Palliative Care, Kerala", "Kerala"), self.gaz))

    def test_listed_district_when_no_town_matches(self):
        self.assertEqual(geo.locate(centre("Clinic, Unknownpuram", "Kerala", district="Trivandrum"), self.gaz),
                         [8.52, 76.94, "district"])

    def test_no_state_level_guess(self):
        self.assertIsNone(geo.locate(centre("Some Hospital, Kerala", "Kerala"), self.gaz))
        self.assertIsNone(geo.locate(centre("Clinic, Bengaluru", "Atlantis"), self.gaz))

    def test_pin_in_another_state_is_ignored(self):
        self.assertIsNone(self.gaz.pincode(KERALA, "560034"))

    def test_aliases_match_both_spellings(self):
        self.assertEqual(geo.key("Bangalore"), geo.key("Bengaluru"))
        self.assertEqual(geo.key("Trivandrum"), "thiruvananthapuram")


if __name__ == "__main__":
    unittest.main()
