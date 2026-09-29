#!/usr/bin/env python3
"""Seeds a local CommerceX backend with a running-store catalog through the real admin APIs.

Usage: python3 infra/seed/seed_catalog.py [--api http://localhost:8080]
Uses the bootstrap admin from the `local` profile. Safe to re-run: existing slugs/codes are skipped.
"""
import argparse
import json
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

ADMIN = {"email": "admin@commercex.local", "password": "CommerceX-Admin-2026!"}

CATEGORIES = [
    ("Road running", "road", "Cushioned and responsive shoes for pavement miles."),
    ("Trail running", "trail", "Grip, protection and stability for dirt, rock and mud."),
    ("Track & spikes", "track", "Lightweight spikes and flats for the oval."),
    ("Apparel", "apparel", "Layers, shorts and socks that keep up."),
]

SIZES = ["40", "41", "42", "43", "44", "45"]

# name, category, price (minor), compare-at, colours, tags, short description, description, stock per variant
SHOES = [
    ("Tempo 3", "road", 14000, None, ["Track Blue", "Asphalt", "Chalk"], ["daily", "cushioned"],
     "A light daily trainer for easy days and steady tempos.",
     "Tempo 3 pairs a nitrogen-infused foam midsole with an engineered mesh upper that breathes on long runs. "
     "An 8 mm drop and a rocker profile keep your stride rolling from the first mile to the last.", 12),
    ("Longline Max", "road", 17500, None, ["Chalk", "Asphalt"], ["max-cushion", "long-run"],
     "Maximum cushioning for long runs and recovery days.",
     "Built for the miles that matter most. A 42 mm stack of soft foam, a wide base for stability and a "
     "padded heel collar that stays comfortable past the two-hour mark.", 8),
    ("Split Racer", "road", 22000, 25000, ["Signal Red", "Chalk"], ["race", "carbon-plate"],
     "A carbon-plated racer for race day.",
     "A full-length carbon plate, a firm-to-soft foam sandwich and a translucent mono-mesh upper. Split Racer "
     "is built for 5K to marathon efforts when every second counts.", 4),
    ("Cadence", "road", 11000, 13000, ["Navy", "Grey"], ["daily", "value"],
     "A dependable everyday trainer at a friendly price.",
     "Cadence covers everything from first 5Ks to weekly long runs with a balanced ride and a durable "
     "rubber outsole rated for 800 km.", 20),
    ("Ridgeline GTX", "trail", 16500, None, ["Moss", "Asphalt"], ["waterproof", "grip"],
     "Waterproof trail shoe with 5 mm lugs.",
     "A waterproof membrane keeps puddles out while 5 mm multi-directional lugs bite into mud and loose rock. "
     "A rock plate protects the forefoot on technical descents.", 10),
    ("Scree", "trail", 14500, None, ["Sand", "Signal Red"], ["technical", "grip"],
     "Nimble grip for technical mountain trails.",
     "Scree sits low to the ground for precise footing on rock and roots, with a sticky rubber compound that "
     "holds on wet stone.", 6),
    ("Fire Road", "trail", 13000, None, ["Grey", "Moss"], ["door-to-trail", "cushioned"],
     "Door-to-trail comfort for mixed surfaces.",
     "Road-shoe cushioning with a trail-ready outsole: ideal when your run starts on pavement and ends on "
     "gravel paths.", 2),
    ("Lane 1 Spike", "track", 12000, None, ["Track Blue", "Chalk"], ["spikes", "sprint"],
     "An aggressive spike for 100 m to 400 m.",
     "A stiff Pebax plate with seven removable pins and a snug knit upper for explosive starts and fast "
     "turnover on the bend.", 5),
    ("Miler Flat", "track", 10500, None, ["Asphalt", "Signal Red"], ["spikes", "middle-distance"],
     "A versatile spike for 800 m to 5,000 m.",
     "Enough cushioning for longer track races and a plate that stays snappy over the final lap.", 0),
]

APPAREL = [
    ("Splits Short 5\"", 5500, None, ["Asphalt", "Track Blue"], ["S", "M", "L", "XL"], ["shorts"],
     "Split-hem racing short with a zip pocket.",
     "A lightweight woven short with a built-in liner, a rear zip pocket that fits a phone and split hems for "
     "a full stride.", 15),
    ("Pacer Long Sleeve", 6500, None, ["Chalk", "Navy"], ["S", "M", "L", "XL"], ["tops", "cold-weather"],
     "A brushed long sleeve for cold morning miles.",
     "Soft brushed-back fabric, thumb loops and reflective details for early starts in the dark.", 10),
    ("Grip Sock 3-Pack", 2200, None, ["Chalk", "Asphalt"], ["S", "M", "L"], ["socks"],
     "Cushioned running socks with arch support.",
     "Targeted cushioning under the heel and forefoot, a supportive arch band and seamless toes.", 30),
]


def call(api, method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(api + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read()
        return e.code, (json.loads(raw) if raw else None)


def sku(name, *parts):
    base = "".join(ch for ch in name.upper() if ch.isalnum())[:8]
    return "-".join([base] + ["".join(ch for ch in p.upper() if ch.isalnum())[:5] for p in parts])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--api", default="http://localhost:8080")
    api = parser.parse_args().api

    status, auth = call(api, "POST", "/api/v1/auth/login", ADMIN)
    if status != 200:
        sys.exit(f"Admin login failed ({status}): {auth}")
    token = auth["accessToken"]

    existing_categories = {c["slug"]: c["id"] for c in call(api, "GET", "/api/v1/admin/categories", token=token)[1]}
    category_ids = {}
    for position, (name, slug, description) in enumerate(CATEGORIES):
        if slug in existing_categories:
            category_ids[slug] = existing_categories[slug]
            continue
        status, created = call(api, "POST", "/api/v1/admin/categories",
                               {"name": name, "slug": slug, "description": description, "position": position}, token)
        category_ids[slug] = created["id"]
        print("category", slug, status)

    brands = {b["slug"]: b["id"] for b in call(api, "GET", "/api/v1/admin/brands", token=token)[1]}
    if "commercex" not in brands:
        status, brand = call(api, "POST", "/api/v1/admin/brands", {"name": "CommerceX", "slug": "commercex"}, token)
        brands["commercex"] = brand["id"]
    brand_id = brands["commercex"]

    def create(name, category, price, compare, colours, sizes, tags, short, description, stock):
        variants = []
        for colour in colours:
            for size in sizes:
                v = {"sku": sku(name, colour, size), "options": {"Color": colour, "Size": size}, "price": price}
                if compare:
                    v["compareAtPrice"] = compare
                variants.append(v)
        body = {"name": name, "shortDescription": short, "description": description,
                "categoryId": category_ids[category], "brandId": brand_id, "options": ["Color", "Size"],
                "tags": tags, "variants": variants}
        status, product = call(api, "POST", "/api/v1/admin/products", body, token)
        if status == 409:
            print("exists  ", name)
            return
        if status != 201:
            sys.exit(f"Creating {name} failed ({status}): {product}")
        call(api, "POST", f"/api/v1/admin/products/{product['id']}/publish", token=token)
        for i, variant in enumerate(product["variants"]):
            qty = stock if i % 4 else max(stock // 3, 0)  # uneven stock so low-stock states appear
            if qty > 0:
                call(api, "POST", f"/api/v1/admin/inventory/{variant['id']}/adjustments",
                     {"type": "RESTOCK", "quantity": qty, "reason": "seed"}, token)
        print("product ", name, len(product["variants"]), "variants")

    for name, category, price, compare, colours, tags, short, description, stock in SHOES:
        create(name, category, price, compare, colours, SIZES, tags, short, description, stock)
    for name, price, compare, colours, sizes, tags, short, description, stock in APPAREL:
        create(name, "apparel", price, compare, colours, sizes, tags, short, description, stock)

    now = datetime.now(timezone.utc)
    status, _ = call(api, "POST", "/api/v1/admin/coupons", {
        "code": "FIRSTRUN", "description": "10% off your first order", "type": "PERCENTAGE", "value": 1000,
        "perCustomerLimit": 1, "startsAt": (now - timedelta(minutes=1)).isoformat(),
        "endsAt": (now + timedelta(days=365)).isoformat()}, token)
    print("coupon   FIRSTRUN", status)


if __name__ == "__main__":
    main()
