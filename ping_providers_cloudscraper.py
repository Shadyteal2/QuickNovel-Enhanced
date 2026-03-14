import cloudscraper
import urllib.error

providers = {
    "AllNovelProvider": "https://allnovel.org",
    "AnnasArchive": "https://annas-archive.org",
    "FreewebnovelProvider": "https://freewebnovel.com",
    "FanMtlnProvider": "https://fanmtl.com",
    "GraycityProvider": "https://graycity.net",
    "HiraethTranslationProvider": "https://hiraethtranslation.com",
    "IndoWebNovelProvider": "https://indowebnovel.id",
    "KolNovelProvider": "https://kolnovel.com",
    "LibReadProvider": "https://libread.com",
    "LightNovelTranslationsProvider": "https://lightnoveltranslations.com",
    "MeioNovelProvider": "https://meionovel.id",
    "MoreNovelProvider": "https://morenovel.net",
    "MtlNovelProvider": "https://www.mtlnovel.com",
    "NovelBinProvider": "https://novelbin.com",
    "NovelFullNETProvider": "https://novelfull.net",
    "NovelFullProvider": "https://novelfull.com",
    "NovelFireProvider": "https://novelfire.net",
    "NovelsOnlineProvider": "https://novelsonline.net",
    "NovLoveProvider": "https://novlove.com",
    "PawReadProver": "https://pawread.com",
    "ReadfromnetProvider": "https://readfrom.net",
    "ReadNovelFullProvider": "https://readnovelfull.com",
    "RoyalRoadProvider": "https://www.royalroad.com",
    "SakuraNovelProvider": "https://sakuranovel.id",
    "ScribblehubProvider": "https://www.scribblehub.com",
    "WtrLabProvider": "https://wtr-lab.com",
    "WuxiaBoxProvider": "https://www.wuxiabox.com"
}

scraper = cloudscraper.create_scraper(
    browser={
        'browser': 'chrome',
        'platform': 'windows',
        'desktop': True
    }
)

working = []
broken = []

print("Starting provider ping test with Cloudscraper...")
for name, url in providers.items():
    try:
        response = scraper.get(url, timeout=15)
        status = response.status_code
        if status == 200:
            print(f"[OK] {name} ({url}) is UP")
            working.append(name)
        elif status in [403, 503]:
            # If still getting 403/503 even with cloudscraper, it might be heavily protected or actually down
            print(f"[WARN] {name} ({url}) returned {status} (Cloudflare block or down)")
            # We'll consider it broken for the sake of the script if cloudscraper fails to bypass
            broken.append(name)
        else:
            print(f"[FAIL] {name} ({url}) returned status {status}")
            broken.append(name)
    except Exception as e:
        print(f"[FAIL] {name} ({url}) failed: {e}")
        broken.append(name)

print("\n--- RESULTS ---")
print(f"Working ({len(working)}): {', '.join(working)}")
print(f"Broken/Blocked ({len(broken)}): {', '.join(broken)}")
